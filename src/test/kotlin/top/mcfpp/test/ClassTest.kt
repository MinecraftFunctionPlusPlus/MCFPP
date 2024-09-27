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

    @Test
    fun propertyTest(){
        val test =
            """
                class Test{
                    int i {
                        get {
                            print("get i");
                            return this.i;
                        }
                        
                        set {
                            print("set i");
                            this.i = value;
                        }
                    };
                    
                    int j {
                        get {
                            print("get j");
                            return 0;
                        }
                    };
                    
                    int k {
                        set {
                            print("set k");
                            this.k = value;
                        }
                    };
                }
                
                func main{
                    Test test = Test();
                    print(test.i);
                    print(test.j);
                    print(test.k);
                    test.i = 4;
                    test.j = 5;
                    test.k = 7;
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test, arrayOf())
    }

}