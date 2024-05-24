package top.mcfpp.lang.type

/**
 * 类型单例
 */
class MCFPPBaseType {
    object Any : MCFPPType(listOf()) {
        override val typeName: kotlin.String
            get() = "any"
    }

    object Int : MCFPPType(listOf(Any)) {
        override val typeName: kotlin.String
            get() = "int"
    }

    object String : MCFPPType(listOf(Any)) {
        override val typeName: kotlin.String
            get() = "string"
    }

    object Float : MCFPPType(listOf(Any)) {
        override val typeName: kotlin.String
            get() = "float"
    }

    object Bool : MCFPPType(listOf(Any)) {
        override val typeName: kotlin.String
            get() = "bool"
    }

    object Type : MCFPPType(listOf()) {
        override val typeName: kotlin.String
            get() = "type"
    }

    object Void : MCFPPType(listOf()) {
        override val typeName: kotlin.String
            get() = "void"
    }

    object Selector : MCFPPType(listOf(BaseEntity)) {
        override val typeName: kotlin.String
            get() = "selector"
    }

    object JavaVar : MCFPPType(listOf(Any)) {
        override val typeName: kotlin.String
            get() = "JavaVar"
    }

    object BaseEntity : MCFPPType(listOf(Any)) {
        override val typeName: kotlin.String
            get() = "entity"
    }

    object JsonText : MCFPPType(listOf(MCFPPNBTType.NBT)) {
        override val typeName: kotlin.String
            get() = "jtext"
    }

    init {
        Any.data = top.mcfpp.lang.MCAny.data
        Int.data = top.mcfpp.lang.MCInt.data
        String.data = top.mcfpp.lang.MCString.data
        Float.data = top.mcfpp.lang.MCFloat.data
        Bool.data = top.mcfpp.lang.MCBool.data
        Type.data = top.mcfpp.lang.type.MCFPPType.data
        Void.data = top.mcfpp.lang.Void.data
        //Selector.data = top.mcfpp.lang.Selector.data
        JavaVar.data = top.mcfpp.lang.JavaVar.data
        //BaseEntity.data = top.mcfpp.lang.Entity.data
        JsonText.data = top.mcfpp.lang.JsonText.data
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

