package top.mcfpp.lib

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.Tag
import top.mcfpp.command.Command
import top.mcfpp.lang.*
import top.mcfpp.util.LogProcessor
import java.io.Serializable

class NBTPath(var source: NBTSource): Serializable {

    val pathList = ArrayList<Path>()

    fun intIndex(index: MCInt): NBTPath{
        pathList.add(IntPath(index))
        return this
    }

    fun nbtIndex(index: NBTBasedData<Tag<*>>): NBTPath{
        pathList.add(NBTPredicatePath(index))
        return this
    }

    fun memberIndex(index: MCString): NBTPath{
        pathList.add(MemberPath(index.identifier, false))
        return this
    }

    fun memberIndex(index: String): NBTPath{
        pathList.add(MemberPath(index))
        return this
    }

    fun iteratorIndex(): NBTPath{
        pathList.add(IteratorPath())
        return this
    }

    fun toCommandPart(): Command{
        val cmd = source.toCommand()
        for (path in pathList.withIndex()){
            if(path.index == 0 && path.value !is MemberPath){
                LogProcessor.warn("Invalid nbt path: $path")
                cmd.build(" ", false)
            }
            when(path.value){
                is MemberPath ->{
                    val value = path.value as MemberPath
                    if(!value.isMacro){
                        cmd.build(".${value.value}", false)
                    }else{
                        cmd.build(".", false).buildMacro(value.value, false)
                    }
                }

                is IntPath -> {
                    val value = (path.value as IntPath).value
                    if(value is MCIntConcrete){
                        cmd.build("[${value.value}]", false)
                    }else{
                        cmd.build("[", false).buildMacro(value.identifier, false).build("]", false)
                    }
                }

                is NBTPredicatePath -> {
                    val value = (path.value as NBTPredicatePath).value
                    if(value is NBTBasedDataConcrete){
                        cmd.build("[${SNBTUtil.toSNBT(value.value)}]", false)
                    }else{
                        cmd.build("[", false).buildMacro(value.identifier, false).build("]", false)
                    }
                }

                is IteratorPath -> {
                    cmd.build("[]",false)
                }
            }
        }
        return cmd
    }

    companion object{
        val STORAGE = "storage"
        val ENTITY = "entity"
    }

}

interface Path: Serializable

data class IntPath(val value : MCInt) : Path

data class NBTPredicatePath(val value : NBTBasedData<Tag<*>>) : Path

data class MemberPath(val value : String, val isMacro: Boolean = false): Path

class IteratorPath: Path