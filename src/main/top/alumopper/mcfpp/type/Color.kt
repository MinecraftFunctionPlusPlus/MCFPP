package top.alumopper.mcfpp.type

import top.alumopper.mcfpp.exception.IllegalFormatException
import java.util.regex.Pattern

/**
 * 颜色类，储存了颜色有关的信息
 */
class Color {
    constructor()

    /**
     * 聊天栏颜色枚举
     */
    enum class Colors {
        black, dark_blue, dark_green, dark_aqua, dark_red, dark_purple, gold, gray, dark_gray, blue, green, aqua, red, light_purple, yellow, white;

        val value: Int
            get() = this.ordinal

        companion object {
            val SIZE: Int = java.lang.Integer.SIZE
            fun forValue(value: Int): Colors {
                return values()[value]
            }
        }
    }

    /**
     * hex颜色码
     */
    var hex: Vector3<String>? = null
    var r = 0
    var g = 0
    var b = 0
    var alpha = 255

    /**
     * 根据RGB数值创建一个颜色对象
     */
    constructor(red: Int, green: Int, blue: Int) {
        r = red
        g = green
        b = blue
        hex = RGBtoHEX(red, green, blue)
    }

    /**
     * 根据RGBA数值创建一个颜色对象
     */
    constructor(red: Int, green: Int, blue: Int, alpha: Int) {
        r = red
        g = green
        b = blue
        this.alpha = alpha
        hex = RGBtoHEX(red, green, blue)
    }

    /**
     * 根据一个hex码字符串创建一个颜色对象
     * @exception IllegalFormatException 传入的hex码格式不正确
     */
    constructor(hex: String) {
        this.hex = Vector3("","","")
        if (!Pattern.matches("^#[a-zA-Z0-9]{6}$", hex)) {
            throw IllegalFormatException("无效的HEX颜色码:$hex")
        }
        this.hex!![0] = hex.substring(1, 3)
        this.hex!![1] = hex.substring(3, 5)
        this.hex!![2] = hex.substring(5, 7)
        val qwq: Vector3<Int> = HEXtoRGB(this.hex)
        r = qwq[0]!!
        g = qwq[1]!!
        b = qwq[2]!!
    }

    @Override
    override fun toString(): String {
        return "#" + hex!![0] + hex!![1] + hex!![2]
    }

    fun ToParticleRGBString(): String {
        return (r / 255.0).toString() + " " + g / 255.0 + " " + b / 255.0
    }

    companion object {
        var black = Color(0, 0, 0)
        var dark_blue = Color(0, 0, 170)
        var dark_green = Color(0, 170, 0)
        var dark_aqua = Color(0, 170, 170)
        var dark_red = Color(170, 0, 0)
        var dark_purple = Color(170, 0, 170)
        var gold = Color(255, 170, 0)
        var gray = Color(42, 42, 42)
        var dark_gray = Color(85, 85, 85)
        var blue = Color(85, 85, 255)
        var green = Color(85, 255, 85)
        var aqua = Color(85, 255, 255)
        var red = Color(255, 85, 85)
        var light_purple = Color(255, 85, 255)
        var yellow = Color(255, 255, 85)
        var white = Color(255, 255, 255)

        /**
         * 将rgb转换为hex
         *
         * @return
         */
        private fun RGBtoHEX(R: Int, G: Int, B: Int): Vector3<String> {
            return Vector3(String.format("%1$.2x", R), String.format("%1$.2x", G), String.format("%1$.2x", B))
        }

        /**
         * 将hex转换为rgb
         *
         * @return
         */
        private fun HEXtoRGB(hex: Vector3<String>?): Vector3<Int> {
            return Vector3<Int>(
                Integer.parseInt(hex!![0], 16), Integer.parseInt(hex[1], 16), Integer.parseInt(
                    hex[2], 16
                )
            )
        }
    }
}