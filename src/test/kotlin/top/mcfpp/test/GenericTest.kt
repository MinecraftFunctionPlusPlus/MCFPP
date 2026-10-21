package top.mcfpp.test

import top.mcfpp.test.util.MCFPPStringTest
import kotlin.test.Test

class GenericTest {

    @Test
    fun genericFunctionTest(){
        val test =
            """
                func test<i: int>(p: int){
                     print(i)
                     print(p)
                }

                func main(){
                    external qwq: int
                    test<2>(qwq)
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

    @Test
    fun typeFieldTest(){
        val test =
            """
                func main(){
                    test<int>()
                }
                
                func test<T>(){
                    var i: T = 5
                    print(i)
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test, arrayOf("debug"))
    }
}