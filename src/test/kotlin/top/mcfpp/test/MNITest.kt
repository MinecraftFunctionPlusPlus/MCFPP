package top.mcfpp.test

import top.mcfpp.test.util.MCFPPStringTest
import kotlin.test.Test

class MNITest {

    @Test
    fun baseMNITest(){
        val test =
            """
                func base(){
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

}