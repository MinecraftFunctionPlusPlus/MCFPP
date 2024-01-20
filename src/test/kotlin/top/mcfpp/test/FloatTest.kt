package top.mcfpp.test

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

fun convertToEightDigit(input: Float): Pair<Int, Int> {
    val exponent = floor(log10(input.toDouble())).toInt()
    val factor = 10.0.pow((8 - exponent - 1).toDouble()).toInt()
    return Pair((input * factor).toInt(), exponent+1)
}

fun printArray(array: Array<Int>) {
    for (element in array) {
        println(element)
    }
}
fun main(){
    val pair = convertToEightDigit(114514.01f)
    println(pair.first)
    println(pair.second)
}