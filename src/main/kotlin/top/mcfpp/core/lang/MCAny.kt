package top.mcfpp.core.lang

import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.model.FieldContainer
import top.mcfpp.model.compound.ObjectClass
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/**
 *
 * any是所有类型的基类。在mcfpp中，any的作用更像是将一个变量包装起来，将不同变量统一为一种类型。如果将一种类型转换为any类，它原则上只能被转换回原
 *变量能转换的类型。如果被转换为其他类型，编译器会发出一个警告。
 *
 *如果因为分支语句，导致any不能被编译器获知它的类型，那么编译器就会允许任何转换的进行。
 *
 * 但是你不能访问到any包装的变量中的成员，除非你将它转换为原变量的类型。
 *
 * ```java
 * any i = 5;
 * i = i + 1; // 警告
 * i = (int) i + 1; // 正确
 * i = "string";    // 正确，可以被赋值为任意类型
 * ```
 *
 * 由于不知道any的种类是什么，因此编译器不能对any进行任何操作。
 *
 * ```java
 * any i;
 * any b;
 * i = b;   //错误。编译器不知道如何赋值
 * b = 5;
 * i = b;   //正确。编译器知道b是int类型的，因此知道如何进行赋值
 * i = (nbt)b;  //警告。编译器知道b的类型。但是如果此时编译器不知道b的类型，那么不会发生警告。
 * b = (nbt)i;  //正确。将i强制转换为nbt进行处理
 *```
 *
 * 要访问成员，使用`object`
 * ```java
 * any i;
 * i.method();  //错误
 * ((object)i).method();  //正确
 * ```
 *
 * `any`与`var`的区别在于，`any`是一个变量的类型，而`var`是一个变量的声明关键字。用`any`声明的变量的类型为any，而用`var`声明的对象的类
 *型为这个变量当时值的类型。例如，`any i = 5`中`i`的类型为`any`，而`var i = 5`中`i`的类型为`int`。
 *
 *
 * @constructor Create empty MCAny
 */
open class MCAny : Var<MCAny> {

    override var type: MCFPPType = MCFPPBaseType.Any

    var lastVar : Var<*>? = null

    var container: FieldContainer? = null

    val inferredType: MCFPPType?
        get() = lastVar?.type

    /**
     * 创建一个int值。它的标识符和mc名相同。
     * @param identifier identifier
     */
    constructor(identifier: String = TempPool.getVarIdentify()) : super(identifier)

    /**
     * 复制一个int
     * @param b 被复制的int值
     */
    constructor(b: MCAny) : super(b){
        lastVar = b.lastVar
    }

    /**
     * 将b中的值赋值给此变量。
     *
     * @param b 变量的对象
     *
     * @return 重新获取跟踪的此变量
     */
    override fun doAssignedBy(b: Var<*>): MCAny {
        when (b) {
            is MCAnyConcrete -> {
                val q = MCAnyConcrete(this, b.value)
                return q
            }

            is MCAny -> {
                if (b.inferredType == null && this.inferredType == null){
                    LogProcessor.warn("Attempt to assign any to any, but cannot infer any type. Default to nbt type.")
                    NBTBasedData().setAs(this).assignedBy(NBTBasedData().setAs(b))
                    return this
                }
                if(b.lastVar != null){
                    lastVar = b.lastVar
                }
                val temp = buildInferredVar(inferredType!!)
                val tempb = b.buildInferredVar(b.inferredType!!)
                temp.assignedBy(tempb)
                return this
            }

            else -> {
                lastVar = b
                val temp = buildInferredVar(inferredType!!)
                temp.assignedBy(b)
                return this
            }
        }
    }
    override fun explicitCast(type: MCFPPType): Var<*> {
        return when(type){
            MCFPPBaseType.Any -> this
            else -> {
                buildInferredVar(type)
            }
        }
    }

    override fun canExplicitCast(type: MCFPPType) = true

    override fun implicitCast(type: MCFPPType): Var<*> {
        return when(type){
            MCFPPBaseType.Any -> this
            else -> {
                buildInferredVar(type)
            }
        }
    }

    override fun canImplicitCast(type: MCFPPType) = true

    override fun clone(): MCAny {
        return MCAny(this)
    }

    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @return
     */
    override fun getTempVar(): MCAny {
        return this
    }

    override fun storeToStack() {}

    override fun getFromStack() {}

    open fun buildInferredVar(type: MCFPPType): Var<*>{
        val re = if(container != null){
            type.buildUnConcrete(this.identifier, container!!).setAs(this)
        } else{
            type.buildUnConcrete(this.identifier).setAs(this)
        }
        if(parentClass() is ObjectClass && re is OnScoreboard){
            re.name = (parentClass() as ObjectClass).mcuuid.uuid.toString()
            re.setObj(parentClass()!!.getIntSbObject(re.identifier))
        }else if(parentClass() != null && re is OnScoreboard){
            re.name = "@s"
            re.setObj(parentClass()!!.getIntSbObject(re.identifier))
        }
        return re
    }
}

class MCAnyConcrete : MCAny, MCFPPValue<Any?> {

    override var value: Any?

    /**
     * 创建一个固定的any。它的标识符和mc名一致
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: Any?, identifier: String = TempPool.getVarIdentify()) : super(identifier) {
        this.value = value
    }

    /**
     * 创建一个MCAny类型的变量。它是v的跟踪版本
     */
    constructor(v : MCAny, value: Any?): super(v){
        this.value = value
    }

    constructor(v: MCAnyConcrete) : super(v){
        this.value = v.value
    }

    override fun clone(): MCAnyConcrete {
        return MCAnyConcrete(this)
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        if(inferredType == null){
            LogProcessor.warn("Unable to infer the type of any")
        }else{
            val temp = buildInferredVar(inferredType!!)
            (temp as MCFPPValue<*>).toDynamic(false)
        }
        val re = MCAny(this)
        if(replace){
            if(parentTemplate() != null){
                parentTemplate()!!.field.putVar(identifier, re, true)
            }else{
                Function.currFunction.scope.putVar(identifier, re, true)
            }
        }
        return re
    }

    override fun buildInferredVar(type: MCFPPType): Var<*> {
        val re = if(container != null){
            type.build(this.identifier, container!!, value).setAs(this)
        } else{
            type.build(this.identifier, value).setAs(this)
        }
        if(parentClass() is ObjectClass && re is OnScoreboard){
            re.name = (parentClass() as ObjectClass).mcuuid.uuid.toString()
            re.setObj(parentClass()!!.getIntSbObject(re.identifier))
        }else if(parentClass() != null && re is OnScoreboard){
            re.name = "@s"
            re.setObj(parentClass()!!.getIntSbObject(re.identifier))
        }
        return re
    }

}