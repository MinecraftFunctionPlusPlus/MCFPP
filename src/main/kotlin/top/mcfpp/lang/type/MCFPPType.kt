package top.mcfpp.lang.type

import top.mcfpp.lang.CanSelectMember
import top.mcfpp.lang.Var
import top.mcfpp.lib.*
import top.mcfpp.lib.function.Function
import top.mcfpp.lib.field.GlobalField
import top.mcfpp.lib.field.IFieldWithType
import top.mcfpp.lib.function.ExtensionFunction
import top.mcfpp.lib.function.UnknownFunction
import top.mcfpp.util.LogProcessor

/**
 * 所有类型的接口
 */
abstract class MCFPPType(

    /**
     * 类型名
     */
    open var typeName:String,

    /**
     * 父类型，一个列表
     */
    open var parentType: List<MCFPPType> = listOf(),

    open var data: CompoundData = CompoundData("unknown","mcfpp")

) : CanSelectMember {
    init {
        registerType()
    }

    /**
     * 是否是指定类型的子类型
     *
     * @param parentType 指定类型
     */
    fun isSubOf(parentType: MCFPPType):Boolean{
        if(this == parentType) return true
        for(parentTypeSingle in this.parentType){
            if(parentTypeSingle.isSubOf(parentType)) return true
        }
        return false
    }

    override fun toString(): String {
        return typeName
    }

    /**
     * 获取这个类中的一个静态成员字段。
     *
     * @param key 字段的标识符
     * @param accessModifier 访问者的访问权限
     * @return 第一个值是对象中获取到的字段，若不存在此字段则为null；第二个值是是否有足够的访问权限访问此字段。如果第一个值是null，那么第二个值总是为true
     */
    @Override
    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val member = data.getVar(key,true)
        return if(member == null){
            Pair(null, true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    /**
     * 获取这个类中的一个静态成员方法。
     *
     * @param key 方法的标识符
     * @param normalParams 方法的参数
     * @param accessModifier 访问者的访问权限
     * @return 第一个值是对象中获取到的方法，若不存在此方法则为null；第二个值是是否有足够的访问权限访问此方法。如果第一个值是null，那么第二个值总是为true
     */
    @Override
    override fun getMemberFunction(key: String, readOnlyParams: List<MCFPPType>, normalParams: List<MCFPPType>, accessModifier: Member.AccessModifier): Pair<Function, Boolean> {
        //获取函数
        val member = data.staticField.getFunction(key, readOnlyParams, normalParams)
        return if(member is UnknownFunction){
            Pair(UnknownFunction(key), true)
        }else{
            Pair(member, accessModifier >= member.accessModifier)
        }
    }

    override fun getAccess(function: Function): Member.AccessModifier {
        return if(function !is ExtensionFunction && function.ownerType == Function.Companion.OwnerType.CLASS){
            function.parentClass()!!.getAccess(data)
        }else if(function !is ExtensionFunction && function.ownerType == Function.Companion.OwnerType.TEMPLATE){
            function.parentStruct()!!.getAccess(data)
        }else{
            Member.AccessModifier.PUBLIC
        }
    }

    companion object{

        val data = CompoundData("type","mcfpp")

        private var typeCache:MutableMap<String,MCFPPType> = mutableMapOf()

        /**
         * 类型注册缓存。键值对的第一个元素判断字符串是否满足条件，而第二个元素则是用于从一个字符串中解析出一个类型
         */
        private var typeActionCache:MutableList<Pair<(String)->Boolean,(String)->MCFPPType>> = mutableListOf()
        val baseType:Set<MCFPPType> = setOf(
            MCFPPBaseType.Void,
            MCFPPBaseType.BaseEntity,
            MCFPPBaseType.Type,
            MCFPPBaseType.Int,
            MCFPPBaseType.Bool,
            MCFPPBaseType.String,
            MCFPPBaseType.Float,
            MCFPPBaseType.Any,
            MCFPPBaseType.Selector,
            MCFPPBaseType.JavaVar,
            MCFPPBaseType.JsonText,
            MCFPPNBTType.NBT
        )
        val nbtType:Set<MCFPPType> = setOf(
            MCFPPNBTType.NBT,
            MCFPPNBTType.BaseList,
            MCFPPNBTType.Map,
            MCFPPNBTType.Dict
        )

        /**
         * 注册一个类型
         *
         * @param predicate 判断字符串是否满足条件
         * @param typeParser 从字符串中解析类型
         *
         */
        fun registerType(
            predicate:(String)->Boolean,
            typeParser:(String)->MCFPPType
        ){
            typeActionCache.add(predicate to typeParser)
        }

        /**
         * 将这个类型注册入缓存
         */
        fun MCFPPType.registerType(){
            typeCache[this.typeName] = this
        }

        /**
         * 从类型字符串中获取一个类型。此类型将会进行正则条件匹配
         */
        fun parseFromTypeName(typeName: String) : MCFPPType{
            if(typeCache.contains(typeName)) return typeCache[typeName]!!
            for(typeAction in typeActionCache){
                //判断字符串是否满足条件
                if(typeAction.first(typeName)){
                    return typeAction.second(typeName)
                }
            }
            //TODO 是不是不应该在这里输出日志呢
            LogProcessor.warn("Unknown type: $typeName")
            return MCFPPBaseType.Any
        }

        /**
         * 从类型标识符中获取一个类型。通常是从类名或者模板名中获取，也可以从泛型中获取
         */
        fun parseFromIdentifier(identifier: String, typeScope: IFieldWithType):MCFPPType{
            if(typeCache.contains(identifier)) return typeCache[identifier]!!
            //类和模板
            val clazz = GlobalField.getClass(null,identifier)
            if(clazz!=null) return clazz.getType()
            val template = GlobalField.getTemplate(null,identifier)
            if(template!=null) return template.getType()
            //泛型
            if(typeScope.containType(identifier)){
                return typeScope.getType(identifier)!!
            }
            return MCFPPBaseType.Any
        }

    }
}