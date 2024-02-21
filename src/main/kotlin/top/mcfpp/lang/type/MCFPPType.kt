package top.mcfpp.lang.type

import top.mcfpp.lib.GlobalField

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
    open var parentType: List<MCFPPType>
) {
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

    companion object{
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
         * 从字符串中获取一个类型
         */
        fun parse(typeName: String):MCFPPType{
            if(typeCache.contains(typeName)) return typeCache[typeName]!!
            for(typeAction in typeActionCache){
                //判断字符串是否满足条件
                if(typeAction.first(typeName)){
                    return typeAction.second(typeName)
                }
            }
            //接下来就是说可能是类的类型/泛型变量类型
            val clazz = GlobalField.getClass(null,typeName)
            if(clazz!=null)
                return MCFPPClassType(
                    clazz,
                    listOf() //TODO: 没有输入父类，这个父类应该要和那个CompoundData那个合并！
                )
            //TODO: 泛型变量类型也是在这里取的


            return MCFPPBaseType.Any
        }


    }
}