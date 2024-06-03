package top.mcfpp.test

import kotlin.test.Test
import top.mcfpp.test.util.MCFPPStringTest

class NBTTypeTest {

    @Test
    fun listTest(){
        val test =
            """
                func main(){
                    list<int> l = [];
                    l.add(1);
                    l.add(2);
                    print(l.getJavaVar());
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
        println()
    }

}