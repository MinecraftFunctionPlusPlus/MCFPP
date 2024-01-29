package top.mcfpp.exception

import kotlin.reflect.KClass

class ParserException: RuntimeException {
    constructor():super(){}
    constructor(message:String):super(message){

    }
    companion object{
        fun <T : Any> at(e: KClass<T>):ParserException{
            return ParserException(e::qualifiedName.name)
        }
    }
}