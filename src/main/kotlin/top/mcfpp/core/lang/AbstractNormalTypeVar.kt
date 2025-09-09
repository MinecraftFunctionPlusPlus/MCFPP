package top.mcfpp.core.lang

import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate

class AbstractNormalTypeVar: Var<AbstractNormalTypeVar>, MCFPPValue<Var<*>?> {

    override var value: Var<*>? = null

    constructor(identifier: String, type: MCFPPType): super(identifier){
        this.type = type
    }

    constructor(type: MCFPPType): super(){
        this.type = type
    }

    override fun doAssignedBy(b: Var<*>): AbstractNormalTypeVar {
        if (b.type.isSubOf(type)){
            value = b.type.build(identifier).assignedBy(b)
        }else{
            LogProcessor.error(TextTranslator.CAST_ERROR.translate(b.type.typeName, type.typeName))
        }
        return this
    }

    override fun clone(): AbstractNormalTypeVar {
        return AbstractNormalTypeVar(identifier, type).apply {
            this.value = value?.clone()
        }
    }

    override fun getTempVar(): AbstractNormalTypeVar {
        return AbstractNormalTypeVar(type).apply {
            this.value = value?.getTempVar()
            this.value?.identifier = identifier
            isTemp = true
        }
    }

    override fun storeToStack() {
        value?.storeToStack()
    }

    override fun getFromStack() {
        value?.getFromStack()
    }

    override fun toDynamic(replace: Boolean): Var<*> {
        value = null
        return this
    }

}