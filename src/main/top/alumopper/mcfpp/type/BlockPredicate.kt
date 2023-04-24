package top.alumopper.mcfpp.type

import top.alumopper.mcfpp.exception.IllegalFormatException
import java.util.regex.Pattern

/**
 * 代表世界中某类方块的判据。
 * 检查方块状态属性和（方块实体）NBTTag数据。允许用方块标签筛选方块类型。
 *
 *
 * 一些例子:
 * <list type="bullet">
 * <item>stone</item>
 * <item>minecraft:stone</item>
 * <item>stone[foo=bar]</item>
 * <item>#stone</item>
 * <item>#stone[foo=bar]{baz:nbt}</item>
</list> *
 *
 * [BlockState]
 */
class BlockPredicate(pre: String, nbt: NBTTag) {
    /**
     * 能直接被Minecraft解析的方块判据。
     */
    var pre: String

    //todo
    var nbt: NBTTag

    /**
     * 创建一个方块判据。
     *
     * @param pre
     * @exception IllegalFormatException
     */
    init {
        if (!Pattern.matches(
                "^#?([a-z0-9_]+|([a-z0-9_]+:[a-z0-9_]+))+([\\[][a-z0-9_]+=[a-z0-9_]+[\\]])*$",
                pre
            )
        ) {
            throw IllegalFormatException("无法解析字符串" + pre + "为方块谓词")
        }
        this.pre = pre
        this.nbt = nbt
    }

    @Override
    override fun toString(): String {
        return "$pre{$nbt}"
    }
}