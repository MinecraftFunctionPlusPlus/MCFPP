package top.mcfpp.io

/** Bump the schema when serialized language signatures or type metadata change. */
object LibBinFormat {
    const val MAGIC: Int = 0x4D43464C // MCFL
    const val VERSION: Int = 5
}
