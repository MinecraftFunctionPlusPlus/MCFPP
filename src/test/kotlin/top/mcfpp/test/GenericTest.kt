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
}