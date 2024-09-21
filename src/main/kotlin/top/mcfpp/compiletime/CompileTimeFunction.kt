package top.mcfpp.compiletime

import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.ClassPointer
import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPType
import top.mcfpp.model.function.Function
import top.mcfpp.model.field.IField
import kotlin.collections.ArrayList

class CompileTimeFunction : Function {
    constructor(name:String, namespace:String, returnType: MCFPPType, context:mcfppParser.FunctionBodyContext):super(name,namespace,returnType, context)

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

    override fun invoke(normalArgs: ArrayList<Var<*>>, callerClassP: ClassPointer) {
        val field = makeField()
        argPass(field, normalArgs)
        val visitor = MCFPPCompileTimeVisitor(field)
        visitor.visit(this.ast)
    }
}