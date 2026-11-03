package top.mcfpp.io

import top.mcfpp.Project
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import java.util.Collections
import java.util.IdentityHashMap

/** Includes bound bodies and private generated functions without completing unused declarations. */
internal object FunctionInventory {
    fun all(): List<Function> {
        val functions = Collections.newSetFromMap(IdentityHashMap<Function, Boolean>())
        val compounds = Collections.newSetFromMap(IdentityHashMap<CompoundData, Boolean>())
        fun function(value: Function) {
            if (!functions.add(value)) return
            value.compiledFunctions.values.forEach(::function)
            value.innerFunction.forEach(::function)
            value.child.forEach(::function)
        }
        fun compound(value: CompoundData) {
            if (!compounds.add(value)) return
            value.scope.forEachFunction(::function)
            if (value is DataTemplate) {
                value.constructors.forEach(::function)
                value.companionObject?.let(::compound)
            }
            if (value is GenericDataTemplate) value.compiledTemplates.values.forEach(::compound)
        }
        (GlobalScope.localNamespaces.values + GlobalScope.stdNamespaces.values + GlobalScope.libNamespaces.values).forEach { namespace ->
            namespace.scope.forEachFunction(::function)
            namespace.scope.forEachTemplate(::compound)
            namespace.scope.genericTemplate.values.forEach(::compound)
            namespace.scope.forEachObject(::compound)
        }
        Project.libraryImportSession?.functions()?.forEach(::function)
        PackEntrypoints.functions().forEach(::function)
        return functions.toList()
    }
}
