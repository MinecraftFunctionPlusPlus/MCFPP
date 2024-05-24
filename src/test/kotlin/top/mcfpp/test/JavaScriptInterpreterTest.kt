package top.mcfpp.test

import top.mcfpp.compiletime.JavaScriptInterpreter
import kotlin.test.Test

class JavaScriptInterpreterTest {
    @Test
    fun test() {
        JavaScriptInterpreter.eval("print(\"hello world\")")
    }
}

