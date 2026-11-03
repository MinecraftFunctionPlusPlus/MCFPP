package top.mcfpp.library.format

import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*

object NativeCodec : DeclarationPayloadHandler {
    override val kinds: Set<Int> = setOf(11, 12, 13)
    override fun collectStrings(declaration: LibraryDeclaration): Set<String> = buildSet {
        fun macro(type: MacroType) {
            if (type is MacroType.Callback) { type.parameters.forEach { add(it.name); macro(it.type) }; macro(type.result) }
        }
        when (declaration) {
            is MacroDeclaration -> declaration.binding.let { binding ->
                add(binding.native.ownerClass); add(binding.native.method); addAll(binding.native.jvmParameters); add(binding.native.jvmResult)
                add(binding.sourceName); binding.parameters.forEach { add(it.name); macro(it.type) }; macro(binding.result)
            }
            is ReferenceHelperDeclaration -> declaration.recipe.let { recipe ->
                add(recipe.namespace); add(recipe.name)
                (recipe.target as? LibraryReferenceTarget.Selector)?.filters?.forEach { add(it.name) }
                recipe.path.filterIsInstance<LibraryReferencePath.Field>().forEach { add(it.name) }
            }
            is AnnotationProviderDeclaration -> declaration.binding.let { add(it.ownerClass); addAll(it.constructorParameters); add(it.schema) }
        }
    }

    override fun writePayload(writer: LibraryBinaryWriter, declaration: LibraryDeclaration, strings: LibraryStrings) = with(writer) {
        when (declaration) {
            is MacroDeclaration -> declaration.binding.let { binding ->
                FunctionCodec.writeNative(this, binding.native, strings); u32(strings.reference(binding.sourceName))
                list(binding.parameters) { writeParameter(this, it, strings) }; writeType(this, binding.result, strings)
            }
            is ReferenceHelperDeclaration -> declaration.recipe.let { recipe ->
                u32(strings.reference(recipe.namespace)); u32(strings.reference(recipe.name)); u8(recipe.kind.wireValue)
                optional(recipe.target) { target -> when (target) {
                    LibraryReferenceTarget.PlayerName -> tagged(1) {}
                    LibraryReferenceTarget.UUIDText -> tagged(2) {}
                    is LibraryReferenceTarget.Selector -> tagged(3) {
                        u8(target.kind.wireValue); list(target.filters) { u32(strings.reference(it.name)); bool(it.reverse); u32(it.type.value) }
                    }
                } }
                bool(recipe.singleRead); list(recipe.operandTypes) { u32(it.value) }
                list(recipe.path) { when (it) {
                    is LibraryReferencePath.Field -> tagged(1) { u32(strings.reference(it.name)) }
                    LibraryReferencePath.Index -> tagged(2) {}
                } }
            }
            is AnnotationProviderDeclaration -> declaration.binding.let {
                optional(it.providerPackage) { TypeTableCodec.writePackage(this, it) }
                u32(strings.reference(it.ownerClass)); list(it.constructorParameters) { u32(strings.reference(it)) }
                u32(strings.reference(it.schema)); u32(it.schemaVersion)
            }
            else -> error("Native codec cannot write declaration kind ${declaration.tag}")
        }
    }

    override fun readPayload(reader: LibraryBinaryReader, tag: Int, common: DeclCommon, strings: LibraryStrings): LibraryDeclaration = checked(reader) { with(reader) {
        when (tag) {
            11 -> MacroDeclaration(common, MacroBinding(FunctionCodec.readNative(this, strings), strings.value(u32()),
                list { readParameter(this, strings) }, readType(this, strings)))
            12 -> {
                val namespace = strings.value(u32()); val name = strings.value(u32())
                val kind = enumValue(u8(), LibraryReferenceKind.entries, { it.wireValue }, this)
                val target = optional { tagged { target -> when (target) {
                    1 -> LibraryReferenceTarget.PlayerName
                    2 -> LibraryReferenceTarget.UUIDText
                    3 -> LibraryReferenceTarget.Selector(enumValue(u8(), NativeSelectorKind.entries, { it.wireValue }, this),
                        list { ReferenceFilter(strings.value(u32()), bool(), TypeRef(u32())) })
                    else -> fail("Unknown reference target $target", LibraryFormatCode.UNSUPPORTED)
                } } }
                val single = bool(); val operands = list { TypeRef(u32()) }
                val path = list { tagged { part -> when (part) {
                    1 -> LibraryReferencePath.Field(strings.value(u32())); 2 -> LibraryReferencePath.Index
                    else -> fail("Unknown reference path $part", LibraryFormatCode.UNSUPPORTED)
                } } }
                ReferenceHelperDeclaration(common, ReferenceRecipe(namespace, name, kind, target, single, operands, path))
            }
            13 -> AnnotationProviderDeclaration(common, AnnotationProviderBinding(optional { TypeTableCodec.readPackage(this) },
                strings.value(u32()), list { strings.value(u32()) }, strings.value(u32()), u32()))
            else -> fail("Unknown native declaration kind $tag", LibraryFormatCode.UNSUPPORTED)
        }
    } }

