package top.mcfpp.test

import top.mcfpp.test.util.MCFPPStringTest
import kotlin.test.Test

class BaseTest {

    @Test
    fun baseTest(){
        val test =
            """
                func base(){
                    var i = 0;
                    import var p as int;
                    i = i + p;
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test, arrayOf("debug"))
    }

    @Test
    fun helloTest(){
        val test =
            """
                func hello(){
                    print("Hello World");
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

    @Test
    fun functionTest(){
        val test =
            """
                func main(){
                    dynamic var qwq as int;
                    test(qwq);
                }
                
                func test(qwq as int){
                    var a = qwq + 1;
                    print(a);
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test, arrayOf("debug"))
    }

    @Test
    fun functionReturnTest(){
        val test =
            """
                func test<i as int>()->int{
                     var p = i + 1;
                     return p;
                }

                func main(){
                    var q = test<2>();
                    print(q);
                    print(test<6>());
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

    @Test
    fun funcDefaultValueTest(){
        val test =
            """
                func test(i as int = 1) -> int{
                     var p = i + 1;
                     return p;
                }

                func main(){
                    var a = test(10);
                    print(a);
                    var b = test();
                    print(a);
                    print(b);
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test, arrayOf("debug"))
    }

    @Test
    fun topStatementTest(){
        val test =
            """
                int i = 0;
                dynamic int qwq;
                print(i);
                print(qwq);
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

    @Test
    fun concreteTest(){
        val test =
            """
                func test() -> int!{
                    dynamic var a = 1;
                    return a;
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

}