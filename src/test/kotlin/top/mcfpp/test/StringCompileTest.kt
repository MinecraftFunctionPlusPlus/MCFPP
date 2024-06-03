package top.mcfpp.test

import kotlin.test.Test
import top.mcfpp.test.util.MCFPPStringTest

class StringCompileTest {

    @Test
    fun main(){
        val string =
            """
                func main(){test();}
                
                func test(){}
            """.trimIndent()
        MCFPPStringTest.readFromString(string)
    }
}