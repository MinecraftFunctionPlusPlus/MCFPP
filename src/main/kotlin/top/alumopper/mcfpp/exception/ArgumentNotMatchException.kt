package top.alumopper.mcfpp.exception

/**
 * 当参数不正确时抛出此异常。例如参数类型错误，参数数量不匹配等。
 */
class ArgumentNotMatchException(s: String?) : RuntimeException(s)