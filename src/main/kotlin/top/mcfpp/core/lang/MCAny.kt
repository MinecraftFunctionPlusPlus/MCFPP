package top.mcfpp.core.lang

import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

/**
 *
 * 这是MCAny的未跟踪类。这个类表示编译器不知道any类对应的类型是什么。
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

    var inferredType: MCFPPType? = null

    var name: String? = null

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
        inferredType = b.inferredType
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
                q.inferredType = b.inferredType
                return q
            }

            is MCAny -> {
                if (b.inferredType == null && this.inferredType == null){
                    LogProcessor.warn("Try to assign any to any")
                    NBTBasedData().setAs(this).assignedBy(NBTBasedData().setAs(b))
                    return this
                }
                if(b.inferredType != null){
                    inferredType = b.inferredType
                }
                val temp = inferredType!!.buildUnConcrete(this.identifier, parentClass()?:parentTemplate()?:Function.currFunction).setAs(this)
                val tempb = inferredType!!.buildUnConcrete(b.identifier, parentClass()?:parentTemplate()?:Function.currFunction).setAs(b)
                temp.assignedBy(tempb)
                return this
            }

            is MCFPPValue<*> -> {
                inferredType = (b as Var<*>).type
                name = if(b is OnScoreboard) b.name else null
                return MCAnyConcrete(this, b.value)
            }

            else -> {
                inferredType = b.type

                val temp = inferredType!!.buildUnConcrete(this.identifier, parentClass()?:parentTemplate()?:Function.currFunction).setAs(this)
                temp.assignedBy(b)
                return this
            }
        }
    }

    override fun canAssignedBy(b: Var<*>): Boolean {
        return !b.implicitCast(type).isError
    }

    override fun explicitCast(type: MCFPPType): Var<*> {
        return when(type){
            MCFPPBaseType.Any -> this
            else -> {
                type.buildUnConcrete(this.identifier, parentClass()?:parentTemplate()?:Function.currFunction).setAs(this)
            }
        }
    }

    override fun implicitCast(type: MCFPPType): Var<*> {
        return when(type){
            MCFPPBaseType.Any -> this
            else -> {
                type.buildUnConcrete(this.identifier, parentClass()?:parentTemplate()?:Function.currFunction).setAs(this)
            }
        }
    }

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

    open fun buildInferredVar(): Var<*>?{
        return inferredType?.buildUnConcrete(this.identifier)?.setAs(this)
    }
}

/**
 * 这是MCAny的跟踪变种。这个类表示编译器知道any类对应的类型是什么。
 *
 * 它的javaValue值是一个MCFPP变量，也就是[Var]。在诸如`any i = a`（其中a是一个int值）这样的赋值过程中，将会将a作为javaValue储存到i中，
 *MCAnyConcrete类根据自己javaValue中存放的变量的类型来判断自己是什么类型。然而，在后续的赋值过程中，比如再有`int j = (int)i`，并不是将a赋值
 *给i，而是创建一个和i有一样标识符但是javaValue值（若有）和a一致的新的MCInt变量（这个变量不会进入编译器的作用域缓存中），然后让这个新的变量去进行
 *赋值操作。
 *
 * 由于编译器知道这个any类中代表的类型是什么，因此会进行一些类型检查，但是不会报错。比如
 * ```java
 * any i = 5;
 * any str = "abc";
 * i = str;
 * ```
 *
 * 这会警告表示两个的类型不一致，但是不会报错。允许这种赋值的存在，此后i的类型也被跟踪为`string`类型。
 */
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
            val temp = inferredType!!.build(value!!).setAs(this)
            (temp as MCFPPValue<*>).toDynamic(false)
        }
        val re = MCAny(this)
        if(replace){
            if(parentTemplate() != null){
                parentTemplate()!!.field.putVar(identifier, re, true)
            }else{
                Function.currFunction.field.putVar(identifier, re, true)
            }
        }
        return re
    }

    override fun buildInferredVar(): Var<*>? {
        return inferredType?.build(value!!)?.setAs(this)
    }

}