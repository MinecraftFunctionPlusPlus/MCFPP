package top.mcfpp.test

import top.mcfpp.test.util.MCFPPStringTest
import kotlin.test.Test

class DataTemplateTest {
    @Test
    fun basicTest(){
        val test =
            """
                data Test{
                    nbt qwq;
                    
                    func test(){}
                }
                
                func main(){
                    Test t = {qwq:1};
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

}