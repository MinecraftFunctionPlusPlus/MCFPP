package top.mcfpp.lang.annotation

import top.mcfpp.Project
import top.mcfpp.lang.MCString
import top.mcfpp.lib.Class
import top.mcfpp.lib.ClassAnnotation
import top.mcfpp.lib.CompoundData
import top.mcfpp.util.LogProcessor
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.declaredMemberProperties

class Inject : ClassAnnotation {

    val data: CompoundData

    constructor(targetClass: MCString): super("Inject", "mcfpp.lang.annotation"){
        if(!targetClass.isConcrete){
            LogProcessor.error("Cannot pass a non-concrete value to a concrete parameter")
        }
        //找到目标类
        val clazz = java.lang.Class.forName(targetClass.value!!.toString()).kotlin
        data = clazz.companionObject!!.declaredMemberProperties.find { it.name == "data" }!!.getter.call(null) as CompoundData
    }

    override fun forClass(clazz: Class) {
        //将class中的内容注入到目标

    }
}