package top.mcfpp.test

import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.Project
import kotlin.test.Test
import kotlin.test.assertEquals

class NBTTypeTest {

    @Test
    fun listTest(){
        val test =
            """
                func main(){
                    var l = [] as list<int>
                    l.add(1)
                    l.add(2)
                    print(l)
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test, arrayOf("-debug"))
        assertEquals(0, Project.errorCount)
        println()
    }

    @Test
    fun listAPITest(){
        val test = """
            func main(){
                var l: list<int> = [1,2,3,4]
                var l1: list<int> = [5,6,7]
                l.addAll(l1)
                l.insert(0,114514)
                l.removeAt(1)
                print(l)
                print(l.indexOf(2))
                print(l.contains(8))
                print(l.contains(2))
                l.clear()
                print(l)
            }
        """.trimIndent()
        MCFPPStringTest.readFromString(test, arrayOf("-debug","-printAll"))
        assertEquals(0, Project.errorCount)
        println()
    }

    @Test
    fun listAPITest2(){
        val test = """
            func main(){
                external l: list<int> = [1,2,3,4]
                external l1: list<int> = [5,6,7]
                l.addAll(l1)
                l.insert(0,114514)
                l.removeAt(1)
                print(l)
                print(l.indexOf(2))
                print(l.contains(8))
                print(l.contains(2))
                l.clear()
                print(l)
            }
        """.trimIndent()
        MCFPPStringTest.readFromString(test, arrayOf("-debug","-printAll"))
        assertEquals(0, Project.errorCount)
        println()
    }

}
