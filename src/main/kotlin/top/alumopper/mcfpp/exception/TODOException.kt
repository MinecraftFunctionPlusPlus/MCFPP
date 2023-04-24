package top.alumopper.mcfpp.exception

/**
 * 当编译器意外进入未完成的区域，即被`//TODO`标记的区域时抛出。
 * 这样做的目的是更好的观察到底程序在哪里进入了错误的区域，而不是单纯地
 * 返回一个null，导致后续程序出现[NullPointerException]
 */
class TODOException(msg: String?) : RuntimeException(msg)