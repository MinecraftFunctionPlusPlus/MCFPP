package top.mcfpp.lib

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.Tag
import top.mcfpp.Project
import top.mcfpp.command.Command
import top.mcfpp.core.lang.*
import top.mcfpp.util.LogProcessor
import java.io.Serializable

class NBTPath(var source: NBTSource): Serializable {

    val pathList = ArrayList<Path>()

    //qwq[1]
    fun intIndex(index: MCInt): NBTPath{
        pathList.add(IntPath(index))
        return this
    }

    //qwq[{"text":"1"}]
    fun nbtIndex(index: NBTBasedData): NBTPath{
        pathList.add(NBTPredicatePath(index))
        return this
    }

    fun memberIndex(index: MCString): NBTPath{
        if(index is MCStringConcrete) {
            pathList.add(MemberPath(index.value.value))
        }else{
            pathList.add(MemberPath(index.identifier, true))
        }
        return this
    }

    //qwq.index
    fun memberIndex(index: String): NBTPath{
        pathList.add(MemberPath(index))
        return this
    }

    /**
     * 添加一个迭代器路径
     */
    fun iteratorIndex(): NBTPath{
        pathList.add(IteratorPath())
        return this
    }

    /**
     * 移除最后一个路径
     */
    fun removeLast(): Path{
        return pathList.removeLast()
    }

    /**
     * 获取上一层路径
     *
     * @return 父路径。如果不存在则返回null
     */
    fun parent(): NBTPath?{
        if(pathList.isEmpty()){
            return null
        }
        val re = clone()
        re.pathList.removeLast()
        return re
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
                    if(value is NBTBasedDataConcrete<*>){
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

    fun clone(): NBTPath{
        val re = NBTPath(source)
        re.pathList.addAll(pathList.map { it.clone() })
        return re
    }

    companion object{

        val FRAME = NBTPath(StorageSource("mcfpp:system")).memberIndex(Project.config.rootNamespace + ".stack_frame[0]")

        val STORAGE = "storage"
        val ENTITY = "entity"
        val BLOCK = "block"
    }

}

interface Path: Serializable, Cloneable{
    public override fun clone(): Path
}

data class IntPath(val value : MCInt) : Path {
    override fun clone(): Path {
        return IntPath(value)
    }

}

data class NBTPredicatePath(val value : NBTBasedData) : Path {
    override fun clone(): Path {
        return NBTPredicatePath(value)
    }


}

data class MemberPath(var value : String, var isMacro: Boolean = false): Path {
    override fun clone(): Path {
        return MemberPath(value, isMacro)
    }
}

class IteratorPath: Path {
    override fun clone(): Path {
        return IteratorPath()
    }
}