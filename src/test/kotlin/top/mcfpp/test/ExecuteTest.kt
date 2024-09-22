package top.mcfpp.test

import top.mcfpp.test.util.MCFPPStringTest
import kotlin.test.Test

class ExecuteTest {

    @Test
    fun test1(){
        val test =
            """
                func main(){
                    execute(pos = ~ ~1 ~){
                        /say hi
                        /say qwq
                    }
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

}