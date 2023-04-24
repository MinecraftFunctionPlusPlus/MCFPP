package top.alumopper.mcfpp.type

import top.alumopper.mcfpp.lang.MCInt

/**
 * 游戏模式，作为命令中设置玩家游戏模式或者判断游戏模式的参数
 */
enum class Gamemodes {
    survival, creative, adventure, specture;

    val value: Int
        get() = this.ordinal

    companion object {
        val SIZE: Int = java.lang.Integer.SIZE
        fun forValue(value: Int): Gamemodes {
            return values()[value]
        }
    }
}