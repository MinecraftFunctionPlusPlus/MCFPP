package top.mcfpp.model.function

import top.mcfpp.command.Commands
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.Void
import top.mcfpp.lib.NamespaceID
import top.mcfpp.model.CanSelectMember

class MCFunction(namespace: String, val path: String?, identifier: String): Function(identifier, namespace, null) {

    override val namespaceID: NamespaceID
        get() {
            return if(path == null){
                NamespaceID(namespace, identifier)
            }else{
                NamespaceID(namespace, path, identifier)
            }
        }

    override fun invoke(normalArgs: LinkedHashMap<String, Var<*>>, caller: CanSelectMember?): Var<*> {
        addCommand(Commands.function(this))
        return Void
    }

}