    fun writeType(writer: LibraryBinaryWriter, value: MacroType, strings: LibraryStrings) {
        when (value) {
            is MacroType.Source -> writer.tagged(1) { u32(value.type.value) }
            is MacroType.Callback -> writer.tagged(2) { list(value.parameters) { writeParameter(this, it, strings) }; writeType(this, value.result, strings) }
        }
    }
    fun readType(reader: LibraryBinaryReader, strings: LibraryStrings): MacroType = reader.tagged { tag -> when (tag) {
        1 -> checked(this) { MacroType.Source(TypeRef(u32())) }
        2 -> MacroType.Callback(list { readParameter(this, strings) }, readType(this, strings))
        else -> fail("Unknown macro type $tag", LibraryFormatCode.UNSUPPORTED)
    } }
    private fun writeParameter(writer: LibraryBinaryWriter, value: MacroParameter, strings: LibraryStrings) = with(writer) {
        u32(strings.reference(value.name)); u8(value.mode.wireValue); writeType(this, value.type, strings)
    }
    private fun readParameter(reader: LibraryBinaryReader, strings: LibraryStrings): MacroParameter = with(reader) {
        MacroParameter(strings.value(u32()), enumValue(u8(), MacroParameterMode.entries, { it.wireValue }, this), readType(this, strings))
    }

    override fun validate(declaration: LibraryDeclaration, id: DeclarationId, context: LibraryModelValidation) {
        require(declaration.common.flags == 0L) { "Native metadata declarations have no applicable declaration flags" }
        fun parameters(values: List<MacroParameter>) {
            require(values.map { it.name }.distinct().size == values.size && values.all { identifier.matches(it.name) })
        }
        fun type(value: MacroType) {
            when (value) {
                is MacroType.Source -> context.type(value.type)
                is MacroType.Callback -> { parameters(value.parameters); value.parameters.forEach { type(it.type) }; type(value.result) }
            }
        }
        when (declaration) {
            is MacroDeclaration -> declaration.binding.let { binding ->
                require(identifier.matches(binding.sourceName)); parameters(binding.parameters)
                binding.native.caller?.let { context.type(it) }; binding.parameters.forEach { type(it.type) }; type(binding.result)
            }
            is ReferenceHelperDeclaration -> declaration.recipe.let { recipe ->
                require(recipe.namespace.isNotEmpty() && recipe.name.isNotEmpty())
                require(recipe.operandTypes.distinct().size == recipe.operandTypes.size)
                recipe.operandTypes.forEach { context.type(it) }
                (recipe.target as? LibraryReferenceTarget.Selector)?.filters?.forEach { context.type(it.type) }
            }
            is AnnotationProviderDeclaration -> Unit
            else -> error("Native codec cannot validate declaration kind ${declaration.tag}")
        }
    }
    private val identifier = Regex("[\\p{L}_][\\p{L}\\p{Nd}_]*")
    private fun <T> enumValue(value: Int, values: List<T>, wire: (T) -> Int, reader: LibraryBinaryReader): T =
        values.firstOrNull { wire(it) == value } ?: reader.fail("Unknown native metadata enum $value", LibraryFormatCode.UNSUPPORTED)
    private inline fun <T> checked(reader: LibraryBinaryReader, action: () -> T): T = try { action() }
    catch (failure: IllegalArgumentException) { throw LibraryFormatException(LibraryFormatCode.CORRUPT,
        failure.message ?: "Invalid native metadata", reader.byteOffset, reader.sectionId, reader.recordIndex, failure) }
}
