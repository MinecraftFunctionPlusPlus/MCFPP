package top.mcfpp.io

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import org.antlr.v4.runtime.CommonToken
import org.objenesis.strategy.StdInstantiatorStrategy
import top.mcfpp.io.info.DataTemplateInfo
import top.mcfpp.io.info.FunctionTagInfo
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.UnsolvedObjectTemplate
import top.mcfpp.model.compound.UnsolvedTemplate
import top.mcfpp.model.function.FunctionTag
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPGenericDataTemplateType
import top.mcfpp.type.TypeId
import top.mcfpp.type.MCFPPObjectDataTemplateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPNotCompiledGenericType
import top.mcfpp.type.MCFPPTypeDataTemplateType

object KryoManager {
    val kryo = Kryo().apply {
        isRegistrationRequired = false
        references = true
        addDefaultSerializer(top.mcfpp.type.TypeId::class.java, TypeIdentitySerializer())
        addDefaultSerializer(MCFPPType::class.java, TypeDescriptorSerializer())
        addDefaultSerializer(top.mcfpp.analysis.CompilerValue::class.java, object : Serializer<top.mcfpp.analysis.CompilerValue>() {
            private val identities = TypeIdentitySerializer()
            override fun write(kryo: Kryo, output: Output, value: top.mcfpp.analysis.CompilerValue) = identities.writeValue(output, value)
            override fun read(kryo: Kryo, input: Input, type: Class<out top.mcfpp.analysis.CompilerValue>) = identities.readValue(input)
        })
        instantiatorStrategy = StdInstantiatorStrategy()

        register(DataTemplate::class.java, object : Serializer<DataTemplate>() {
            override fun write(p0: Kryo, p1: Output, p2: DataTemplate) {
                writeIdentity(p1, p2)
                val info = DataTemplateInfo.from(p2)
                p0.writeObject(p1, info)
            }

            override fun read(p0: Kryo, p1: Input, p2: Class<out DataTemplate>): DataTemplate {
                val identity = readIdentity(p1)
                val template = UnsolvedTemplate(identity.name, identity.namespace, identity.isInterface, identity.isAbstract, identity.isFinal)
                // Register before nested reads can refer back: Kryo 5.6.2 README, Serializer references.
                // https://github.com/EsotericSoftware/kryo/blob/kryo-parent-5.6.2/README.md#serializer-references
                p0.reference(template)
                template.info = p0.readObject(p1, DataTemplateInfo::class.java)
                return template
            }
        })

        register(FunctionTag::class.java, object : Serializer<FunctionTag>() {
            override fun write(p0: Kryo, p1: Output, p2: FunctionTag) {
                val info = FunctionTagInfo.from(p2)
                p0.writeObject(p1, info)
            }

            override fun read(p0: Kryo, p1: Input, p2: Class<out FunctionTag>): FunctionTag {
                val tag = p0.readObject(p1, FunctionTagInfo::class.java)
                return tag.get()
            }
        })

        register(MCFPPDataTemplateType::class.java, object : Serializer<MCFPPDataTemplateType>() {
            override fun write(p0: Kryo, p1: Output, p2: MCFPPDataTemplateType) {
                writeIdentity(p1, p2.template)
                val info = DataTemplateInfo.from(p2.template)
                p0.writeObject(p1, info)
                p0.writeObject(p1, p2.parentType)
            }

            @Suppress("UNCHECKED_CAST")
            override fun read(p0: Kryo, p1: Input, p2: Class<out MCFPPDataTemplateType>): MCFPPDataTemplateType {
                val identity = readIdentity(p1)
                val template = UnsolvedTemplate(identity.name, identity.namespace, identity.isInterface, identity.isAbstract, identity.isFinal)
                val type = MCFPPDataTemplateType(template, arrayListOf())
                p0.reference(type)
                template.info = p0.readObject(p1, DataTemplateInfo::class.java)
                type.parentType = p0.readObject(p1, ArrayList::class.java) as ArrayList<MCFPPType>
                return type
            }
        })

        register(MCFPPTypeDataTemplateType::class.java, object : Serializer<MCFPPTypeDataTemplateType>() {
            override fun write(kryo: Kryo, output: Output, value: MCFPPTypeDataTemplateType) {
                writeIdentity(output, value.template)
                kryo.writeObject(output, DataTemplateInfo.from(value.template))
                kryo.writeObject(output, value.parentType)
            }

            @Suppress("UNCHECKED_CAST")
            override fun read(kryo: Kryo, input: Input, clazz: Class<out MCFPPTypeDataTemplateType>): MCFPPTypeDataTemplateType {
                val identity = readIdentity(input)
                val template = UnsolvedTemplate(identity.name, identity.namespace, identity.isInterface, identity.isAbstract, identity.isFinal)
                val type = MCFPPTypeDataTemplateType(template)
                kryo.reference(type)
                template.info = kryo.readObject(input, DataTemplateInfo::class.java)
                type.parentType = kryo.readObject(input, ArrayList::class.java) as ArrayList<MCFPPType>
                return type
            }
        })

        register(MCFPPGenericDataTemplateType::class.java, object : Serializer<MCFPPGenericDataTemplateType>() {
            private val identitySerializer = TypeIdentitySerializer()

            override fun write(kryo: Kryo, output: Output, value: MCFPPGenericDataTemplateType) {
                kryo.writeObject(output, value.typeId, identitySerializer)
            }

            override fun read(kryo: Kryo, input: Input, type: Class<out MCFPPGenericDataTemplateType>): MCFPPGenericDataTemplateType {
                val identity = kryo.readObject(input, TypeId.Specialized::class.java, identitySerializer)
                val declaration = identity.constructor as TypeId.Declaration
                return MCFPPGenericDataTemplateType(
                    DataTemplate(declaration.name, declaration.namespace), arrayListOf(), identity
                )
            }
        })

        register(top.mcfpp.type.MCFPPGenericObjectDataTemplateType::class.java, object : Serializer<top.mcfpp.type.MCFPPGenericObjectDataTemplateType>() {
            private val identities = TypeIdentitySerializer()
            override fun write(kryo: Kryo, output: Output, value: top.mcfpp.type.MCFPPGenericObjectDataTemplateType) = identities.write(kryo, output, value.typeId)
            override fun read(kryo: Kryo, input: Input, type: Class<out top.mcfpp.type.MCFPPGenericObjectDataTemplateType>): top.mcfpp.type.MCFPPGenericObjectDataTemplateType {
                val id = identities.read(kryo, input, TypeId::class.java) as TypeId.Specialized
                val declaration = id.constructor as TypeId.Declaration
                return top.mcfpp.type.MCFPPGenericObjectDataTemplateType(
                    top.mcfpp.model.compound.ObjectDataTemplate(declaration.name, declaration.namespace), arrayListOf(), id)
            }
        })

        register(top.mcfpp.type.MCFPPGenericInterfaceType::class.java, object : Serializer<top.mcfpp.type.MCFPPGenericInterfaceType>() {
            private val identities = TypeIdentitySerializer()
            override fun write(kryo: Kryo, output: Output, value: top.mcfpp.type.MCFPPGenericInterfaceType) = identities.write(kryo, output, value.typeId)
            override fun read(kryo: Kryo, input: Input, type: Class<out top.mcfpp.type.MCFPPGenericInterfaceType>): top.mcfpp.type.MCFPPGenericInterfaceType {
                val id = identities.read(kryo, input, TypeId::class.java) as TypeId.Specialized
                val declaration = id.constructor as TypeId.Declaration
                return top.mcfpp.type.MCFPPGenericInterfaceType(DataTemplate(declaration.name, declaration.namespace).apply { isInterface = true }, arrayListOf(), id)
            }
        })

        register(top.mcfpp.type.MCFPPInterfaceType::class.java, object : Serializer<top.mcfpp.type.MCFPPInterfaceType>() {
            override fun write(kryo: Kryo, output: Output, value: top.mcfpp.type.MCFPPInterfaceType) {
                writeIdentity(output, value.i)
                kryo.writeObject(output, DataTemplateInfo.from(value.i))
                kryo.writeObject(output, value.parentType)
            }
            @Suppress("UNCHECKED_CAST")
            override fun read(kryo: Kryo, input: Input, type: Class<out top.mcfpp.type.MCFPPInterfaceType>): top.mcfpp.type.MCFPPInterfaceType {
                val id = readIdentity(input)
                val template = UnsolvedTemplate(id.name, id.namespace, true, id.isAbstract, id.isFinal)
                val result = top.mcfpp.type.MCFPPInterfaceType(template, arrayListOf())
                kryo.reference(result)
                template.info = kryo.readObject(input, DataTemplateInfo::class.java)
                result.parentType = kryo.readObject(input, ArrayList::class.java) as ArrayList<MCFPPType>
                return result
            }
        })

        register(MCFPPObjectDataTemplateType::class.java, object : Serializer<MCFPPObjectDataTemplateType>() {
            override fun write(p0: Kryo, p1: Output, p2: MCFPPObjectDataTemplateType) {
                writeIdentity(p1, p2.template)
                val info = DataTemplateInfo.from(p2.template)
                p0.writeObject(p1, info)
                p0.writeObject(p1, p2.parentType)
            }

            @Suppress("UNCHECKED_CAST")
            override fun read(p0: Kryo, p1: Input, p2: Class<out MCFPPObjectDataTemplateType>): MCFPPObjectDataTemplateType {
                val identity = readIdentity(p1)
                val template = UnsolvedObjectTemplate(identity.name, identity.namespace, identity.isInterface, identity.isAbstract, identity.isFinal)
                val type = MCFPPObjectDataTemplateType(template, arrayListOf())
                p0.reference(type)
                template.info = p0.readObject(p1, DataTemplateInfo::class.java)
                type.parentType = p0.readObject(p1, ArrayList::class.java) as ArrayList<out MCFPPType>
                return type
            }
        })

        register(MCFPPNotCompiledGenericType::class.java, object : Serializer<MCFPPNotCompiledGenericType>() {
            override fun write(kryo: Kryo, output: Output, value: MCFPPNotCompiledGenericType) {
                output.writeString((value.typeId as TypeId.Builtin).key.removePrefix("constructor:"))
            }

            override fun read(kryo: Kryo, input: Input, type: Class<out MCFPPNotCompiledGenericType>): MCFPPNotCompiledGenericType {
                val name = input.readString()
                return MCFPPType.nativeConstructor(name) ?: error("Unknown native type constructor: $name")
            }
        })

        register(CommonToken::class.java, object : Serializer<CommonToken>(){
            override fun write(kryo: Kryo, output: Output, `object`: CommonToken) {
                output.writeInt(`object`.type)
                output.writeInt(`object`.line)
                output.writeInt(`object`.charPositionInLine)
                output.writeInt(`object`.channel)
                output.writeString(`object`.text)
                output.writeInt(`object`.tokenIndex)
                output.writeInt(`object`.startIndex)
                output.writeInt(`object`.stopIndex)
            }

            override fun read(kryo: Kryo, input: Input, type: Class<out CommonToken>): CommonToken {
                val t = input.readInt()
                val token = CommonToken(t)
                token.line = input.readInt()
                token.charPositionInLine = input.readInt()
                token.channel = input.readInt()
                token.text = input.readString()
                token.tokenIndex = input.readInt()
                token.startIndex = input.readInt()
                token.stopIndex = input.readInt()
                return token
            }
        })
    }

    // Metadata graphs can refer to a DataTemplateInfo while its fields are still being
    // read. Identity is therefore a separate prefix, never read from that partial object.
    private data class DeclarationIdentity(val name: String, val namespace: String, val isInterface: Boolean, val isAbstract: Boolean, val isFinal: Boolean)
    private fun writeIdentity(output: Output, template: DataTemplate) {
        output.writeString(template.identifier)
        output.writeString(template.namespace)
        output.writeBoolean(template.isInterface)
        output.writeBoolean(template.isAbstract)
        output.writeBoolean(template.isFinal)
    }
    private fun readIdentity(input: Input) = DeclarationIdentity(input.readString(), input.readString(), input.readBoolean(), input.readBoolean(), input.readBoolean())
}
