package top.mcfpp.test

import kotlin.test.Test

class CompileTimeTest {
    @Test
    fun test() {
        println(""" """")
        val code = """
            func main(){
                test();
            }
            const func test(){
                int i=1;
                if(i == 1){
                    print(0);
                }
                else{
                    print(2);
                }
                while(i!=10){
                    i=i+1;
                    if(i==4){
                        continue;
                    }
                    else if(i==7){
                        break;
                    }
                    print(i);
                    
                }
                for(int j=0;j<3;j=j+1){
                    insert(${"\"\"\""}${'$'}{j}${"\"\"\""});
                }
            }
        """.trimIndent()
        //MCFPPStringTest.readFromString(code)
        MCFPPStringTest.readFromSingleFile("./test/test.mcfpp")
    }
}