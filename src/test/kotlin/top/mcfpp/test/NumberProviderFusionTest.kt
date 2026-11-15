package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.io.FunctionInventory
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.primitive.FloatTag
import top.mcfpp.optimization.OptimizationConfig
import top.mcfpp.optimization.PassIds
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.*
import kotlin.test.Test

class NumberProviderFusionTest {
    private fun isolated(action: () -> Unit) {
        val previous = Project.config
        try { action() } finally { Project.config = previous }
    }

    private fun compile(source: String, level: Int = 1, disabled: Set<String> = emptySet(),
                        version: String = "26.3"): Function {
        Project.config = ProjectConfig(optimization = OptimizationConfig(level, disabled))
        MCFPPStringTest.readFromString(source.trimIndent(), version = version)
        assertEquals(0, Project.errorCount)
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single().also {
            assertNotNull(it.typedIR)
            assertNotNull(it.optimizedIR)
        }
    }

    private fun computes(main: Function) = main.commands.analyzeAll().filter { "set compute default float" in it }
    private fun execute(main: Function) = ScoreCommandExecutor(main.commands.analyzeAll(),
        FunctionInventory.all().associate { it.namespaceID.toString() to it.commands.analyzeAll() })
    private fun value(machine: ScoreCommandExecutor, name: String) =
        (machine.readNbt("fixture:fusion", name) as FloatTag).value

    @Test fun nestedArithmeticBecomesOneInlineProviderAndCanBeDisabled() = isolated {
        val source = """
            func main(){
                external a=2.0; external b=3.0; external c=7.0; external d=1.0;
                var result=(a+b)*(c-d);
                /data modify storage fixture:fusion result set from storage mcfpp:system stack_frame[0].result
            }
        """
        for ((level, disabled, expected) in listOf(
            Triple(0, emptySet(), 3), Triple(1, emptySet(), 1), Triple(2, emptySet(), 1),
            Triple(2, setOf(PassIds.PROVIDERS), 3))) {
            val main = compile(source, level, disabled)
            assertEquals(expected, computes(main).size)
            assertEquals(3 - expected, main.optimizedIR!!.fusedFloatResults.size)
            assertTrue(main.typedIR!!.fusedFloatResults.isEmpty())
            assertEquals(30f, value(execute(main), "result"))
            if (expected == 1) {
                assertTrue(computes(main).single().contains("minecraft:add"))
                assertTrue(computes(main).single().contains("minecraft:sub"))
                assertTrue(computes(main).single().contains("minecraft:mul"))
            }
        }
    }

    @Test fun parenthesesRoundingAndSignedZeroArePreserved() = isolated {
        for (level in 0..2) {
            val main = compile("""
                func main(){
                    external a=100000000.0; external b=-100000000.0; external c=1.0;
                    external zero=-0.0;
                    var left=(a+b)+c; var right=a+(b+c);
                    var negative=(zero*1.0)*1.0;
                    /data modify storage fixture:fusion left set from storage mcfpp:system stack_frame[0].left
                    /data modify storage fixture:fusion right set from storage mcfpp:system stack_frame[0].right
                    /data modify storage fixture:fusion negative set from storage mcfpp:system stack_frame[0].negative
                }
            """, level)
            val machine = execute(main)
            assertEquals(1f, value(machine, "left"))
            assertEquals(0f, value(machine, "right"))
            assertEquals((-0f).toRawBits(), value(machine, "negative").toRawBits())
        }
    }

    @Test fun sharedResultsRemainMaterialized() = isolated {
        val main = compile("""
            func main(){
                external a=2.0; external b=3.0;
                var result=(a+b)*(a+b);
                /data modify storage fixture:fusion result set from storage mcfpp:system stack_frame[0].result
            }
        """)
        assertEquals(2, computes(main).size)
        assertTrue(main.optimizedIR!!.fusedFloatResults.isEmpty())
        assertEquals(25f, value(execute(main), "result"))
    }

    @Test fun callsAndWritesKeepEarlierSnapshots() = isolated {
        val main = compile("""
            func touch():float {
                /say touched
                return 4.0;
            }
            func main(){
                external a=2.0; external b=3.0;
                var result=(a+b)*touch()+1.0;
                a=100.0;
                var next=(a+b)*2.0;
                /data modify storage fixture:fusion result set from storage mcfpp:system stack_frame[0].result
                /data modify storage fixture:fusion next set from storage mcfpp:system stack_frame[0].next
            }
        """, disabled = setOf(PassIds.INLINE))
        assertEquals(3, computes(main).size)
        val machine = execute(main)
        assertEquals(listOf("touched"), machine.messages)
        assertEquals(21f, value(machine, "result"))
        assertEquals(206f, value(machine, "next"))
    }

    @Test fun arithmeticCanFeedAComparisonProvider() = isolated {
        val main = compile("""
            func main(){
                external a=2.0; external b=3.0;
                var result=(a+b)*2.0 > 9.0;
                if(result){
                    /say matched
                }
            }
        """)
        assertTrue(computes(main).isEmpty())
        assertEquals(2, main.optimizedIR!!.fusedFloatResults.size)
        assertEquals(listOf("matched"), execute(main).messages)
    }

    @Test fun nonFiniteIntermediatesFollowNativeNestedEvaluation() = isolated {
        for (level in 0..1) {
            val main = compile("""
                func main(){
                    external a=1.0; external zero=0.0;
                    var result=(a/zero)+1.0;
                    /data modify storage fixture:fusion result set from storage mcfpp:system stack_frame[0].result
                }
            """, level)
            assertEquals(if (level == 0) 1f else 0f, value(execute(main), "result"))
        }
    }

    @Test fun loopBackedgesKeepTheResultBetweenIterations() = isolated {
        for (level in 0..1) {
            val main = compile("""
                func main(){
                    external a=1.0; external index=0;
                    while(index < 3){ a=(a+1.0)*2.0; index+=1; }
                    /data modify storage fixture:fusion result set from storage mcfpp:system stack_frame[0].a
                }
            """, level)
            assertEquals(22f, value(execute(main), "result"))
            assertEquals(level != 0, main.optimizedIR!!.fusedFloatResults.isNotEmpty())
        }
    }

    @Test fun legacyTargetsDoNotSelectProviderFusion() = isolated {
        val main = compile("""
            func main(){ external a=2.0; var result=(a+1.0)*2.0; }
        """, version = "1.20.1")
        assertTrue(main.optimizedIR!!.fusedFloatResults.isEmpty())
        assertTrue(computes(main).isEmpty())
    }
}
