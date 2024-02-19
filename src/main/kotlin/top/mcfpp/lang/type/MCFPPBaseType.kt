package top.mcfpp.lang.type

import top.mcfpp.util.ResourceLocation

/**
 * 类型单例
 */
class MCFPPBaseType {
   object Any:MCFPPType("any", listOf(Any))
   object Int:MCFPPType("int",listOf(Any))
   object String:MCFPPType("string",listOf(Any))
   object Float:MCFPPType("float",listOf(Any))
   object Bool:MCFPPType("bool",listOf(Any))
   object Type:MCFPPType("type", listOf())
   object Void: MCFPPType("void",listOf(Any)){}
   object Selector: MCFPPType("selector",listOf(BaseEntity)){}
   object JavaVar: MCFPPType("JavaVar",listOf(Any)){}
   object BaseEntity: MCFPPType("entity",listOf(Any)){}
   object JsonText: MCFPPType("jtext",listOf(MCFPPNBTType.NBT)){}
}

class MCFPPEntityType(
    val resourceLocation: ResourceLocation
) : MCFPPType("entity($resourceLocation)",listOf(MCFPPBaseType.BaseEntity)) {
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

