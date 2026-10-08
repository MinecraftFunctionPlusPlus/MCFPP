package top.mcfpp.io.info

import top.mcfpp.antlr.mcfppParser.CurlBlockContext
import top.mcfpp.io.info.AbstractFunctionInfo.Companion.currFunction
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.function.ExtensionFunction
import top.mcfpp.model.function.GenericExtensionFunction
import top.mcfpp.type.MCFPPType

interface AbstractFunctionInfo<T: Function>: ModelInfo<T> {

    override fun get(): T

    companion object {
        var currFunction : Function? = null

        fun from(function: Function): AbstractFunctionInfo<*> {
            return when(function){
                is NativeFunction -> NativeFunctionInfo.from(function)
                is ExtensionFunction -> ExtensionFunctionInfo.from(function)
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
    var context: CurlBlockContext?,
    val declarationEnvironment: DeclarationEnvironmentInfo?,
    val accessModifier: Member.AccessModifier
): AbstractFunctionInfo<Function> {
    override fun get(): Function {
        val f = Function(identifier, namespace, null)
        f.declarationFile = null
        f.declarationEnvironment = declarationEnvironment
        f.returnType = returnType
        currFunction = f
        normalParams.forEach {
            f.normalParams.add(it.get())
        }
        for (tag in tags){
            f.addTag(tag.get())
        }
        f.isOverride = isOverride
        f.isAbstract = isAbstract
        f.accessModifier = accessModifier
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
                function.ast,
                DeclarationEnvironmentInfo.from(function.declarationFile) ?: function.declarationEnvironment,
                function.accessModifier
            )
        }
    }
}

data class GenericFunctionInfo(
    var namespace: String,
    var identifier: String,
    var normalParams: List<FunctionParamInfo>,
    var readonlyParams: List<FunctionParamInfo>,
    var context: CurlBlockContext,
    var returnType: MCFPPType,
    var isAbstract: Boolean,
    var tags: List<FunctionTagInfo>,
    var isOverride: Boolean,
    val declarationEnvironment: DeclarationEnvironmentInfo?,
    val accessModifier: Member.AccessModifier
): AbstractFunctionInfo<GenericFunction> {
    override fun get(): GenericFunction {
        val f = GenericFunction(identifier, namespace, context)
        f.declarationFile = null
        f.declarationEnvironment = declarationEnvironment
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
        f.isAbstract = isAbstract
        f.accessModifier = accessModifier
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
                genericFunction.isOverride,
                DeclarationEnvironmentInfo.from(genericFunction.declarationFile) ?: genericFunction.declarationEnvironment,
                genericFunction.accessModifier
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
    var caller: MCFPPType,
    val accessModifier: Member.AccessModifier
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
        f.isAbstract = isAbstract
        f.accessModifier = accessModifier
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
                function.caller,
                function.accessModifier
            )
        }
    }
}

/** An extension declaration, without compiled instances or the active call graph. */
data class ExtensionFunctionInfo(
    val declaration: FunctionInfo,
    val ownerType: MCFPPType,
    val readonlyParams: List<FunctionParamInfo>?,
): AbstractFunctionInfo<ExtensionFunction> {
    override fun get(): ExtensionFunction {
        ownerType.tryResolve()
        val owner = ownerType.instanceData
        val context = requireNotNull(declaration.context) { "Extension declaration has no body" }
        val function = if (readonlyParams != null) {
            GenericExtensionFunction(declaration.identifier, owner, declaration.namespace, context)
        } else ExtensionFunction(declaration.identifier, owner, declaration.namespace, context)
        function.declarationFile = null
        function.declarationEnvironment = declaration.declarationEnvironment
        function.returnType = declaration.returnType
        currFunction = function
        try {
            declaration.normalParams.forEach { function.normalParams.add(it.get()) }
            if (function is GenericExtensionFunction) readonlyParams!!.forEach { function.readOnlyParams.add(it.get()) }
            declaration.tags.forEach { function.addTag(it.get()) }
            function.isOverride = declaration.isOverride
            function.isAbstract = declaration.isAbstract
            function.accessModifier = declaration.accessModifier
            function.buildParamVar()
        } finally { currFunction = null }
        return function
    }

    companion object {
        fun from(function: ExtensionFunction) = ExtensionFunctionInfo(
            FunctionInfo.from(function),
            requireNotNull(function.owner).getType(),
            (function as? GenericExtensionFunction)?.readOnlyParams?.map { FunctionParamInfo.from(it) },
        )
    }
}
