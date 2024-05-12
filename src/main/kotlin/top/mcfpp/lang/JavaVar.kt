package top.mcfpp.lang

import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.Tag
import top.mcfpp.exception.VariableConverseException
import top.mcfpp.lang.type.*
import top.mcfpp.lang.value.MCFPPValue
import top.mcfpp.model.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.JavaFunction
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil.toJava
import java.lang.Class
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

/**
 * Java var是一个仅仅在编译期间存在的变量。JavaVar对应了编译过程中，编译器的一个变量的对象，可以通过它访问一个编译器变量的成员甚至方法。
 *
 * 一个变量如果被转换为编译器变量就不能够再被转换为普通的变量。编译器变量不能在数据包中被找到。因此JavaVar必定是编译器已知的。
 *
 * Java var只能通过调用NativeFunction获取。
 *
 * @constructor Create empty Java var
 */

class JavaVar : Var<Any>, MCFPPValue<Any?>{

    override var value : Any? = null

    override var type: MCFPPType = MCFPPBaseType.JavaVar
    /**
     * 创建一个固定的JavaVar
     *
     * @param identifier 标识符
     * @param curr 域容器
     * @param value 值
     */
    constructor(
        curr: FieldContainer,
        value: Any?,
        identifier: String = UUID.randomUUID().toString()
    ) : super(curr.prefix + identifier) {
        this.value = value
    }

    /**
     * 创建一个固定的JavaVar。它的标识符和mc名一致
     * @param identifier 标识符。如不指定，则为随机uuid
     * @param value 值
     */
    constructor(value: Any?, identifier: String = UUID.randomUUID().toString()) : super(identifier) {
        this.value = value
    }

    /**
     * 复制一个JavaVar
     * @param b 被复制的JavaVar值
     */
    constructor(b: JavaVar) : super(b)

    /**
     * 将b中的值赋值给此变量
     * @param b 变量的对象
     */
    override fun assign(b: Var<*>): Var<Any> {
        hasAssigned = true
        when (b) {
            is JavaVar -> {
                this.value = b.value
            }
            else -> {
                this.value = b
            }
        }
        return this
    }

    /**
     * 将这个变量强制转换为一个类型
     * @param type 要转换到的目标类型
     */
    override fun cast(type: MCFPPType): Var<*> {
        return when(type){
            MCFPPBaseType.JavaVar -> this
            MCFPPBaseType.Any -> MCAnyConcrete(this)
            else -> throw VariableConverseException()
        }
    }

    override fun clone(): JavaVar {
        return JavaVar(this)
    }

    /**
     * 返回一个临时变量。这个变量将用于右值的计算过程中，用于避免计算时对原来的变量进行修改
     *
     * @return
     */
    override fun getTempVar(): Var<*> { return this }

    override fun storeToStack() {}

    override fun getFromStack() {}

    override fun toDynamic() {}

    /**
     * 根据标识符获取一个成员。
     *
     * @param key 成员的mcfpp标识符
     * @param accessModifier 访问者的访问权限
     * @return 返回一个值对。第一个值是成员变量或null（如果成员变量不存在），第二个值是访问者是否能够访问此变量。
     */
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        //获取value中的一个成员变量
        if(value == null) {
            LogProcessor.error("Cannot access properties in $identifier because its value is null")
            throw NullPointerException()
        }
        val member = value!!::class.memberProperties.find { it.name == key } as KProperty1<Any, *>?
        if(member != null){
            return Pair(JavaVar(member.get(value!!)), member.visibility == KVisibility.PUBLIC)
        }
        return Pair(null, true)
    }

    /**
     * 根据方法标识符和方法的参数列表获取一个方法。如果没有这个方法，则返回null
     *
     * @param key 成员方法的标识符
     * @param normalParams 成员方法的参数
     * @return
     */
    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        //获取value中的一个成员方法
        if(value == null) {
            LogProcessor.error("Cannot access properties in $identifier because its value is null")
            throw NullPointerException()
        }
        try{
            val member = value!!::class.java.getDeclaredMethod(key, *getTypeArray(normalParams))
            return Pair(JavaFunction(member, this), member.canAccess(Any()))
        }catch (e: NoSuchMethodException){
            LogProcessor.error("No method '$key' in $identifier}")
            throw e
        }
    }

    private fun getTypeArray(params: List<MCFPPType>): Array<Class<*>>{
        return params.map {
            when(it){
                MCFPPBaseType.Int -> Int::class.java
                MCFPPBaseType.Float -> Float::class.java
                MCFPPBaseType.Bool -> Long::class.java
                MCFPPBaseType.String -> String::class.java
                is MCFPPListType -> ArrayList::class.java
                MCFPPNBTType.Dict -> HashMap::class.java
                MCFPPNBTType.Map -> HashMap::class.java
                MCFPPNBTType.NBT -> Tag::class.java
                else -> Var::class.java
            }
        }.toTypedArray()
    }
//    private fun getTypeArray(params: List<String>): Array<Class<*>>{
//        return params.map {
//            when(it){
//                "int" -> Int::class.java
//                "float" -> Float::class.java
//                "bool" -> Long::class.java
//                "string" -> String::class.java
//                "list" -> ArrayList::class.java
//                "dict" -> HashMap::class.java
//                "map" -> HashMap::class.java
//                "nbt" -> Tag::class.java
//                else -> Var::class.java
//            }
//        }.toTypedArray()
//    }

    override fun toString(): String {
        return "JavaVar[$value]"
    }

    companion object{

        val data = CompoundData("JavaVar","mcfpp")

        fun mcToJava(v : Var<*>) : Any{
            if(v !is MCFPPValue<*>){
                return v
            }
            return when(v){
                is MCIntConcrete -> v.value
                is MCFloatConcrete -> v.value
                is MCBoolConcrete -> v.value
                is MCStringConcrete -> v.value.valueToString()
                is NBTListConcrete<*> -> v.value.toJava()
                is NBTMapConcrete -> ((v.value as CompoundTag)["data"] as CompoundTag).toJava()
                is NBTDictionaryConcrete -> v.value.toJava()
                is NBTBasedDataConcrete -> v.value
                else -> v
            }
        }

        fun mcToJava(v: ArrayList<Var<*>>): ArrayList<Any>{
            val re = ArrayList<Any>()
            for (i in v){
                re.add(mcToJava(i))
            }
            return re
        }
    }

}