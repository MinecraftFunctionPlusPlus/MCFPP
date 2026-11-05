package top.mcfpp.type

import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.util.LogProcessor
import kotlin.reflect.KClass

class MCFPPNotCompiledGenericType(val type: KClass<out MCFPPType>): MCFPPType() {

    override val typeId: TypeId = TypeId.Builtin("constructor:" + when (type) {
        MCFPPListType::class -> "list"
        MCFPPDictType::class -> "dict"
        MCFPPMapType::class -> "map"
        MCFPPImmutableListType::class -> "ImmutableList"
        MCFPPNBTRefType::class -> "NBTRef"
        else -> error("Unregistered type constructor: $type")
    })

    override val isValueType: Boolean get() = false

    override val typeName: String
        get() = type.simpleName!!

    fun compile(vararg args: MCFPPType): MCFPPType {
        require(args.size == 1) { "$typeName requires one type argument" }
        // These are the language's closed built-in constructors. Direct calls work in AOT
        // builds and avoid loading kotlin-reflect for a five-entry dispatch table.
        return when (type) {
            MCFPPListType::class -> MCFPPListType(args[0])
            MCFPPDictType::class -> MCFPPDictType(args[0])
            MCFPPMapType::class -> MCFPPMapType(args[0])
            MCFPPImmutableListType::class -> MCFPPImmutableListType(args[0])
            MCFPPNBTRefType::class -> MCFPPNBTRefType(args[0])
            else -> error("Unregistered type constructor: $type")
        }
    }

    override fun build(identifier: String, value: Any?): Var<*> {
        LogProcessor.error("Missing readonly parameter(s) for type: $typeName")
        return UnknownVar(identifier)
    }


    override fun buildUnConcrete(identifier: String): Var<*> {
        LogProcessor.error("Missing readonly parameter(s) for type: $typeName")
        return UnknownVar(identifier)
    }

}
