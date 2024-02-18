package top.mcfpp.lang.type

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
}

