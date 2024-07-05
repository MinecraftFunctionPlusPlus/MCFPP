package top.mcfpp.test

import top.mcfpp.test.util.MCFPPStringTest
import kotlin.test.Test

class ClassTest {
    @Test
    fun defineTest(){
        val test =
            """
                class Test{
                    int i = 0;
                    
                    constructor(){
                        Test.id = Test.id + 1;
                        this.i = Test.id;
                    }
                }
                
                object class Test{
                    int id = 0;
                }
                
                func main{
                    Test test = Test();
                    print(test.i);
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

}