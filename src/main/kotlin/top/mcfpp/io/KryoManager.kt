package top.mcfpp.io

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import org.antlr.v4.runtime.CommonToken
import org.objenesis.strategy.StdInstantiatorStrategy
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.io.info.ClassInfo
import top.mcfpp.io.info.DataTemplateInfo
import top.mcfpp.io.info.FunctionTagInfo
import top.mcfpp.io.info.GenericClassInfo
import top.mcfpp.model.compound.*
import top.mcfpp.model.compound.GenericClass
import top.mcfpp.model.function.FunctionTag
import top.mcfpp.type.*

object KryoManager {
    val kryo = Kryo().apply {
        isRegistrationRequired = false
        references = true
        instantiatorStrategy = StdInstantiatorStrategy()

        register(Class::class.java, object : Serializer<Class>() {
            override fun write(p0: Kryo, p1: Output, p2: Class) {
                val info = ClassInfo.from(p2)
                p0.writeObject(p1, info)
            }

            override fun read(p0: Kryo, p1: Input, p2: java.lang.Class<out Class>): Class {
                val cls = p0.readObject(p1, ClassInfo::class.java)
                return UnsolvedClass(cls)
            }
        })

        register(GenericClass::class.java, object : Serializer<GenericClass>(){
            override fun write(p0: Kryo, p1: Output, p2: GenericClass) {
                val info = GenericClassInfo.from(p2)
                p0.writeObject(p1, info)
            }

            override fun read(p0: Kryo, p1: Input, p2: java.lang.Class<out GenericClass>): GenericClass {
                val cls = p0.readObject(p1, GenericClassInfo::class.java)
                return cls.get()
            }
        })

        register(DataTemplate::class.java, object : Serializer<DataTemplate>() {
            override fun write(p0: Kryo, p1: Output, p2: DataTemplate) {
                val info = DataTemplateInfo.from(p2)
                p0.writeObject(p1, info)
            }

            override fun read(p0: Kryo, p1: Input, p2: java.lang.Class<out DataTemplate>): DataTemplate {
                val data = p0.readObject(p1, DataTemplateInfo::class.java)
                return UnsolvedTemplate(data)
            }
        })

        register(FunctionTag::class.java, object : Serializer<FunctionTag>() {
            override fun write(p0: Kryo, p1: Output, p2: FunctionTag) {
                val info = FunctionTagInfo.from(p2)
                p0.writeObject(p1, info)
            }

            override fun read(p0: Kryo, p1: Input, p2: java.lang.Class<out FunctionTag>): FunctionTag {
                val tag = p0.readObject(p1, FunctionTagInfo::class.java)
                return tag.get()
            }
        })

        register(MCFPPDataTemplateType::class.java, object : Serializer<MCFPPDataTemplateType>() {
            override fun write(p0: Kryo, p1: Output, p2: MCFPPDataTemplateType) {
                val info = DataTemplateInfo.from(p2.template)
                p0.writeObject(p1, info)
                p0.writeObject(p1, p2.parentType)
            }

            @Suppress("UNCHECKED_CAST")
            override fun read(p0: Kryo, p1: Input, p2: java.lang.Class<out MCFPPDataTemplateType>): MCFPPDataTemplateType {
                val data = p0.readObject(p1, DataTemplateInfo::class.java)
                val parentType = p0.readObject(p1, ArrayList::class.java) as ArrayList<MCFPPType>
                return MCFPPDataTemplateType(UnsolvedTemplate(data), parentType)
            }
        })


        register(MCFPPClassType::class.java, object : Serializer<MCFPPClassType>() {
            override fun write(p0: Kryo, p1: Output, p2: MCFPPClassType) {
                val info = ClassInfo.from(p2.cls)
                p0.writeObject(p1, info)
                p0.writeObject(p1, p2.parentType)
            }

            @Suppress("UNCHECKED_CAST")
            override fun read(p0: Kryo, p1: Input, p2: java.lang.Class<out MCFPPClassType>): MCFPPClassType {
                val cls = p0.readObject(p1, ClassInfo::class.java)
                val parentType = p0.readObject(p1, ArrayList::class.java) as ArrayList<out MCFPPType>
                return MCFPPClassType(UnsolvedClass(cls), parentType)
            }
        })

        register(MCFPPGenericClassType::class.java, object : Serializer<MCFPPGenericClassType>() {
            override fun write(p0: Kryo, p1: Output, p2: MCFPPGenericClassType) {
                val info = ClassInfo.from(p2.cls)
                p0.writeObject(p1, info)
                p0.writeObject(p1, p2.parentType)
                p0.writeObject(p1, p2.genericVar)
            }

            @Suppress("UNCHECKED_CAST")
            override fun read(p0: Kryo, p1: Input, p2: java.lang.Class<out MCFPPGenericClassType>): MCFPPGenericClassType {
                val cls = p0.readObject(p1, ClassInfo::class.java)
                val parentType = p0.readObject(p1, ArrayList::class.java) as ArrayList<out MCFPPType>
                val genericVar = p0.readObject(p1, ArrayList::class.java) as ArrayList<out MCFPPValue<*>>
                return MCFPPGenericClassType(UnsolvedClass(cls), genericVar, parentType)
            }
        })

        register(MCFPPObjectClassType::class.java, object : Serializer<MCFPPObjectClassType>() {
            override fun write(p0: Kryo, p1: Output, p2: MCFPPObjectClassType) {
                val info = ClassInfo.from(p2.cls)
                p0.writeObject(p1, info)
                p0.writeObject(p1, p2.parentType)
            }

            @Suppress("UNCHECKED_CAST")
            override fun read(p0: Kryo, p1: Input, p2: java.lang.Class<out MCFPPObjectClassType>): MCFPPObjectClassType {
                val cls = p0.readObject(p1, ClassInfo::class.java)
                val parentType = p0.readObject(p1, ArrayList::class.java) as ArrayList<out MCFPPType>
                return MCFPPObjectClassType(UnsolvedObjectClass(cls), parentType)
            }
        })

        register(MCFPPObjectDataTemplateType::class.java, object : Serializer<MCFPPObjectDataTemplateType>() {
            override fun write(p0: Kryo, p1: Output, p2: MCFPPObjectDataTemplateType) {
                val info = DataTemplateInfo.from(p2.template)
                p0.writeObject(p1, info)
                p0.writeObject(p1, p2.parentType)
            }

            @Suppress("UNCHECKED_CAST")
            override fun read(p0: Kryo, p1: Input, p2: java.lang.Class<out MCFPPObjectDataTemplateType>): MCFPPObjectDataTemplateType {
                val data = p0.readObject(p1, DataTemplateInfo::class.java)
                val parentType = p0.readObject(p1, ArrayList::class.java) as ArrayList<out MCFPPType>
                return MCFPPObjectDataTemplateType(UnsolvedObjectTemplate(data), parentType)
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

            override fun read(kryo: Kryo, input: Input, type: java.lang.Class<out CommonToken>): CommonToken {
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
}