package top.mcfpp.model.function

import top.mcfpp.lang.CanSelectMember
import top.mcfpp.lang.JavaVar
import top.mcfpp.lang.Var
import java.lang.reflect.Method

class JavaFunction : Function {

    val method: Method

    val caller: JavaVar

    constructor(method: Method, caller: JavaVar) : super(method.name) {
        this.method = method
        this.caller = caller
    }

    override fun invoke(/*readOnlyArgs: ArrayList<Var<*>>, */normalArgs: ArrayList<Var<*>>, caller: CanSelectMember?) {
        //将变量转换为Java变量
        /*val readOnlyJavaVars = JavaVar.mcToJava(readOnlyArgs)*/
        val normalJavaVars = JavaVar.mcToJava(normalArgs)
        returnVar =
            JavaVar(method.invoke(this.caller.value,/* *readOnlyJavaVars.toArray(), */*normalJavaVars.toArray()))
    }
}