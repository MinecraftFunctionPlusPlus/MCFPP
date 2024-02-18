package top.mcfpp.lang.type

import top.mcfpp.lib.GlobalField

/**
 * 所有类型的接口
 */
abstract class MCFPPType(
    open var typeName:String,
    open var parentType: List<MCFPPType>
) {
    init {
        registerType()
    }

    fun isSub(parentType: MCFPPType):Boolean{
        if(this == parentType) return true
        for(parentTypeSingle in this.parentType){
            if(parentTypeSingle.isSub(parentType)) return true
        }
        return false
    }

    override fun toString(): String {
        return typeName
    }
    companion object{
        private var typeCache:MutableMap<String,MCFPPType> = mutableMapOf()
        private var typeActionCache:MutableList<Pair<(String)->Boolean,(String)->MCFPPType>> = mutableListOf()
        fun registerType(
            predicate:(String)->Boolean,
            typeParser:(String)->MCFPPType
        ){
            typeActionCache.add(predicate to typeParser)
        }
        fun MCFPPType.registerType(){
            typeCache[this.typeName] = this
        }
        fun parse(typeName: String):MCFPPType{
            if(typeCache.contains(typeName)) return typeCache[typeName]!!
            for(typeAction in typeActionCache){
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