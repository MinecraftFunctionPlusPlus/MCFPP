package top.mcfpp.test

import top.mcfpp.util.StringHelper
import kotlin.test.Test

class StringTest {
    @Test
    fun qwq() {
        println("a:b".split(":"))
        println("b".split(":"))
    }

    @Test
    fun test1() {
        println(StringHelper.toLegalIdentifier("你好qwq000AAA"))
    }

}