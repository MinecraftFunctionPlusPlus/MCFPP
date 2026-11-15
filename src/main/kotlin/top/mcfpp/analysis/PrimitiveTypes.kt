package top.mcfpp.analysis

import top.mcfpp.type.*

/** 标量、擦除值及集合 IR 共同支持的类型集合；分析和生成使用同一份能力判断。 */
internal object PrimitiveTypes {
    val int = MCFPPBaseType.Int.typeId
    val float = MCFPPBaseType.Float.typeId
    val bool = MCFPPBaseType.Bool.typeId
    val any = MCFPPBaseType.Any.typeId
    val obj = MCFPPBaseType.Object.typeId
    val erased = setOf(any, obj)
    val types = listOf(MCFPPBaseType.Int, MCFPPBaseType.Float, MCFPPBaseType.Bool, MCFPPBaseType.Any, MCFPPBaseType.Object,
        MCFPPBaseType.String, MCFPPBaseType.Range, MCFPPNBTType.Byte, MCFPPNBTType.Short, MCFPPNBTType.Long,
        MCFPPNBTType.Double, MCFPPNBTType.NBT,
        MCFPPNBTType.ByteArray, MCFPPNBTType.IntArray, MCFPPNBTType.LongArray).associateBy { it.typeId }
    /** 此 IR 后端仅将 int/bool 放入记分板，其余支持类型使用 NBT 布局。 */
    fun nbt(type: TypeId) = type != int && type != bool
    fun supportedType(type: MCFPPType): Boolean = when (type) {
        is MCFPPListType, is MCFPPDictType, is MCFPPImmutableListType, is MCFPPMapType -> (type as MCFPPTypeWithGeneric).generic.all {
            it === MCFPPPrivateType.Wildcard || supportedType(it)
        }
        is MCFPPUnionType -> type.types.all(::supportedType)
        else -> type.typeId in types
    }
}
