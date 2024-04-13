package top.mcfpp.test

import kotlin.test.Test

class CompileTest {

    @Test
    fun baseTest(){
        val test =
            """
                func base(){
                    int i = 0;
                    dynamic int p;
                    i = i + p;
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
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
                    dynamic int qwq;
                    test(qwq);
                }
                
                func test(int qwq){
                    qwq = qwq + 1;
                    print(qwq);
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

    @Test
    fun genericFunctionTest(){
        val test =
            """
                func test<int i>(int p){
                     print(i);
                     print(p);
                }

                func main(){
                    dynamic int qwq;
                    test<2>(qwq);
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

    @Test
    fun functionReturnTest(){
        val test =
            """
                func test<int i>()->int{
                     i = i + 1;
                     return i;
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
    fun typeFieldTest(){
        val test =
            """
                func main(){
                    var q = test<2>();
                    print(q);
                    print(test<6>());
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

}