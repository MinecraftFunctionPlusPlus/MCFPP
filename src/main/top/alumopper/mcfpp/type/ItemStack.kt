package top.alumopper.mcfpp.type

import java.util.regex.Pattern

/**
 * 一个物品堆，包含了一个物品的id，nbt标签以及这个物品的数量
 */
class ItemStack {
    /**
     * 物品堆
     */
    var item_stack: String? = null

    /**
     * 物品id
     */
    var id: String? = null

    /**
     * 物品nbt标签
     */
    var nbt: NBTTag? = null

    /**
     * 物品的数量
     */
    var count: Int

    /**
     * 通过一个物品堆创建一个物品对象
     * @param item_stack
     */
    constructor(item_stack: String) : this(item_stack, 1)
    constructor(item_stack: String, count: Int) {
        if (item_stack.contains("{")) {
            val qwq: Array<String> = item_stack.split("[{]", -1)
            val a = qwq[0] //非nbt
            val b = qwq[1] //nbt部分
            if (!Pattern.matches("^([a-z0-9_]+|([a-z0-9_]+[:][a-z0-9_]+))+$", a)) {
                throw IllegalFormatException("无法解析字符串" + item_stack + "为item_stack")
            }
            id = a
            nbt = NBTTag.Companion.Prase(null) //尝试
        } else {
            if (!Pattern.matches("^([a-z0-9_]+|([a-z0-9_]+[:][a-z0-9_]+))+$", item_stack)) {
                throw IllegalFormatException("无法解析字符串" + item_stack + "为item_stack")
            }
            id = item_stack
        }
        this.item_stack = item_stack
        this.count = count
    }

    constructor(id: String, nbt: NBTTag?) : this(id, nbt, 1)

    /**
     * 根据物品id和nbt创建一个参数
     * @param id
     * @param nbt
     * @param count
     * @exception IllegalFormatException
     */
    constructor(id: String, nbt: NBTTag?, count: Int) {
        if (!Pattern.matches("^([a-z0-9_]+|([a-z0-9_]+:[a-z0-9_]+))+$", id)) {
            throw IllegalFormatException("无法解析字符串" + id + "为标签")
        }
        this.id = id
        this.nbt = nbt
        this.count = count
    }

    @Override
    override fun toString(): String {
        return item_stack.toString() + " " + count
    }
}