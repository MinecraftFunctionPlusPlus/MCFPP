package top.mcfpp.library.format

import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*

interface FunctionReferenceValidator {
    fun type(reference: TypeRef) {}
    fun node(reference: NodeRef) {}
    fun body(reference: BodyId) {}
    fun constant(reference: ConstantRef) {}
    fun annotation(reference: AnnotationId) {}
    fun runtime(reference: RuntimeRef) {}
}

/** D/E codecs share the caller's S table and D numbering with every other declaration kind. */
object FunctionCodec {
    fun collectStrings(model: LibraryDeclarations): Set<String> = buildSet {
        model.declarations.forEach { declaration ->
            add(declaration.common.name)
            if (declaration is FunctionDeclaration) {
                declaration.tags.forEach { add(it.namespace); add(it.name) }
                declaration.native?.let { add(it.ownerClass); add(it.method); addAll(it.jvmParameters); add(it.jvmResult) }
            }
        }
        model.environments.forEach { environment ->
            add(environment.namespace)
            environment.imports.forEach { add(it.targetNamespace); it.member?.let(::add); it.alias?.let(::add); it.from?.let(::add) }
        }
    }

    fun encodeDeclarations(declarations: List<LibraryDeclaration>, strings: LibraryStrings): ByteArray =
        LibraryBinaryWriter().apply { list(declarations) { record { writeDeclaration(this, it, strings) } } }.toByteArray()

    fun decodeDeclarations(bytes: ByteArray, strings: LibraryStrings): List<LibraryDeclaration> {
        val reader = LibraryBinaryReader(bytes, sectionId = LibrarySectionId.DECLARATIONS)
        return checked(reader) {
            var index = 0L
            reader.list { record(++index) { readDeclaration(this, strings) } }.also { reader.requireEnd() }
        }
    }

    fun encodeEnvironments(environments: List<EnvironmentRecord>, strings: LibraryStrings): ByteArray =
        LibraryBinaryWriter().apply { list(environments) { environment -> record {
            u32(strings.reference(environment.namespace))
            list(environment.imports) { value ->
                u32(strings.reference(value.targetNamespace)); optional(value.member) { u32(strings.reference(it)) }
                optional(value.alias) { u32(strings.reference(it)) }; optional(value.from) { u32(strings.reference(it)) }
                list(value.resolvedPackages) { TypeTableCodec.writePackage(this, it) }
            }
            optional(environment.owner) { u32(it.value) }
        } } }.toByteArray()

    fun decodeEnvironments(bytes: ByteArray, strings: LibraryStrings): List<EnvironmentRecord> {
        val reader = LibraryBinaryReader(bytes, sectionId = LibrarySectionId.ENVIRONMENTS)
        return checked(reader) {
            var index = 0L
            reader.list { record(++index) { checked(this) {
                val namespace = strings.value(u32())
                val imports = list { ImportRecord(strings.value(u32()), optional { strings.value(u32()) },
                    optional { strings.value(u32()) }, optional { strings.value(u32()) }, list { TypeTableCodec.readPackage(this) }) }
                EnvironmentRecord(namespace, imports, optional { DeclarationId(u32()) })
            } } }.also { reader.requireEnd() }
        }
    }

    fun writeCommon(writer: LibraryBinaryWriter, value: DeclCommon, strings: LibraryStrings) = with(writer) {
        optional(value.key) { TypeTableCodec.writeDeclarationKey(this, it) }
        optional(value.origin) { TypeTableCodec.writeAnonymousOrigin(this, it) }
        u32(strings.reference(value.name)); optional(value.owner) { u32(it.value) }; u32(value.environment.value)
        u8(value.access.wireValue); u32(value.flags); list(value.annotations) { u32(it.value) }
    }

    fun readCommon(reader: LibraryBinaryReader, strings: LibraryStrings): DeclCommon = checked(reader) { with(reader) {
        DeclCommon(optional { TypeTableCodec.readDeclarationKey(this) }, optional { TypeTableCodec.readAnonymousOrigin(this) },
            strings.value(u32()), optional { DeclarationId(u32()) }, EnvironmentId(u32()),
            enumValue(u8(), DeclarationAccess.entries, { it.wireValue }, this, "access"), u32(), list { AnnotationId(u32()) })
    } }

