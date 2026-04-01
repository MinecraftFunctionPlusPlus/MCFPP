package top.mcfpp.test

import top.mcfpp.test.util.MCFPPStringTest
import kotlin.test.Test

class DataTemplateTest {
    @Test
    fun basicTest(){
        val test =
            """
                data Test{
                    qwq as int;
                    func test(){}
                }
                
                func main(){
                    var t = {qwq:1} as Test;
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
                    qwq as (int|string);
                }
                
                func main(){
                    var t = Test();
                    print(t.qwq as int);
                    print(t.qwq as string);
                    print(t);
                    t.qwq = 6;
                    print(t.qwq as int);
                    print(t);
                    t.qwq = "test";
                    print(t.qwq as string);
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
                    qwq as int;
                }
                
                data Test2: Test{
                    qwq2 as string;
                }
                
                func main(){
                    var t = {qwq:1,qwq2:"test"} as Test2;
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
                    qwq as int;
                }
                
                data Test2 {
                    qwq2 as string;
                }
                
                func main(){
                    var t as (Test1&Test2) = {qwq:1, qwq2: "qwq"};
                    print(t);
                    print(t.qwq);
                    print(t.qwq2);
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

}