package top.mcfpp.core.lang.entity

import top.mcfpp.core.lang.*
import top.mcfpp.lib.EntityFacts
import top.mcfpp.model.Member
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPEntityType
import top.mcfpp.util.StringHelper.toCamelCase

/** One data schema and address lookup for ordinary targets and borrowed executors. */
internal object EntityMembers {
    fun data(target: EntityTargetAccess? = null): DataTemplate {
        return dataForFacts(target?.entityFacts)
    }

    fun dataForFacts(facts: EntityFacts?): DataTemplate {
        val data = SelectorVar.declarationData(MCFPPEntityType.NormalSelector)
        facts?.knownKinds?.singleOrNull()?.takeIf { it.startsWith("minecraft:") }?.let { kind ->
            val name = kind.substringAfter(':').toCamelCase(true) + "Data"
            GlobalScope.getUnsolvedImportNamespace("mcfpp.minecraft.entity")?.scope?.getTemplate(name)?.getType()?.let {
                it.tryResolve()
                data.extends(it.template)
            }
        }
        return data
    }

    fun member(receiver: Var<*>, key: String, access: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        val target = receiver as EntityTargetAccess
        if (key == "dat") return EntityDataAddress.nbt(receiver, data(target).getType()) to true
        if (key == "score") return EntityDataAddress.scores(receiver) to true
        val schema = data(target)
        schema.scope.getProperty(key)?.let { return PropertyVar(it, Void, receiver) to (access >= it.accessModifier) }
        val value = schema.scope.getVar(key)
        if (value is SelectorVar.SelectorParamMap && receiver is SelectorVar) {
            value.selector = receiver
            return value to true
        }
        return null to true
    }
}
