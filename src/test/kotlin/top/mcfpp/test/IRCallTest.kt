package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.core.lang.MCAny
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import kotlin.test.*
import kotlin.test.Test

class IRCallTest {
    private fun function(name: String) = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue(name).single()

    private fun compile(source: String, version: String = "26.3"): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = version)
        assertEquals(0, Project.errorCount)
        return function("main").also { assertNotNull(it.typedIR) }
    }

    private fun execute(main: Function) = ScoreCommandExecutor(main.commands.analyzeAll(),
        GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() }).also {
        assertEquals(0, it.stackDepth)
        assertTrue(it.branchGuards.isEmpty())
    }

    private fun modes(action: () -> Unit) {
        val original = CompileSettings.foldIRConstants
        try {
            for (enabled in listOf(true, false)) { CompileSettings.foldIRConstants = enabled; action() }
        } finally { CompileSettings.foldIRConstants = original }
    }

    @Test fun callsInErasedLoopsKeepTypeKnowledgeAndOneRuntimeBody() = modes {
        val main = compile("""
            func increment(value: int): int { return value + 1; }
            func wrapper(value: int): int { return increment(value); }
            func main(){
                var value: any = 1
                var i = 0
                while(i < 3){ value = wrapper(value); i += 1; }
                external result = value + 2
            }
        """)
        assertEquals(6, execute(main).read(main.scope.getVar("result") as MCInt))
        assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), (main.scope.getVar("value") as MCAny).typeKnowledge)
        for (name in listOf("increment", "wrapper")) {
            assertEquals(Effect.Pure, function(name).runtimeEffect)
            assertTrue(function(name).compiledFunctions.isEmpty())
            assertNotNull(function(name).typedIR)
        }
        assertTrue(main.typedIR!!.blocks.any { block -> block.instructions.any { it is Instruction.Call } })
    }

    @Test fun recursiveCallsPreserveParametersAndEarlierResultsAcrossTargets() = modes {
        for (version in listOf("26.3", "1.20.2", "1.20")) {
            val main = compile("""
                func sum(value: int): int {
                    if(value <= 0){ return 0; }
                    return value + sum(value - 1)
                }
                func add(left: int, right: int): int { return left + right; }
                func main(){ external result = add(sum(3),sum(4)); }
            """, version)
            assertEquals(16, execute(main).read(main.scope.getVar("result") as MCInt))
            assertEquals(Effect.Pure, function("sum").runtimeEffect)
            assertNotNull(function("sum").typedIR)
        }
    }

    @Test fun mutualRecursionAndForwardCallsHavePureSummaries() {
        val main = compile("""
            func even(value: int): bool {
                if(value <= 0){ return true; }
                return odd(value - 1)
            }
            func odd(value: int): bool {
                if(value <= 0){ return false; }
                return even(value - 1)
            }
            func main(){ external result = even(4) && !odd(4); }
        """)
        assertEquals(1, execute(main).read(main.scope.getVar("result") as ScoreBool))
        for (name in listOf("even", "odd")) {
            assertEquals(Effect.Pure, function(name).runtimeEffect)
            assertNotNull(function(name).typedIR)
        }
    }

    @Test fun unknownCallsFlushAndInvalidateErasedPlacesAndPropagateThroughWrappers() {
        val main = compile("""
            func change(){
                /data modify storage mcfpp:system stack_frame[2].value set value 7
            }
            func wrapper(){ change(); }
            func main(){
                var value: any = 2
                wrapper()
                external result = (value as int) + 1
            }
        """)
        assertEquals(8, execute(main).read(main.scope.getVar("result") as MCInt))
        assertEquals(TypeKnowledge.Unknown, (main.scope.getVar("value") as MCAny).typeKnowledge)
        for (name in listOf("change", "wrapper")) assertEquals(Effect.Unknown, function(name).runtimeEffect)
    }

    @Test fun erasedParametersAndReturnsCopyTheirPayloadsAndPreserveProvenTypes() {
        val main = compile("""
            func identity(value: any): any { return value; }
            func main(){
                var original: any = 2
                var copied = identity(original)
                original = 7
                external result = copied + original
            }
        """)
        assertEquals(9, execute(main).read(main.scope.getVar("result") as MCInt))
        assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), (main.scope.getVar("copied") as MCAny).typeKnowledge)
        assertNull(StorageAccess.snapshot(assertNotNull(main.scope.getVar("copied"))))
    }

    @Test fun aLoopBackedgeDoesNotBindAConcreteOverloadFromTheFirstIteration() = modes {
        MCFPPStringTest.readFromString("""
            func consume(value: int): int { return value + 1; }
            func main(){
                var value: any = 2
                var i = 0
                while(i < 2){ var result = consume(value); value = false; i += 1; }
            }
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0)
        assertNotNull(function("main").typedIR)
    }

    @Test fun pureWrappersPreserveFactsForCallersStillUsingCollectionAdapters() {
        MCFPPStringTest.readFromString("""
            func increment(value: int): int { return value + 1; }
            func wrapper(value: int): int { return increment(value); }
            func main(){
                var values = {field:2}
                var result = wrapper(4)
                external number = values["field"] + result
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        assertEquals(Effect.Pure, function("wrapper").runtimeEffect)
        assertEquals(7, execute(function("main")).read(function("main").scope.getVar("number") as MCInt))
        assertNotNull(StorageAccess.snapshot(assertNotNull(function("main").scope.getVar("values"))))
    }

    @Test fun defaultsAndKnownErasedArgumentsUseTheSharedOverloadRules() {
        val main = compile("""
            func choose(value: int): int { return value + 1; }
            func choose(value: bool): int {
                if(value){ return 10; }
                return 20
            }
            func add(value: int, delta: int = 2): int { return value + delta; }
            func main(){
                var value: any = 2
                external result = choose(value) + choose(false) + add(3)
            }
        """)
        assertEquals(28, execute(main).read(main.scope.getVar("result") as MCInt))
        val calls = main.typedIR!!.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Call>()
        assertEquals(3, calls.map { it.declaration }.distinct().size)
    }

    @Test fun overloadBindingUsesReachableFactsInsteadOfLexicalBranchGuesses() = modes {
        val main = compile("""
            func consume(value: int): int { return value + 1; }
            func main(){
                var value: any = 2
                if(false){ value = false; }
                external result = consume(value)
            }
        """)
        assertEquals(3, execute(main).read(main.scope.getVar("result") as MCInt))
        val call = main.typedIR!!.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Call>().single()
        assertEquals(function("consume").declarationId, call.declaration)
        assertFalse(call.provisional)
    }

    @Test fun directErasedCallResultsProvideIndependentPlacesForViews() = modes {
        val main = compile("""
            func identity(value: any): any { return value; }
            func main(){ external result = (identity(2) as int) + (identity(3) as int); }
        """)
        assertEquals(5, execute(main).read(main.scope.getVar("result") as MCInt))
        val calls = main.typedIR!!.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Call>()
        assertEquals(2, calls.map { assertNotNull(it.resultPlace) }.distinct().size)
    }

    @Test fun recursiveErasedReturnsSupportExplicitViewsAcrossTargets() = modes {
        for (version in listOf("26.3", "1.20.2", "1.20")) {
            val main = compile("""
                func sum(value: int): any {
                    if(value <= 0){ return 0; }
                    return value + (sum(value - 1) as int)
                }
                func main(){ external result = (sum(4) as int) + (sum(3) as int); }
            """, version)
            assertEquals(16, execute(main).read(main.scope.getVar("result") as MCInt))
            assertEquals(Effect.Pure, function("sum").runtimeEffect)
        }
    }

    @Test fun provenErasedCallResultsCanUseConcreteOperationsAndOverloadsWithoutValueFolding() = modes {
        val main = compile("""
            func identity(value: any): any { return value; }
            func increment(value: int): int { return value + 1; }
            func main(){
                var result = identity(2) + increment(identity(3))
                external number = result
                var copy = identity(4)
                external typedNumber: int = identity(5)
                external accepted = false
                if(identity(true)){ accepted = true; }
            }
        """)
        val machine = execute(main)
        assertEquals(6, machine.read(main.scope.getVar("number") as MCInt))
        assertEquals(5, machine.read(main.scope.getVar("typedNumber") as MCInt))
        assertEquals(1, machine.read(main.scope.getVar("accepted") as ScoreBool))
        assertIs<MCAny>(main.scope.getVar("copy"))
        assertNull(StorageAccess.snapshot(assertNotNull(main.scope.getVar("result"))))
        val calls = main.typedIR!!.blocks.flatMap { it.instructions }.filterIsInstance<Instruction.Call>()
        assertEquals(6, calls.size)
        assertTrue(calls.all { !it.provisional })
    }

    @Test fun reachableReturnChecksUseConstantConditionsWhileRespectingDynamicBranches() {
        val main = compile("""
            func choose(): int {
                if(1 < 2){ return 7; }
            }
            func main(){ external result = choose(); }
        """)
        assertNotNull(function("choose").typedIR)
        assertEquals(7, execute(main).read(main.scope.getVar("result") as MCInt))
        MCFPPStringTest.readFromString("""
            func choose(): int {
                external condition = 1 < 2
                if(condition){ return 7; }
            }
            func main(){ external result = choose(); }
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0)
    }
}
