package top.mcfpp.lang

import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lib.Class
import top.mcfpp.lib.ClassMember
import top.mcfpp.lib.Function
import java.util.Objects


//TODO
//一个目标选择器
class Selector : CanSelectMember {

    @get:Override
    override var type = "selector"
    var text: String? = null
    var selectorType: String? = null
    var selectorArgs: HashMap<String, Any> = HashMap()

    var singleEntity: Boolean = false

    constructor(b:Var):super(b){

    }

    constructor(string: String){
        text = string
        //@a[a=b,b=c]
        //解析参数
        val args = string.split("[", "]")
        selectorType = args[0]
        if (args.size != 1){
            //解析参数
        }
    }

    @Override
    override fun assign(b: Var?) {
        if (b is MCString) {
            text = b.toString()
        } else {
            throw VariableConverseException()
        }
    }

    @Override
    override fun cast(type: String): Var? {
        return null
    }

    @Override
    override fun clone(): Selector {
        TODO()
    }

    override fun getTempVar(cache: HashMap<Var, String>): Var {
        TODO("Not yet implemented")
    }

    override fun getMemberVar(key: String, accessModifier: ClassMember.AccessModifier): Pair<Var?, Boolean> {
        TODO("Not yet implemented")
    }

    override fun getMemberFunction(key: String, params: List<String>, accessModifier: ClassMember.AccessModifier): Pair<Function?, Boolean> {
        TODO("Not yet implemented")
    }

    fun addParam(key: String, value: String){

    }

    companion object{

        val paramMap: HashMap<String, (value: String) -> Any> = hashMapOf(
            "x" to {value: String -> value.toDouble() },
            "y" to {value: String -> value.toDouble() },
            "z" to {value: String -> value.toDouble() },
            "dx" to {value: String -> value.toDouble() },
            "dy" to {value: String -> value.toDouble() },
            "dz" to {value: String -> value.toDouble() },
            "distance" to {value: String -> run {
                
            }},
            )
        /**
         * 解析参数
         *
         * @param string
         * @return 返回一个Pair，第一个值是参数的key，第二个值是参数的value
         */
        fun resolveParam(string: String): Pair<String, String>{
            val args = string.split("=")
            return Pair(args[0], args[1])
        }
    }
}