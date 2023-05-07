package top.mcfpp.exception

/**
 * 变量重复声明
 */
class VariableDuplicationException : RuntimeException {
    constructor() : super()
    constructor(message: String?) : super(message)
}