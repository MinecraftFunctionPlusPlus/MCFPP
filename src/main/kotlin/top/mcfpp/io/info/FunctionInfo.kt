package top.mcfpp.io.info

import top.mcfpp.antlr.mcfppParser.FunctionBodyContext
import top.mcfpp.io.info.AbstractFunctionInfo.Companion.currFunction
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.generic.GenericFunction
import top.mcfpp.type.MCFPPType

interface AbstractFunctionInfo<T: Function>: ModelInfo<T> {

    override fun get(): T

    companion object {
        var currFunction : Function? = null

        fun from(function: Function): AbstractFunctionInfo<*> {
            return when(function){
                is NativeFunction -> NativeFunctionInfo.from(function)
                is GenericFunction -> GenericFunctionInfo.from(function)
                else -> FunctionInfo.from(function)
            }
        }

    }
}

data class FunctionInfo(
    var namespace: String,
    var identifier: String,
    var normalParams: List<FunctionParamInfo>,
    var returnType: MCFPPType,
    var isAbstract: Boolean,
    var tags: List<FunctionTagInfo>,
    var isOverride: Boolean,
    var context: FunctionBodyContext?
): AbstractFunctionInfo<Function> {
    override fun get(): Function {
        val f = Function(identifier, namespace, null)
        f.returnType = returnType
        currFunction = f
        normalParams.forEach {
            f.normalParams.add(it.get())
        }
        for (tag in tags){
            f.addTag(tag.get())
        }
        f.isOverride = isOverride
        f.ast = context
        f.buildParamVar()
        currFunction = null
        return f
    }

    companion object {
        fun from(function: Function): FunctionInfo{
            return FunctionInfo(
                function.namespace,
                function.identifier,
                function.normalParams.map { FunctionParamInfo.from(it) },
                function.returnType,
                function.isAbstract,
                function.tags.map { FunctionTagInfo.from(it) },
                function.isOverride,
                function.ast
            )
        }
    }
}

data class GenericFunctionInfo(
    var namespace: String,
    var identifier: String,
    var normalParams: List<FunctionParamInfo>,
    var readonlyParams: List<FunctionParamInfo>,
    var context: FunctionBodyContext,
    var returnType: MCFPPType,
    var isAbstract: Boolean,
    var tags: List<FunctionTagInfo>,
    var isOverride: Boolean
): AbstractFunctionInfo<GenericFunction> {
    override fun get(): GenericFunction {
        val f = GenericFunction(identifier, namespace, context)
        f.returnType = returnType
        currFunction = f
        normalParams.forEach {
            f.normalParams.add(it.get())
        }
        readonlyParams.forEach {
            f.readOnlyParams.add(it.get())
        }
        for (tag in tags){
            f.addTag(tag.get())
        }
        f.isOverride = isOverride
        f.buildParamVar()
        currFunction = null
        return f
    }

    companion object {
        fun from(genericFunction: GenericFunction): GenericFunctionInfo{
            return GenericFunctionInfo(
                genericFunction.namespace,
                genericFunction.identifier,
                genericFunction.normalParams.map { FunctionParamInfo.from(it) },
                genericFunction.readOnlyParams.map { FunctionParamInfo.from(it) },
                genericFunction.ast!!,
                genericFunction.returnType,
                genericFunction.isAbstract,
                genericFunction.tags.map { FunctionTagInfo.from(it) },
                genericFunction.isOverride
            )
        }
    }
}

data class NativeFunctionInfo(
    var namespace: String,
    var identifier: String,
    var normalParams: List<FunctionParamInfo>,
    var readonlyParams: List<FunctionParamInfo>,
    var methodString: String,
    var returnType: MCFPPType,
    var isAbstract: Boolean,
    var tags: List<FunctionTagInfo>,
    var isOverride: Boolean,
    var caller: MCFPPType
): AbstractFunctionInfo<NativeFunction> {
    override fun get(): NativeFunction {
        val data = NativeFunction.stringToMethod(methodString)
        val f = NativeFunction(identifier, namespace, data)
        f.returnType = returnType
        currFunction = f
        normalParams.forEach {
            f.normalParams.add(it.get())
        }
        readonlyParams.forEach {
            f.readOnlyParams.add(it.get())
        }
        for (tag in tags){
            f.addTag(tag.get())
        }
        f.isOverride = isOverride
        f.caller = caller
        f.buildParamVar()
        currFunction = null
        return f
    }

    companion object {
        fun from(function: NativeFunction): NativeFunctionInfo {
            return NativeFunctionInfo(
                function.namespace,
                function.identifier,
                function.normalParams.map { FunctionParamInfo.from(it) },
                function.readOnlyParams.map { FunctionParamInfo.from(it) },
                NativeFunction.methodToString(function.javaMethod),
                function.returnType,
                function.isAbstract,
                function.tags.map { FunctionTagInfo.from(it) },
                function.isOverride,
                function.caller
            )
        }
    }
}