package top.mcfpp.antlr

import org.antlr.v4.runtime.RuleContext

object RuleContextExtension {
    fun RuleContext.children() : List<RuleContext>{
        val list = mutableListOf<RuleContext>()
        for(i in 0 until childCount){
            list.add(getChild(i) as RuleContext)
        }
        return list
    }
}