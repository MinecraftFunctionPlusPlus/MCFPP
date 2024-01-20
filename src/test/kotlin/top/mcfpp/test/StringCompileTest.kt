package top.mcfpp.test

import kotlin.test.Test

class StringCompileTest {

    @Test
    fun main(){
        val string =
            """
                void main(){
                    dynamic int a;
                    print(a);
                    a = a + 1;
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(string)
    }
}