package top.mcfpp.io

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import top.mcfpp.io.info.EnumInfo
import top.mcfpp.type.*

/** Declaration descriptors contain identity and type structure, never member scopes. */
class TypeDescriptorSerializer : Serializer<MCFPPType>() {
    private val identities = TypeIdentitySerializer()

    override fun write(kryo: Kryo, output: Output, value: MCFPPType) {
        when (value) {
            is MCFPPTypeAliasType -> {
                output.writeByte(5)
                kryo.writeClassAndObject(output, requireNotNull(value.cachedTarget) { "Unresolved type alias cannot be exported" })
            }
            is UnresolvedType -> {
                output.writeByte(0)
                identities.write(kryo, output, value.typeId)
                output.writeString(value.originalTypeString)
            }
            is MCFPPGenericParamType -> {
                output.writeByte(1)
                identities.write(kryo, output, value.typeId)
                output.writeString(value.identifier)
                output.writeInt(value.parentType.size)
                value.parentType.forEach { kryo.writeClassAndObject(output, it) }
            }
            is MCFPPEnumType -> { output.writeByte(2); kryo.writeObject(output, EnumInfo.from(value.enum)) }
            is MCFPPUnionType -> {
                output.writeByte(3); output.writeInt(value.types.size)
                value.types.forEach { kryo.writeClassAndObject(output, it) }
            }
            else -> {
                val id = value.typeId
                require(id is TypeId.Builtin || id is TypeId.Selector || id is TypeId.Applied) {
                    "No declaration descriptor encoding for ${value.javaClass.name}: $id"
                }
                output.writeByte(4); identities.write(kryo, output, id)
                val arguments = (value as? MCFPPTypeWithGeneric)?.generic.orEmpty()
                output.writeInt(arguments.size)
                arguments.forEach { kryo.writeClassAndObject(output, it) }
            }
        }
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out MCFPPType>): MCFPPType = when (val tag = input.readByte().toInt()) {
        0 -> {
            val identity = identities.read(kryo, input, TypeId::class.java) as TypeId.Opaque
            UnresolvedType(input.readString(), identity)
        }
        1 -> {
            val identity = identities.read(kryo, input, TypeId::class.java) as TypeId.Opaque
            val result = MCFPPGenericParamType(input.readString(), arrayListOf(), identity)
            kryo.reference(result)
            result.parentType = ArrayList(List(input.readInt()) { kryo.readClassAndObject(input) as MCFPPType })
            result
        }
        2 -> kryo.readObject(input, EnumInfo::class.java).get().getType()
        3 -> {
            val count = input.readInt()
            require(count > 0) { "Empty union declaration" }
            val result = MCFPPUnionType(MCFPPPrivateType.Wildcard)
            kryo.reference(result)
            val alternatives = List(count) { kryo.readClassAndObject(input) as MCFPPType }
            result.types = MCFPPUnionType(*alternatives.toTypedArray()).types
            require(result.typeId == TypeId.Union(alternatives.map { it.typeId }.toSet()))
            result
        }
        4 -> {
            val id = identities.read(kryo, input, TypeId::class.java)
            val count = input.readInt()
            val descriptor = if (id is TypeId.Applied && count > 0) {
                require(count == 1) { "Unsupported applied declaration identity: $id" }
                when ((id.constructor as? TypeId.Builtin)?.key) {
                    "list" -> MCFPPListType(MCFPPPrivateType.Wildcard)
                    "ImmutableList" -> MCFPPImmutableListType(MCFPPPrivateType.Wildcard)
                    "dict" -> MCFPPDictType(MCFPPPrivateType.Wildcard)
                    "map" -> MCFPPMapType(MCFPPPrivateType.Wildcard)
                    else -> error("Unknown applied declaration identity: $id")
                }
            } else if (id is TypeId.Builtin && id.key.startsWith("constructor:")) {
                MCFPPType.nativeConstructor(id.key.removePrefix("constructor:"))
            } else when (id) {
                MCFPPPrivateType.Null.typeId -> MCFPPPrivateType.Null
                MCFPPPrivateType.MCFPPCoordinateDimension.typeId -> MCFPPPrivateType.MCFPPCoordinateDimension
                MCFPPConcreteType.JavaVar.typeId -> MCFPPConcreteType.JavaVar
                else -> MCFPPType.resolveTypeId(id)
            }
            requireNotNull(descriptor) { "Unknown declaration type identity: $id" }.also {
                kryo.reference(it)
                if (count > 0) {
                    val argument = kryo.readClassAndObject(input) as MCFPPType
                    when (it) {
                        is MCFPPListType -> it.generic[0] = argument
                        is MCFPPImmutableListType -> it.generic[0] = argument
                        is MCFPPCompoundType -> it.generic[0] = argument
                        else -> error("Unexpected applied declaration descriptor: $id")
                    }
                }
                require(it.typeId == id) { "Restored declaration identity differs: $id" }
            }
        }
        5 -> kryo.readClassAndObject(input) as MCFPPType
        else -> error("Unknown declaration descriptor encoding: $tag")
    }
}
