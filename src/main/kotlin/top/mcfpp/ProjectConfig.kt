package top.mcfpp

import top.mcfpp.command.CommentLevel
import top.mcfpp.util.MCUUID
import top.mcfpp.mni.expand.ExpansionBudget
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/** Limits and diagnostics for MNI code expansion. Each compilation creates its own budget. */
data class MacroExpansionConfig(
    val maxDepth: Int = DEFAULT_MAX_DEPTH,
    val maxNodes: Int = DEFAULT_MAX_NODES,
    val dump: Boolean = false,
) {
    init {
        require(maxDepth > 0) { "Macro expansion depth must be positive" }
        require(maxNodes > 0) { "Macro expansion node limit must be positive" }
    }

    internal fun newBudget(): ExpansionBudget = ExpansionBudget(maxDepth, maxNodes)

    companion object {
        const val DEFAULT_MAX_DEPTH = 64
        const val DEFAULT_MAX_NODES = 100_000
    }
}

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
    var commentLevel : CommentLevel = CommentLevel.INFO,

    /**
     * 工程的根目录
     */
    var root: Path = Paths.get(""),

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

    /**
     * 临时世界实体的UUID
     */
    var tempItemEntityUUID: MCUUID = MCUUID(UUID.fromString("810d6071-f121-4972-80d6-60cc19b40cf8")),

    /**
     * 是否生成bin.mclib
     */
    var noMcLib: Boolean = false,

    /**
    * 数据文件夹路径
    */
    var dataPath: Path? = null,

    /** MNI macro limits and expansion dump setting. */
    var macroExpansion: MacroExpansionConfig = MacroExpansionConfig(),
)

/** Create a fresh budget for one compilation unit; counts are never shared between compilations. */
internal fun ProjectConfig.newExpansionBudget(): ExpansionBudget = macroExpansion.newBudget()
