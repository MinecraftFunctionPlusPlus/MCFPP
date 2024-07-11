package top.mcfpp.lang.type

/**
 * 类型单例
 */
class MCFPPBaseType {
    object Any:MCFPPType(parentType = listOf()){
        override val typeName: kotlin.String
            get() = "any"
    }
    object Int:MCFPPType(parentType = listOf(Any)){
        override val typeName: kotlin.String
            get() = "int"
    }
    object String:MCFPPType(parentType = listOf(Any)){
        override val typeName: kotlin.String
            get() = "string"
    }

    object JText: MCFPPType(parentType = listOf(Any)){
        override val typeName: kotlin.String
            get() = "jtext"
    }

    object JTextElement : MCFPPType(parentType = listOf(Any)){
        override val typeName: kotlin.String
            get() = "jtextE"
    }

    object Float:MCFPPType(parentType = listOf(Any)){
        override val typeName: kotlin.String
            get() = "float"
    }
    object Bool:MCFPPType(parentType = listOf(Any)){
        override val typeName: kotlin.String
            get() = "bool"
    }
    object Type:MCFPPType(parentType = listOf()){
        override val typeName: kotlin.String
            get() = "type"
    }
    object Void: MCFPPType(parentType = listOf()){
        override val typeName: kotlin.String
            get() = "void"
    }
    object Selector: MCFPPType(parentType = listOf(BaseEntity)){
        override val typeName: kotlin.String
            get() = "selector"
    }
    object JavaVar: MCFPPType(parentType = listOf(Any)){
        override val typeName: kotlin.String
            get() = "JavaVar"
    }
    object BaseEntity: MCFPPType(parentType = listOf(Any)){
        override val typeName: kotlin.String
            get() = "entity"
    }
    object JsonText: MCFPPType(parentType = listOf(MCFPPNBTType.NBT)){
        override val typeName: kotlin.String
            get() = "jtext"
    }

    object Range: MCFPPType(parentType = listOf(Any)){
        override val typeName: kotlin.String
            get() = "range"
    }

    init {
        Any.compoundData = top.mcfpp.lang.MCAny.data
        Int.compoundData = top.mcfpp.lang.MCInt.data
        String.compoundData = top.mcfpp.lang.MCString.data
        Float.compoundData = top.mcfpp.lang.MCFloat.data
        Bool.compoundData = top.mcfpp.lang.MCBool.data
        Type.compoundData = top.mcfpp.lang.type.MCFPPType.data
        Void.compoundData = top.mcfpp.lang.Void.data
        //Selector.data = top.mcfpp.lang.Selector.data
        JavaVar.compoundData = top.mcfpp.lang.JavaVar.data
        //BaseEntity.data = top.mcfpp.lang.Entity.data
    }

}

//class MCFPPEntityType(
//    val resourceLocation: ResourceLocation
//) : MCFPPType(listOf(MCFPPBaseType.BaseEntity), Entity.data) {
//    init {
//       registerType({ it.contains(regex) }) {
//          val matcher = regex.find(it)!!.groupValues
//          MCFPPEntityType(ResourceLocation(matcher[1], matcher[2]))
//       }
//    }
//    companion object{
//        val regex = Regex("^entity\\((.+):(.+)\\)$")
//    }
//}

