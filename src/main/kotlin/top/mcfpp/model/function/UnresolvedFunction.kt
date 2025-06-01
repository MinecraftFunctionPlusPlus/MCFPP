package top.mcfpp.model.function

import top.mcfpp.Project
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CanSelectMember

class UnresolvedFunction(identifier: String, namespace: String = Project.currNamespace) : Function(identifier, namespace, context = null) {
    override fun invoke(normalArgs: LinkedHashMap<String, Var<*>>, caller: CanSelectMember?): Var<*> {
        throw UnsupportedOperationException()
    }
}