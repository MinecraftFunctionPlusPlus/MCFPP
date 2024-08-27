package top.mcfpp.model.function

import top.mcfpp.model.CanSelectMember
import top.mcfpp.core.lang.JavaVar
import top.mcfpp.core.lang.Var
import java.lang.reflect.Method

class JavaFunction: Function {

    val method:Method

    val caller: JavaVar

    constructor(method: Method, caller: JavaVar):super(method.name, context = null){
        this.method = method
        this.caller = caller
    }

    override fun invoke(normalArgs: ArrayList<Var<*>>, caller: CanSelectMember?) {
        //将变量转换为Java变量
        val normalJavaVars = JavaVar.mcToJava(normalArgs)
        returnVar = JavaVar(method.invoke(this.caller.value, *normalJavaVars.toArray()))
    }
}