package top.alumopper.mcfpp.type

import top.alumopper.mcfpp.lang.MCInt

class Bossbar {
    enum class Color {
        Blue, Green, Pink, Purple, Red, White, Yellow;

        val value: Int
            get() = this.ordinal

        companion object {
            val SIZE: Int = java.lang.Integer.SIZE
            fun forValue(value: Int): Color {
                return values()[value]
            }
        }
    }

    enum class Style {
        notched_6, notched_10, notched_12, notched_20, progress;

        val value: Int
            get() = this.ordinal

        companion object {
            val SIZE: Int = java.lang.Integer.SIZE
            fun forValue(value: Int): Style {
                return values()[value]
            }
        }
    }
}