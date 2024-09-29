package top.mcfpp.lib

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.StringTag
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
        pathList.add(MemberPath(index))
        return this
    }

    //qwq.index
    fun memberIndex(index: String): NBTPath{
        pathList.add(MemberPath(MCStringConcrete(StringTag(index))))
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

    fun isParentOf(other: NBTPath): Boolean{
        if(pathList.size >= other.pathList.size){
            return false
        }
        for (i in pathList.withIndex()){
            if(i.value != other.pathList[i.index]){
                return false
            }
        }
        return true
    }

    fun isImmediateParentOf(other: NBTPath): Boolean{
        if(pathList.size + 1 != other.pathList.size){
            return false
        }
        for (i in pathList.withIndex()){
            if(i.value != other.pathList[i.index]){
                return false
            }
        }
        return true
    }

    fun isChildOf(other: NBTPath): Boolean{
        return other.isParentOf(this)
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
                    if(value.value is MCStringConcrete){
                        cmd.build(".${(value.value as MCStringConcrete).value.value}", false)
                    }else{
                        cmd.build(".", false).buildMacro(value.value, false)
                    }
                }

                is IntPath -> {
                    val value = (path.value as IntPath).value
                    if(value is MCIntConcrete){
                        cmd.build("[${value.value}]", false)
                    }else{
                        cmd.build("[", false).buildMacro(value, false).build("]", false)
                    }
                }

                is NBTPredicatePath -> {
                    val value = (path.value as NBTPredicatePath).value
                    if(value is NBTBasedDataConcrete){
                        cmd.build("[${SNBTUtil.toSNBT(value.value)}]", false)
                    }else{
                        cmd.build("[", false).buildMacro(value, false).build("]", false)
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

        val macroTemp = NBTPath(StorageSource("mcfpp:system")).memberIndex("macro_temp")

        val STORAGE = "storage"
        val ENTITY = "entity"
        val BLOCK = "block"

        /**
         * 返回一组路径的最大共同路径。如果没有共同路径，或者共同路径为null，则返回null。返回的路径永远不可能是索引路径的父路径。
         */
        fun getMaxImmediateSharedPath(vararg path: NBTPath): NBTPath?{
            if(path.isEmpty()){
                return null
            }
            val map = HashMap<NBTPath?, Int>()
            for (i in path){
                val parent = i.parent()
                map[parent] = 1
                for (j in path){
                    if(j.pathList.last() !is MemberPath) continue
                    if(parent == j.parent()){
                        map[parent] = (map[parent]?:0) + 1
                    }
                }
            }
            val re = map.maxByOrNull { it.value }?.key
            if(map[re] == 1){
                return null
            }
            return re
        }

        fun getSharedPath(vararg path: NBTPath): NBTPath?{
            if(path.isEmpty()){
                return null
            }
            var re = path[0]
            for (i in path.withIndex()){
                re = getSharedPath(re, i.value) ?: return null
            }
            return re
        }

        fun getSharedPath(path1: NBTPath, path2: NBTPath): NBTPath?{
            if(path2.pathList.size < path1.pathList.size) return getSharedPath()
            if(path1.isParentOf(path2)){
                return path1.clone()
            }
            return path1.parent()?.let { getSharedPath(it, path2) }
        }
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

data class MemberPath(var value : MCString): Path {
    override fun clone(): Path {
        return MemberPath(value)
    }
}

class IteratorPath: Path {
    override fun clone(): Path {
        return IteratorPath()
    }
}