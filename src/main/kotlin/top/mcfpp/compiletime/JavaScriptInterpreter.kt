package top.mcfpp.compiletime

import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

class JavaScriptInterpreter {
    companion object {
        val engine: ScriptEngine = ScriptEngineManager().getEngineByName("nashorn");
        fun eval(str: String) {
            engine.eval(str);
        }
    }

}