    fun writeDeclaration(writer: LibraryBinaryWriter, value: LibraryDeclaration, strings: LibraryStrings) {
        validateShape(value)
        writeCommon(writer, value.common, strings)
        writer.tagged(value.tag) {
            when (value) {
                is FunctionDeclaration -> writeFunctionPayload(this, value, strings)
                is ValueDeclaration -> writeValuePayload(this, value)
                else -> throw IllegalArgumentException("Register the codec for declaration kind ${value.tag}")
            }
        }
    }

    fun readDeclaration(reader: LibraryBinaryReader, strings: LibraryStrings): LibraryDeclaration {
        val common = readCommon(reader, strings)
        return reader.tagged { tag -> checked(this) {
            val declaration = when (tag) {
                2 -> readFunctionPayload(this, common, strings)
                5 -> readValuePayload(this, common)
                else -> fail("Unknown function/value declaration kind $tag", LibraryFormatCode.UNSUPPORTED)
            }
            validateShape(declaration)
            declaration
        } }
    }

    fun writeFunctionPayload(writer: LibraryBinaryWriter, value: FunctionDeclaration, strings: LibraryStrings) = with(writer) {
        u8(value.execution.wireValue); optional(value.receiver) { u32(it.value) }
        list(value.readonly) { u32(it.value) }; list(value.normal) { u32(it.value) }
        optional(value.result) { u32(it.value) }; optional(value.body) { u32(it.value) }
        optional(value.native) { writeNative(this, it, strings) }
        list(value.tags) { u32(strings.reference(it.namespace)); u32(strings.reference(it.name)) }
        list(value.linkedRuntime) { u32(it.value) }
    }

    fun readFunctionPayload(reader: LibraryBinaryReader, common: DeclCommon, strings: LibraryStrings): FunctionDeclaration =
        checked(reader) { with(reader) {
            FunctionDeclaration(common, enumValue(u8(), FunctionExecutionKind.entries, { it.wireValue }, this, "execution"),
                optional { TypeRef(u32()) }, list { DeclarationId(u32()) }, list { DeclarationId(u32()) },
                optional { TypeRef(u32()) }, optional { BodyId(u32()) }, optional { readNative(this, strings) },
                list { TagKey(strings.value(u32()), strings.value(u32())) }, list { RuntimeRef(u32()) })
        } }

    fun writeValuePayload(writer: LibraryBinaryWriter, value: ValueDeclaration) = with(writer) {
        u8(value.role.wireValue); u8(value.storage.wireValue); optional(value.type) { u32(it.value) }
        optional(value.declaredType) { u32(it.value) }; optional(value.defaultBody) { u32(it.value) }
        optional(value.frozen) { u32(it.value) }; u8(value.variance.wireValue); optional(value.parameterIndex) { u32(it) }
    }

    fun readValuePayload(reader: LibraryBinaryReader, common: DeclCommon): ValueDeclaration = checked(reader) { with(reader) {
        ValueDeclaration(common, enumValue(u8(), ValueRole.entries, { it.wireValue }, this, "value role"),
            enumValue(u8(), ValueStorage.entries, { it.wireValue }, this, "storage"), optional { TypeRef(u32()) },
            optional { NodeRef(u32()) }, optional { BodyId(u32()) }, optional { ConstantRef(u32()) },
            enumValue(u8(), Variance.entries, { it.wireValue }, this, "variance"), optional { u32() })
    } }

    fun writeNative(writer: LibraryBinaryWriter, value: NativeBinding, strings: LibraryStrings) = with(writer) {
        optional(value.providerPackage) { TypeTableCodec.writePackage(this, it) }
        u32(strings.reference(value.ownerClass)); u32(strings.reference(value.method))
        list(value.jvmParameters) { u32(strings.reference(it)) }; u32(strings.reference(value.jvmResult))
        u8(value.invocation.wireValue); optional(value.caller) { u32(it.value) }
    }

    fun readNative(reader: LibraryBinaryReader, strings: LibraryStrings): NativeBinding = checked(reader) { with(reader) {
        NativeBinding(optional { TypeTableCodec.readPackage(this) }, strings.value(u32()), strings.value(u32()),
            list { strings.value(u32()) }, strings.value(u32()),
            enumValue(u8(), NativeInvocation.entries, { it.wireValue }, this, "native invocation"), optional { TypeRef(u32()) })
    } }

