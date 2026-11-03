package top.mcfpp.io

/** Bump the format when serialized declarations, type descriptors, or compiler value encodings change. */
object LibBinFormat {
    const val MAGIC: Int = 0x4D43464C // MCFL
    // Public reference types/descriptors and owned access-helper recipes.
    const val VERSION: Int = 87
}
