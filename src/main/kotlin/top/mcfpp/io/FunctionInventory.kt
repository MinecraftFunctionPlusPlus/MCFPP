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
    fun clearCallGraph() {
        // Shared intrinsic declarations outlive a Project. Their reverse call edges must
        // release the completed project before its namespace roots are discarded.
        val previous = collect(includeLibrarySession = false, includeSharedCallers = true)
        previous.forEach { it.child.clear(); it.parent.clear() }
    }

    fun all(): List<Function> = collect(includeLibrarySession = true)

    private fun collect(includeLibrarySession: Boolean, includeSharedCallers: Boolean = false): List<Function> {
        val seenFunctions = Collections.newSetFromMap(IdentityHashMap<Function, Boolean>())
        val functions = arrayListOf<Function>()
        val compounds = Collections.newSetFromMap(IdentityHashMap<CompoundData, Boolean>())
        fun function(value: Function) {
            if (!seenFunctions.add(value)) return
            functions.add(value)
            value.compiledFunctions.values.forEach(::function)
            value.innerFunction.forEach(::function)
            value.child.forEach(::function)
            if (includeSharedCallers) value.parent.forEach(::function)
        }
        fun compound(value: CompoundData) {
            if (!compounds.add(value)) return
            // This is an inventory of already-bound functions for optimization/emission.
            // A semantic scope enumeration would load every unused library field/accessor.
            if (value is DataTemplate && value.hasDeferredLibraryMembers) {
                value.companionObject?.let(::compound)
                return
            }
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
        if (includeLibrarySession) Project.libraryImportSession?.functions()?.forEach(::function)
        if (includeSharedCallers) InitializedBuiltinData.all().forEach { value ->
            value.scope.functions.values.forEach { overloads -> overloads.forEach(::function) }
        }
        PackEntrypoints.functions().forEach(::function)
        return functions.toList()
    }
}
