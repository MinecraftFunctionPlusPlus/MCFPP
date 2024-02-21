package top.mcfpp.lang

import top.mcfpp.lib.Template

abstract class IntTemplateBase : Var<MutableMap<String,String>> {

    constructor(b: Var<*>):super(b)

    constructor()

    /**
     * 它的类型
     */
    abstract val structType: Template
}