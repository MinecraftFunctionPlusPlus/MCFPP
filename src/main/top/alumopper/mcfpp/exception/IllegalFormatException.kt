package top.alumopper.mcfpp.exception

/**
 * 当出现非法的格式时抛出此异常
 */
class IllegalFormatException(msg: String?) : RuntimeException(msg)