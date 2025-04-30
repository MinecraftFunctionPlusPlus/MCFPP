package top.mcfpp.compiletime

import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.obj.ClassPointer
import top.mcfpp.core.lang.Var
import top.mcfpp.model.field.IField
import top.mcfpp.model.function.Function

class CompileTimeFunction : Function {
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(name:String, namespace:String, context:mcfppParser.FunctionBodyContext):super(name,namespace, context)

    fun setField(parent: IField){
        this.field = CompileTimeFunctionField(parent)
    }

    private fun makeField():CompileTimeFunctionField{
        return (this.field as CompileTimeFunctionField).clone()
    }
    private fun argPass(field:CompileTimeFunctionField, normalArgs: List<Var<*>>) {
        for (argi in normalArgs.withIndex()){
            field.putVar(normalParams[argi.index].identifier,argi.value,true)
        }
        /*
        for (argi in readOnlyArgs.withIndex()){
            field.putVar(readOnlyParams[argi.index].identifier,argi.value,true)
        }
         */
    }

    override fun invoke(normalArgs: List<Var<*>>, callerClassP: ClassPointer) {
        val field = makeField()
        argPass(field, normalArgs)
        val visitor = MCFPPCompileTimeVisitor(field)
        visitor.visit(this.ast)
    }
}