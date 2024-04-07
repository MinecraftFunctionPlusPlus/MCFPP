package top.mcfpp.lang.type

import top.mcfpp.lang.*
import top.mcfpp.lang.Void
import top.mcfpp.util.ResourceLocation

/**
 * 类型单例
 */
class MCFPPBaseType {
    object Any:MCFPPType("any", listOf())
    object Int:MCFPPType("int",listOf(Any))
    object String:MCFPPType("string",listOf(Any))
    object Float:MCFPPType("float",listOf(Any))
    object Bool:MCFPPType("bool",listOf(Any))
    object Type:MCFPPType("type", listOf())
    object Void: MCFPPType("void",listOf())
    object Selector: MCFPPType("selector",listOf(BaseEntity))
    object JavaVar: MCFPPType("JavaVar",listOf(Any))
    object BaseEntity: MCFPPType("entity",listOf(Any))
    object JsonText: MCFPPType("jtext",listOf(MCFPPNBTType.NBT))

    init {
        Any.data = top.mcfpp.lang.MCAny.data
        Int.data = top.mcfpp.lang.MCInt.data
        String.data = top.mcfpp.lang.MCString.data
        Float.data = top.mcfpp.lang.MCFloat.data
        Bool.data = top.mcfpp.lang.MCBool.data
        Type.data = top.mcfpp.lang.type.MCFPPType.data
        Void.data = top.mcfpp.lang.Void.data
        Selector.data = top.mcfpp.lang.Selector.data
        JavaVar.data = top.mcfpp.lang.JavaVar.data
        BaseEntity.data = top.mcfpp.lang.Entity.data
        JsonText.data = top.mcfpp.lang.JsonText.data
    }

}

class MCFPPEntityType(
    val resourceLocation: ResourceLocation
) : MCFPPType("entity($resourceLocation)",listOf(MCFPPBaseType.BaseEntity), Entity.data) {
    init {
       registerType({ it.contains(regex) }) {
          val matcher = regex.find(it)!!.groupValues
          MCFPPEntityType(ResourceLocation(matcher[1], matcher[2]))
       }
    }
    companion object{
        val regex = Regex("^entity\\((.+):(.+)\\)$")
    }
}

