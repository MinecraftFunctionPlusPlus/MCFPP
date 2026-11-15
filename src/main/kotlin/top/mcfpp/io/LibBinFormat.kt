package top.mcfpp.io

/** Bump the format when serialized declarations, type descriptors, or compiler value encodings change. */
object LibBinFormat {
    const val MAGIC: Int = 0x4D43464C // MCFL
    // Structural object/literal descriptors and ordinary template factories.
    const val VERSION: Int = 88
}
