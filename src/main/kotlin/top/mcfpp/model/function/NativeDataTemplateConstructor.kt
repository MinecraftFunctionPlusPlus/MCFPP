package top.mcfpp.model.function

import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Native
import top.mcfpp.model.compound.DataTemplate
import java.lang.reflect.Method
import kotlin.reflect.jvm.javaMethod

class NativeDataTemplateConstructor(data: DataTemplate, javaMethod: Method = NativeFunction.Companion::defaultNativeFunction.javaMethod!!): DataTemplateConstructor(data, null), Native {

    /**
     * 要调用的java方法
     */
    @Suppress("JoinDeclarationAndAssignment")
    @Transient
    var javaMethod: Method

    /**
     * 引用的java方法名。
     */
    var javaMethodName: String

    init {
        this.javaMethod = javaMethod
        javaMethodName = javaMethod.name
    }

    override fun invoke(normalArgs: LinkedHashMap<String, Var<*>>, caller: CanSelectMember?): Var<*> {
        caller as DataTemplateObject
        //初始化
        for ((k, v) in data.preInit) {
            val init = MCFPPExprVisitor().visitExpression(v)
            val field = DataTemplate.getField(caller, k)!!
            field.replacedBy(field.assignedBy(init))
        }
        javaMethod.invoke(null, *normalArgs.values.toTypedArray(), caller)
        return caller
    }

}