package top.mcfpp.test

import top.mcfpp.test.util.MCFPPStringTest
import kotlin.test.Test

class NBTTypeTest {

    @Test
    fun listTest(){
        val test =
            """
                func main(){
                    var l = [] as list<int>;
                    l.add(1);
                    l.add(2);
                    print(l::jvm);
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test, arrayOf("-debug"))
        println()
    }

    @Test
    fun listAPITest(){
        val test = """
            func main(){
                var l as list<int> = [1,2,3,4];
                var l1 as list<int> = [5,6,7];
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
                dynamic var l as list<int> = [1,2,3,4];
                dynamic var l1 as list<int> = [5,6,7];
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