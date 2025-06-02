package top.mcfpp.test

import top.mcfpp.test.util.MCFPPStringTest
import kotlin.test.Test

class ClassTest {
    @Test
    fun defineTest(){
        val test =
            """
                class Test {
                    i as int = 0;
                    
                    constructor() {
                        Test.id = Test.id + 1;
                        this.i = Test.id;
                    }
                }
                
                object class Test{
                    id as int = 0;
                }
                
                func main(){
                    var test = Test();
                    print(test.i);
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test, arrayOf("debug"))
    }

    @Test
    fun propertyTest(){
        val test =
            """
                class Test{
                    i as int {
                        get {
                            print("get i");
                            return field;
                        }
                        
                        set {
                            print("set i");
                            field = value;
                        }
                    };
                    
                    j as int {
                        get {
                            print("get j");
                            return 0;
                        }
                    };
                    
                    k as int {
                        set {
                            print("set k");
                            field = value;
                        }
                    };
                }
                
                func main{
                    var test = Test();
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