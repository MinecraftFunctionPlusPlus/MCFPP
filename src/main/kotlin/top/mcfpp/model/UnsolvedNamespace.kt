package top.mcfpp.model

import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.util.LogProcessor

class UnsolvedNamespace(val identifier: String) {

    fun resolve(): Namespace{
        if(GlobalScope.getNamespace(identifier) != null){
            return GlobalScope.getNamespace(identifier)!!
        }
        LogProcessor.error("Namespace $identifier not found")
        return Namespace(identifier)
    }

}