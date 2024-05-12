package top.mcfpp.model

import top.mcfpp.Project
import top.mcfpp.lang.*
import java.lang.Class

/**
 * 代表了mcfpp中声明的一个native类
 */
class NativeClass(identifier: String, namespace: String, cls: Class<INativeClass>) : top.mcfpp.model.Class(), Native {
    var cls: Class<INativeClass>

    constructor(identifier: String, cls: Class<INativeClass>) : this(identifier, Project.currNamespace, cls)

    init {
        this.identifier = identifier
        this.namespace = namespace
        this.cls = cls
    }
}