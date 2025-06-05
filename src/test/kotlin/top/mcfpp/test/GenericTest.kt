package top.mcfpp.test

import top.mcfpp.test.util.MCFPPStringTest
import kotlin.test.Test

class GenericTest {

    @Test
    fun genericFunctionTest(){
        val test =
            """
                func test<i as int>(p as int){
                     print(i);
                     print(p);
                }

                func main(){
                    dynamic var qwq as int;
                    test<2>(qwq);
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

    @Test
    fun typeFieldTest(){
        val test =
            """
                func main(){
                    var qwq = int;
                    test<qwq>();
                }
                
                func test<T as type>(){
                    var i as T = 5;
                    print(i);
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test, arrayOf("debug"))
    }

    @Test
    fun classGenericTest(){
        val test =
            """
                class Test<T as type>{
                    public i as T;
                }
                
                func main(){
                    var test = Test<int>();
                    test.i = 5;
                    print(test.i);
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test, arrayOf("debug"))
    }

    @Test
    fun classOverLoad(){
        val test =
            """
                class Test<i as int>{
                    func print(){
                        print(this.i);
                    }
                }
                
                class Test<i as int, j as int>{
                    func print(){
                        print(this.i + this.j);
                    }
                }
                
                func main(){
                    var test = Test<5>();
                    var test2 = Test<5, 6>();
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

}