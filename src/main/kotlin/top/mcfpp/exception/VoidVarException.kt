package top.mcfpp.exception

/**
 * 对空变量进行操作时抛出
 *
 * @constructor Create empty Void var exception
 */
class VoidVarException:RuntimeException{
    constructor():super()
    constructor(msg: String):super(msg)
}