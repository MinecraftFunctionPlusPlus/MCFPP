package top.mcfpp.analysis

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.core.lang.Var
import top.mcfpp.model.function.Function
import java.util.Collections

sealed interface SpecializationArgument {
    data class Constant(val value: CompilerValue) : SpecializationArgument
    object Unknown : SpecializationArgument
    object Error : SpecializationArgument
}

data class GenerationOptions(val targetVersion: String, val maxWhileInline: Int, val foldIRConstants: Boolean = true)
class SpecializationKey(val declaration: SymbolId, arguments: List<SpecializationArgument>, val options: GenerationOptions) {
    val arguments: List<SpecializationArgument> = Collections.unmodifiableList(ArrayList(arguments))
    override fun equals(other: Any?) = other is SpecializationKey && declaration == other.declaration && arguments == other.arguments && options == other.options
    override fun hashCode() = 31 * (31 * declaration.hashCode() + arguments.hashCode()) + options.hashCode()
}

object SpecializationKeys {
    fun argument(value: Var<*>): SpecializationArgument = when {
        value.isError -> SpecializationArgument.Error
        else -> ValueSnapshot.of(value)?.let { SpecializationArgument.Constant(it) } ?: SpecializationArgument.Unknown
    }
    fun isConstant(value: Var<*>): Boolean = argument(value) is SpecializationArgument.Constant
    fun forArguments(function: Function, arguments: Collection<Var<*>>) = forArguments(function.declarationId, arguments)
    fun forArguments(declaration: SymbolId, arguments: Collection<Var<*>>) = SpecializationKey(
        declaration, arguments.map(::argument), GenerationOptions(Project.config.version, CompileSettings.maxWhileInline, CompileSettings.foldIRConstants)
    )
}
