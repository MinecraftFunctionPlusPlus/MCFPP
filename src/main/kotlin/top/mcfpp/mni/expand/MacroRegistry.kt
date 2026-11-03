package top.mcfpp.mni.expand

import top.mcfpp.annotations.MNIMacro
import top.mcfpp.mni.api.*
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** The loader supplies declaration-only resolution and canonical callback type interning. */
internal interface MacroTypeResolver {
    /** Return null for unknown or malformed source types; never compile an expression. */
    fun resolve(typeName: String): TypeRef<*>?

    /** Identity includes the callback parameter types/modes and result type, excluding names. */
    fun inlineType(signature: InlineSignature<*>): TypeRef<*>?
}

internal enum class MacroRegistrationCode { INVALID_JVM, INVALID_METADATA, UNRESOLVED_TYPE, DUPLICATE }

internal class MacroSignatureException(val code: MacroRegistrationCode, reason: String) : IllegalArgumentException(reason)

internal data class MacroRegistrationDiagnostic(
    val code: MacroRegistrationCode,
    val method: Method,
    val reason: String,
    val previous: Method? = null
) {
    val message: String get() = "Macro ${method.declaringClass.name}.${method.name}: $reason" +
        (previous?.let { " (previous: ${it.declaringClass.name}.${it.name})" } ?: "")
}

/** Pure metadata registration. This class neither invokes providers nor mutates compiler scopes. */
internal class MacroRegistry(
    val namespace: String,
    private val universe: Long,
    private val types: MacroTypeResolver,
    private val report: (MacroRegistrationDiagnostic) -> Unit
) {
    private val accepted = ArrayList<MacroFunction>()

    init {
        require(namespace.isNotBlank()) { "A macro registry requires a namespace" }
    }

    fun all(): List<MacroFunction> = immutableList(accepted)
    fun find(identifier: String): List<MacroFunction> = immutableList(accepted.filter { it.identifier == identifier })

    fun register(provider: Class<*>): List<MacroFunction> = immutableList(
        (provider.methods.asList() + provider.declaredMethods.asList()).distinct()
            .filterNot { it.isBridge || it.isSynthetic }
            .sortedBy { it.toGenericString() }
            .mapNotNull { register(it) }
    )

    fun register(method: Method): MacroFunction? {
        val prepared = try {
            MacroSignatureParser.prepare(method) ?: return null
        } catch (failure: MacroSignatureException) {
            report(MacroRegistrationDiagnostic(failure.code, method, failure.message!!))
            return null
        }
        return register(prepared)
    }

    /** Bind the exact preparation tree; this path does not re-read or re-parse annotations. */
    fun register(prepared: PreparedMacroSignature): MacroFunction? {
        val method = prepared.javaMethod
        val candidate = try {
            bind(prepared)
        } catch (failure: MacroSignatureException) {
            report(MacroRegistrationDiagnostic(failure.code, method, failure.message!!))
            return null
        }
        val previous = accepted.firstOrNull { entry ->
            entry.identifier == candidate.identifier &&
                entry.parameters.size == candidate.parameters.size &&
                entry.parameters.zip(candidate.parameters).all { (a, b) -> a.type().sameType(b.type()) }
        }
        if (previous != null) {
            report(
                MacroRegistrationDiagnostic(
                    MacroRegistrationCode.DUPLICATE,
                    method,
                    "duplicate MCFPP signature '${candidate.qualifiedName}'; parameter modes and return type do not distinguish overloads",
                    previous.javaMethod
                )
            )
            return null
        }
        accepted.add(candidate)
        return candidate
    }

    private fun bind(prepared: PreparedMacroSignature): MacroFunction {
        val method = prepared.javaMethod
        val identifier = prepared.identifier
        val voidType = resolve("void")
        val parameters = prepared.parameters.map { bindParameter(it, voidType) }
        val result = bindType(prepared.returnType, voidType)
        val returnsVoid = result.sameType(voidType)
        jvm(
            method.returnType == if (returnsVoid) Block::class.java else Expr::class.java,
            if (returnsVoid) "void macro must return exactly Block" else "non-void macro must return exactly Expr"
        )
        val signature = FunctionSignature.of(parameters, result)
        // Canonical type identity, including generic binding/revision, is the overload key.
        val typeKeys = parameters.joinToString(",") {
            val key = (it.type() as ImmutableTypeRef<*>).key
            "${key.universe}/${key.id}/${key.revision}"
        }
        val key = FunctionKey(universe, "${namespace.length}:$namespace:${identifier.length}:$identifier($typeKeys)")
        return MacroFunction(
            namespace,
            identifier,
            method,
            ImmutableFunctionRef(key, "$namespace:$identifier", signature, FunctionKind.MACRO),
            returnsVoid,
            prepared
        )
    }

    private fun resolve(text: String): TypeRef<*> {
        requireMetadata(text.isNotBlank(), "missing source type")
        val type = types.resolve(text.trim()) ?: invalid(
            MacroRegistrationCode.UNRESOLVED_TYPE,
            "unresolved source type '$text'"
        )
        requireMetadata(type is ImmutableTypeRef<*>, "type resolver must return canonical compiler descriptors")
        return type
    }

    private fun bindParameter(parameter: PreparedMacroParameter, voidType: TypeRef<*>): ParameterView {
        val type = bindType(parameter.type, voidType)
        requireMetadata(!type.sameType(voidType), "parameter '${parameter.name}' cannot have type void")
        return if (parameter.mode == ParamMode.INLINE) ParameterView.of(
            parameter.name,
            type,
            parameter.mode,
            requireNotNull(type.inlineSignature()) { "Canonical INLINE type lost its signature" }
        )
        else ParameterView.of(parameter.name, type, parameter.mode)
    }

    private fun bindType(prepared: PreparedMacroType, voidType: TypeRef<*>): TypeRef<*> = when (prepared) {
        is PreparedMacroType.Source -> resolve(prepared.expression)
        is PreparedMacroType.Callback -> {
            val callback = FunctionSignature.of(
                prepared.signature.parameters.map { bindParameter(it, voidType) },
                bindType(prepared.signature.returnType, voidType)
            )
            val type = types.inlineType(callback) ?: invalid(
                MacroRegistrationCode.UNRESOLVED_TYPE,
                "cannot intern INLINE type from prepared signature"
            )
            requireMetadata(type is ImmutableTypeRef<*>, "type resolver must return canonical compiler descriptors")
            requireMetadata(!type.sameType(voidType), "INLINE parameter cannot have type void")
            requireMetadata(type.inlineSignature() != null, "canonical INLINE type must retain its signature")
            type
        }
    }
}

