package top.mcfpp.test

import top.mcfpp.test.util.MCFPPStringTest
import kotlin.test.Test

class LogicStatementTest {

    @Test
    fun ifTest(){
        val test =
            """
                func base(){
                    dynamic int i = 5;
                    if(i < 7){
                        print("i < 7");
                        dynamic int p;
                    }else{
                        print("i >= 7");
                        dynamic int p;
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
                    dynamic int i = 0;
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
                dynamic int i = 0;
                do {
                    print(i);
                    i = i + 1;
                } while(i < 10);
            }
        """.trimIndent()
        MCFPPStringTest.readFromString(test, targetPath = "D:\\.minecraft\\saves\\MCFPP Studio\\datapacks")
    }

    @Test
    fun forTest() {
        val test =
            """
            func generateSequenceFor(){
                for(dynamic int i = 0; i < 10; i = i + 1){
                    print(i);
                }
            }
        """.trimIndent()
        MCFPPStringTest.readFromString(test, targetPath = "D:\\.minecraft\\saves\\MCFPP Studio\\datapacks")
    }
}