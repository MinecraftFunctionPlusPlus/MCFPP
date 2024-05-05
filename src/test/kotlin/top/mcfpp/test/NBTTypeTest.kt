package top.mcfpp.test

import top.mcfpp.lib.field.GlobalField
import kotlin.test.Test

class NBTTypeTest {

    @Test
    fun listTest(){
        val test =
            """
                func main(){
                    list<int> l = [];
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
        println()
    }

}