package top.mcfpp.reference

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.backend.ReferenceAccessHelpers
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.ref.ReferenceAccess
import top.mcfpp.core.lang.ref.ReferenceKind
import top.mcfpp.core.lang.ref.ReferencePathPart
import top.mcfpp.core.lang.ref.ReferenceValues
import top.mcfpp.core.lang.ref.ReferenceVar
import top.mcfpp.io.DatapackCreator
import top.mcfpp.io.LibBinReader
import top.mcfpp.io.LibBinWriter
import top.mcfpp.library.format.CompoundCodec
import top.mcfpp.library.format.LibraryModelCodec
import top.mcfpp.library.format.NativeCodec
import top.mcfpp.library.model.ReferenceHelperDeclaration
import top.mcfpp.lib.EntitySelector
import top.mcfpp.lib.NBTPath
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.collection.ListTag
import top.mcfpp.nbt.tags.primitive.IntTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.test.util.ContextCommandExecutor
import top.mcfpp.test.util.ContextEntity
import top.mcfpp.test.util.ContextPosition
import top.mcfpp.test.util.ContextRotation
import top.mcfpp.type.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferenceLibraryTest {
    @Test fun persistedDescriptorUsesRestoredSelectorAndPathHelpersInANewCompilerSession() {
        val previousConfig = Project.config
        val previousIgnore = CompileSettings.ignoreStdLib
        val temporary = Files.createTempDirectory("mcfpp-reference-library-")
        try {
            val namespace = "reference.library"
            Project.config = ProjectConfig(version = "26.3", rootNamespace = namespace).apply { name = "library" }
            CompileSettings.ignoreStdLib = true
            Project.init()
            val declaration = DataTemplate("Saved", namespace)
            GlobalScope.getOrCreateNamespace(namespace).scope.addTemplate("Saved", declaration)
            val creator = Function("descriptor_factory", namespace, null)
            lateinit var frozen: ReferenceVar
            creator.runInFunction {
                val selector = EntitySelector(EntitySelector.Companion.SelectorType.ALL_PLAYERS).apply {
                    name("Alice", false)
                    limit(1)
                }
                val reference = ReferenceValues.create(MCFPPNBTRefType(MCFPPBaseType.Int), ReferenceKind.NBT_ENTITY,
                    mapOf("path" to MCString(StringTag("Items"))), SelectorVar(selector),
                    pathSuffix = listOf(ReferencePathPart.Index(MCInt(0)), ReferencePathPart.Field("a.b")))
                frozen = assertIs<ReferenceVar>(StorageAccess.freezeReadonly(reference, "address"))
                frozen.declaredParentTemplate = declaration
                declaration.scope.putVar("address", frozen, true)
            }
            val nestedType = MCFPPNBTRefType(MCFPPListType(MCFPPScoreRefType))
            declaration.scope.putVar("nested", nestedType.buildUnConcrete("nested").apply { declaredParentTemplate = declaration }, true)
            declaration.scope.putVar("score", MCFPPScoreRefType.buildUnConcrete("score").apply { declaredParentTemplate = declaration }, true)
            val payload = assertIs<CompilerValue.Typed>(assertNotNull(StorageAccess.snapshot(frozen)))
            val record = assertIs<CompilerValue.Record>(payload.payload)
            val sourceReadId = (record.fields.getValue("read") as CompilerValue.Text).value
            val sourceWriteId = (record.fields.getValue("write") as CompilerValue.Text).value
            val sourceRecipe = ReferenceAccessHelpers.recipes(namespace).single {
                it.readId == sourceReadId && it.writeId == sourceWriteId
            }
            LibBinWriter.write(temporary.resolve("library").toString())
            assertEquals(0, Project.errorCount)
            val published = LibraryModelCodec(listOf(CompoundCodec, NativeCodec))
                .decode(Files.readAllBytes(temporary.resolve("library/bin.mclib")))
            val helper = published.declarations.declarations.filterIsInstance<ReferenceHelperDeclaration>()
                .single { it.recipe.namespace == namespace && it.recipe.singleRead }
            assertEquals(helper.common.name, helper.recipe.name)
            val readId = "${helper.recipe.namespace}:${helper.recipe.name}_read"
            val writeId = "${helper.recipe.namespace}:${helper.recipe.name}_write"

            // Clear all executable helpers/macros. No constructor AST exists in this library.
            Project.config = ProjectConfig(version = "26.3").apply { name = "consumer" }
            Project.init()
            assertTrue(ReferenceAccessHelpers.recipes(namespace).isEmpty())
            assertTrue(Project.macroFunction.isEmpty())
            LibBinReader.read(temporary.resolve("library/bin.mclib").toString())
            assertEquals(0, Project.errorCount)
            val importedNamespace = GlobalScope.libNamespaces.getValue(namespace)
            assertTrue(GlobalScope.localNamespaces[namespace] == null, "Helpers must belong to the imported namespace")
            val restored = importedNamespace.scope.getTemplate("Saved")!!
            val address = assertIs<ReferenceVar>(restored.scope.getVar("address"))
            val restoredPayload = assertIs<CompilerValue.Typed>(StorageAccess.snapshot(address))
            val restoredRecord = assertIs<CompilerValue.Record>(restoredPayload.payload)
            assertEquals(payload.type, restoredPayload.type)
            assertEquals(record.fields.keys, restoredRecord.fields.keys)
            assertEquals(record.fields - setOf("read", "write"), restoredRecord.fields - setOf("read", "write"),
                "Only the known descriptor helper links may change during publication")
            assertEquals(CompilerValue.Text(readId), restoredRecord.fields.getValue("read"))
            assertEquals(CompilerValue.Text(writeId), restoredRecord.fields.getValue("write"))
            val restoredRecipe = ReferenceAccessHelpers.recipes(namespace).single {
                it.readId == readId && it.writeId == writeId
            }
            assertEquals(sourceRecipe.copy(identifier = helper.recipe.name), restoredRecipe)
            assertEquals(MCFPPScoreRefType.typeId, restored.scope.getVar("score")!!.type.typeId)
            assertEquals(nestedType.typeId, restored.scope.getVar("nested")!!.type.typeId)
            assertEquals(MCFPPBaseType.Int.typeId,
                address.type.instanceData.scope.functions.getValue("set").single().normalParams.single().type.typeId)

            val consumer = Function("consume", "reference.consumer", null)
            GlobalScope.getOrCreateNamespace(consumer.namespace).scope.addFunction(consumer, false)
            val result = MCFPPBaseType.Int.buildUnConcrete("loaded")
            val success = ScoreBool("loaded_success")
            consumer.runInFunction {
                Function.addCommand(Commands.stackIn())
                val runtime = address.referenceType.buildUnConcrete("runtime_address").apply {
                    nbtPath = NBTPath.stack.intIndex(0).memberIndex("runtime_address")
                }
                StorageAccess.encodeTo(runtime.nbtPath, address)
                StorageAccess.publishNbt(runtime)
                assertNull(ReferenceValues.descriptor(runtime))
                ReferenceAccess.set(runtime, MCInt(17))
                ReferenceAccess.readInto(runtime, result, success)
            }
            val output = temporary.resolve("export")
            DatapackCreator.createDatapack(output.toString())
            val functions = exportedReferenceFunctions(output.resolve("consumer/data"))
            assertTrue(readId in functions)
            assertTrue(writeId in functions)
            val entity = ContextEntity("Alice", "minecraft:player", position = ContextPosition(0.0, 64.0, 0.0),
                rotation = ContextRotation(0.0, 0.0), nbt = mapOf("Items" to ListTag().apply {
                    add(CompoundTag("a.b" to IntTag(0)))
                }))
            val machine = ContextCommandExecutor(listOf("function ${consumer.namespaceID}"), functions,
                listOf(entity), entity, macros = exportedReferenceMacros(functions))
            assertEquals(17, (machine.readEntity("Alice", "Items[0].\"a.b\"").value as Number).toInt())
            assertEquals(17, (machine.readStorage("mcfpp:system", StorageAccess.ensure(result).path.pathToCommandPart().analyze()).value as Number).toInt())
            assertEquals(1, machine.score(success.name, success.boolObject.toString()))
            assertTrue(machine.executed.contains("function $writeId"))
            assertTrue(machine.executed.contains("function $readId"))
            assertEquals(0, Project.errorCount)
        } finally {
            Project.config = previousConfig
            CompileSettings.ignoreStdLib = previousIgnore
            temporary.toFile().deleteRecursively()
        }
    }
}

internal fun exportedReferenceFunctions(vararg roots: Path): Map<String, List<String>> {
    val functions = linkedMapOf<String, List<String>>()
    for (root in roots) {
        if (!Files.isDirectory(root)) continue
        Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfunction") }.forEach { file ->
            val relative = root.relativize(file)
            if (relative.nameCount >= 3 && relative.getName(1).toString() == "function") {
                val name = relative.subpath(2, relative.nameCount).joinToString("/") { it.toString() }.removeSuffix(".mcfunction")
                functions["${relative.getName(0)}:$name"] = Files.readAllLines(file)
            }
        } }
    }
    return functions
}

internal fun exportedReferenceMacros(functions: Map<String, List<String>>): Map<String, String> =
    functions.filterValues { lines -> lines.any { it.startsWith("$") } }.mapValues { (_, lines) -> lines.joinToString("\n") }
