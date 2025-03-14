package top.mcfkt.model

import top.mcfpp.model.Namespace
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.TempPool

typealias MCFPPFunction = top.mcfpp.model.function.Function

class Namespace(val namespace: Namespace) {

    fun func(identifier: String, returnType: MCFPPType = MCFPPBaseType.Void, operation: Function.() -> Unit): MCFPPFunction {
        val f = MCFPPFunction(identifier, context =  null)
        f.returnType = returnType
        f.runInFunction {
            Function(f).operation()
        }
        if (namespace.field.hasFunction(f, true)) {
            LogProcessor.error("Already defined function: " + f.namespaceID)
            MCFPPFunction.currFunction = MCFPPFunction.nullFunction
        } else if(namespace.field.hasDeclaredType(f.identifier)) {
            LogProcessor.error("Function name conflicted with type name: " + f.identifier)
            MCFPPFunction.currFunction = MCFPPFunction.nullFunction
        } else{
            namespace.field.addFunction(f,false)
        }
        return f
    }

    fun func(returnType: MCFPPType = MCFPPBaseType.Void, operation: Function.() -> Unit): MCFPPFunction {
        return func(TempPool.getFunctionIdentify("temp"),returnType, operation)
    }

}