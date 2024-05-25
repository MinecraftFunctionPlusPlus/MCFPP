package top.mcfpp.test

import kotlin.test.Test

class StringCompileTest {

    @Test
    fun main() {
        val string =
            """
                func main(){test();}
                
                func test(){}
            """.trimIndent()
        MCFPPStringTest.readFromString(string)
    }
}