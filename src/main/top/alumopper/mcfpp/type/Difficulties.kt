package top.alumopper.mcfpp.type

import top.alumopper.mcfpp.lang.MCInt

enum class Difficulties {
    peaceful, easy, normal, hard;

    val value: Int
        get() = this.ordinal

    companion object {
        val SIZE: Int = java.lang.Integer.SIZE
        fun forValue(value: Int): Difficulties {
            return values()[value]
        }
    }
}