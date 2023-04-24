package top.alumopper.mcfpp.type

import top.alumopper.mcfpp.exception.IllegalFormatException
import java.util.regex.Pattern

/**
 * 代表某类方块状态的判据。
 * 精确匹配某种方块状态，或者包含指定NBT的方块实体NBT。不能使用标签。
 *
 * 一些例子
 * <list type="bullet">
 * <item>stone</item>
 * <item>minecraft:stone</item>
 * <item>stone[foo=bar]</item>
 * <item>foo{bar:baz}</item>
</list> *
 * 注意，例子中使用的方块状态，方块id或者方块nbt并不一定是游戏中实际存在的，仅用于格式参考。
 */
class BlockState(pre: String, nbt: NBTTag) {
    /**
     * 能直接被Minecraft解析的方块状态。
     */
    var pre: String

    //todo
    var nbt: NBTTag

    init {
        if (!Pattern.matches("^([a-z0-9_]+|([a-z0-9_]+[:][a-z0-9_]+))+([\\[][a-z0-9_]+[=][a-z0-9_]+[\\]])*$", pre)) {
            throw IllegalFormatException("无法解析字符串" + pre + "为方块状态")
        }
        this.pre = pre
        this.nbt = nbt
    }

    @Override
    override fun toString(): String {
        return "$pre{$nbt}"
    }
}