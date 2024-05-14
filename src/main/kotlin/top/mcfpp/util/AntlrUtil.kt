package top.mcfpp.util

import org.antlr.v4.runtime.tree.ParseTree

object AntlrUtil {

    fun getReadableText(context: ParseTree) : String{
        if(context.childCount == 0){
            return context.text
        }
        var re = ""
        var index = 0
        while(index < context.childCount){
            re += getReadableText(context.getChild(index))
            re += " "
            index++
        }
        return re
    }

}