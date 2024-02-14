package top.mcfpp.compiletime

import top.mcfpp.antlr.mcfppParser
import top.mcfpp.lang.ClassPointer
import top.mcfpp.lang.Var
import top.mcfpp.lib.Function
import top.mcfpp.lib.IField
import java.util.*
import kotlin.collections.ArrayList

class CompileTimeFunction :Function{
    var context: mcfppParser.FunctionBodyContext
    constructor(name:String,namespace:String,returnType:String,context:mcfppParser.FunctionBodyContext):super(name,namespace,returnType){
        this.context=context
    }

    fun setField(parent:IField){
        this.field = CompileTimeFunctionField(parent,this)
    }

    private fun makeField():CompileTimeFunctionField{
        return (this.field as CompileTimeFunctionField).clone()
    }
    private fun argPass(field:CompileTimeFunctionField, args: ArrayList<Var>) {
        for (argi in args.withIndex()){
            field.putVar(params[argi.index].identifier,argi.value,true)
        }
    }

    override fun invoke(args: ArrayList<Var>, callerClassP: ClassPointer?) {
        val field = makeField()
        argPass(field,args)
        val visitor = McfppCompileTimeVisitor(field)
        visitor.visit(this.context)
    }
}