package top.alumopper.mcfpp.lib

import top.alumopper.mcfpp.Project
import top.alumopper.mcfpp.command.Commands
import top.alumopper.mcfpp.lang.*
import java.util.*
import kotlin.collections.ArrayList

/**
 * 一个构造函数
 */
open class Constructor    //检查此类中是否已经重复定义一个相同的构造函数
    (
    /**
     * 此构造函数对应的类。
     */
    var target: Class
) : Function("_init_" + target.identifier.lowercase(Locale.getDefault()) + "_" + target.constructors.size, target, false) {
    /**
     * 调用构造函数
     * @param args 函数的参数
     * @param lineNo 调用此函数的上下文的行数，用于错误日志
     * @param cls 调用函数的实例
     */
    @Override
    override operator fun invoke(args: ArrayList<Var>, lineNo: Int, cls: ClassPointer) {
        //对象创建
        Function.addCommand(
            "execute in minecraft:overworld " +
                    "run summon marker 0 0 0 {Tags:[" + cls.obj!!.tag + ",mcfpp_classPointer_just],data:{pointers:[]}}"
        )
        //给函数开栈
        Function.addCommand("data modify storage mcfpp:system " + Project.name + ".stack_frame prepend value {}")
        //参数传递
        for (i in 0 until params.size) {
            when (params[i].type) {
                "int" -> {
                    val tg = args[i].cast(params[i].type) as MCInt
                    //参数传递和子函数的参数压栈
                    Function.addCommand(
                        "execute store result storage mcfpp:system " + Project.name + ".stack_frame[0]." + params[i].identifier + " run "
                                + Commands.SbPlayerOperation(MCInt("_param_" + params[i].identifier, this), "=", tg)
                    )
                }
            }
        }
        //函数调用的命令
        //不应当立即调用它自己的函数，应当先调用init，再调用constructor
        Function.addCommand(
            "execute as @e[tag=" + cls.obj!!.tag + ",tag=mcfpp_classPointer_just,limit=1] at @s run " +
                    Commands.Function(parentClass!!.classPreInit!!)
        )
        Function.addCommand(
            "execute as @e[tag=" + cls.obj!!.tag + ",tag=mcfpp_classPointer_just,limit=1] at @s run " +
                    Commands.Function(this)
        )
        //调用完毕，将子函数的栈销毁
        Function.addCommand("data remove storage mcfpp:system " + Project.name + ".stack_frame[0]")
        //取出栈内的值到记分板
        for (`var` in Function.currFunction!!.cache.allVars) {
            if (`var` is MCInt) {
                //如果是int取出到记分板
                Function.addCommand(
                    "execute store result score " + `var`.identifier + " " + `var`.`object` + " run "
                            + "data get storage mcfpp:system " + Project.name + ".stack_frame[0]." + `var`.key
                )
            }
        }
        //临时指针的创建
        Function.addCommand("scoreboard players operation " + cls.address.identifier + " " + cls.address.`object`.name + " = @e[tag=" + cls.obj!!.tag + ",tag=mcfpp_classPointer_just,limit=1] " + cls.address.`object`.name)
        //去除临时标签
        Function.addCommand("tag remove @e[tag=" + cls.obj!!.tag + ",tag=mcfpp_classPointer_just,limit=1] mcfpp_classPointer_just")
    }

    @get:Override
    override val prefix: String
        get() = namespace + "_class_" + target.identifier + "_init_"

    @Override
    override fun equals(other: Any?): Boolean {
        if (other is Constructor) {
            if (other.parentClass?.equals(parentClass) == true) {
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
        return target.hashCode() ?: 0
    }
}