package top.mcfpp.io

import java.io.InputStream

abstract class McfppReader {
    /**
     * 用于读取用的流
     */
    var input: InputStream? = null

    /**
     * 行数定位
     */
    var line = 0

    /**
     * 对应的文件绝对路径
     */
    var path: String? = null

    /**
     * 文件相对根目录的路径
     */
    var rpath: String? = null
}