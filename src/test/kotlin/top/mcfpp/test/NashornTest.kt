package top.mcfpp.test

import javax.script.ScriptEngineManager


object Hello {
    @Throws(Throwable::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val engineManager = ScriptEngineManager()
        val engine = engineManager.getEngineByName("nashorn")
        engine.eval("function sum(a, b) { return a + b; }")
        println(engine.eval("sum(1, 2);"))
    }
}