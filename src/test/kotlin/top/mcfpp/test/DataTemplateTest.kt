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

    @Test
    fun extendTest(){
        val test =
            """
                data Test{
                    int qwq;
                }
                
                data Test2: Test{
                    string qwq2;
                }
                
                func main(){
                    Test2 t = {qwq:1,qwq2:"test"};
                    print(t.qwq);
                    print(t.qwq2);
                    print(t);
                    t.qwq = 6;
                    print(t.qwq);
                    print(t);
                    t.qwq2 = "test2";
                    print(t.qwq2);
                    print(t);
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

    @Test
    fun unionTemplateTest(){
        val test =
            """
                data Test1 {
                    int qwq;
                }
                
                data Test2 {
                    string qwq2;
                }
                
                func main(){
                    (Test1&Test2) t = {qwq:1, qwq2: "qwq"};
                    print(t);
                    print(t.qwq);
                    print(t.qwq2);
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

}