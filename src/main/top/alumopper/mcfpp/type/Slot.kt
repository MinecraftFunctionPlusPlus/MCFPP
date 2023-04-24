package top.alumopper.mcfpp.type

/**
 * 表示一个物品栏槽位
 * 物品栏的槽位通常都应该是xxx.xxx或者xxx.数值这样的格式。对此，非数字的部分和数字部分被分开处理
 */
class Slot
/**
 * 创建一个Slot
 *
 * @param slothead 字符串部分
 * @param slot 数字部分。若为负数则表示没有，即此Slot为纯字母
 */(private val slothead: String, private val slot: Int) {
    constructor(slothead: String) : this(slothead, -1)

    @Override
    override fun toString(): String {
        return slothead + if (slot < 0) "" else ".$slot"
    }

    object Armor {
        var chest = Slot("armor.chest")
        var feet = Slot("armor.feet")
        var head = Slot("armor.head")
        var legs = Slot("armor.legs")
    }

    object Weapon {
        var mainhand = Slot("weapon.mainhand")
        var offhand = Slot("weapon.offhand")
    }

    class Dw5da6fDAWFDaw654 {
        operator fun get(i: Int): Slot {
            var i = i
            if (i < 0) {
                i = 0
                Project.logger.warn("索引应当大于1，但是传入了:$i")
            }
            return Slot("container", i)
        }
    }

    class DWD5d4w5d4awdawd7 {
        operator fun get(i: Int): Slot {
            var i = i
            if (i < 0) {
                i = 0
                Project.logger.warn("索引应当大于1，但是传入了:$i")
            }
            return Slot("enderchest", i)
        }
    }

    class EDJWDwd5a4d8wa4d8 {
        operator fun get(i: Int): Slot {
            var i = i
            if (i < 0) {
                i = 0
                Project.logger.warn("索引应当大于1，但是传入了:$i")
            }
            return Slot("hotbar", i)
        }
    }

    object Horse {
        var saddle = Slot("horse.saddle")
        var chest = Slot("horse.chest")
        var armor = Slot("horse.armor")
    }

    class GRSGsef548sf4fdsd {
        operator fun get(i: Int): Slot {
            var i = i
            if (i < 0) {
                i = 0
                Project.logger.warn("索引应当大于1，但是传入了:$i")
            }
            return Slot("horse", i)
        }
    }

    class OEASdqd4wa84d88d4 {
        operator fun get(i: Int): Slot {
            var i = i
            if (i < 0) {
                i = 0
                Project.logger.warn("索引应当大于1，但是传入了:$i")
            }
            return Slot("villager", i)
        }
    }

    companion object {
        var weapon = Slot("weapon")
        var container = Dw5da6fDAWFDaw654()
        var enderchest = DWD5d4w5d4awdawd7()
        var hotbar = EDJWDwd5a4d8wa4d8()
        var horse = GRSGsef548sf4fdsd()
        var villager = OEASdqd4wa84d88d4()
    }
}