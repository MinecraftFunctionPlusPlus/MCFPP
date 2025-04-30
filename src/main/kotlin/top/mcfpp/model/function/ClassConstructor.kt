package top.mcfpp.model.function

import top.mcfpp.annotations.InsertCommand
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.obj.ClassPointer
import top.mcfpp.core.lang.Var
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.compound.Class
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.type.MCFPPType
import java.util.*

/**
 * 一个构造函数。它是一个特殊的成员方法，将会在类的初始化阶段之后调用。
 */
open class ClassConstructor(var target: Class)
    : Function(
    "init_${target.identifier.lowercase(Locale.getDefault())}_${target.constructors.size}",
    target,
    context = null
) {

    private val leadFunction: Function by lazy {
        val f = Function(this.identifier + "_lead",this.namespace, context = null).apply {
            owner = this@ClassConstructor.target
            ownerType = Companion.OwnerType.CLASS
        }
        f.runInFunction {
            //获取所有函数
            val funcs = StringBuilder("functions:{")
            target.field.forEachFunction { f ->
                run {
                    funcs.append("${f.identifier}:\"${f.namespaceID}\",")
                }
            }
            funcs.append("}")
            //对象实体创建
            when (target.baseEntity) {
                Class.ENTITY_MARKER -> {
                    addCommand("data merge entity @s {Tags:[${target.tag},${target.tag}_data,mcfpp_ptr${if(!target.isStaticClass)",mcfpp_gc" else ""},just],data:{$funcs}}")
                }
                Class.ENTITY_ITEM_DISPLAY -> {
                    addCommand("data modify entity @s item.components.\"minecraft:custom_data\".mcfppData set value {Tags:[${target.tag},${target.tag}_data,mcfpp_ptr${if(!target.isStaticClass)",mcfpp_gc" else ""},just],data:{$funcs}}")
                }
                else -> {
                    addCommand("tag @s add ${target.tag}")
                    addCommand("summon marker ~ ~ ~ {Tags:[${target.tag}_data,mcfpp_ptr${if(!target.isStaticClass)",mcfpp_gc" else ""},just],data:{$funcs}}")
                    addCommand("ride @n[tag=just, type=marker] mount @s")
                    addCommand("tag @n[tag=just, type=marker] remove just")
                }
            }
            //初始指针
            addCommand(Command("data modify").build(Class.tempPtr.toCommandPart()).build("set from entity @s UUID"))
            //初始化
            if(target.classPreInit.commands.size > 0){
                //给函数开栈
                addCommand(Commands.stackIn())
                //不应当立即调用它自己的函数，应当先调用init，再调用constructor
                addCommand(Commands.function(target.classPreInit))
                //调用完毕，将子函数的栈销毁
                addCommand(Commands.stackOut())
            }
            //调用构造函数
            if(target.baseEntity != Class.ENTITY_MARKER && target.baseEntity != Class.ENTITY_ITEM_DISPLAY){
                addCommand("execute on passengers run function ${this.namespaceID}")
            }else{
                addCommand(Commands.function(this))
            }
            //销毁指针，释放堆内存
            for (p in field.allVars){
                if (p is ClassPointer){
                    p.dispose()
                }
            }
        }
        target.field.addFunction(f,false)
        f
    }
    init {
        //添加this指针
        val thisObj = ClassPointer(target,"this")
        thisObj.nbtPath = NBTPath.getNormalStackPath(thisObj)
        field.putVar("this",thisObj)
    }

    /**
     * 调用构造函数。类的实例的实体的生成，类的初始化（preinit和init函数），自身的调用和地址分配都在此方法进行。
     * @param normalArgs 函数的参数
     * @param callerClassP 构造方法将要构建的对象的临时指针
     */
    @Override
    @InsertCommand
    override fun invoke(normalArgs: List<Var<*>>, callerClassP: ClassPointer) {
        //参数传递
        addCommand(Commands.stackIn())
        argPass(normalArgs)
        addCommand("execute in minecraft:overworld summon ${target.baseEntity} run function " + leadFunction.namespaceID)
        //取出栈内的值
        fieldRestore()
        addCommand(Commands.stackOut())
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
        if (other is ClassConstructor) {
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

    fun isSelf(d: CompoundData, normalParams: List<MCFPPType>) : Boolean{
        if (this.target == d && this.normalParams.size == normalParams.size) {
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