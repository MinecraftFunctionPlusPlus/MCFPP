package top.mcfpp

import top.mcfpp.command.CommentLevel
import java.nio.file.Path
import kotlin.io.path.Path

//TODO 标准库引用逻辑优化
open class ProjectConfig(
    /**
     * 工程对应的mc版本
     */
    var version: String = "1.21.8",

    /**
     * 工程的默认命名空间
     */
    var rootNamespace: String = "default",

    /**
     * 数据包输出的文件夹
     */
    var targetPath : Path? = null,

    /**
     * 标准库列表
     */
    val stdLib: List<String> = listOf("%std"),

    /**
     * 默认命名空间注册
     */
    val stdNamespace: List<String> = listOf("mcfpp.lang","mcfpp.sys","mcfpp"),

    /**
     * 注释输出等级
     */
    var commentLevel : CommentLevel = CommentLevel.DEBUG,

    /**
     * 工程的根目录
     */
    var root: Path = Path("."),

    /**
     * 工程的名字
     */
    var name: String = "new_mcfpp_project",

    /**
     * 数据包的描述。原始Json文本 TODO
     */
    var description: String = "A new datapack",

    /**
     * 工程包含的所有引用
     */
    var includes: ArrayList<String> = ArrayList(),

    /**
     * 所有Jars文件的路径
     */
    var jars: ArrayList<String> = ArrayList(),

    /**
     * mcfpp源代码根目录
     */
    var sourcePath: Path? = null,

    /**
     * 不生成数据包
     */
    var noDatapack: Boolean = false,

    /**
     * 工程复制导入的库
     */
    var copyImport: Boolean = true,
)