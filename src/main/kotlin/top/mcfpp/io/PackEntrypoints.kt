package top.mcfpp.io

import top.mcfpp.Project
import top.mcfpp.api.resources.PackResources
import top.mcfpp.model.Member
import top.mcfpp.model.Native
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.ExtensionFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.util.LogProcessor

/** Export through the ordinary call convention, including frame setup and cleanup. */
internal object PackEntrypoints {
    private val wrappers = linkedMapOf<String, Function>()
    internal fun functions(): Collection<Function> = wrappers.values
    internal fun reset() = wrappers.clear()

    fun prepare() {
        wrappers.clear()
        for ((id, targetId) in Project.config.entrypoints) {
            if (!PackResources.validId(id) || ':' !in targetId ||
                !PackResources.validId(targetId.substringBefore(':') + ":entry") ||
                !Regex("[\\p{L}_][\\p{L}\\p{N}_]*").matches(targetId.substringAfter(':'))) {
                LogProcessor.error("entrypoints: expected explicit namespace:path IDs: $id = $targetId")
                continue
            }
            val candidates = arrayListOf<Function>()
            GlobalScope.localNamespaces[targetId.substringBefore(':')]?.scope?.forEachFunction {
                if (it.identifier == targetId.substringAfter(':')) candidates += it
            }
            val target = candidates.singleOrNull()
            if (target == null || target is Native || target is ExtensionFunction ||
                target.normalParams.isNotEmpty() || target.returnType != MCFPPPrivateType.Void ||
                target.accessModifier != Member.AccessModifier.PUBLIC || target.javaClass != Function::class.java) {
                LogProcessor.error("entrypoints.$id: $targetId must identify one public, non-generic, parameterless void project function")
                continue
            }
            if (id == target.namespaceID.toString()) {
                LogProcessor.error("entrypoints.$id: choose an ID different from the generated function ${target.namespaceID}")
                continue
            }
            val wrapper = Function("vanilla_entry", context = null)
            // The exported wrapper owns the call frame; suppress automatic entrance wrapping on its target.
            target.parent.add(wrapper)
            wrapper.child.add(target)
            wrapper.runInFunction { target.invoke(emptyList(), null) }
            wrappers[id] = wrapper
        }
    }

    fun resources(): Map<String, ByteArray> = wrappers.mapKeys { (id, _) ->
        "${id.substringBefore(':')}/function/${id.substringAfter(':')}.mcfunction"
    }.mapValues { (_, function) -> function.cmdStr.toByteArray() }
}
