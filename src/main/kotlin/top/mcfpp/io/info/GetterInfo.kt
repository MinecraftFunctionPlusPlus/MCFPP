package top.mcfpp.io.info

import top.mcfpp.antlr.mcfppParser
import top.mcfpp.model.property.*

abstract class GetterInfo<T : AbstractAccessor>: ModelInfo<T> {
    companion object {
        fun from(accessor: AbstractAccessor): GetterInfo<*>{
            return when(accessor){
                is SimpleAccessor -> SimpleAccessorInfo()
                is ExpressionAccessor -> ExpressionAccessorInfo(accessor.ctx)
                is FunctionAccessor -> FunctionAccessorInfo(FunctionInfo.from(accessor.function))
                is NativeAccessor -> NativeAccessorInfo(NativeFunctionInfo.from(accessor.function))
                else -> throw IllegalArgumentException("Unknown accessor type: ${accessor::class.simpleName}")
            }
        }
    }
}

class SimpleAccessorInfo: GetterInfo<SimpleAccessor>(){
    override fun get(): SimpleAccessor {
        return SimpleAccessor()
    }
}

class ExpressionAccessorInfo(
    val ctx: mcfppParser.ExpressionContext
): GetterInfo<ExpressionAccessor>(){
    override fun get(): ExpressionAccessor {
        return ExpressionAccessor(ctx)
    }
}

class FunctionAccessorInfo(
    val function: FunctionInfo
): GetterInfo<FunctionAccessor>(){
    override fun get(): FunctionAccessor {
        return FunctionAccessor(function.get())
    }
}

class NativeAccessorInfo(
    val nativeFunction: NativeFunctionInfo
): GetterInfo<NativeAccessor>(){
    override fun get(): NativeAccessor {
        return NativeAccessor(nativeFunction.get())
    }
}


