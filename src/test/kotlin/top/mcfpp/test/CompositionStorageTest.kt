package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.PropertyVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.Member
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.Property
import top.mcfpp.model.property.SimpleAccessor
import top.mcfpp.model.property.SimpleMutator
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.*
import kotlin.test.*
import kotlin.test.Test

class CompositionStorageTest {
    private fun compile(source: String = "func main(){}") : Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
            .also { Function.currFunction = it }
    }

    private fun objectValue(type: MCFPPType, fields: Map<String, Var<*>>, name: String): Var<*> =
        StorageAccess.dictionaryLiteral(type, fields, name).also { it.bindDeclaration() }

    private fun field(value: Var<*>, name: String): PropertyVar =
        assertIs(value.getMemberVar(name, Member.AccessModifier.PUBLIC).first)

    private fun propertyTemplate(name: String, readable: Boolean, writable: Boolean): DataTemplate =
        DataTemplate(name, "fixture.access").also { template ->
            val field = MCInt("x").apply { isStatic = false; declaredParentTemplate = template }
            template.scope.putVar("x", field)
            template.scope.putProperty("x", Property("x", if (readable) SimpleAccessor() else null,
                if (writable) SimpleMutator() else null).apply {
                isStatic = false
                declaredParentTemplate = template
            })
        }

    @Test fun constantsKeepLiteralTypesWhileArithmeticAndMutableCopiesUsePrimitives() {
        val main = compile("""
            func asFloat(value: float): float { return value; }
            func main(){
                const count = 2 + 3
                const label = "hi"
                const enabled = true
                var copy = count
                copy = 7
                external result = count + copy
                external promoted = asFloat(count)
            }
        """)
        val count = assertIs<MCFPPLiteralType>(main.scope.getVar("count")!!.type)
        assertEquals(MCFPPBaseType.Int, count.primitive)
        assertEquals(CompilerValue.Integral(5), count.value)
        assertEquals(CompilerValue.Text("hi"), assertIs<MCFPPLiteralType>(main.scope.getVar("label")!!.type).value)
        assertEquals(CompilerValue.Bool(true), assertIs<MCFPPLiteralType>(main.scope.getVar("enabled")!!.type).value)
        assertEquals(MCFPPBaseType.Int, main.scope.getVar("copy")!!.type)
        val machine = top.mcfpp.test.util.executePublishedFunctions(main)
        assertEquals(12, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(MCFPPBaseType.Float, main.scope.getVar("promoted")!!.type)
    }

    @Test fun widenedSharedViewRetainsTheOriginalFieldConstraint() {
        compile()
        val one = MCFPPLiteralType(MCFPPBaseType.Int, CompilerValue.Integral(1))
        val narrow = MCFPPObjectType(listOf(ObjectFieldContract("x", one)))
        val broad = MCFPPObjectType(listOf(ObjectFieldContract("x", MCFPPBaseType.Int)))
        val original = objectValue(narrow, mapOf("x" to StorageAccess.literal(one, one.value)), "original")
        val access = field(StorageAccess.view(original, broad, diagnose = false), "x")
        val before = assertNotNull(StorageAccess.snapshot(original))
        assertTrue(StorageAccess.write(access.field, MCInt(2)).isError)
        assertEquals(before, StorageAccess.snapshot(original))
        assertTrue(Project.errorCount > 0)
    }

    @Test fun reinterpretationCannotWriteAnIncompatiblePrimitiveField() {
        compile()
        val integer = MCFPPObjectType(listOf(ObjectFieldContract("x", MCFPPBaseType.Int)))
        val boolean = MCFPPObjectType(listOf(ObjectFieldContract("x", MCFPPBaseType.Bool)))
        val original = objectValue(integer, mapOf("x" to MCInt(1)), "original")
        val access = field(StorageAccess.view(original, boolean, diagnose = false), "x")
        val before = assertNotNull(StorageAccess.snapshot(original))
        assertTrue(StorageAccess.write(access.field, top.mcfpp.core.lang.bool.ScoreBool(true)).isError)
        assertEquals(before, StorageAccess.snapshot(original))
    }

    @Test fun independentWidenedCopyCanChangeOrdinaryLiteralFields() {
        compile()
        val one = MCFPPLiteralType(MCFPPBaseType.Int, CompilerValue.Integral(1))
        val narrow = MCFPPObjectType(listOf(ObjectFieldContract("x", one)))
        val broad = MCFPPObjectType(listOf(ObjectFieldContract("x", MCFPPBaseType.Int)))
        val original = objectValue(narrow, mapOf("x" to StorageAccess.literal(one, one.value)), "original")
        val copy = broad.buildUnConcrete("copy")
        assertFalse(StorageAccess.write(copy, original).isError)
        assertNotSame(StorageAccess.ensure(original).data, StorageAccess.ensure(copy).data)
        assertEquals(broad.typeId, StorageAccess.actualType(copy).typeId)
        assertFalse(StorageAccess.write(field(copy, "x").field, MCInt(2)).isError)
        assertEquals(broad.typeId, StorageAccess.actualType(copy).typeId)
        assertEquals(CompilerValue.Integral(1), assertIs<CompilerValue.Typed>(StorageAccess.snapshot(field(original, "x").get())).payload)
        assertEquals(CompilerValue.Integral(2), assertIs<CompilerValue.Typed>(StorageAccess.snapshot(field(copy, "x").get())).payload)
        assertEquals(0, Project.errorCount)
    }

    @Test fun aPrimitiveViewOfAMutableCopyCanReplaceItsPreviousLiteralValue() {
        compile()
        val constant = MCInt("constant").apply { isConst = true; bindDeclaration() }
        assertFalse(StorageAccess.write(constant, MCInt(5)).isError)
        val copy = MCInt("copy")
        assertFalse(StorageAccess.write(copy, constant).isError)
        assertIs<MCFPPLiteralType>(StorageAccess.actualType(copy))
        val view = StorageAccess.view(copy, MCFPPBaseType.Int, diagnose = false)
        assertFalse(StorageAccess.write(view, MCInt(6)).isError)
        assertEquals(CompilerValue.Integral(6), assertIs<CompilerValue.Typed>(StorageAccess.snapshot(copy)).payload)
        assertEquals(CompilerValue.Integral(5), assertIs<CompilerValue.Typed>(StorageAccess.snapshot(constant)).payload)
        assertEquals(0, Project.errorCount)
    }

    @Test fun unionLookupUsesAllAlternativesAndWritesSatisfyEveryFieldType() {
        compile()
        val one = MCFPPLiteralType(MCFPPBaseType.Int, CompilerValue.Integral(1))
        val left = MCFPPObjectType(listOf(ObjectFieldContract("x", one), ObjectFieldContract("left", MCFPPBaseType.Int)))
        val right = MCFPPObjectType(listOf(ObjectFieldContract("x", MCFPPBaseType.Int), ObjectFieldContract("right", MCFPPBaseType.Int)))
        val original = objectValue(left, mapOf("x" to StorageAccess.literal(one, one.value), "left" to MCInt(3)), "original")
        val union = StorageAccess.view(original, TypeCompositions.union(left, right), diagnose = false)
        assertNull(union.getMemberVar("left", Member.AccessModifier.PUBLIC).first)
        val common = field(union, "x")
        assertEquals(MCFPPBaseType.Int, common.type)
        assertNotNull(StorageAccess.snapshot(common.get()))
        assertFalse(StorageAccess.write(common.field, MCInt(1)).isError)
        val before = StorageAccess.snapshot(original)
        assertTrue(StorageAccess.write(common.field, MCInt(2)).isError)
        assertEquals(before, StorageAccess.snapshot(original))
    }

    @Test fun optionalViewCannotDeleteARequiredStorageField() {
        compile()
        val required = MCFPPObjectType(listOf(ObjectFieldContract("x", MCFPPBaseType.Int)))
        val optional = MCFPPObjectType(listOf(ObjectFieldContract("x", MCFPPBaseType.Int, optional = true)))
        val original = objectValue(required, mapOf("x" to MCInt(1)), "original")
        val access = field(StorageAccess.view(original, optional, diagnose = false), "x")
        val before = StorageAccess.snapshot(original)
        StorageAccess.removeField(access)
        assertEquals(before, StorageAccess.snapshot(original))
        assertTrue(Project.errorCount > 0)
    }

    @Test fun aMutableViewCannotExposeAnActualConstantForWriting() {
        compile()
        val one = MCFPPLiteralType(MCFPPBaseType.Int, CompilerValue.Integral(1))
        val fixed = MCFPPObjectType(listOf(ObjectFieldContract("x", one, kind = ObjectMemberKind.CONST)))
        val mutable = MCFPPObjectType(listOf(ObjectFieldContract("x", MCFPPBaseType.Int)))
        val original = objectValue(fixed, mapOf("x" to StorageAccess.literal(one, one.value)), "original")
        val access = field(StorageAccess.view(original, mutable, diagnose = false), "x")
        assertFalse(StorageAccess.canWrite(access.field))
        assertEquals(one.value, assertIs<CompilerValue.Typed>(StorageAccess.snapshot(field(original, "x").get())).payload)
    }

    @Test fun callEffectsRespectTheContractOfEachAffectedSharedField() {
        compile()
        val one = MCFPPLiteralType(MCFPPBaseType.Int, CompilerValue.Integral(1))
        val narrow = MCFPPObjectType(listOf(ObjectFieldContract("x", one), ObjectFieldContract("y", MCFPPBaseType.Int)))
        val broad = MCFPPObjectType(listOf(ObjectFieldContract("x", MCFPPBaseType.Int), ObjectFieldContract("y", MCFPPBaseType.Int)))
        val original = objectValue(narrow, mapOf("x" to StorageAccess.literal(one, one.value), "y" to MCInt(3)), "original")
        val view = StorageAccess.view(original, broad, diagnose = false)
        val place = StorageAccess.ensure(view).place
        assertTrue(StorageAccess.checkCallWrites("changeY", Effect.Writes(setOf(place.field("y"))), listOf(view)))
        assertEquals(0, Project.errorCount)
        assertFalse(StorageAccess.checkCallWrites("changeX", Effect.Writes(setOf(place.field("x"))), listOf(view)))
        assertTrue(Project.errorCount > 0)
    }

    @Test fun unionCannotReadASetterOnlyBackedProperty() {
        compile()
        val setterOnly = propertyTemplate("SetterOnly", readable = false, writable = true).getType()
        val getterOnly = propertyTemplate("GetterOnly", readable = true, writable = false).getType()
        val original = objectValue(getterOnly, mapOf("x" to MCInt(1)), "original")
        val union = StorageAccess.view(original, TypeCompositions.union(setterOnly, getterOnly), diagnose = false)
        val common = field(union, "x")
        assertNull(common.property.accessor)
        assertTrue(StorageAccess.read(common.field).isError)
        assertTrue(Project.errorCount > 0)
    }

    @Test fun aWritableSharedViewCannotReplaceAGetterOnlySourceProperty() {
        compile()
        val source = propertyTemplate("GetterOnly", readable = true, writable = false).getType()
        val writable = MCFPPObjectType(listOf(ObjectFieldContract("x", MCFPPBaseType.Int)))
        val original = objectValue(source, mapOf("x" to MCInt(1)), "original")
        val view = StorageAccess.view(original, writable, diagnose = false)
        val access = field(view, "x")
        val before = StorageAccess.snapshot(original)
        assertTrue(StorageAccess.write(access.field, MCInt(2)).isError)
        val place = StorageAccess.ensure(view).place
        assertFalse(StorageAccess.checkCallWrites("replaceX", Effect.Writes(setOf(place.field("x"))), listOf(view)))
        assertFalse(StorageAccess.checkCallWrites("replaceContents", Effect.Writes(setOf(place)), listOf(view)))
        assertEquals(before, StorageAccess.snapshot(original))
        assertEquals(CompilerValue.Integral(1), assertIs<CompilerValue.Typed>(StorageAccess.snapshot(field(original, "x").get())).payload)
    }

    @Test fun knownExtraFieldsCannotBeRetaggedAsIncompatibleOptionalFields() {
        compile()
        val public = MCFPPObjectType(listOf(ObjectFieldContract("x", MCFPPBaseType.Int)))
        val targetType = MCFPPObjectType(listOf(ObjectFieldContract("x", MCFPPBaseType.Int),
            ObjectFieldContract("y", MCFPPBaseType.Int, optional = true)))
        val absent = objectValue(public, mapOf("x" to MCInt(1)), "absent")
        val validTarget = targetType.buildUnConcrete("validTarget")
        assertFalse(StorageAccess.write(validTarget, absent).isError)
        assertEquals(0, Project.errorCount)

        val actual = MCFPPObjectType(listOf(ObjectFieldContract("x", MCFPPBaseType.Int),
            ObjectFieldContract("y", MCFPPBaseType.String)))
        val producer = objectValue(actual, mapOf("x" to MCInt(1), "y" to
            StorageAccess.literal(MCFPPBaseType.String, CompilerValue.Text("wrong"))), "producer")
        val hidden = public.buildUnConcrete("hidden")
        assertFalse(StorageAccess.write(hidden, producer).isError)
        val before = StorageAccess.snapshot(hidden)
        val target = targetType.buildUnConcrete("target")
        assertTrue(StorageAccess.write(target, hidden).isError)
        assertNull(StorageAccess.snapshot(target))
        assertEquals(before, StorageAccess.snapshot(hidden))
        assertTrue(Project.errorCount > 0)
    }

    @Test fun incomingErasedObjectsCanReadRequiredFieldsThroughRuntimeViewsWithoutInventingActualTypes() {
        val main = compile()
        val incoming = top.mcfpp.core.lang.MCAny("incoming")
        incoming.bindDeclaration()
        StorageAccess.bindIncomingParameter(incoming)
        val shape = MCFPPObjectType(listOf(ObjectFieldContract("x", MCFPPBaseType.Int)))
        val view = StorageAccess.view(incoming, shape, diagnose = false)
        val member = field(view, "x")
        assertFalse(member.get().isError)
        assertFalse(StorageAccess.capture(member.field).isError)
        assertFalse(StorageAccess.write(MCInt("copied"), member.field).isError)
        val root = StorageAccess.ensure(incoming)
        assertSame(TypeKnowledge.Unknown, incoming.typeKnowledge)
        assertNull(root.data.facts.read(root.place.field("x")))
        assertNull(root.data.declarations[root.place.field("x")])
        assertTrue(main.commands.analyzeAll().any { "incoming.x" in it })
        assertEquals(0, Project.errorCount)
    }

    @Test fun anIncomingFixedFieldRetainsItsSingletonValueBesideAnUnknownRuntimeField() {
        compile()
        val five = MCFPPLiteralType(MCFPPBaseType.Int, CompilerValue.Integral(5))
        val type = MCFPPObjectType(listOf(ObjectFieldContract("schema", five, kind = ObjectMemberKind.CONST),
            ObjectFieldContract("value", MCFPPBaseType.Int)))
        val incoming = type.buildUnConcrete("incoming")
        incoming.bindDeclaration()
        StorageAccess.bindIncomingParameter(incoming)
        assertNull(StorageAccess.snapshot(incoming))
        val fixed = field(incoming, "schema").get()
        assertEquals(CompilerValue.Typed(five.typeId, five.value), StorageAccess.snapshot(fixed))
        assertFalse(StorageAccess.capture(fixed).isError)
        assertFalse(StorageAccess.binary(fixed, MCInt(10), "*").isError)
        assertEquals(0, Project.errorCount)
    }

    @Test fun presenceGuardsPreserveTheDeclaredRuntimeLayoutAtEachRecursiveField() {
        compile()
        val template = DataTemplate("Node", "fixture.presence")
        fun member(value: Var<*>) {
            value.declaredParentTemplate = template
            template.scope.putVar(value.identifier, value)
            template.scope.putProperty(value.identifier, Property(value.identifier, SimpleAccessor(), SimpleMutator()).apply {
                isStatic = false
                declaredParentTemplate = template
            })
        }
        member(MCInt("value"))
        member(top.mcfpp.core.lang.obj.DataTemplateObject(template, "next").apply { nullable = true })
        var current: Var<*> = template.getType().buildUnConcrete("incoming")
        current.bindDeclaration()
        StorageAccess.bindIncomingParameter(current)
        repeat(4) {
            val next = field(current, "next")
            assertIs<top.mcfpp.core.lang.bool.PresenceBool>(StorageAccess.presence(next, true)).assume(true)
            current = next.get()
            assertFalse(current.isError)
        }
        assertFalse(field(current, "value").get().isError)
        assertEquals(0, Project.errorCount)
    }

    @Test fun incomingAndProducedUnionValuesExposeOnlyTheirGuaranteedRuntimeFields() {
        compile()
        fun branch(unique: String, optional: Boolean) = MCFPPObjectType(listOf(
            ObjectFieldContract("common", MCFPPBaseType.Int), ObjectFieldContract(unique, MCFPPBaseType.Int),
            ObjectFieldContract("maybe", MCFPPBaseType.Int, optional = optional)))
        val union = assertIs<MCFPPUnionType>(TypeCompositions.union(branch("left", true), branch("right", false)))
        val incoming = union.buildUnConcrete("incoming")
        incoming.bindDeclaration()
        StorageAccess.bindIncomingParameter(incoming)
        assertFalse(field(incoming, "common").get().isError)
        val returned = union.buildUnConcrete("returned")
        returned.bindDeclaration()
        val output = StorageAccess.ensure(returned)
        Function.addCommand("data modify ${output.path.toCommandPart()} set value {common:1,left:2}")
        StorageAccess.publishNbt(returned)
        val copied = union.buildUnConcrete("copied")
        assertFalse(StorageAccess.write(copied, returned).isError)
        assertFalse(field(copied, "common").get().isError)
        assertNull(copied.getMemberVar("left", Member.AccessModifier.PUBLIC).first)
        assertEquals(0, Project.errorCount)
        val optional = field(copied, "maybe")
        assertTrue(optional.get().isError)
        val errors = Project.errorCount
        assertIs<top.mcfpp.core.lang.bool.PresenceBool>(StorageAccess.presence(optional, true)).assume(true)
        assertFalse(optional.get().isError)
        assertEquals(errors, Project.errorCount)
    }

    @Test fun rawNbtCopiesPreserveTheOriginalPhysicalTagWithoutInventingViewConstants() {
        compile()
        val emptyType = MCFPPObjectType(emptyList())
        val original = objectValue(emptyType, emptyMap(), "empty")
        val view = StorageAccess.view(original, MCFPPNBTType.NBT, diagnose = false)
        assertNull(StorageAccess.snapshot(view))
        val copy = MCFPPNBTType.NBT.buildUnConcrete("copy")
        assertFalse(StorageAccess.write(copy, view).isError)
        assertIs<top.mcfpp.nbt.tags.CompoundTag>(StorageAccess.constantEncoding(copy))
        assertEquals(StorageAccess.snapshot(original),
            (StorageAccess.ensure(copy).data.facts.read(StorageAccess.ensure(copy).place)?.value as? ValueKnowledge.Constant)?.value)
        assertNull(StorageAccess.snapshot(copy))
        val secondCopy = MCFPPNBTType.NBT.buildUnConcrete("secondCopy")
        assertFalse(StorageAccess.write(secondCopy, copy).isError)
        assertIs<top.mcfpp.nbt.tags.CompoundTag>(StorageAccess.constantEncoding(secondCopy))
        val incompatible = StorageAccess.view(MCInt(1), MCFPPBaseType.Bool, diagnose = false)
        assertNull(StorageAccess.constantEncoding(incompatible))
        assertEquals(0, Project.errorCount)
    }
}
