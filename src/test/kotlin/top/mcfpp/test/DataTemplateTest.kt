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
                    print(t.qwq);
                    print(t);
                    t.qwq = 6;
                    print(t.qwq);
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

}