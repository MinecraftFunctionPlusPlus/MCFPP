package top.mcfpp.test

import top.mcfpp.test.util.MCFPPStringTest
import kotlin.test.Test

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
        MCFPPStringTest.readFromString(test, targetPath = "D:\\.minecraft\\saves\\MCFPP Studio\\datapacks")
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
        MCFPPStringTest.readFromString(test, targetPath = "D:\\.minecraft\\saves\\MCFPP Studio\\datapacks")
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
        MCFPPStringTest.readFromString(test, targetPath = "D:\\.minecraft\\saves\\MCFPP Studio\\datapacks")
    }
}