package top.mcfpp.lib

import top.mcfpp.lang.CanSelectMember
import top.mcfpp.lang.JavaVar
import top.mcfpp.lang.Var
import java.lang.reflect.Method

class JavaFunction: Function {

    val method:Method

    val caller: JavaVar

    constructor(method: Method, caller: JavaVar):super(method.name){
        this.method = method
        this.caller = caller
    }

    override fun invoke(args: ArrayList<Var<*>>, caller: CanSelectMember?) {
        //将变量转换为Java变量
        val javaVars = JavaVar.mcToJava(args)
        returnVar = JavaVar(method.invoke(this.caller.javaValue, *javaVars.toArray()))
    }
}