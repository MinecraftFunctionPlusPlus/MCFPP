package top.mcfpp.model

import top.mcfpp.Project
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.field.NamespaceField

class Namespace(val identifier: String) {

    val field : NamespaceField

    init {
        this.field = NamespaceField()
    }

    companion object {
        val currNamespaceField: NamespaceField
            get() = GlobalField.localNamespaces[Project.currNamespace]!!.field
    }
}