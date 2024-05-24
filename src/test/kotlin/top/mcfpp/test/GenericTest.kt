package top.mcfpp.test

import kotlin.test.Test

class GenericTest {

    @Test
    fun genericFunctionTest() {
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
    fun typeFieldTest() {
        val test =
            """
                func main(){
                    type qwq = int;
                    test<qwq>();
                }
                
                func test<type T>(){
                    T i = 5;
                    print(i);
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

    @Test
    fun classGenericTest() {
        val test =
            """
                class Test<type T>{
                    public T i;
                }
                
                func main(){
                    var test = Test<int>();
                    test.i = 5;
                    print(test.i);
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

    @Test
    fun classOverLoad() {
        val test =
            """
                class Test<int i>{
                    func print(){
                        print(this.i);
                    }
                }
                
                class Test<int i, int j>{
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