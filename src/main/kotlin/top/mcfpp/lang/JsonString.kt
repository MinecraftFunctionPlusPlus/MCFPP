package top.mcfpp.lang

class JsonString : Var{

    val jsonText : JsonText

    constructor(jsonText: JsonText){
        this.jsonText = jsonText
    }

    constructor(jsonString: JsonString):super(jsonString){
        jsonText = jsonString.jsonText
    }

    override val type: String
        get() = "jstring"

    override fun assign(b: Var?) {
        TODO("Not yet implemented")
    }

    override fun cast(type: String): Var? {
        TODO("Not yet implemented")
    }

    override fun clone(): Any {
        TODO("Not yet implemented")
    }

    override fun getTempVar(cache: HashMap<Var, String>): Var {
        TODO("Not yet implemented")
    }
}