/** The single metadata parser, independent of source catalogs, code arenas and provider execution. */
internal object MacroSignatureParser {
    fun prepare(method: Method, source: SourceSpan = SourceSpan.unknown()): PreparedMacroSignature? {
        if (method.isBridge || method.isSynthetic) return null
        val metadata = method.getAnnotation(MNIMacro::class.java) ?: return null
        jvm(Modifier.isPublic(method.modifiers) && Modifier.isStatic(method.modifiers), "entry must be public static")
        jvm(
            generateSequence(method.declaringClass) { it.enclosingClass }.all { Modifier.isPublic(it.modifiers) },
            "entry provider and its enclosing classes must be public"
        )
        val declared = metadata.params
        jvm(
            method.parameterCount == declared.size + 1,
            "expected MacroContext followed by ${declared.size} parameters, found ${method.parameterCount}"
        )
        jvm(method.parameterTypes.first() == MacroContext::class.java, "first parameter must be exactly MacroContext")
        declared.forEachIndexed { index, parameter ->
            val wrapper = when (parameter.mode) {
                ParamMode.VALUE -> Value::class.java
                ParamMode.REF -> Ref::class.java
                ParamMode.CONST -> Const::class.java
                ParamMode.INLINE -> InlineFn::class.java
            }
            jvm(
                method.parameterTypes[index + 1] == wrapper,
                "parameter '${parameter.name}' (${parameter.mode}) must use exactly ${wrapper.simpleName}"
            )
        }
        jvm(
            method.returnType == Block::class.java || method.returnType == Expr::class.java,
            "entry must return exactly Block or Expr"
        )
        val identifier = if (metadata.identifier.isEmpty()) method.name else metadata.identifier
        requireMetadata(isIdentifier(identifier), "invalid macro identifier '$identifier'")
        val names = hashSetOf<String>()
        val parameters = declared.map { parameter ->
            requireMetadata(isIdentifier(parameter.name), "invalid parameter name '${parameter.name}'")
            requireMetadata(names.add(parameter.name), "duplicate parameter name '${parameter.name}'")
            val type = parseType(parameter.type, source)
            if (parameter.mode == ParamMode.INLINE)
                requireMetadata(type is PreparedMacroType.Callback, "INLINE type must be a function signature '(types)->result'")
            PreparedMacroParameter(parameter.name, parameter.mode, type, source)
        }
        return PreparedMacroSignature(identifier, method, parameters, parseType(metadata.returnType, source), source)
    }

    private fun parseType(text: String, source: SourceSpan): PreparedMacroType {
        val input = text.trim()
        requireMetadata(input.isNotEmpty(), "missing source type")
        return if (input.startsWith('(')) PreparedMacroType.Callback(parseInline(input, source), source)
        else PreparedMacroType.Source(input, source)
    }

