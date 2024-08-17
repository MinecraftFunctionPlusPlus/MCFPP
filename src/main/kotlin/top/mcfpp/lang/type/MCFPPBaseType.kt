package top.mcfpp.lang.type

import top.mcfpp.lang.*
import top.mcfpp.model.CompoundData

/**
 * 类型单例
 */
class MCFPPBaseType {
    object Any:MCFPPType(parentType = listOf()){

        override val objectData: CompoundData
            get() = MCAny.data

        override val typeName: kotlin.String
            get() = "any"
    }
    object Int:MCFPPType(parentType = listOf(Any)){

        override val objectData: CompoundData
            get() = MCInt.data

        override val typeName: kotlin.String
            get() = "int"
    }
    object String:MCFPPType(parentType = listOf(Any)){

        override val objectData: CompoundData
            get() = MCString.data

        override val typeName: kotlin.String
            get() = "string"
    }

    object Float:MCFPPType(parentType = listOf(Any)){

        override val objectData: CompoundData
            get() = MCFloat.data

        override val typeName: kotlin.String
            get() = "float"
    }
    object Bool:MCFPPType(parentType = listOf(Any)){

        override val objectData: CompoundData
            get() = MCBool.data

        override val typeName: kotlin.String
            get() = "bool"
    }
    object Type:MCFPPType(parentType = listOf()){

        override val objectData: CompoundData
            get() = data

        override val typeName: kotlin.String
            get() = "type"
    }
    object Void: MCFPPType(parentType = listOf()){

        override val objectData: CompoundData
            get() = top.mcfpp.lang.Void.data

        override val typeName: kotlin.String
            get() = "void"
    }
    object Selector: MCFPPType(parentType = listOf(BaseEntity)){

        override val objectData: CompoundData
            get() = SelectorVar.data

        override val typeName: kotlin.String
            get() = "selector"
    }
    object JavaVar: MCFPPType(parentType = listOf(Any)){

        override val objectData: CompoundData
            get() = top.mcfpp.lang.JavaVar.data
        override val typeName: kotlin.String
            get() = "JavaVar"
    }
    object BaseEntity: MCFPPType(parentType = listOf(Any)){

        override val objectData: CompoundData
            get() = EntityVar.data

        override val typeName: kotlin.String
            get() = "entity"
    }
    object JsonText: MCFPPType(parentType = listOf(MCFPPNBTType.NBT)){

        override val objectData: CompoundData
            get() = top.mcfpp.lang.JsonText.data

        override val typeName: kotlin.String
            get() = "text"
    }

    object Range: MCFPPType(parentType = listOf(Any)){

        override val objectData: CompoundData
            get() = RangeVar.data

        override val typeName: kotlin.String
            get() = "range"
    }
}

class LimitedSelectorType(val limit: Int): MCFPPType(parentType = listOf(MCFPPBaseType.BaseEntity)){

    override val objectData: CompoundData
        get() = SelectorVar.data

    override val typeName: String
        get() = "selector[$limit]"
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

