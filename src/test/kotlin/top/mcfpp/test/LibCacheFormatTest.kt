package top.mcfpp.test

import com.esotericsoftware.kryo.io.Output
import top.mcfpp.Project
import top.mcfpp.io.LibBinFormat
import top.mcfpp.io.LibBinReader
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class LibCacheFormatTest {
    @Test fun oldAndUnknownCacheFormatsProduceARecompileDiagnostic() {
        for ((magic, version) in listOf(0 to 0, LibBinFormat.MAGIC to (LibBinFormat.VERSION - 1), LibBinFormat.MAGIC to (LibBinFormat.VERSION + 1))) {
            val buffer = Output(64)
            buffer.writeInt(magic)
            buffer.writeInt(version)
            val errors = Project.errorCount
            LibBinReader.readFromStream(ByteArrayInputStream(buffer.toBytes()))
            assertEquals(errors + 1, Project.errorCount)
        }
        for (bytes in listOf(byteArrayOf(), byteArrayOf(1, 2))) {
            val errors = Project.errorCount
            LibBinReader.readFromStream(ByteArrayInputStream(bytes))
            assertEquals(errors + 1, Project.errorCount)
        }
    }
    @Test fun immutableTypeIdentitiesRoundTripThroughLibrarySerialization() {
        val identities = listOf(
            top.mcfpp.type.MCFPPUnionType(top.mcfpp.type.MCFPPBaseType.Int, top.mcfpp.type.MCFPPBaseType.Float).typeId,
            top.mcfpp.type.MCFPPListType(top.mcfpp.type.MCFPPBaseType.Int).typeId,
            top.mcfpp.type.TypeId.Selector(1, listOf("minecraft:pig"), false),
            top.mcfpp.type.TypeId.Specialized(top.mcfpp.type.TypeId.Declaration("template", "test", "Box"),
                listOf(top.mcfpp.analysis.ValueSnapshot.of(mapOf("value" to listOf(1, null, -0.0f)))!!))
        )
        val kryo = top.mcfpp.io.KryoManager.kryo
        for (identity in identities) {
            val output = Output(4096)
            kryo.writeClassAndObject(output, identity)
            val restored = kryo.readClassAndObject(com.esotericsoftware.kryo.io.Input(output.toBytes()))
            assertEquals(identity, restored)
            assertEquals(identity.hashCode(), restored.hashCode())
        }
    }

    @Test fun aliasesAndInterfaceIdentitySurviveNamespaceSerialization() {
        val namespace = top.mcfpp.model.Namespace("library.identity")
        namespace.scope.putResolvedAlias("Count", top.mcfpp.type.MCFPPBaseType.Int)
        namespace.scope.putResolvedAlias("Counts", top.mcfpp.type.MCFPPListType(top.mcfpp.type.MCFPPBaseType.Int))
        val capability = top.mcfpp.model.compound.DataTemplate("Capability", namespace.identifier).apply {
            isInterface = true
            isAbstract = true
        }
        namespace.scope.addInterface(capability.identifier, capability)
        namespace.scope.putResolvedAlias("CapabilityAlias", capability.getType())
        val info = top.mcfpp.io.info.NamespaceInfo.from(namespace)
        val output = Output(16384)
        val kryo = top.mcfpp.io.KryoManager.kryo
        kryo.writeObject(output, info)
        val restored = kryo.readObject(com.esotericsoftware.kryo.io.Input(output.toBytes()), top.mcfpp.io.info.NamespaceInfo::class.java).get()
        assertEquals(top.mcfpp.type.MCFPPBaseType.Int, restored.scope.getType("Count"))
        assertEquals(top.mcfpp.type.MCFPPListType(top.mcfpp.type.MCFPPBaseType.Int), restored.scope.getType("Counts"))
        val alias = restored.scope.getType("CapabilityAlias")!!
        assertEquals(capability.getType(), alias)
        assertEquals(capability.getType().hashCode(), alias.hashCode())
        kotlin.test.assertTrue(restored.scope.interfaces.getValue("Capability").isInterface)
        kotlin.test.assertTrue(restored.scope.interfaces.getValue("Capability").isAbstract)
        alias.tryResolve()
        assertEquals(capability.getType(), alias)
    }

}
