package top.mcfpp.test

import top.mcfpp.analysis.CompilerValue
import top.mcfpp.Project
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.SpecializationPolicy
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TypeCompositionTest {
    private val integer get() = MCFPPBaseType.Int
    private fun field(name: String, type: MCFPPType = integer, optional: Boolean = false) =
        ObjectFieldContract(name, type, optional)
    private fun shape(vararg fields: ObjectFieldContract) = MCFPPObjectType(fields.toList())
    private fun literal(value: Long) = MCFPPLiteralType(integer, CompilerValue.Integral(value))
    private fun compileSource(source: String) {
        val diagnostics = top.mcfpp.test.util.compilerDiagnostics {
            MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
        }
        assertEquals(0, Project.errorCount, diagnostics.joinToString("\n"))
    }

    @Test fun objectIdentityUsesSortedFieldsTypesAndPresenceOnly() {
        val first = shape(field("x"), field("label", MCFPPBaseType.String, true))
        val second = shape(ObjectFieldContract("label", MCFPPBaseType.String, true, ObjectMemberKind.CONST,
            setOf(TypeId.Declaration("template", "example", "Origin"))), field("x"))
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(listOf("label", "x"), first.fields.map { it.name })
        assertNotEquals(first, shape(field("x"), field("label", MCFPPBaseType.String)))
    }

    @Test fun literalIdentityKeepsPrimitiveAndValueWithoutMutablePayloads() {
        val one = literal(1)
        assertEquals(one, MCFPPLiteralType(integer, CompilerValue.Typed(integer.typeId, CompilerValue.Integral(1))))
        assertNotEquals(one, literal(2))
        assertNotEquals(one, MCFPPLiteralType(MCFPPNBTType.Byte, CompilerValue.Integral(1)))
        assertTrue(one.isSubOf(integer))
        assertFalse(integer.isSubOf(one))
        assertFalse(one.isSubOf(literal(2)))
        assertEquals(TypeRelations.Conversion.INT_TO_FLOAT, TypeRelations.resolveImplicitConversion(one, MCFPPBaseType.Float))
        assertNull(TypeCompositions.literalOrNull(integer, CompilerValue.Text("1")))
    }

    @Test fun openObjectSubtypingChecksWidthPresenceAndFieldContracts() {
        val required = shape(field("x"))
        val optional = shape(field("x", optional = true))
        val absent = shape()
        assertTrue(shape(field("x"), field("y")).isSubOf(required))
        assertTrue(required.isSubOf(optional))
        assertFalse(optional.isSubOf(required))
        assertFalse(absent.isSubOf(required))
        assertTrue(absent.isSubOf(optional))
        assertFalse(shape(field("x", MCFPPBaseType.Bool)).isSubOf(optional))
        assertTrue(shape(field("x", literal(1))).isSubOf(required))
    }

    @Test fun mutableCollectionInferenceWidensNestedSingletonFields() {
        val entry = shape(field("value", literal(2)),
            ObjectFieldContract("fixed", literal(1), kind = ObjectMemberKind.CONST))
        for (collection in listOf(MCFPPListType(entry), MCFPPDictType(entry), MCFPPMapType(entry))) {
            val widened = TypeCompositions.widen(collection) as MCFPPTypeWithGeneric
            val element = assertIs<MCFPPObjectType>(widened.generic.single())
            assertEquals(integer, element.fieldsByName.getValue("value").type)
            assertEquals(literal(1), element.fieldsByName.getValue("fixed").type)
        }
        val immutable = MCFPPImmutableListType(entry)
        assertSame(immutable, TypeCompositions.widen(immutable))
    }

    @Test fun deferredIntersectionsUseTheCompletedOperandContracts() {
        val first = DataTemplate("DeferredFirst", "test.compositions")
        val second = DataTemplate("DeferredSecond", "test.compositions")
        second.addMember(MCInt("y").apply { declaredParentTemplate = second })
        val firstIdentity = first.getType().typeId
        val deferred = TypeCompositions.deferIntersection(listOf(first.getType(), second.getType()))
        val declaration = deferred.buildUnConcrete("pair")
        assertSame(deferred, declaration.type)
        assertFalse(declaration.isError)
        assertIs<MCFPPUnionType>(TypeCompositions.union(deferred, second.getType()))
        val deferredUnion = TypeCompositions.deferUnion(listOf(first.getType(), second.getType()))
        first.addMember(MCInt("x").apply { declaredParentTemplate = first })
        assertEquals(2, assertIs<MCFPPUnionType>(TypeCompositions.normalizeResolved(deferredUnion)).types.size)
        val outer = shape(field("pair", deferred))
        assertTrue(TypeCompositions.hasDeferredComposition(outer))
        val normalized = assertIs<MCFPPObjectType>(TypeCompositions.normalizeResolved(outer))
        val pair = assertIs<MCFPPObjectType>(normalized.fieldsByName.getValue("pair").type)
        assertEquals(listOf("x", "y"), pair.fields.map { it.name })
        assertEquals(firstIdentity, first.getType().typeId)
        assertSame(deferred, outer.fieldsByName.getValue("pair").type)
    }

    @Test fun intersectionsMergeFieldsIndependentlyOfOperandOrderAndGrouping() {
        val x = shape(field("x"))
        val y = shape(field("y"))
        val z = shape(field("z", MCFPPBaseType.String))
        val expected = shape(field("x"), field("y"), field("z", MCFPPBaseType.String))
        assertEquals(expected, TypeCompositions.intersection(x, y, z))
        assertEquals(expected, TypeCompositions.intersection(z, TypeCompositions.intersection(y, x)))
        assertEquals(x, TypeCompositions.intersection(x, x))
        assertEquals(literal(1), TypeCompositions.intersection(integer, literal(1)))
        assertSame(MCFPPNeverType, TypeCompositions.intersection(literal(1), literal(2)))
        assertSame(MCFPPNeverType, TypeCompositions.intersection(integer, MCFPPBaseType.Bool))
    }

    @Test fun requiredConflictsAreBottomWhileOptionalConflictsAllowOnlyAbsence() {
        val intField = shape(field("x"))
        val boolField = shape(field("x", MCFPPBaseType.Bool))
        assertSame(MCFPPNeverType, TypeCompositions.intersection(intField, boolField))
        val optionalNever = assertIs<MCFPPObjectType>(TypeCompositions.intersection(
            shape(field("x", optional = true)), shape(field("x", MCFPPBaseType.Bool, true))))
        assertTrue(optionalNever.fieldsByName.getValue("x").optional)
        assertSame(MCFPPNeverType, optionalNever.fieldsByName.getValue("x").type)
        assertTrue(shape().isSubOf(optionalNever))
        assertFalse(intField.isSubOf(optionalNever))
        assertTrue(optionalNever.isSubOf(shape(field("x", MCFPPBaseType.String, true))))
        assertFalse(optionalNever.isSubOf(shape(field("x"))))
    }

    @Test fun intersectionRequirednessIsLogicalOr() {
        val merged = assertIs<MCFPPObjectType>(TypeCompositions.intersection(
            shape(field("x", optional = true)), shape(field("x", literal(1)))))
        assertFalse(merged.fieldsByName.getValue("x").optional)
        assertEquals(literal(1), merged.fieldsByName.getValue("x").type)
    }

    @Test fun unionsFlattenSortDeduplicateAndRemoveSubsumedAlternatives() {
        val alternatives = TypeCompositions.union(MCFPPBaseType.String, integer)
        assertEquals(alternatives, TypeCompositions.union(integer, alternatives, MCFPPBaseType.String))
        assertEquals(integer, TypeCompositions.union(literal(1), integer, MCFPPNeverType))
        assertEquals(integer, TypeCompositions.union(integer, integer))
        assertSame(MCFPPNeverType, TypeCompositions.union())
        assertTrue(literal(1).isSubOf(TypeCompositions.union(literal(1), MCFPPBaseType.String)))
        assertEquals(shape(field("x")), TypeCompositions.union(shape(field("x"), field("y")), shape(field("x"))))
    }

    @Test fun equivalentObjectUnionBranchesIntersectTheirAccessCapabilities() {
        val getter = shape(ObjectFieldContract("x", integer, writable = false))
        val setter = shape(ObjectFieldContract("x", integer, readable = false))
        assertEquals(getter.typeId, setter.typeId, "Access declarations do not change the data identity")
        val merged = assertIs<MCFPPObjectType>(TypeCompositions.union(getter, setter))
        val contract = merged.fieldsByName.getValue("x")
        assertFalse(contract.readable)
        assertFalse(contract.writable)
        val property = merged.templateForRuntime().scope.getProperty("x")!!
        assertNull(property.accessor)
        assertNull(property.mutator)
        assertEquals(merged, TypeCompositions.union(setter, getter))
    }

    @Test fun nominalUnionSubsumptionRetainsIncompatibleAccessorCapabilities() {
        fun template(name: String, readable: Boolean, writable: Boolean): MCFPPDataTemplateType {
            val owner = DataTemplate(name, "composition.access")
            owner.scope.putVar("x", MCInt("x"))
            owner.scope.putProperty("x", top.mcfpp.model.property.Property("x",
                if (readable) top.mcfpp.model.property.SimpleAccessor() else null,
                if (writable) top.mcfpp.model.property.SimpleMutator() else null))
            return owner.getType()
        }
        val getter = template("Getter", readable = true, writable = false)
        val setter = template("Setter", readable = false, writable = true)
        assertTrue(getter.isSubOf(setter))
        val getterShape = TypeCompositions.objectShape(getter)!!.fieldsByName.getValue("x")
        assertTrue(getterShape.readable)
        assertFalse(getterShape.writable)
        val union = assertIs<MCFPPUnionType>(TypeCompositions.union(getter, setter))
        assertEquals(2, union.types.size)
        assertEquals(union, TypeCompositions.union(setter, getter))
    }

    @Test fun intersectionNarrowingPreservesRestrictedAccess() {
        val readonly = shape(ObjectFieldContract("x", integer, writable = false))
        val precise = shape(field("x", literal(1)))
        val result = assertIs<MCFPPObjectType>(TypeCompositions.intersection(readonly, precise))
        assertEquals(literal(1), result.fieldsByName.getValue("x").type)
        assertFalse(result.fieldsByName.getValue("x").writable)
        assertFalse(assertIs<MCFPPObjectType>(TypeCompositions.widen(result)).fieldsByName.getValue("x").writable)
    }

    @Test fun nominalTemplatesProjectToShapesWithoutChangingDeclarationIdentity() {
        val first = DataTemplate("First", "composition.test")
        val second = DataTemplate("Second", "composition.test")
        first.scope.putVar("x", MCInt("x"))
        second.scope.putVar("x", MCInt("x"))
        val left = first.getType()
        val right = second.getType()
        assertNotEquals(left, right)
        assertEquals(TypeCompositions.objectShape(left), TypeCompositions.objectShape(right))
        assertTrue(left.isSubOf(right))
        assertFalse(TypeRelations.isNominalSubtype(left, right))
        assertEquals(TypeId.Declaration("template", "composition.test", "First"), left.typeId)
    }

    @Test fun nominalConstProjectionUsesItsDeclarationLiteral() {
        val template = DataTemplate("Const", "composition.test")
        template.scope.putVar("x", MCInt("x").apply {
            isConst = true
            declarationConstant = CompilerValue.Typed(integer.typeId, CompilerValue.Integral(7))
        })
        val projected = TypeCompositions.objectShape(template.getType())!!.fieldsByName.getValue("x")
        assertEquals(ObjectMemberKind.CONST, projected.kind)
        assertEquals(literal(7), projected.type)
    }

    @Test fun mutableInferenceWidensOrdinaryFieldsAndPreservesConstContracts() {
        val precise = shape(field("x", literal(1)),
            ObjectFieldContract("tag", literal(2), kind = ObjectMemberKind.CONST))
        val widened = assertIs<MCFPPObjectType>(TypeCompositions.widen(precise))
        assertEquals(integer, widened.fieldsByName.getValue("x").type)
        assertEquals(literal(2), widened.fieldsByName.getValue("tag").type)
    }

    @Test fun recursiveObjectNormalizationRetainsFiniteComponentIdentities() {
        val left = DataTemplate("RecursiveLeft", "composition.test")
        val right = DataTemplate("RecursiveRight", "composition.test")
        val leftType = left.getType()
        val rightType = right.getType()
        left.scope.putVar("next", leftType.buildUnConcrete("next"))
        left.scope.putVar("x", MCInt("x"))
        right.scope.putVar("next", rightType.buildUnConcrete("next"))
        right.scope.putVar("y", MCInt("y"))
        val result = assertIs<MCFPPObjectType>(TypeCompositions.intersection(leftType, rightType))
        assertEquals(listOf("next", "x", "y"), result.fields.map { it.name })
        assertIs<MCFPPIntersectionType>(result.fieldsByName.getValue("next").type)
        assertEquals(result, TypeCompositions.intersection(rightType, leftType))
        assertTrue(result.isSubOf(leftType))
        assertTrue(result.isSubOf(rightType))
    }

    @Test fun compositionIdentityRestoresThroughTheExistingIdentityGraph() {
        val original = shape(field("x", literal(1)), field("optional", MCFPPNeverType, true))
        fun resolve(id: TypeId): MCFPPType? = TypeCompositions.restore(id, ::resolve)
            ?: listOf(integer, MCFPPBaseType.String).firstOrNull { it.typeId == id }
        assertEquals(original, resolve(original.typeId))
        assertTrue(TypeCompositions.isCompositionIdentity(original.typeId))
        assertEquals(MCFPPNeverType, resolve(MCFPPNeverType.typeId))
    }

    @Test fun genericBindingRecursesThroughObjectFieldsAndCompositionOperands() {
        val parameter = MCFPPGenericParamType("T", arrayListOf())
        val original = shape(field("value", parameter))
        val bound = assertIs<MCFPPObjectType>(SpecializationPolicy.bind(original, mapOf("T" to integer)))
        assertEquals(integer, bound.fieldsByName.getValue("value").type)
        assertEquals(integer, SpecializationPolicy.bind(MCFPPUnionType(parameter, integer), mapOf("T" to integer)))
    }

    @Test fun duplicateGenericObjectSignaturesIgnoreReadonlyFormalNames() {
        fun declaration(formalName: String): GenericFunction {
            val parser = top.mcfpp.antlr.mcfppParser(org.antlr.v4.runtime.CommonTokenStream(
                top.mcfpp.antlr.mcfppLexer(org.antlr.v4.runtime.CharStreams.fromString("{}"))))
            val function = GenericFunction("duplicate", ctx = parser.curlBlock())
            function.readOnlyParams.add(FunctionParam(MCFPPConcreteType.Type, formalName, function, isReadOnly = true))
            function.appendNormalParam(shape(field("value", MCFPPGenericParamType(formalName, arrayListOf()))), "argument")
            return function
        }
        assertTrue(ParameterMatcher.sameSignature(declaration("T"), declaration("Element")))
    }

    @Test fun sourceObjectContractsWorkInVariablesArgumentsAndReturnTypes() {
        compileSource("""
            func read(value: {x: int}): int { return value.x; }
            func produce(): {x: int, y: int} { return {x: 3, y: 4}; }
            func main(){
                var point: {x: int} = produce()
                external result = read(point)
            }
        """)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertEquals(3, top.mcfpp.test.util.executePublishedFunctions(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun objectLiteralsBoxIntoAnyForVariablesArgumentsAndReturns() {
        compileSource("""
            func read(value: any): int { return (value as {x: int}).x; }
            func produce(): any { return {x: 7}; }
            func main(){
                var erased: any = {x: 3}
                external result = read(erased) + read({x: 5}) + read(produce())
            }
        """)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertEquals(15, top.mcfpp.test.util.executePublishedFunctions(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun sourceGenericObjectContractsBindTheirEnclosingTypeParameter() {
        compileSource("""
            func echo<T>(value: {x: T}): {x: T} { return value; }
            func main(){
                var echoed = echo<int>({x: 9})
                external result = echoed.x
            }
        """)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertEquals(9, top.mcfpp.test.util.executePublishedFunctions(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun sourceGenericFactoryAcceptsAnObjectTypeAsItsReadonlyArgument() {
        compileSource("""
            data Box<T> {
                var value: T
                new(value: T) { return {value}; }
            }
            func main(){
                var box = Box<{x: int}>({x: 3})
                external result = box.value.x
            }
        """)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val box = main.scope.getVar("box")!!
        val self = assertIs<MCFPPDataTemplateType>(box.type)
        assertEquals(shape(field("x")), TypeCompositions.objectShape(self)!!.fieldsByName.getValue("value").type)
        assertEquals(3, top.mcfpp.test.util.executePublishedFunctions(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun emptyObjectTypeUsesTheReadonlyTypeFormalContext() {
        compileSource("""
            data Box<T> {
                var value: T
                new(value: T) { return {value}; }
            }
            func main(){
                var box = Box<{}>({})
                var copy: {} = box.value
            }
        """)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val self = assertIs<MCFPPDataTemplateType>(main.scope.getVar("box")!!.type)
        assertEquals(shape(), TypeCompositions.objectShape(self)!!.fieldsByName.getValue("value").type)
        assertEquals(shape(), main.scope.getVar("copy")!!.type)
    }

    @Test fun sourceIntersectionContractsShareTheCanonicalFieldMerger() {
        compileSource("""
            func read(value: ({x: int} & {y: int})): int { return value.x + value.y; }
            func main(){ external result = read({x: 3, y: 4, extra: 5}); }
        """)
        val read = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("read").single()
        assertEquals(shape(field("x"), field("y")), read.normalParams.single().type)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertEquals(7, top.mcfpp.test.util.executePublishedFunctions(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun sourceStructuralOverloadsPreferStrictlyNarrowerContracts() {
        compileSource("""
            func choose(value: {x: int}): int { return 1; }
            func choose(value: {x: int, y: int}): int { return 2; }
            func main(){ external result = choose({x: 3, y: 4}); }
        """)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertEquals(2, top.mcfpp.test.util.executePublishedFunctions(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun structurallyEquivalentNominalOverloadsRemainAmbiguous() {
        val diagnostics = top.mcfpp.test.util.compilerDiagnostics {
            MCFPPStringTest.readFromString("""
                data First { var x: int }
                data Second { var x: int }
                func choose(value: First): int { return 1; }
                func choose(value: Second): int { return 2; }
                func main(){ var result = choose({x: 3}); }
            """.trimIndent(), version = "26.3")
        }
        assertTrue(Project.errorCount > 0)
        assertTrue(diagnostics.any { "Ambiguous overload" in it }, diagnostics.joinToString("\n"))
    }
}
