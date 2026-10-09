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
                listOf(top.mcfpp.analysis.CompilerValue.Record(mapOf("value" to top.mcfpp.analysis.CompilerValue.Sequence(listOf(
                    top.mcfpp.analysis.CompilerValue.Integral(1), top.mcfpp.analysis.CompilerValue.NullValue,
                    top.mcfpp.analysis.CompilerValue.FloatBits((-0.0f).toRawBits())))))))
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

    @Test fun recursiveContainerAndEnumDeclarationsRestoreInAFreshLibrarySession() {
        val savedConfig = Project.config
        val output = java.nio.file.Files.createTempDirectory("mcfpp-declaration-descriptors")
        Project.config = top.mcfpp.ProjectConfig()
        try {
            val declarations = """
                namespace fixture.descriptors;
                enum Mode {first=3,second=7}
                data Frozen<M as Mode>{constructor(){}}
                data Node {
                    children as list<Node>;
                    constructor(){this.children=[] as list<Node>;}
                }
                func choose(modes as list<Mode> = [Mode.second])->int{return 7;}
            """.trimIndent()
            val body = """
                func main(){
                    var node=Node();var frozen=Frozen<Mode.second>();
                    dynamic var result=node.children.size()+choose();
                }
            """.trimIndent()
            fun check(): Pair<top.mcfpp.model.compound.DataTemplate,top.mcfpp.type.MCFPPEnumType> {
                assertEquals(0,Project.errorCount)
                val ns=top.mcfpp.model.scope.GlobalScope.getCanonicalTemplate("fixture.descriptors","Node")!!
                val childType=ns.scope.getVar("children")!!.type as top.mcfpp.type.MCFPPListType
                val child=childType.generic.single() as top.mcfpp.type.MCFPPDataTemplateType
                child.tryResolve()
                kotlin.test.assertSame(ns,child.template)
                val main=top.mcfpp.model.scope.GlobalScope.localNamespaces.values.flatMap { it.scope.functions["main"].orEmpty() }.single()
                val actualTemplates = listOf(ns) + main.scope.allVars.filterIsInstance<top.mcfpp.core.lang.obj.DataTemplateObject>()
                    .map { it.templateType }
                val functions=(top.mcfpp.model.scope.GlobalScope.localNamespaces.values+top.mcfpp.model.scope.GlobalScope.libNamespaces.values)
                    .flatMap { it.scope.functions.values.flatten() }.plus(actualTemplates.flatMap {
                        it.constructors + it.scope.functions.values.flatten()
                    }).associate { it.namespaceID.toString() to it.commands.analyzeAll() }+
                    Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) }
                val machine=top.mcfpp.test.util.ScoreCommandExecutor(main.commands.analyzeAll(),functions)
                assertEquals(7,machine.read(main.scope.getVar("result") as top.mcfpp.core.lang.MCInt))
                assertEquals(0,machine.stackDepth)
                val choose=top.mcfpp.model.scope.GlobalScope.getFunctionCandidates("fixture.descriptors","choose",null).single()
                val defaultType=choose.normalParams.single().type as top.mcfpp.type.MCFPPListType
                val mode=defaultType.generic.single() as top.mcfpp.type.MCFPPEnumType
                val member=mode.enum.members.getValue("second")
                assertEquals(1,member.value);assertEquals(7,member.dataAsInt())
                val expected=top.mcfpp.analysis.CompilerValue.Typed(mode.typeId,top.mcfpp.analysis.CompilerValue.Record(mapOf(
                    "ordinal" to top.mcfpp.analysis.CompilerValue.Integral(1),"data" to top.mcfpp.analysis.CompilerValue.Nbt("7"))))
                assertEquals(top.mcfpp.analysis.CompilerValue.Typed(defaultType.typeId,
                    top.mcfpp.analysis.CompilerValue.Sequence(listOf(expected))),choose.normalParams.single().defaultValue)
                val frozen=main.scope.getVar("frozen")!!.type.typeId as top.mcfpp.type.TypeId.Specialized
                assertEquals(listOf(expected),frozen.arguments)
                return ns to mode
            }
            top.mcfpp.test.util.MCFPPStringTest.readFromString("$declarations\n$body",targetPath=output.toString(),version="26.3")
            val source=check()
            Project.config.includes=arrayListOf(output.toString())
            top.mcfpp.test.util.MCFPPStringTest.readFromString("import fixture.descriptors:*;\n$body",version="26.3")
            val fresh=check()
            kotlin.test.assertNotSame(source.first,fresh.first)
            kotlin.test.assertNotSame(source.second.enum,fresh.second.enum)
            assertEquals(source.first.getType().typeId,fresh.first.getType().typeId)
        } finally {
            Project.config=savedConfig
            java.nio.file.Files.walk(output).use { paths -> paths.sorted(java.util.Comparator.reverseOrder()).forEach(java.nio.file.Files::deleteIfExists) }
        }
    }

}
