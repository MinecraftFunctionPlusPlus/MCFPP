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

    @Test
    fun unionTest(){
        val test =
            """
                data Test{
               
                    (int|string) qwq;
                    
                }
                
                func main(){
                    Test t = Test();
                    print((int)t.qwq);
                    print((string)t.qwq);
                    print(t);
                    t.qwq = 6;
                    print((int)t.qwq);
                    print(t);
                    t.qwq = "test";
                    print((string)t.qwq);
                    print(t);
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test, arrayOf("debug"))
    }

}