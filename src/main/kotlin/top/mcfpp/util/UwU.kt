package top.mcfpp.util

import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

/**
 * 写tips用的
 *
 */
object UwU {
    var tips: ArrayList<String> = ArrayList()

    init {
        tips.add("Hello Minecraft!")
        tips.add("Also try JustMCF!")
        tips.add("Also try FrostLeaf ToolBox!")
        tips.add("\$time")
        tips.add("今天你也很努力呢喵！")
        tips.add("Mojang什么时候出动态命令捏……（呆")
        tips.add("喂，刚刚咱在编译的时候，你有在看咱吧")
        tips.add("咕咕咕咕咕咕咕咕咕")
        tips.add("嘤嘤嘤嘤嘤嘤嘤嘤")
        tips.add("疯狂星期四，vivo50")
        tips.add("今天的编译器也很可爱呢w")
        tips.add("当编程遇到bug的时候，不妨出去走一走")
        tips.add("让我猜猜现在是什么时候呢~是晚上咩？")
        tips.add("让我猜猜现在是什么时候呢~是早上嘤？")
        tips.add("让我猜猜现在是什么时候呢~是下午喵？")
        tips.add("让我猜猜现在是什么时候呢~是中午嘛？")
        tips.add("来自Java！")
        tips.add("来自Kotlin！")
        tips.add("不好看，在IDE睡了三小时")
        tips.add("不好看。睡了IDE三小时")
        tips.add("外面的天空怎么样呢~")
        tips.add("你有没有忘记什么要做的事情呀（盯")
        tips.add("qwq")
        tips.add("pwp")
        tips.add("就决定是你了！使用NullPointerException")
        tips.add("此代码妙不可言，唯有你和上帝知道")
        tips.add("注意休息喵（歪头")
        tips.add("给我玩Minecraft!.jpg")
        tips.add("嗯~是第几次看到这条tips了呢？")
        tips.add("Never gonna give you up~")
        tips.add("mcfpp中的函数没有返回值哦")
        tips.add("支持1.19.4+")
        tips.add("别写错别字哦（盯")
        tips.add("mcfpp会在函数中同时生成一些注释，让抽象的命令变得不那么抽象（或许（")
        tips.add("mcfpp至少应当在1.18的版本以上运行，因为1.18解除了记分板长度限制")
        tips.add("少用nbt可以有效地提升数据包的性能")
        tips.add("mcfpp会在编译的时候自动计算一些确定的值")
        tips.add("MNI调用的java/kotlin方法只能是静态的哦")
        tips.add("最新支持到1.20.1")
        tips.add("库的完善需要社区的共同参与~")
    }

    val tip: String
        get() {
            var s: String = tips[(Math.random() * tips.size).toInt()]
            if (s == "\$time") {
                val date = Date()
                val hour: Int = Integer.parseInt(SimpleDateFormat("HH").format(date))
                s = if (hour < 6) {
                    "已经到" + SimpleDateFormat("HH:mm").format(date) + "了欸，好晚了，还是要早点睡觉哦qwq"
                } else {
                    "滴，编译器喵为您报时~现在是" + SimpleDateFormat("HH:mm").format(date)
                }
            } else if (s == "疯狂星期四，vivo50") {
                if (Calendar.DAY_OF_WEEK !== Calendar.THURSDAY) {
                    s += "。欸……什么，今天不是星期四吗（失落"
                }
            }
            return s
        }
}