package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*

/**
 * One fully bound macro invocation inside a concrete expansion session.
 *
 * [prelude] and [arguments] are one lexical unit: every VALUE argument is read from a temporary
 * declared in [prelude], and every freshly frozen REF argument is declared there by a
 * [StmtNode.CaptureAddress]. [sources] keeps the session-owned reference that produced each
 * argument so a nested child can alias the same physical position instead of copying or
 * re-evaluating it. The final expansion must place [prelude] and the host result in the same
 * block/expression region.
 */
internal class MacroInvocation(
    val macro: MacroFunction,
    val site: SourceSpan,
    val scope: ScopeId,
    val prelude: Block,
    arguments: List<MacroInput>,
    readonlyArguments: List<TypedConstant> = emptyList(),
    sources: List<Ref<*>?> = emptyList()
) {
    val arguments: List<MacroInput> = immutableList(arguments)
    val readonlyArguments: List<TypedConstant> = immutableList(readonlyArguments)
    private val argumentSources: List<Ref<*>?> = immutableList(sources)

    init {
        require(this.arguments.size == macro.parameters.size) {
            "Macro invocation argument count differs from its prepared signature"
        }
        require(this.readonlyArguments.size == macro.reference.readonlyParameters().size) {
            "Macro invocation readonly count differs from its prepared signature"
        }
        require(argumentSources.isEmpty() || argumentSources.size == this.arguments.size) {
            "Macro invocation source aliases must line up with its arguments"
        }
    }

    val qualifiedName: String get() = macro.qualifiedName

    fun source(index: Int): Ref<*>? = argumentSources.getOrNull(index)

    /** A stable chain label; never contains physical session or arena identities. */
    fun canonicalSignature(): String = buildString {
        append(macro.qualifiedName)
        append('(')
        macro.parameters.forEachIndexed { index, parameter ->
            if (index > 0) append(',')
            append(parameter.mode()).append(' ').append(parameter.type().name())
        }
        append(")->").append(macro.returnType.name())
    }

    fun expansionSite(configurationKey: String = ""): ExpansionSite = ExpansionSite(site, canonicalSignature(), configurationKey)
}
