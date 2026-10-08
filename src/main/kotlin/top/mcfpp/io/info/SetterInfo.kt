package top.mcfpp.io.info

import top.mcfpp.model.property.*


abstract class SetterInfo<T : AbstractMutator>: ModelInfo<T> {
    companion object {
        fun from(mutator: AbstractMutator): SetterInfo<*>{
            return when(mutator){
                is SimpleMutator -> SimpleMutatorInfo()
                is FunctionMutator -> FunctionMutatorInfo(FunctionInfo.from(mutator.function))
                is NativeMutator -> NativeMutatorInfo(NativeFunctionInfo.from(mutator.function))
                else -> throw IllegalArgumentException("Unknown mutator type: ${mutator::class.simpleName}")
            }
        }
    }
}

class SimpleMutatorInfo: SetterInfo<SimpleMutator>(){
    override fun get(): SimpleMutator {
        return SimpleMutator()
    }
}

class FunctionMutatorInfo(
    val function: FunctionInfo
): SetterInfo<FunctionMutator>(){
    override fun get(): FunctionMutator {
        return FunctionMutator(function.get())
    }
}

class NativeMutatorInfo(
    val nativeFunction: NativeFunctionInfo
): SetterInfo<NativeMutator>(){
    override fun get(): NativeMutator {
        return NativeMutator(nativeFunction.get())
    }
}
