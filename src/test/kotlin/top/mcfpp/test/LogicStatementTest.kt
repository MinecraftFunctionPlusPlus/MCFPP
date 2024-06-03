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
                    }else{
                        print("i >= 7");
                    }
                    print("end");
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test, "D:\\.minecraft\\saves\\MCFPP Studio\\datapacks")
    }
}