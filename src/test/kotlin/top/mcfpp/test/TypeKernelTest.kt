package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.core.lang.MCAny
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCIntConcrete
import top.mcfpp.core.lang.nbt.MCByteConcrete
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.function.UnknownFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertIs

class TypeKernelTest {
    @Test fun nativeInteropCanReadAnErasedCompilerViewWithoutClaimingAConstantSnapshot() {
        MCFPPStringTest.readFromString("""
            func main(){
                dynamic var input as int = 1;
                var erased as any = input::jvm;
                print(erased);
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val function = GlobalScope.localNamespaces["default.test"]!!.scope.functions.getValue("main").single()
        val erased = function.scope.getVar("erased") as MCAny
        assertEquals(MCFPPConcreteType.JavaVar, erased.inferredType)
        assertNull(top.mcfpp.analysis.ValueSnapshot.of(erased))
        assertFalse(function.commands.analyzeAll().any { it.contains(".erased") })
        assertTrue(function.commands.analyzeAll().any { it.startsWith("tellraw") })
    }
    @Test fun arithmeticTypeDoesNotImplySingleScoreboardStorage() {
        val integer: top.mcfpp.core.lang.Var<*> = MCInt("score")
        val floating: top.mcfpp.core.lang.Var<*> = top.mcfpp.core.lang.MCFloat("providerOrComponents")
        assertTrue(integer is top.mcfpp.core.lang.OnScoreboard)
        assertFalse(floating is top.mcfpp.core.lang.OnScoreboard)
    }
    @Test fun branchMaterializationKeepsTheIdentityOfNbtScoreMappings() {
        MCFPPStringTest.readFromString("""
            func arithmetic(){
                var small = 7b;
                var wider = 300s;
                dynamic var condition = true;
                if(condition){ small = 8b; } else { small = 9b; }
                var result = toInt(small) + toInt(wider);
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val function = GlobalScope.localNamespaces["default.test"]!!.scope.functions.getValue("arithmetic").single()
        assertEquals(MCFPPNBTType.Byte, function.scope.getVar("small")!!.type)
        assertEquals(MCFPPNBTType.Short, function.scope.getVar("wider")!!.type)
        assertEquals(MCFPPBaseType.Int, function.scope.getVar("result")!!.type)
    }
    @Test fun anyWithARuntimePredicateKeepsBoolTypeWithoutClaimingAConstant() {
        MCFPPStringTest.readFromString("""
            func arithmetic(){
                dynamic var input as bool = true;
                var erased as any = !input;
                var result = erased == true;
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val function = GlobalScope.localNamespaces["default.test"]!!.scope.functions.getValue("arithmetic").single()
        val erased = function.scope.getVar("erased") as MCAny
        assertFalse(erased is top.mcfpp.core.lang.MCAnyConcrete)
        assertEquals(MCFPPBaseType.Bool, erased.inferredType)
        assertFalse(function.commands.analyzeAll().any { it.contains("execute store score") })
        assertEquals(MCFPPBaseType.Bool, function.scope.getVar("result")!!.type)
    }
    @Test fun primitiveMembersHaveOneSignatureTableForAllValueStates() {
        for (type in listOf(MCFPPBaseType.Int, MCFPPBaseType.Float, MCFPPBaseType.Bool)) {
            assertSame(type.instanceData, type.concreteInstanceData)
            assertEquals(type.instanceData.scope.functions, type.concreteInstanceData.scope.functions)
        }
    }
    @Test fun targetCapabilitiesNeverGuessUnverifiedFutureVersions() {
        assertEquals(top.mcfpp.command.FloatBackend.SCOREBOARD_EMULATION,
            top.mcfpp.command.TargetCapabilities.forVersion("26.2")!!.floatBackend)
        assertEquals(top.mcfpp.command.FloatBackend.NUMBER_PROVIDER,
            top.mcfpp.command.TargetCapabilities.forVersion("26.3")!!.floatBackend)
        assertNull(top.mcfpp.command.TargetCapabilities.forVersion("26.4"))
        assertNull(top.mcfpp.command.TargetCapabilities.forVersion("unknown"))
        assertEquals(18, top.mcfpp.util.Utils.getVersion("1.20.2"))
    }
    @Test fun aliasesResolveTransparentlyIncludingForwardChainsAndContainerArguments() {
        MCFPPStringTest.readFromString("""
            typealias Number as Count;
            typealias int as Number;
            typealias list<Count> as Counts;
            func consume(value as Count, values as Counts) {}
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val scope = GlobalScope.localNamespaces["default.test"]!!.scope
        assertEquals(MCFPPBaseType.Int, scope.getType("Count"))
        assertEquals(MCFPPBaseType.Int.hashCode(), scope.getType("Count")!!.hashCode())
        assertEquals(MCFPPListType(MCFPPBaseType.Int), scope.getType("Counts"))
        val params = scope.functions.getValue("consume").single().normalParams
        assertEquals(MCFPPBaseType.Int, params[0].type)
        assertEquals(MCFPPListType(MCFPPBaseType.Int), params[1].type)
    }

    @Test fun cyclicAliasesReportAnErrorWithoutRecursingIndefinitely() {
        MCFPPStringTest.readFromString("""
            typealias Second as First;
            typealias First as Second;
            func consume(value as First) {}
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0)
        assertNull(GlobalScope.localNamespaces["default.test"]!!.scope.getType("First"))
    }

    @Test fun namespacesAndTypeKindsHaveDistinctIdentity() {
        val a = DataTemplate("Point", "one").getType()
        val b = DataTemplate("Point", "two").getType()
        assertNotEquals(a, b)
        assertEquals(a, DataTemplate("Point", "one").getType())
        assertNotEquals<MCFPPType>(a, MCFPPInterfaceType(a.template, arrayListOf()))
        assertEquals<MCFPPType>(a, MCFPPDeclaredConcreteType(a))
        assertEquals(a.hashCode(), MCFPPDeclaredConcreteType(a).hashCode())
    }

    @Test fun containersAreInvariantAndHaveConstructorSensitiveHashes() {
        val ints = MCFPPListType(MCFPPBaseType.Int)
        val objects = MCFPPListType(MCFPPBaseType.Object)
        assertFalse(ints.isSubOf(objects))
        assertNull(TypeRelations.resolveImplicitConversion(ints, objects))
        assertNotEquals<MCFPPType>(MCFPPDictType(MCFPPBaseType.Int), MCFPPMapType(MCFPPBaseType.Int))
        assertNotEquals(0, ints.hashCode())
        assertEquals(ints.hashCode(), MCFPPListType(MCFPPBaseType.Int).hashCode())
        assertEquals(2, setOf(ints, objects, MCFPPListType(MCFPPBaseType.Int)).size)
    }

    @Test fun unionsNormalizeOrderDuplicatesAndNesting() {
        val a = MCFPPUnionType(MCFPPBaseType.Float, MCFPPBaseType.Int, MCFPPBaseType.Int)
        val b = MCFPPUnionType(MCFPPBaseType.Int, MCFPPUnionType(MCFPPBaseType.Float, MCFPPBaseType.Int))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(2, a.types.size)
        assertTrue(MCFPPBaseType.Int.isSubOf(a))
        assertTrue(a.isSubOf(MCFPPBaseType.Object))
        assertFalse(a.isSubOf(MCFPPBaseType.Int))
    }

    @Test fun objectIsSupertypeAndAnyIsAnErasedConversion() {
        for (type in listOf(MCFPPBaseType.Int, MCFPPBaseType.Float, MCFPPBaseType.Bool,
            MCFPPBaseType.String, MCFPPBaseType.Any, MCFPPNBTType.NBT, MCFPPConcreteType.Type,
            MCFPPConcreteType.JavaVar, DataTemplate.baseDataTemplate.getType())) {
            assertTrue(type.isSubOf(MCFPPBaseType.Object), type.toString())
        }
        assertFalse(MCFPPPrivateType.Void.isSubOf(MCFPPBaseType.Object))
        assertFalse(MCFPPPrivateType.Wildcard.isSubOf(MCFPPBaseType.Int))
        assertFalse(MCFPPBaseType.Int.isSubOf(MCFPPBaseType.Any))
        assertEquals(TypeRelations.Conversion.ANY, TypeRelations.resolveImplicitConversion(MCFPPBaseType.Int, MCFPPBaseType.Any))
        assertFalse(MCFPPBaseType.JsonText.isSubOf(MCFPPNBTType.NBT))
        assertFalse(MCFPPListType(MCFPPBaseType.Int).isSubOf(MCFPPNBTType.NBT))
    }

    @Test fun nbtMappingIsNotArithmeticAndByteBuildKeepsItsValue() {
        assertFalse(MCFPPNBTType.Byte.isSubOf(MCFPPBaseType.Int))
        assertFalse(MCFPPNBTType.Short.isSubOf(MCFPPBaseType.Int))
        assertNull(TypeRelations.resolveImplicitConversion(MCFPPNBTType.Byte, MCFPPBaseType.Int))
        assertNull(TypeRelations.resolveNumericOperator("+", MCFPPNBTType.Byte, MCFPPNBTType.Byte))
        assertEquals(37.toByte(), (MCFPPNBTType.Byte.build("value", 37.toByte()) as MCByteConcrete).value)
        assertEquals(TypeRelations.Conversion.INT_TO_FLOAT, TypeRelations.resolveImplicitConversion(MCFPPBaseType.Int, MCFPPBaseType.Float))
        assertNull(TypeRelations.resolveImplicitConversion(MCFPPBaseType.Float, MCFPPBaseType.Int))
        val byte = MCByteConcrete(1)
        assertFalse(byte.canImplicitCast(MCFPPBaseType.Int))
        assertTrue(byte.implicitCast(MCFPPBaseType.Float).isError)
    }

    @Test fun nbtNumericArithmeticAndImplicitAssignmentAreRejectedByTheCompiler() {
        for (literal in listOf("1b", "1s", "1l", "1d")) {
            MCFPPStringTest.readFromString("func arithmetic(){ var encoded = $literal; var result = encoded + encoded; }", version = "26.3")
            assertTrue(Project.errorCount > 0, literal)
        }
        MCFPPStringTest.readFromString("func arithmetic(){ var encoded = 1b; var result as int = encoded; }", version = "26.3")
        assertTrue(Project.errorCount > 0)
    }

    @Test fun anyMatchingUsesActualTypeEvenWhenValueIsUnknown() {
        val value = MCAny("erased").apply { lastVar = MCInt("runtime") }
        assertTrue(ParameterMatcher.accepts(value, MCFPPBaseType.Int))
        assertTrue(ParameterMatcher.accepts(value, MCFPPBaseType.Float))
        assertFalse(ParameterMatcher.accepts(value, MCFPPBaseType.String))
        val unknown = MCAny("unknown")
        assertFalse(ParameterMatcher.accepts(unknown, MCFPPBaseType.Int))
        assertTrue(ParameterMatcher.accepts(unknown, MCFPPBaseType.Any))
        assertTrue(ParameterMatcher.accepts(unknown, MCFPPBaseType.Object))
    }

    private fun overload(type: MCFPPType, default: Boolean = false) = Function("choose", context = null).apply {
        appendNormalParam(type, "value")
        if (default) {
            appendNormalParam(MCFPPBaseType.Int, "fallback")
            normalParams.last().hasDefault = true
            normalParams.last().defaultVar = MCIntConcrete(0)
        }
    }

    @Test fun overloadsPreferExactThenPromotionThenObjectThenAny() {
        val int = overload(MCFPPBaseType.Int)
        val float = overload(MCFPPBaseType.Float)
        val obj = overload(MCFPPBaseType.Object)
        val any = overload(MCFPPBaseType.Any)
        val arg = listOf(MCInt("arg"))
        assertSame(int, ParameterMatcher.select(listOf(any, obj, float, int), "choose", emptyList(), arg))
        assertSame(float, ParameterMatcher.select(listOf(any, obj, float), "choose", emptyList(), arg))
        assertSame(obj, ParameterMatcher.select(listOf(any, obj), "choose", emptyList(), arg))
        assertTrue(overload(MCFPPBaseType.Float, true).isSelfWithDefaultValue("choose", arg))
        val errors = Project.errorCount
        assertIs<UnknownFunction>(ParameterMatcher.select(listOf(int, overload(MCFPPBaseType.Int)), "choose", emptyList(), arg))
        assertEquals(errors + 1, Project.errorCount)
    }

    @Test fun parserAcceptsObjectAndMismatchOnSingleOverloadIsRejected() {
        MCFPPStringTest.readFromString("""
            func consume(value as object) {}
            func arithmetic(){ consume(1); }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val consume = GlobalScope.localNamespaces["default.test"]!!.scope.functions["consume"]!!.first()
        assertEquals(MCFPPBaseType.Object, consume.normalParams.single().type)
        assertFalse(consume.isSelf("consume", listOf(top.mcfpp.core.lang.Void)))
    }
    @Test fun anyKeepsActualTypeWhileForcingRuntimeValueAndPromotesThroughItsSourceType() {
        MCFPPStringTest.readFromString("""
            func arithmetic(){
                dynamic var value as any = 3;
                var result = value + 2;
                var promoted as float = value;
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val function = GlobalScope.localNamespaces["default.test"]!!.scope.functions["arithmetic"]!!.first()
        assertEquals(MCFPPBaseType.Int, function.scope.getVar("result")!!.type)
        assertEquals(MCFPPBaseType.Float, function.scope.getVar("promoted")!!.type)
        assertEquals(MCFPPBaseType.Int, (function.scope.getVar("value") as MCAny).inferredType)
        assertTrue(function.commands.analyzeAll().any { it.contains("minecraft:from_int") || it.contains("minecraft:score") })
    }

    @Test fun unknownAnyRequiresAsAndObjectDoesNotExposeConcreteMembers() {
        MCFPPStringTest.readFromString("func arithmetic(){ var value as any; var result = value + 2; }", version = "26.3")
        assertTrue(Project.errorCount > 0)
        MCFPPStringTest.readFromString("func arithmetic(){ var value as object = 3; value.toText(); }", version = "26.3")
        assertTrue(Project.errorCount > 0)
        assertFalse(ParameterMatcher.accepts(MCInt("runtime"), MCFPPDeclaredConcreteType(MCFPPBaseType.Int)))
        assertTrue(ParameterMatcher.accepts(MCIntConcrete(1), MCFPPDeclaredConcreteType(MCFPPBaseType.Int)))
    }

    @Test fun severalGenericBindingsUseTheSameDirectionalConversionQuery() {
        val parser = top.mcfpp.antlr.mcfppParser(org.antlr.v4.runtime.CommonTokenStream(
            top.mcfpp.antlr.mcfppLexer(org.antlr.v4.runtime.CharStreams.fromString("{}"))))
        val function = top.mcfpp.model.function.GenericFunction("generic", ctx = parser.curlBlock())
        function.readOnlyParams.add(top.mcfpp.model.function.FunctionParam(MCFPPConcreteType.Type, "T", function, isReadOnly = true))
        function.readOnlyParams.add(top.mcfpp.model.function.FunctionParam(MCFPPConcreteType.Type, "U", function, isReadOnly = true))
        val t = MCFPPGenericParamType("T", arrayListOf())
        val u = MCFPPGenericParamType("U", arrayListOf())
        function.appendNormalParam(MCFPPListType(t), "first")
        function.appendNormalParam(u, "second")
        val readonly = listOf(top.mcfpp.core.lang.MCFPPTypeVar(MCFPPBaseType.Int), top.mcfpp.core.lang.MCFPPTypeVar(MCFPPBaseType.Float))
        assertTrue(function.isSelf("generic", readonly, listOf(MCFPPListType(MCFPPBaseType.Int).buildUnConcrete("items"), MCInt("promoted"))))
        assertFalse(function.isSelf("generic", readonly, listOf(MCFPPListType(MCFPPBaseType.Float).buildUnConcrete("items"), MCInt("promoted"))))
        function.normalParams[1].type = MCFPPDeclaredConcreteType(u)
        assertFalse(function.isSelf("generic", readonly, listOf(MCFPPListType(MCFPPBaseType.Int).buildUnConcrete("items"), MCInt("promoted"))))
        assertTrue(function.isSelf("generic", readonly, listOf(MCFPPListType(MCFPPBaseType.Int).buildUnConcrete("items"), MCIntConcrete(1))))
    }

    @Test fun nominalTemplateUpcastRetainsExtraDataWithoutExposingExtraMembers() {
        val template = DataTemplate("Child", "test")
        template.extends(DataTemplate.baseDataTemplate)
        template.scope.putVar("count", MCInt("count"))
        val source = top.mcfpp.core.lang.obj.DataTemplateObjectConcrete(template, hashMapOf("count" to MCIntConcrete(7)), "source")
        val target = source.implicitCast(DataTemplate.baseDataTemplate.getType())
        assertFalse(target.isError)
        assertIs<top.mcfpp.core.lang.obj.DataTemplateObjectConcrete>(target)
        assertEquals(7, (target.value.getValue("count") as MCIntConcrete).value)
        assertNull(target.instanceField.getVar("count"))
    }

}
