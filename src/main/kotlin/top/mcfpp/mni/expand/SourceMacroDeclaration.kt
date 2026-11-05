package top.mcfpp.mni.expand

import top.mcfpp.annotations.MNIMacro
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.io.info.MacroFunctionInfo
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import java.lang.reflect.Method

/** Bind a source `func ... = Owner.method` declaration without executing provider code. */
internal object SourceMacroDeclaration {
    /** False means the JVM target is an ordinary native function. */
    fun bind(syntax: mcfppParser.NativeFuncDeclarationContext, declaration: NativeFunction,
             methods: List<Method>, namespace: Namespace): Boolean {
        val candidates = methods.filter { it.isAnnotationPresent(MNIMacro::class.java) }
        if (candidates.isEmpty()) return false
        if (declaration.readOnlyParams.isNotEmpty()) {
            LogProcessor.error("Macro '${declaration.identifier}' does not accept readonly parameters; use Const parameters in the Kotlin implementation")
            return true
        }
        if (declaration.normalParams.any { it.hasDefault }) {
            LogProcessor.error("Macro '${declaration.identifier}' does not accept default arguments")
            return true
        }
        val failures = arrayListOf<String>()
        val matches = candidates.mapNotNull { method ->
            try {
                MacroSignatureParser.prepare(method, sourceIdentifier = declaration.identifier) ?: return@mapNotNull null
                val metadata = method.getAnnotation(MNIMacro::class.java)
                fun type(text: String) = MCFPPType.parseFromString(text, declaration.scope, declaration)
                if (metadata.normalParams.size != declaration.normalParams.size ||
                    metadata.normalParams.zip(declaration.normalParams).any { (text, parameter) -> type(text)?.typeId != parameter.type.typeId } ||
                    type(metadata.returnType)?.typeId != declaration.returnType.typeId) return@mapNotNull null
                requireNotNull(MacroFunctionInfo.from(method)).declaredAs(declaration.identifier,
                    declaration.normalParams.map { it.identifier })
            } catch (failure: MacroSignatureException) {
                failures.add(failure.message.orEmpty())
                null
            }
        }
        if (matches.size != 1) {
            val reason = if (matches.isEmpty()) "no implementation matches the declared parameter and return types" else "ambiguous JVM implementations"
            LogProcessor.error("Cannot bind macro '${declaration.identifier}' to ${syntax.javaRefer().text}: $reason" +
                failures.takeIf { it.isNotEmpty() }?.joinToString("; ", " (", ")").orEmpty())
            return true
        }
        val descriptor = matches.single()
        val duplicate = namespace.macroDeclarations().any { previous ->
            previous.identifier == descriptor.identifier && previous.parameters.size == descriptor.parameters.size &&
                previous.parameters.zip(declaration.normalParams).all { (parameter, source) ->
                    MCFPPType.parseFromString(parameter.type, declaration.scope, declaration)?.typeId == source.type.typeId
                }
        }
        if (duplicate) LogProcessor.error("Already defined macro: ${namespace.identifier}:${declaration.identifier}")
        else namespace.restoreMacroDeclarations(listOf(descriptor))
        return true
    }
}
