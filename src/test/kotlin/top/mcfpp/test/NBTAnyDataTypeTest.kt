package top.mcfpp.test

import top.mcfpp.test.util.MCFPPStringTest
import kotlin.test.Test

class NBTTypeTest {

    @Test
    fun listTest(){
        val test =
            """
                func main(){
                    list<int> l = [];
                    l.add(1);
                    l.add(2);
                    print(l.getJavaVar());
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test, arrayOf("-debug"))
        println()
    }

    @Test
    fun listAPITest(){
        val test = """
            func main(){
                list<int> l = [1,2,3,4];
                list<int> l1 = [5,6,7];
                l.addAll(l1);
                l.insert(0,114514);
                l.removeAt(1);
                print(l::jvm);
                print(l.indexOf(2));
                print(l.contains(8));
                print(l.contains(2));
                l.clear();
                print(l::jvm);
            }
        """.trimIndent()
        MCFPPStringTest.readFromString(test, arrayOf("-debug","-printAll"))
        println()
    }

    @Test
    fun listAPITest2(){
        val test = """
            func main(){
                dynamic list<int> l = [1,2,3,4];
                dynamic list<int> l1 = [5,6,7];
                l.addAll(l1);
                l.insert(0,114514);
                l.removeAt(1);
                print(l::jvm);
                print(l.indexOf(2));
                print(l.contains(8));
                print(l.contains(2));
                l.clear();
                print(l::jvm);
            }
        """.trimIndent()
        MCFPPStringTest.readFromString(test, arrayOf("-debug","-printAll"))
        println()
    }

}