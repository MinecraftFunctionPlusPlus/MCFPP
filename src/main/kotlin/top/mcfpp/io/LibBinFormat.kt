package top.mcfpp.io

/** Bump the format when serialized signatures, type metadata, or host default-value field layouts change. */
object LibBinFormat {
    const val MAGIC: Int = 0x4D43464C // MCFL
    const val VERSION: Int = 66
}
