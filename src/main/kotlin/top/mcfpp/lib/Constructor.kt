package top.mcfpp.lib

import top.mcfpp.Project
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.command.Commands
import top.mcfpp.lang.*
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

    init {
        //添加this指针
        val thisObj = ClassPointer(target,"this")
        thisObj.identifier = "this"
        field.putVar("this",thisObj)
    }

    /**
     * 调用构造函数。类的实例的实体的生成，类的初始化（preinit和init函数），自身的调用和地址分配都在此方法进行。
     * @param args 函数的参数
     * @param cls 构造方法将要构建的对象的临时指针
     */
    @Override
    @InsertCommand
    override fun invoke(args: ArrayList<Var>, cls: ClassBase?) {
        cls as ClassPointer
        //获取所有函数
        val funcs = StringBuilder("functions:{")
        target.field.forEachFunction { f ->
            run {
                funcs.append("${f.identifier}:\"${f.namespaceID}\",")
            }
        }
        funcs.append("}")
        //对象实体创建
        addCommand(
            "execute in minecraft:overworld " +
                    "run summon marker 0 1 0 {Tags:[" + cls.tag + ",mcfpp_classObject_just],data:{pointers:[],$funcs}}"
        )

        //初始化
        if(target.classPreInit.commands.size > 3){
            //给函数开栈
            addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")
        }
        //不应当立即调用它自己的函数，应当先调用init，再调用constructor
        addCommand(
            "execute as @e[tag=" + cls.tag + ",tag=mcfpp_classObject_just,limit=1] at @s run " +
                    Commands.Function(target.classPreInit)
        )
        if(target.classPreInit.commands.size > 3){
            //调用完毕，将子函数的栈销毁
            addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")
        }
        //给函数开栈，调用构造函数
        addCommand("data modify storage mcfpp:system " + Project.defaultNamespace + ".stack_frame prepend value {}")

        //传入this参数

        val thisPoint = field.getVar("this")!! as ClassPointer
        thisPoint.assign(cls)

        //参数传递
        for (i in 0 until params.size) {
            when (params[i].type) {
                "int" -> {
                    val tg = args[i].cast(params[i].type) as MCInt
                    //参数传递和子函数的参数压栈
                    addCommand(
                        "execute store result storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]." + params[i].identifier + " int 1 run "
                                + Commands.SbPlayerOperation(MCInt(this,"_param_" + params[i].identifier), "=", tg)
                    )
                }
            }
        }

        addCommand(Commands.Function(this))

        //销毁指针，释放堆内存
        for (p in field.allVars){
            if (p is ClassPointer){
                p.dispose()
            }
        }
        //调用完毕，将子函数的栈销毁
        addCommand("data remove storage mcfpp:system " + Project.defaultNamespace + ".stack_frame[0]")


        //取出栈内的值到记分板
        addCommand("#[Function ${this.namespaceID}] Take vars out of the Stack")
        for (i in 0 until params.size) {
            when (params[i].type) {
                "int" -> {
                    val tg = args[i].cast(params[i].type) as MCInt
                    //参数传递和子函数的参数压栈
                    //如果是int取出到记分板
                    addCommand(
                        "execute store result score ${tg.name} ${tg.`object`} run "
                                + "data get storage mcfpp:system ${Project.defaultNamespace}.stack_frame[0].int.${tg.identifier}"
                    )
                }
                else -> {
                    //是引用类型
                    val tg = args[i].cast(params[i].type) as ClassBase
                    addCommand(
                        "execute store result score ${tg.address.name} ${tg.address.`object`} run "
                                + "data get storage mcfpp:system ${Project.defaultNamespace}.stack_frame[0].int.${tg.identifier}"
                    )
                }
            }
        }
        //去除临时标签
        addCommand("tag @e[tag=" + cls.tag + ",tag=mcfpp_classObject_just,limit=1] remove mcfpp_classObject_just")
    }

    @get:Override
    override val prefix: String
        get() = namespace + "_class_" + target.identifier + "_init_"

    @Override
    override fun equals(other: Any?): Boolean {
        if (other is Constructor) {
            if (other.target == target) {
                if (other.params.size == params.size) {
                    for (i in 0 until other.params.size) {
                        if (other.params[i] != params[i]) {
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
}