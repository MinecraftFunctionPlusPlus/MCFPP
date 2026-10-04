package top.mcfpp.test

import java.nio.file.Files
import top.mcfpp.Project
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogicStatementTest {

    @Test
    fun boolTest(){
        val test =
            """
                func main(){
                    var a = true;
                    dynamic var b = false;
                    var c = a && b;
                    var d = a || b;
                    var e = !a;
                    var f = !b;
                    print(c);
                    print(d);
                    print(e);
                    print(f);
                    dynamic var x as bool;
                    print(a && b || x && b);
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test, arrayOf("debug"))
    }

    @Test
    fun ifTest(){
        val test =
            """
                func base(){
                    dynamic var i = 5;
                    if(i < 7){
                        print("i < 7");
                        dynamic var p as int;
                    }else{
                        print("i >= 7");
                        dynamic var p as int;
                    }
                    print(p);
                    print("end");
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test, targetPath = Files.createTempDirectory("mcfpp-if-").toString())
    }

    @Test
    fun whileTest(){
        val test =
            """
                func generateSequence(){
                    dynamic var i = 0;
                    while(i < 10){
                        print(i);
                        i = i + 1;
                    }
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test, targetPath = Files.createTempDirectory("mcfpp-while-").toString())
    }

    @Test
    fun doWhileTest() {
        val test =
            """
            func generateSequenceDoWhile(){
                dynamic var i = 0;
                do {
                    print(i);
                    i = i + 1;
                } while(i < 10);
            }
        """.trimIndent()
        MCFPPStringTest.readFromString(test, targetPath = Files.createTempDirectory("mcfpp-do-while-").toString())
    }

    private fun executeOutput(output: java.nio.file.Path): ScoreCommandExecutor {
        val directory = output.resolve("debug/data/default.test/function")
        val functions = Files.walk(directory).use { paths ->
            paths.filter { it.toString().endsWith(".mcfunction") }.toList().associate {
                "default.test:" + directory.relativize(it).toString().removeSuffix(".mcfunction") to Files.readAllLines(it)
            }
        }
        return ScoreCommandExecutor(functions.getValue("default.test:nested"), functions).also {
            assertEquals(0, it.stackDepth)
        }
    }

    @Test
    fun nestedIfKeepsStatementsAfterOuterBranch() {
        for ((input, expected) in listOf(5 to 1, 1 to 2, -5 to 3)) {
            val output = Files.createTempDirectory("mcfpp-nested-if-")
            MCFPPStringTest.readFromString("""
                func nested(){
                    dynamic var n = $input;
                    if(n > 0){
                        if(n == 5){
                            /scoreboard players set #case result 1
                        }else{
                            /scoreboard players set #case result 2
                        }
                    }else{
                        /scoreboard players set #case result 3
                    }
                    /say continued
                }
            """.trimIndent(), targetPath = output.toString(), version = "26.3")
            assertEquals(0, Project.errorCount)
            val machine = executeOutput(output)
            assertEquals(expected, machine.values.getValue("#case result"))
            assertEquals(listOf("continued"), machine.messages)
        }
    }

    @Test
    fun nestedConstantIfDoesNotHideOuterElse() {
        val output = Files.createTempDirectory("mcfpp-nested-constant-")
        MCFPPStringTest.readFromString("""
            func nested(){
                dynamic var n = -5;
                if(n > 0){
                    if(true){
                        /scoreboard players set #case result -1
                    }
                }else{
                    /scoreboard players set #case result 9
                }
                /say continued
            }
        """.trimIndent(), targetPath = output.toString(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val machine = executeOutput(output)
        assertEquals(9, machine.values.getValue("#case result"))
        assertEquals(listOf("continued"), machine.messages)
    }
}