    /** Parse structural delimiters only; source leaves remain expressions for the one prepare resolver. */
    private fun parseInline(text: String, source: SourceSpan): PreparedInlineSignature {
        val input = text.trim()
        requireMetadata(input.startsWith('('), "INLINE type must be a function signature '(types)->result'")
        var depth = 0
        var end = -1
        for ((index, char) in structuralCharacters(input)) {
            if (char == '(') depth++
            if (char == ')' && --depth == 0) {
                end = index
                break
            }
        }
        requireMetadata(end >= 0, "unclosed INLINE parameter list")
        val tail = input.substring(end + 1).trim()
        requireMetadata(tail.startsWith("->"), "INLINE signature requires '->result'")
        val result = parseType(tail.substring(2), source)
        val body = input.substring(1, end).trim()
        val parameters = if (body.isEmpty()) emptyList() else splitTypes(body).mapIndexed { index, raw ->
            val match = Regex("^(ref|const|value|inline)\\s+(.+)$").matchEntire(raw)
            val mode = when (match?.groupValues?.get(1)) {
                "ref" -> ParamMode.REF
                "const" -> ParamMode.CONST
                "inline" -> ParamMode.INLINE
                else -> ParamMode.VALUE
            }
            val typeName = match?.groupValues?.get(2) ?: raw
            val type = parseType(typeName, source)
            if (mode == ParamMode.INLINE) requireMetadata(
                type is PreparedMacroType.Callback,
                "INLINE callbacks cannot accept INLINE parameters without a callback signature"
            )
            PreparedMacroParameter("arg$index", mode, type, source)
        }
        return PreparedInlineSignature(parameters, result, source)
    }

    private fun splitTypes(body: String): List<String> {
        val parts = ArrayList<String>()
        val nesting = ArrayList<Char>()
        var start = 0
        for ((index, char) in structuralCharacters(body)) {
            when (char) {
                '<', '(', '[' -> nesting.add(char)
                '>', ')', ']' -> if (!(char == '>' && index > 0 && body[index - 1] == '-')) {
                    val open = when (char) {
                        '>' -> '<'
                        ')' -> '('
                        else -> '['
                    }
                    requireMetadata(nesting.isNotEmpty() && nesting.last() == open, "unbalanced INLINE parameter type")
                    nesting.removeAt(nesting.lastIndex)
                }
                ',' -> if (nesting.isEmpty()) {
                    parts.add(body.substring(start, index).trim())
                    start = index + 1
                }
            }
        }
        requireMetadata(nesting.isEmpty(), "unbalanced INLINE parameter type")
        parts.add(body.substring(start).trim())
        requireMetadata(parts.none { it.isEmpty() }, "empty INLINE parameter type")
        return parts
    }

    /** Delimiters inside a source type's literal argument are not callback separators. */
    private fun structuralCharacters(text: String): Sequence<IndexedValue<Char>> = sequence {
        var quote: Char? = null
        var escaped = false
        for ((index, char) in text.withIndex()) {
            if (quote != null) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == quote -> quote = null
                }
            } else if (char == '\'' || char == '"') quote = char
            else yield(IndexedValue(index, char))
        }
        requireMetadata(quote == null, "unclosed literal in INLINE source type")
    }
}

private fun jvm(condition: Boolean, reason: String) {
    if (!condition) invalid(MacroRegistrationCode.INVALID_JVM, reason)
}
private fun requireMetadata(condition: Boolean, reason: String) {
    if (!condition) invalid(MacroRegistrationCode.INVALID_METADATA, reason)
}
private fun invalid(code: MacroRegistrationCode, reason: String): Nothing = throw MacroSignatureException(code, reason)

/** Mirrors the lexer's Letter/UnicodeDigit categories without running the source parser. */
private fun isIdentifier(name: String): Boolean {
    if (name.isEmpty() || name in reservedNames || name.matches(Regex("vec[0-9]+"))) return false
    val points = name.codePoints().toArray()
    return (points.first() == '_'.code || Character.isLetter(points.first())) &&
        points.drop(1).all { it == '_'.code || Character.isLetter(it) || Character.isDigit(it) }
}

// Keyword spellings from mcfppLexer.g4, including REF's Identifier action.
private val reservedNames = setOf(
    "this", "super", "if", "else", "while", "for", "do", "try", "store", "as", "from", "execute",
    "break", "continue", "return", "extends", "native", "concrete", "final", "public",
    "protected", "private", "override", "abstract", "impl", "const", "external", "import", "inline",
    "object", "data", "func", "enum", "operator", "typealias", "out", "in", "constructor",
    "global", "var", "get", "set", "namespace", "vec", "int", "entity", "bool", "byte", "short", "long",
    "float", "double", "selector", "string", "text", "nbt", "any", "void", "list", "ImmutableList",
    "map", "dict", "Type", "ByteArray", "IntArray", "LongArray", "true", "false", "null", "ref"
)
