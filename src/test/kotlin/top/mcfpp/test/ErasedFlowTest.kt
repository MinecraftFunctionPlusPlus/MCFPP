package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.analysis.TypeKnowledge
import top.mcfpp.analysis.ValueSnapshot
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

class ErasedFlowTest {
    private fun compile(source: String, version: String = "26.3"): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = version)
        assertEquals(0, Project.errorCount)
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
            .also { assertNotNull(it.typedIR) }
    }

    private fun execute(function: Function): ScoreCommandExecutor = ScoreCommandExecutor(function.commands.analyzeAll(),
        GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() }).also {
        assertEquals(0, it.stackDepth)
        assertTrue(it.branchGuards.isEmpty())
    }

    private fun withBothFoldingModes(action: () -> Unit) {
        val previous = CompileSettings.foldIRConstants
        try {
            for (enabled in listOf(true, false)) {
                CompileSettings.foldIRConstants = enabled
                action()
            }
        } finally { CompileSettings.foldIRConstants = previous }
    }

    @Test fun sameTypeLoopUpdatesBindWithoutAsAcrossTargetsAndFoldingModes() = withBothFoldingModes {
        for (version in listOf("26.3", "1.20.2", "1.20")) {
            val main = compile("""
                func main(){
                    var value as any = 1;
                    var i = 0;
                    while(i < 4){ value += i; i += 1; }
                    dynamic var result = value + 2;
                }
            """, version)
            assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), (main.scope.getVar("value") as MCAny).typeKnowledge)
            assertEquals(9, execute(main).read(main.scope.getVar("result") as MCInt))
        }
    }

    @Test fun backedgeTypeChangesRequireAsEvenBeforeTheAssignmentInTheBody() = withBothFoldingModes {
        MCFPPStringTest.readFromString("""
            func main(){
                var value as any = 1;
                var i = 0;
                while(i < 2){ dynamic var result = value + 1; value = false; i += 1; }
            }
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0, "A later iteration must not bind arithmetic to the first iteration's int")
        assertNotNull(GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single().typedIR)
    }

    @Test fun zeroIterationAndBackedgeTypesJoinAsCandidates() = withBothFoldingModes {
        val main = compile("""
            func main(){
                var value as any = 1;
                var i = 0;
                while(i < 2){ value = false; i += 1; }
                dynamic var result = (value as bool) == false;
            }
        """)
        assertEquals(setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Bool.typeId),
            ((main.scope.getVar("value") as MCAny).typeKnowledge as TypeKnowledge.Candidates).types)
        assertEquals(1, execute(main).read(main.scope.getVar("result") as ScoreBool))
    }

    @Test fun breakAndContinueOnlyContributeReachableTypes() = withBothFoldingModes {
        val main = compile("""
            func main(){
                var value as any = 1;
                var i = 0;
                while(i < 6){
                    i += 1;
                    if(i == 2){ continue; value = false; }
                    if(i == 4){ break; value = false; }
                    value += i;
                }
                dynamic var result = value + 2;
            }
        """)
        assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), (main.scope.getVar("value") as MCAny).typeKnowledge)
        assertEquals(7, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun anUnconditionalBreakUsesTheBreakStateInsteadOfTheLoopEntry() = withBothFoldingModes {
        val main = compile("""
            func main(){
                var value as any = 1;
                while(true){ value = false; break; }
                dynamic var result = value == false;
            }
        """)
        assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Bool.typeId), (main.scope.getVar("value") as MCAny).typeKnowledge)
        assertEquals(1, execute(main).read(main.scope.getVar("result") as ScoreBool))
    }

    @Test fun erasedCopiesAndObjectPayloadsSurviveNestedLoops() = withBothFoldingModes {
        val main = compile("""
            func main(){
                var value as any = 1;
                var copied as any = 0;
                var opaque as object = 0;
                var i = 0;
                while(i < 2){
                    var j = 0;
                    while(j < 2){ copied = value; value += 1; j += 1; }
                    opaque = copied;
                    i += 1;
                }
                dynamic var result = value + copied + (opaque as int);
            }
        """)
        assertEquals(13, execute(main).read(main.scope.getVar("result") as MCInt))
        assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), (main.scope.getVar("copied") as MCAny).typeKnowledge)
    }

    @Test fun anUnreachableConditionalWriteDoesNotPreventConcreteBinding() = withBothFoldingModes {
        val main = compile("""
            func main(){
                var value as any = 1;
                if(false){ value = false; }
                dynamic var result = value + 2;
            }
        """)
        assertEquals(3, execute(main).read(main.scope.getVar("result") as MCInt))
        assertEquals(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), (main.scope.getVar("value") as MCAny).typeKnowledge)
    }

    @Test fun objectRetainsItsStaticSignatureInsideTheLoop() {
        MCFPPStringTest.readFromString("""
            func main(){
                var value as object = 1;
                var i = 0;
                while(i < 2){ dynamic var result = value + 1; i += 1; }
            }
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0)
    }

    @Test fun returnKnowledgePreservesRuntimeConditionsAndDoesNotEvaluateTheCall() {
        MCFPPStringTest.readFromString("""
            func choose() -> any {
                dynamic var condition = true;
                while(condition){ return false; }
                return 2;
            }
            func main(){ var value = choose(); dynamic var result = (value as bool) == false; }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val functions = GlobalScope.localNamespaces.getValue("default.test").scope.functions
        assertNotNull(functions.getValue("choose").single().typedIR)
        val main = functions.getValue("main").single()
        assertEquals(setOf(MCFPPBaseType.Int.typeId, MCFPPBaseType.Bool.typeId),
            ((main.scope.getVar("value") as MCAny).typeKnowledge as TypeKnowledge.Candidates).types)
        assertNull(ValueSnapshot.of(main.scope.getVar("value")))
        assertEquals(1, execute(main).read(main.scope.getVar("result") as ScoreBool))
    }
}
