package top.mcfpp.lib

import net.querz.nbt.io.SNBTUtil
import net.querz.nbt.tag.CompoundTag
import net.querz.nbt.tag.StringTag
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.MCIntConcrete
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.MCStringConcrete
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.core.lang.nbt.NBTBasedDataConcrete
import top.mcfpp.util.LogProcessor
import java.io.Serializable

class NBTPath(var source: NBTSource): Serializable {

    val pathList = ArrayList<Path>()

    //qwq[1]
    fun intIndex(index: MCInt): NBTPath{
        return this.clone().apply {
            pathList.add(IntPath(index))
        }
    }

    fun intIndex(index: Int): NBTPath{
        return this.clone().apply {
            pathList.add(IntPath(MCIntConcrete(index)))
        }
    }

    //qwq[{"text":"1"}]
    fun nbtIndex(index: NBTBasedData): NBTPath{
        return this.clone().apply {
            pathList.add(NBTPredicatePath(index))
        }
    }

    fun nbtIndex(tag: CompoundTag): NBTPath {
        return this.clone().apply {
            pathList.add(NBTPredicatePath(NBTBasedDataConcrete(tag)))
        }
    }

    fun memberIndex(index: MCString): NBTPath{
        return this.clone().apply {
            pathList.add(MemberPath(index))
        }
    }

    //qwq.index
    fun memberIndex(index: String): NBTPath{
        return this.clone().apply {
            pathList.add(MemberPath(MCStringConcrete(StringTag(index))))
        }
    }

    /**
     * 添加一个迭代器路径
     */
    fun iteratorIndex(): NBTPath{
        return this.clone().apply {
            pathList.add(IteratorPath())
        }
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

    fun pathToCommandPart(): Command {
        val cmd = Command()
        for (path in pathList.withIndex()){
            if(path.index == 0 && path.value !is MemberPath){
                LogProcessor.warn("Invalid nbt path: $path")
                cmd.build(" ", false)
            }
            when(path.value){
                is MemberPath -> {
                    val value = path.value as MemberPath
                    if(path.index == 0){
                        if(value.value is MCStringConcrete){
                            cmd.build((value.value as MCStringConcrete).value.value, false)
                        }else{
                            cmd.buildMacro(value.value, false)
                        }
                    }else{
                        if(value.value is MCStringConcrete){
                            cmd.build(".${(value.value as MCStringConcrete).value.value}", false)
                        }else{
                            cmd.build(".", false).buildMacro(value.value, false)
                        }
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

    fun toCommandPart(): Command{
        return source.toCommand().build(pathToCommandPart())
    }

    fun toChatComponentPart(): Command{
        return Command("\"nbt\":\"").build(pathToCommandPart(), false).build("\",", false)
            .build(source.toChatComponentPart())
    }

    fun clone(): NBTPath{
        val re = NBTPath(source)
        re.pathList.addAll(pathList.map { it.clone() })
        return re
    }

    override fun equals(other: Any?): Boolean {
        if(other == this) return true
        if(other !is NBTPath) return false
        if(source != other.source) return false
        for (i in pathList.withIndex()){
            if(i.value != other.pathList[i.index]){
                return false
            }
        }
        return true
    }

    companion object{

        val macroTemp = NBTPath(StorageSource("mcfpp:system")).memberIndex("macro_temp")

        val temp = NBTPath(StorageSource("mcfpp:system")).memberIndex("temp")

        val stack = NBTPath(StorageSource("mcfpp:system")).memberIndex("stack_frame")

        val global = NBTPath(StorageSource("mcfpp:system")).memberIndex("global")

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
            //保证源一致
            if(path.any { it.source != path[0].source }) return null
            val map = HashMap<NBTPath?, Int>()
            for (i in path){
                val parent = i.parent()
                map[parent] = (map[parent]?:0) + 1
            }
            val re = map.maxByOrNull { it.value }?.key
            if(map[re] == 1 && map.size != 1){
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

        //storage mcfpp:system test.stack_frame[0].qwq
        fun getNormalStackPath(v: Var<*>): NBTPath{
            return NBTPath(StorageSource("mcfpp:system"))
                .memberIndex("stack_frame[${v.stackIndex}]")
                .memberIndex(v.identifier)
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