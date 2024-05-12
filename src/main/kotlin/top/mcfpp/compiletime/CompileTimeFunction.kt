package top.mcfpp.compiletime

import top.mcfpp.antlr.mcfppParser
import top.mcfpp.lang.ClassPointer
import top.mcfpp.lang.Var
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.model.function.Function
import top.mcfpp.model.field.IField
import kotlin.collections.ArrayList

class CompileTimeFunction : Function {
    var context: mcfppParser.FunctionBodyContext
    constructor(name:String, namespace:String, returnType: MCFPPType, context:mcfppParser.FunctionBodyContext):super(name,namespace,returnType){
        this.context=context
    }

    fun setField(parent: IField){
        this.field = CompileTimeFunctionField(parent,this)
    }

    private fun makeField():CompileTimeFunctionField{
        return (this.field as CompileTimeFunctionField).clone()
    }
    private fun argPass(field:CompileTimeFunctionField, /*readOnlyArgs: ArrayList<Var<*>>, */normalArgs: ArrayList<Var<*>>) {
        for (argi in normalArgs.withIndex()){
            field.putVar(normalParams[argi.index].identifier,argi.value,true)
        }
        /*
        for (argi in readOnlyArgs.withIndex()){
            field.putVar(readOnlyParams[argi.index].identifier,argi.value,true)
        }
         */
    }

    override fun invoke(/*readOnlyArgs: ArrayList<Var<*>>, */normalArgs: ArrayList<Var<*>>, callerClassP: ClassPointer?) {
        val field = makeField()
        argPass(field, /*readOnlyArgs, */normalArgs)
        val visitor = McfppCompileTimeVisitor(field)
        visitor.visit(this.context)
    }
}