package top.mcfpp.model

import top.mcfpp.model.field.NamespaceField

class Namespace(val identifier: String) {

    val field: NamespaceField

    init {
        this.field = NamespaceField()
    }
}