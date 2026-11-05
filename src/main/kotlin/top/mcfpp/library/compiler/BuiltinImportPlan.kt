package top.mcfpp.library.compiler

import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Collections

/**
 * Build-produced, target-specific directories over the immutable library protocol. IDs refer to
 * the exact bin.mclib digest in the header. No Function, Var, class loader, parser context, or
 * provider instance is persisted here. Mutable language objects remain owned by a compiler host.
 */
internal class BuiltinImportPlan private constructor(
    val namespaces: Map<String, List<Int>>,
    val publicKeys: Map<Int, List<Byte>>,
    val signatures: Map<Int, Signature>,
    val compounds: Map<Int, CompoundLayout>,
    val nativeBindings: List<NativeBinding>,
    val annotationProviders: List<Int>,
    val macroDeclarations: List<Int>,
    val cellHints: Map<Int, AnnotationCellHint>,
) {
    data class Signature(val declaration: Int, val owner: Int, val readonly: List<Int>,
                         val normal: List<Int>, val parameterTypes: List<Int>, val result: Int)
    data class CompoundLayout(val declaration: Int, val members: List<Int>, val functions: List<Int>,
                              val parents: List<Int>, val inheritedFields: List<Int>, val deferred: Boolean)

    companion object {
        const val RESOURCE = "import-plan.bin"
        private const val MAGIC = 0x4d435049L // MCPI
        private const val SCHEMA = 1L

        fun build(model: LibraryModel): BuiltinImportPlan {
            val records = model.declarations.declarations
            fun ids(values: List<DeclarationId>) = values.map { it.value.toInt() }
            val owners = IntArray(records.size)
            fun compoundOwner(index: Int): Int {
                val visited = hashSetOf<Int>()
                var owner = records[index].common.owner?.value?.toInt() ?: return 0
                while (owner != 0) {
                    require(visited.add(owner)) { "Cyclic declaration ownership" }
                    val record = records[owner - 1]
                    if (record is CompoundDeclaration) return owner
                    owner = record.common.owner?.value?.toInt() ?: 0
                }
                return 0
            }
            records.indices.forEach { owners[it] = compoundOwner(it) }
            val namespaces = linkedMapOf<String, MutableList<Int>>()
            val keys = linkedMapOf<Int, List<Byte>>()
            val signatures = linkedMapOf<Int, Signature>()
            val functionsByOwner = linkedMapOf<Int, MutableList<Int>>()
            val bindings = linkedSetOf<NativeBinding>()
            val providers = arrayListOf<Int>()
            val macros = arrayListOf<Int>()
            records.forEachIndexed { index, record ->
                val id = index + 1
                val namespace = model.declarations.environment(record.common.environment).namespace
                namespaces.getOrPut(namespace) { arrayListOf() }.add(id)
                record.common.key?.let { keys[id] = frozen(TypeTableCodec.declarationKeyBytes(it).toList()) }
                when (record) {
                    is FunctionDeclaration -> {
                        functionsByOwner.getOrPut(owners[index]) { arrayListOf() }.add(id)
                        signatures[id] = Signature(id, owners[index], frozen(ids(record.readonly)), frozen(ids(record.normal)),
                            frozen((record.readonly + record.normal).map {
                                ((model.declarations.declaration(it) as ValueDeclaration).type?.value ?: 0).toInt()
                            }), record.result?.value?.toInt() ?: 0)
                        record.native?.let(bindings::add)
                    }
                    is MacroDeclaration -> { macros.add(id); bindings.add(record.binding.native) }
                    is AnnotationProviderDeclaration -> providers.add(id)
                    else -> Unit
                }
            }
            val completed = model.manifest.dependencies.isEmpty() && model.annotations.all { it.phase == AnnotationPhase.COMPLETED }
            val compounds = linkedMapOf<Int, CompoundLayout>()
            records.forEachIndexed { index, record -> if (record is CompoundDeclaration) {
                val id = index + 1
                compounds[id] = CompoundLayout(id, frozen(ids(record.members)), frozen(functionsByOwner[id].orEmpty()),
                    frozen(record.parents.map { it.type?.value?.toInt() ?: 0 }), frozen(record.inherited.map { it.field.value.toInt() }),
                    completed && record.category == CompoundCategory.DATA && record.readonly.isEmpty() && record.parents.all { parent ->
                        val type = parent.type?.let(model.tables::type) as? LibraryType.Declaration
                        val target = (type?.target as? DeclarationReference.Local)?.target?.let(model.declarations::declaration) as? CompoundDeclaration
                        target != null && target.readonly.isEmpty()
                    } &&
                        functionsByOwner[id].orEmpty().none { (records[it - 1] as FunctionDeclaration).tags.isNotEmpty() })
            } }
            // An eagerly bound object/type-as/generic declaration needs its parent layout.
            // Prepare that ancestor closure once in the kernel, rather than discovering it
            // in every request's global inheritance barrier (e.g. EnderPearlData).
            val eager = ArrayDeque(compounds.values.filterNot { it.deferred }.map { it.declaration })
            val visited = hashSetOf<Int>()
            while (eager.isNotEmpty()) {
                val id = eager.removeFirst()
                if (!visited.add(id)) continue
                val pendingTypes = ArrayDeque(compounds.getValue(id).parents.filter { it != 0 })
                val seenTypes = hashSetOf<Int>()
                while (pendingTypes.isNotEmpty()) {
                    val reference = pendingTypes.removeFirst()
                    if (!seenTypes.add(reference)) continue
                    when (val type = model.tables.type(TypeRef(reference.toLong()))) {
                        is LibraryType.Declaration -> {
                            val parent = (type.target as? DeclarationReference.Local)?.target?.value?.toInt() ?: continue
                            val layout = compounds[parent] ?: continue
                            if (layout.deferred) compounds[parent] = layout.copy(deferred = false)
                            eager.add(parent)
                        }
                        is LibraryType.Applied -> pendingTypes.add(type.constructor.value.toInt())
                        is LibraryType.Specialized -> pendingTypes.add(type.constructor.value.toInt())
                        is LibraryType.Intersection -> pendingTypes.addAll(type.components.map { it.value.toInt() })
                        else -> Unit
                    }
                }
            }
            return BuiltinImportPlan(frozenMap(namespaces.mapValues { frozen(it.value) }), frozenMap(keys), frozenMap(signatures),
                frozenMap(compounds), frozen(bindings.toList()), frozen(providers), frozen(macros), frozenMap(prepareCellHints(model)))
        }

        /** Decode only after checking the producing protocol bytes; mismatches never fall back silently. */
        fun decode(bytes: ByteArray, binary: ByteArray, model: LibraryModel): BuiltinImportPlan {
            val reader = LibraryBinaryReader(bytes)
            require(reader.u32() == MAGIC && reader.u32() == SCHEMA) { "Unsupported built-in import plan" }
            require(reader.bytes().contentEquals(MessageDigest.getInstance("SHA-256").digest(binary))) { "Built-in import plan does not match bin.mclib" }
            require(reader.string() == model.manifest.target.minecraftVersion) { "Built-in import plan target differs" }
            require(reader.u32() == model.declarations.declarations.size.toLong()) { "Built-in import plan declaration inventory differs" }
            fun LibraryBinaryReader.ids(): List<Int> = list {
                u32().toInt().also { require(it in 1..model.declarations.declarations.size) { "Import plan declaration is out of bounds" } }
            }
            val namespaces = reader.list { string() to ids() }.toMap()
            val keys = reader.list { u32().toInt() to frozen(bytes().toList()) }.toMap()
            val signatures = reader.list {
                val id = u32().toInt(); val owner = u32().toInt()
                Signature(id, owner, ids(), ids(), list { u32().toInt() }, u32().toInt())
            }.associateBy { it.declaration }
            val compounds = reader.list {
                val id = u32().toInt()
                CompoundLayout(id, ids(), ids(), list { u32().toInt() }, ids(), bool())
            }.associateBy { it.declaration }
            val bindings = reader.list {
                NativeBinding(optional { TypeTableCodec.readPackage(this) }, string(), string(), list { string() }, string(),
                    caller = optional { TypeRef(u32()) })
            }
            val providers = with(reader) { ids() }
            val macros = with(reader) { ids() }
            val hints = reader.list {
                val id = u32().toInt()
                require(id in model.stateCells.indices)
                id to AnnotationCellHint(list { string() }, list { string() })
            }.toMap()
            reader.requireEnd()
            require(namespaces.values.sumOf { it.size } == model.declarations.declarations.size)
            require(namespaces.values.flatten().toSet().size == model.declarations.declarations.size)
            require(keys.keys.all { model.declarations.declaration(DeclarationId(it.toLong())).common.key != null })
            require(signatures.keys.all { model.declarations.declaration(DeclarationId(it.toLong())) is FunctionDeclaration })
            require(compounds.keys.all { model.declarations.declaration(DeclarationId(it.toLong())) is CompoundDeclaration })
            return BuiltinImportPlan(frozenMap(namespaces), frozenMap(keys), frozenMap(signatures), frozenMap(compounds),
                bindings, providers, macros, frozenMap(hints))
        }

        fun encode(plan: BuiltinImportPlan, binary: ByteArray, model: LibraryModel): ByteArray = LibraryBinaryWriter().apply {
            u32(MAGIC); u32(SCHEMA); bytes(MessageDigest.getInstance("SHA-256").digest(binary))
            string(model.manifest.target.minecraftVersion); u32(model.declarations.declarations.size.toLong())
            fun LibraryBinaryWriter.ids(values: List<Int>) = list(values) { u32(it.toLong()) }
            list(plan.namespaces.entries.toList()) { (name, values) -> string(name); ids(values) }
            list(plan.publicKeys.entries.toList()) { (id, key) -> u32(id.toLong()); bytes(key.toByteArray()) }
            list(plan.signatures.values.toList()) { value ->
                u32(value.declaration.toLong()); u32(value.owner.toLong()); ids(value.readonly); ids(value.normal)
                list(value.parameterTypes) { u32(it.toLong()) }; u32(value.result.toLong())
            }
            list(plan.compounds.values.toList()) { value ->
                u32(value.declaration.toLong()); ids(value.members); ids(value.functions)
                list(value.parents) { u32(it.toLong()) }; ids(value.inheritedFields); bool(value.deferred)
            }
            list(plan.nativeBindings) { value ->
                optional(value.providerPackage) { TypeTableCodec.writePackage(this, it) }
                string(value.ownerClass); string(value.method); list(value.jvmParameters) { string(it) }; string(value.jvmResult)
                optional(value.caller) { u32(it.value) }
            }
            ids(plan.annotationProviders); ids(plan.macroDeclarations)
            list(plan.cellHints.entries.toList()) { (id, hint) ->
                u32(id.toLong()); list(hint.keys) { string(it) }; list(hint.values) { string(it) }
            }
        }.toByteArray()

        private fun prepareCellHints(model: LibraryModel): Map<Int, AnnotationCellHint> {
            val hints = linkedMapOf<Int, AnnotationCellHint>()
            val visited = hashSetOf<StateCellRef>()
            fun sequence(value: PortableValue): List<PortableValue> =
                (model.stateCells[((value as PortableValue.Cell).value.value - 1).toInt()] as StateCell.Sequence).values
            fun strings(value: PortableValue): List<String> = frozen(sequence(value).map {
                (model.tables.constant((it as PortableValue.Constant).value) as ConstantValue.Text).value
            })
            model.annotations.forEach { record ->
                val metadata = record.state?.fields?.singleOrNull { it.name == "@mcfpp.cell-hints" }?.value as? PortableValue.Cell
                    ?: return@forEach
                if (!visited.add(metadata.value)) return@forEach
                sequence(metadata).forEach { item ->
                    val values = sequence(item)
                    require(values.size == 3)
                    val id = ((values[0] as PortableValue.Cell).value.value - 1).toInt()
                    val hint = AnnotationCellHint(strings(values[1]), strings(values[2]))
                    require(hints.putIfAbsent(id, hint)?.let { it == hint } != false) { "Conflicting annotation cell hints" }
                }
            }
            return hints
        }

        private fun <T> frozen(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))
        private fun <K, V> frozenMap(values: Map<K, V>): Map<K, V> = Collections.unmodifiableMap(LinkedHashMap(values))
    }
}

/** Gradle runs this on the desktop JVM before resources are copied into the compiler artifact. */
object BuiltinImportPlanGenerator {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size >= 3) { "Expected input resources, output resources, and Minecraft target(s)" }
        val source = Path.of(args[0])
        val output = Path.of(args[1])
        val codec = LibraryModelCodec(listOf(CompoundCodec, NativeCodec))
        args.drop(2).forEach { target ->
            val relative = Path.of("datapack/targets/$target")
            val binary = Files.readAllBytes(source.resolve(relative).resolve("bin.mclib"))
            val model = codec.decode(binary, LibraryCompatibility(minecraftVersion = target))
            val plan = BuiltinImportPlan.build(model)
            val bytes = BuiltinImportPlan.encode(plan, binary, model)
            BuiltinImportPlan.decode(bytes, binary, model)
            val destination = output.resolve(relative).resolve(BuiltinImportPlan.RESOURCE)
            Files.createDirectories(destination.parent)
            Files.write(destination, bytes)
            println("Prepared $target: ${plan.publicKeys.size} symbols, ${plan.signatures.size} signatures, " +
                "${plan.compounds.size} layouts, ${plan.nativeBindings.size} native descriptors; ${bytes.size} bytes")
        }
    }
}
