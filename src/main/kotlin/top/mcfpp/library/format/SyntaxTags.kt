package top.mcfpp.library.format

/** Stable v1 operation numbers, independent of parser rules and Kotlin/JVM classes. */
object SyntaxTags {
    const val LITERAL = 0x0101
    const val NAME = 0x0102
    const val THIS = 0x0103
    const val SUPER = 0x0104
    const val TYPE_VALUE = 0x0105
    const val GROUP = 0x0106
    const val UNARY = 0x0107
    const val BINARY = 0x0108
    const val CAST = 0x0109
    const val MEMBER = 0x010a
    const val INDEX = 0x010b
    const val PROPERTY_FILTER = 0x010c
    const val CALL = 0x010d
    const val LAMBDA = 0x010e
    const val RANGE = 0x010f
    const val COORDINATE = 0x0110
    const val SELECTOR = 0x0111
    const val LIST = 0x0112
    const val COMPOUND = 0x0113
    const val NBT_ARRAY = 0x0114
    const val INTERPOLATED_STRING = 0x0115
    const val TYPE_NAME = 0x0201
    const val TYPE_APPLY = 0x0202
    const val CONTAINER_TYPE = 0x0203
    const val VECTOR_TYPE = 0x0204
    const val ENTITY_TYPE = 0x0205
    const val UNION_TYPE = 0x0206
    const val INTERSECTION_TYPE = 0x0207
    const val ANONYMOUS_DATA_TYPE = 0x0208
    const val FUNCTION_TYPE = 0x0209
    const val VOID_TYPE = 0x020a
    const val INFERRED_TYPE = 0x020b
    const val BLOCK = 0x0301
    const val LOCAL = 0x0302
    const val EVALUATE = 0x0303
    const val ASSIGN = 0x0304
    const val IF = 0x0305
    const val WHILE = 0x0306
    const val DO_WHILE = 0x0307
    const val FOREACH = 0x0308
    const val TRY_STORE = 0x0309
    const val EXECUTE = 0x030a
    const val COMMAND = 0x030b
    const val BREAK = 0x030c
    const val CONTINUE = 0x030d
    const val RETURN = 0x030e
    const val EMPTY = 0x030f
    const val TEMPLATE_BODY = 0x0401
    const val MEMBER_DECLARATION = 0x0402
    const val ANNOTATION_APPLICATION = 0x0403
    const val ANNOTATION_CONSTRUCTION = 0x0404

    fun isKnown(tag: Int): Boolean = tag in LITERAL..INTERPOLATED_STRING ||
        tag in TYPE_NAME..INFERRED_TYPE || tag in BLOCK..EMPTY || tag in TEMPLATE_BODY..ANNOTATION_CONSTRUCTION
}
