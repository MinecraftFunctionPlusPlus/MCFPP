package top.mcfpp.lib

import top.mcfpp.command.Command
import top.mcfpp.command.Commands
import top.mcfpp.core.lang.Coordinate3Var
import top.mcfpp.core.lang.CoordinateDimension
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.model.CompoundData
import top.mcfpp.model.Member
import top.mcfpp.model.function.Function
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor

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

                        override fun doAssignedBy(b: Var<*>): WriteOnlyVar {
                            if(b is CoordinateDimension){
                                command.build("positioned").build(b.toCommandPart()).build("~ ~")
                            }else{
                                LogProcessor.error("execute.pos.x can only be assigned with CoordinateDimension")
                            }
                            return this
                        }
                    })
                    field.putVar("y", object : WriteOnlyVar(){
                        override fun getData(): CompoundData {
                            return CompoundData("execute.pos.y", "mcfpp.shadow")
                        }

                        override fun doAssignedBy(b: Var<*>): WriteOnlyVar {
                            if(b is CoordinateDimension){
                                command.build("positioned ~").build(b.toCommandPart()).build("~")
                            }else{
                                LogProcessor.error("execute.pos.y can only be assigned with CoordinateDimension")
                            }
                            return this
                        }
                    })
                    field.putVar("z", object : WriteOnlyVar(){
                        override fun getData(): CompoundData {
                            return CompoundData("execute.pos.z", "mcfpp.shadow")
                        }

                        override fun doAssignedBy(b: Var<*>): WriteOnlyVar {
                            if(b is CoordinateDimension){
                                command.build("positioned ~ ~").build(b.toCommandPart())
                            }else{
                                LogProcessor.error("execute.pos.z can only be assigned with CoordinateDimension")
                            }
                            return this
                        }
                    })
                }
            }

            override fun doAssignedBy(b: Var<*>): WriteOnlyVar {
                if(b is Coordinate3Var){
                    command.build("positioned").build(b.toCommandPart())
                }else{
                    LogProcessor.error("execute.pos can only be assigned with Coordinate3Var")
                }
                return this
            }
        })
    }

    fun run(function: Function): Command{
        return command.build("run").build(Commands.function(function))
    }

    abstract class WriteOnlyVar: Var<WriteOnlyVar>(){

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