package top.mcfpp.model.function

import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.command.Commands
import top.mcfpp.lang.*
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.model.Class
import java.util.*
import kotlin.collections.ArrayList

/**
 * 一个构造函数。它是一个特殊的成员方法，将会在类的初始化阶段之后调用。
 */
open class Constructor    //检查此类中是否已经重复定义一个相同的构造函数
    (
    /**
     * 此构造函数对应的类。
     */
    var target: Class
) : Function("_init_" + target.identifier.lowercase(Locale.getDefault()) + "_" + target.constructors.size, target, false) {

    private val leadFunction: Function
    init {
        //添加this指针
        val thisObj = ClassPointer(target,"this")
        thisObj.identifier = "this"
        field.putVar("this",thisObj)
        leadFunction = Function(this.identifier + "_lead",this.namespace, MCFPPBaseType.Void)
        target.field.addFunction(leadFunction,false)
    }

    /**
     * 调用构造函数。类的实例的实体的生成，类的初始化（preinit和init函数），自身的调用和地址分配都在此方法进行。
     * @param args 函数的参数
     * @param callerClassP 构造方法将要构建的对象的临时指针
     */
    @Override
    @InsertCommand
    override fun invoke(/*readOnlyArgs: ArrayList<Var<*>>, */normalArgs: ArrayList<Var<*>>, callerClassP: ClassPointer?) {
        callerClassP as ClassPointer
        addCommand("execute in minecraft:overworld positioned 0 1 0 summon marker run function " + leadFunction.namespaceID)
        val qwq = currFunction
        currFunction = leadFunction
        //获取所有函数
        val funcs = StringBuilder("functions:{")
        target.field.forEachFunction { f ->
            run {
                funcs.append("${f.identifier}:\"${f.namespaceID}\",")
            }
        }
        funcs.append("}")
        //对象实体创建
        addCommand("data merge entity @s {Tags:[${callerClassP.tag}],data:{$funcs}}")
        //初始指针
        addCommand("data modify storage mcfpp:system ${Project.currNamespace}.stack_frame[${callerClassP.stackIndex}].${callerClassP.identifier} set from entity @s UUID")
        //初始化
        if(target.classPreInit.commands.size > 0){
            //给函数开栈
            addCommand("data modify storage mcfpp:system " + Project.config.rootNamespace + ".stack_frame prepend value {}")
            //不应当立即调用它自己的函数，应当先调用init，再调用constructor
            addCommand(Commands.function(target.classPreInit))
            //调用完毕，将子函数的栈销毁
            addCommand("data remove storage mcfpp:system " + Project.config.rootNamespace + ".stack_frame[0]")
        }
        //给函数开栈，调用构造函数
        addCommand("data modify storage mcfpp:system " + Project.config.rootNamespace + ".stack_frame prepend value {}")
        //参数传递
        argPass(/*readOnlyArgs, */normalArgs)
        //调用构造函数
        addCommand("function " + this.namespaceID)
        //销毁指针，释放堆内存
        for (p in field.allVars){
            if (p is ClassPointer){
                p.dispose()
            }
        }
        //调用完毕，将子函数的栈销毁
        addCommand("data remove storage mcfpp:system " + Project.config.rootNamespace + ".stack_frame[0]")
        //取出栈内的值到记分板
        fieldRestore()
        currFunction = qwq
    }

    fun addParamsFromContext(ctx: mcfppParser.NormalParamsContext) {
        val n = ctx.parameterList()?:return
        for (param in n.parameter()) {
            val (p,v) = parseParam(param)
            normalParams.add(p)
            field.putVar(p.identifier, v)
        }
    }

    @get:Override
    override val prefix: String
        get() = namespace + "_class_" + target.identifier + "_init_"

    @Override
    override fun equals(other: Any?): Boolean {
        if (other is Constructor) {
            if (other.target == target) {
                if (other.normalParams.size == normalParams.size) {
                    for (i in 0 until other.normalParams.size) {
                        if (other.normalParams[i] != normalParams[i]) {
                            return false
                        }
                    }
                    return true
                }
            }
        }
        return false
    }

    override fun hashCode(): Int {
        return target.hashCode()
    }

    fun isSelf(cls: Class, normalParams: List<MCFPPType>) : Boolean{
        if (this.target == cls && this.normalParams.size == normalParams.size) {
            if (this.normalParams.size == 0) {
                return true
            }
            var hasFoundFunc = true
            //参数比对
            for (i in normalParams.indices) {
                if (!FunctionParam.isSubOf(normalParams[i],this.normalParams[i].type)) {
                    hasFoundFunc = false
                    break
                }
            }
            return hasFoundFunc
        }else{
            return false
        }
    }
}