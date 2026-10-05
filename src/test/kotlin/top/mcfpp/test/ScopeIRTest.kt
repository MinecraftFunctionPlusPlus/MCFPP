package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.*
import kotlin.test.Test

class ScopeIRTest {
    private fun compile(source: String, version: String = "26.3"): Function {
        MCFPPStringTest.readFromString(source.trimIndent(), version = version)
        assertEquals(0, Project.errorCount)
        val functions = GlobalScope.localNamespaces.getValue("default.test").scope.functions
        functions.values.flatten().filter { it.ast != null }.forEach { assertNotNull(it.typedIR, "IR missing for ${it.identifier}") }
        return functions.getValue("main").single()
    }
    private fun execute(main: Function) = ScoreCommandExecutor(main.commands.analyzeAll(),
        GlobalScope.localNamespaces.values.flatMap { it.scope.functions.values.flatten() }
            .associate { it.namespaceID.toString() to it.commands.analyzeAll() } +
            Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) }).also {
        assertEquals(0, it.stackDepth)
        assertTrue(it.branchGuards.isEmpty())
    }
    private fun modes(action: () -> Unit) {
        val original = CompileSettings.foldIRConstants
        try { for (enabled in listOf(true, false)) { CompileSettings.foldIRConstants = enabled; action() } }
        finally { CompileSettings.foldIRConstants = original }
    }

    @Test fun siblingBlocksAndNestedDeclarationsKeepTheirOwnSymbols() = modes {
        val main = compile("""
            func choose(flag as bool) -> int {
                var result = 0;
                if(flag){ var value = 2; result = value; }
                else { var value = 3; result = value; }
                return result;
            }
            func main(){
                var value = 4;
                var captured = 0;
                if(true){ var value = value + 1; captured = value; }
                if(true){ var temporary = 1; }
                var temporary = 9;
                dynamic var result = choose(true)*1000 + choose(false)*100 + value*10 + captured;
            }
        """)
        assertEquals(2345, execute(main).read(main.scope.getVar("result") as MCInt))
        assertFalse(main.scope.vars.keys.any { it.startsWith("$") })
    }

    @Test fun shadowedCollectionsAndViewsRetainTheCorrectSharedPlace() = modes {
        val main = compile("""
            func main(){
                var source = [2,3];
                var view = source as list<int>;
                if(true){
                    var view = view as list<int>;
                    var source = [7,8];
                    view[0] = 5;
                    source[0] = 9;
                }
                if(true){ var source = [11,12]; source[1] = 13; }
                dynamic var result = source[0]*10 + view[1];
            }
        """, "1.20.2")
        assertEquals(53, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun recursiveCallsAndRepeatedLoopScopesKeepLocalStorageSeparate() = modes {
        val main = compile("""
            func recurse(value as int) -> int {
                if(value == 0){ return 0; }
                if(true){ var value = value + 1; return value + recurse(value-2); }
                return 99;
            }
            func main(){
                var value = 50;
                var sum = 0;
                for(index : 1 .. 3){
                    var value = index;
                    if(index > 1){ var value = value*10; sum += value; }
                    sum += value;
                }
                do { var value = 2; sum += value; } while(false);
                dynamic var result = recurse(3)*1000 + sum*10 + value;
            }
        """, "1.20.1")
        assertEquals(9630, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun unreadNbtLocalsStillHaveAnInitializedStorageParent() {
        val main = compile("""
            func main(){
                if(true){ var messageValue = "stored"; }
                /data modify storage mcfpp:system temp.frame set from storage mcfpp:system stack_frame[0]
            }
        """)
        val frame = assertIs<CompoundTag>(execute(main).readNbt("mcfpp:system", "temp.frame"))
        val locals = assertIs<CompoundTag>(frame.get("${'$'}ir"))
        assertTrue(locals.value.values.any { it is StringTag && it.value == "stored" })
    }

    @Test fun duplicatesAreRejectedWithinTheirOwnScope() {
        for (source in listOf(
            "func main(){ var value = 1; var value = 2; }",
            "func duplicate(value as int){ var value = 2; }\nfunc main(){ duplicate(1); }",
            "func main(){ if(true){ var value = 1; var value = 2; }; }",
            "func main(){ for(value : 1 .. 2){ var value = 3; }; }",
            "func main(){ if(true){ var inside = 1; }; dynamic var result = inside; }")) {
            MCFPPStringTest.readFromString(source, version = "26.3")
            assertTrue(Project.errorCount > 0, source)
        }
    }
}
