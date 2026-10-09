package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.StorageLayout
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.core.lang.*
import top.mcfpp.lib.SbObject
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPNBTType
import kotlin.test.*
import kotlin.test.Test

class VarIdentityTest {
    private fun compile(source: String): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
    }

    private fun execute(main: Function, prefix: List<String> = emptyList()) = ScoreCommandExecutor(
        prefix + main.commands.analyzeAll(), GlobalScope.localNamespaces.values
            .flatMap { it.scope.functions.values.flatten() }.associate { it.namespaceID.toString() to it.commands.analyzeAll() } +
            Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) })

    @Test fun independentAdaptersAndCoordinateClonesHaveDistinctIdentity() {
        assertNotEquals(MCInt("first"), MCInt("second"))
        val integer = MCInt(1)
        val long = StorageAccess.literal(MCFPPNBTType.Long, CompilerValue.Integral(1))
        assertNotEquals(integer, long)
        assertNotEquals(StorageAccess.ensure(integer).place, StorageAccess.ensure(long).place)
        assertEquals(CompilerValue.Typed(top.mcfpp.type.MCFPPBaseType.Int.typeId, CompilerValue.Integral(1)), StorageAccess.snapshot(integer))
        assertEquals(CompilerValue.Typed(MCFPPNBTType.Long.typeId, CompilerValue.Integral(1)), StorageAccess.snapshot(long))
        val position = Pos3Var()
        assertEquals(position, position)
        assertNotEquals(position, position.clone())
        assertFalse(position.equals(Pos2Var()))
        assertFalse(position.equals(MCInt(0)))
        val dimension = PosDimension("~", 1, "dimension")
        assertNotEquals(dimension, dimension.clone())
        assertNotEquals(Pos2Var(), Pos2Var())
    }

    @Test fun hashSetMembershipSurvivesMutableAdapterState() {
        val value = MCInt(1).apply { bindDeclaration() }
        val position = StorageAccess.literal(top.mcfpp.type.MCFPPBaseType.Pos3, CompilerValue.Sequence(List(3) {
            CompilerValue.Typed(top.mcfpp.type.MCFPPPrivateType.MCFPPCoordinateDimension.typeId,
                CompilerValue.Sequence(listOf(CompilerValue.Text(""), CompilerValue.Integral(0))))
        })) as Pos3Var
        position.bindDeclaration()
        val dimension = PosDimension("", 0, "dimension")
        val values = hashSetOf<Var<*>>(value, position, dimension)
        val hash = value.hashCode()
        value.identifier = "renamed"
        value.name = "new_score"
        StorageAccess.write(value, MCInt(2))
        assertEquals(top.mcfpp.type.MCFPPBaseType.Int.typeId, value.type.typeId)
        value.parent = position
        value.stackIndex = 3
        StorageAccess.write(position.x, PosDimension("~", 5))
        dimension.bindDeclaration()
        StorageAccess.write(dimension, PosDimension("^", 2.5))
        assertEquals(hash, value.hashCode())
        assertTrue(values.containsAll(listOf(value, position, dimension)))
        assertTrue(values.remove(value))
        assertTrue(values.remove(position))
        assertTrue(values.remove(dimension))
        assertTrue(values.isEmpty())
    }

    @Test fun expressionCacheRemovesOnlyTheRequestedTemporary() {
        val first = MCInt("same").apply { isTemp = true }
        val second = MCInt("same").apply { isTemp = true }
        val visitor = MCFPPExprVisitor()
        visitor.processVarCache.addAll(listOf(first, second))
        assertTrue(visitor.processVarCache.remove(second))
        assertEquals(1, visitor.processVarCache.size)
        assertSame(first, visitor.processVarCache.single())
    }

    @Test fun spillPreservesIndependentSameNamedTemporariesAndDeduplicatesRepeatedReferences() {
        val main = compile("func main() {}")
        Function.currFunction = main
        main.commands.clear()
        val first = MCInt("same").apply { isTemp = true; sbObject = SbObject("first_temp") }
        val second = MCInt("same").apply { isTemp = true; sbObject = SbObject("second_temp") }
        StorageAccess.publishScore(first, StorageLayout.Scoreboard(first.name, first.sbObject.toString()))
        StorageAccess.publishScore(second, StorageLayout.Scoreboard(second.name, second.sbObject.toString()))
        val spills = StorageAccess.spill(listOf(first, second, first))
        assertEquals(2, spills.size)
        assertSame(first, spills[0].value)
        assertSame(second, spills[1].value)
        Function.addCommand("scoreboard players set ${first.name} ${first.sbObject} 0")
        Function.addCommand("scoreboard players set ${second.name} ${second.sbObject} 0")
        StorageAccess.restore(spills)
        Function.addCommand("data remove storage mcfpp:system stack_frame[0]")
        val machine = execute(main, listOf(
            "data modify storage mcfpp:system stack_frame prepend value {}",
            "scoreboard players set ${first.name} ${first.sbObject} 7",
            "scoreboard players set ${second.name} ${second.sbObject} 11"
        ))
        assertEquals(7, machine.read(first))
        assertEquals(11, machine.read(second))
    }

    @Test fun legacyRecursiveExpressionsPreserveBothPendingValuesAcrossCalls() {
        val main = compile("""
            func sum(value as int) -> int {
                var ignored = 6/2;
                if(value <= 0){ return 0; }
                return value + ((value + 1) + sum(value - 1));
            }
            func main(){ dynamic var result = sum(4); }
        """)
        val sum = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("sum").single()
        assertNull(sum.typedIR)
        val machine = execute(main)
        assertEquals(24, machine.read(main.scope.getVar("result") as MCInt))
        assertEquals(0, machine.stackDepth)
    }
}