    fun validate(model: LibraryDeclarations, references: FunctionReferenceValidator = object : FunctionReferenceValidator {}) {
        model.environments.forEach { it.owner?.let(model::declaration) }
        model.declarations.forEachIndexed { index, declaration ->
            val id = DeclarationId(index + 1L)
            validateShape(declaration)
            val common = declaration.common
            model.environment(common.environment); common.owner?.let(model::declaration)
            common.annotations.forEach(references::annotation)
            when (declaration) {
                is FunctionDeclaration -> {
                    fun parameters(ids: List<DeclarationId>, role: ValueRole) = ids.forEachIndexed { parameterIndex, reference ->
                        val parameter = model.declaration(reference) as? ValueDeclaration
                            ?: throw IllegalArgumentException("Function parameter $reference is not D5")
                        require(parameter.role == role || (role == ValueRole.NORMAL_PARAMETER && parameter.role == ValueRole.LAMBDA_PARAMETER))
                        require(parameter.common.owner == id && parameter.parameterIndex == parameterIndex.toLong())
                        require(parameter.common.environment == common.environment)
                    }
                    parameters(declaration.readonly, ValueRole.READONLY_PARAMETER)
                    parameters(declaration.normal, ValueRole.NORMAL_PARAMETER)
                    declaration.receiver?.let(references::type); declaration.result?.let(references::type)
                    declaration.body?.let(references::body); declaration.native?.caller?.let(references::type)
                    declaration.linkedRuntime.forEach(references::runtime)
                }
                is ValueDeclaration -> {
                    declaration.type?.let(references::type); declaration.declaredType?.let(references::node)
                    declaration.defaultBody?.let(references::body); declaration.frozen?.let(references::constant)
                }
            }
        }
    }

    private fun validateShape(value: LibraryDeclaration) {
        when (value) {
            is FunctionDeclaration -> {
                require(value.tag == 2)
                require(value.common.flags and (DeclarationFlags.NULLABLE or DeclarationFlags.FORCE_RUNTIME or DeclarationFlags.DATA_ONLY) == 0L)
                val parameters = value.readonly + value.normal
                require(parameters.distinct().size == parameters.size) { "Duplicate function parameter reference" }
                if (value.execution != FunctionExecutionKind.NATIVE)
                    require((value.common.flags and DeclarationFlags.CONST != 0L) == (value.execution == FunctionExecutionKind.COMPILE_TIME))
                if (value.execution == FunctionExecutionKind.NATIVE) require(value.native != null && value.body == null)
                else {
                    require(value.native == null)
                    require(value.body != null || value.common.flags and DeclarationFlags.ABSTRACT != 0L) { "Analyzable function requires a body" }
                }
                require(value.result != null || (value.common.key == null && value.common.access == DeclarationAccess.PRIVATE))
            }
            is ValueDeclaration -> {
                require(value.common.flags and (DeclarationFlags.ABSTRACT or DeclarationFlags.OVERRIDE) == 0L)
                require((value.storage == ValueStorage.CONST) == (value.common.flags and DeclarationFlags.CONST != 0L))
                require(value.type != null || value.role == ValueRole.LAMBDA_PARAMETER)
                val parameter = value.role in setOf(ValueRole.READONLY_PARAMETER, ValueRole.NORMAL_PARAMETER, ValueRole.LAMBDA_PARAMETER)
                require(parameter == (value.parameterIndex != null))
                if (parameter) require(value.storage == ValueStorage.PARAMETER && value.common.owner != null)
            }
        }
    }

    private fun <T> enumValue(value: Int, values: List<T>, wire: (T) -> Int, reader: LibraryBinaryReader, name: String): T =
        values.firstOrNull { wire(it) == value } ?: reader.fail("Unknown $name $value", LibraryFormatCode.UNSUPPORTED)

    private inline fun <T> checked(reader: LibraryBinaryReader, action: () -> T): T = try { action() }
    catch (failure: IllegalArgumentException) {
        throw LibraryFormatException(LibraryFormatCode.CORRUPT, failure.message ?: "Invalid declaration record",
            reader.byteOffset, reader.sectionId, reader.recordIndex, failure)
    }
}
