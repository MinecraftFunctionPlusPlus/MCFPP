package top.mcfpp.lib

import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CompoundData
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPType

class Execute {

    val command = Command("execute")

    val data = CompoundData("execute", "mcfpp.shadow").apply {
        field.putVar("pos", object: WriteOnlyVar(){
            override fun getData(): CompoundData {
                return CompoundData("execute.pos", "mcfpp.shadow").apply {
                    field.putVar("x", object : WriteOnlyVar(){
                        override fun getData(): CompoundData {
                            return CompoundData("execute.pos.x", "mcfpp.shadow")
                        }

                        override fun doAssign(b: Var<*>): WriteOnlyVar {
                            if(b is MCInt){

                            }
                            return this
                        }
                    })
                }
            }

            override fun doAssign(b: Var<*>): WriteOnlyVar {
                TODO("Not yet implemented")
            }
        })
    }

    private abstract class WriteOnlyVar: Var<WriteOnlyVar>(){

        abstract fun getData(): CompoundData

        override fun clone(): WriteOnlyVar = this

        override fun getTempVar(): WriteOnlyVar = this

        override fun storeToStack() {}

        override fun getFromStack() {}

        override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
            return getData().getVar(key) to true
        }

        override fun getMemberFunction(
            key: String,
            readOnlyParams: List<MCFPPType>,
            normalParams: List<MCFPPType>,
            accessModifier: Member.AccessModifier
        ): Pair<Function, Boolean> {
            return getData().getFunction(key, readOnlyParams, normalParams) to true
        }

    }

}