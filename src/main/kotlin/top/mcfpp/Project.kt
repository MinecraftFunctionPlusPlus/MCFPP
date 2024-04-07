package top.mcfpp

import com.alibaba.fastjson2.*
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.apache.logging.log4j.*
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.io.LibReader
import top.mcfpp.io.LibWriter
import top.mcfpp.io.McfppFile
import top.mcfpp.lang.MCFloat
import top.mcfpp.lang.UnresolvedVar
import top.mcfpp.lang.type.MCFPPBaseType
import top.mcfpp.lib.*
import top.mcfpp.lib.function.Function
import top.mcfpp.lib.field.GlobalField
import top.mcfpp.lib.field.NamespaceField
import top.mcfpp.util.LogProcessor
import java.io.*
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

/**
 * 一个工程。工程文件包含了这个mcfpp工程编译需要的所有信息。编译器将会以这个文件为入口开始编译。
 * 同时，这个工程文件的名字也是此文件编译生成的数据包的命名空间。
 */
object Project {
    private var logger: Logger = LogManager.getLogger("mcfpp")
//TODO config

    var ctx: ParserRuleContext? = null

    /**
     * 工程的根目录
     */
    lateinit var root: Path
    /**
     * 工程包含的所有文件
     */
    var files: ArrayList<McfppFile> = ArrayList()

    /**
     * 工程对应的mc版本
     */
    var version: String? = null

    /**
     * 工程的名字
     */
    lateinit var name: String

    /**
     * 数据包的描述。原始Json文本 TODO
     */
    var description: String? = null

    /**
     * 工程包含的所有引用
     */
    var includes: ArrayList<String> = ArrayList()

    /**
     * 当前解析文件的语法树
     */
    var trees:MutableMap<McfppFile,ParseTree> = mutableMapOf()

    /**
     * 工程的名字
     */
    var defaultNamespace: String = "default"

    /**
     * 当前的命名空间
     */
    var currNamespace = defaultNamespace

    /**
     * 工程中的总错误数量
     */
    private var errorCount = 0

    /**
     * 工程中的总警告数量
     */
    private var warningCount = 0

    var targetPath : String = "out/"

    val stdLib = listOf("mcfpp/sys/.mclib","mcfpp/math/.mclib","mcfpp/dynamic/.mclib")

    /**
     * 初始化
     */
    fun init() {
        //全局缓存初始化
        GlobalField.init()
        //初始化mcfpp的tick和load函数
        //添加命名空间
        GlobalField.localNamespaces["mcfpp"] = NamespaceField()
        val mcfppTick = Function("tick","mcfpp", MCFPPBaseType.Void)
        val mcfppLoad = Function("load","mcfpp", MCFPPBaseType.Void)
        GlobalField.localNamespaces["mcfpp"]!!.addFunction(mcfppLoad,true)
        GlobalField.localNamespaces["mcfpp"]!!.addFunction(mcfppTick,true)
        GlobalField.functionTags["minecraft:tick"]!!.functions.add(mcfppTick)
        GlobalField.functionTags["minecraft:load"]!!.functions.add(mcfppLoad)
    }

    /**
     * 读取工程
     * @param path 工程的json文件的路径
     */
    fun readProject(path: String) {
        //工程信息读取
        try {
            //读取json
            logger.debug("Reading project from file \"$path\"")
            val reader = FileReader(path)
            val qwq = File(path)
            root = Path.of(path).parent
            name = qwq.name.substring(0, qwq.name.lastIndexOf('.'))
            val json = reader.readText()
            //解析json
            val jsonObject: JSONObject = JSONObject.parse(json) as JSONObject
            //代码文件
            files = ArrayList()
            val filesJson: JSONArray = jsonObject.getJSONArray("files")
            for (o in filesJson.toArray()) {
                var s = o as String
                if (s.endsWith("*")) {
                    //通配符
                    s = s.substring(0, s.length - 1)
                }
                val r: File = if (s.length > 2 && s[1] == ':') {
                    //绝对路径
                    File(s)
                } else {
                    //相对路径
                    File(root.absolutePathString() + s)
                }
                logger.info("Finding file in \"" + r.absolutePath + "\"")
                files = getFiles(r)
            }
            //版本
            version = jsonObject.getString("version")
            if (version == null) {
                version = "1.20"
            }
            //描述
            description = jsonObject.getString("description")
            if (description == null) {
                description = "A datapack compiled by MCFPP"
            }
            //默认命名空间
            defaultNamespace = if(jsonObject.getString("namespace") != null){
                jsonObject.getString("namespace")
            }else{
                "default"
            }
            //调用库
            val includesJson: JSONArray = jsonObject.getJSONArray("includes")?: JSONArray()
            for (i in 0 until includesJson.size) {
                includes.add(includesJson.getString(i))
            }
            //输出目录
            targetPath = jsonObject.getString("targetPath")?: "out/"
        } catch (e: Exception) {
            logger.error("Error while reading project from file \"$path\"")
            errorCount++
            e.printStackTrace()
        }
    }

    /**
     * 读取库文件，并将库写入缓存
     */
    fun readLib(){
        //默认的
        if(!CompileSettings.ignoreStdLib){
            includes.addAll(stdLib)
        }
        //写入缓存
        for (include in includes) {
            val filePath = if(!include.endsWith("/.mclib")) "$include/.mclib" else include
            val file = File(filePath)
            if(file.exists()){
                LibReader.read(filePath)
            }else{
                LogProcessor.error("Cannot find lib file at: ${file.absolutePath}")
            }
        }
        //库读取完了，现在实例化所有类中的成员字段吧
        for(namespace in GlobalField.libNamespaces.values){
            namespace.forEachClass { c ->
                run {
                    for (v in c.field.allVars){
                        if(v is UnresolvedVar){
                            c.field.putVar(c.identifier, v.resolve(c), true)
                        }
                    }
                    for (v in c.staticField.allVars){
                        if(v is UnresolvedVar){
                            c.staticField.putVar(c.identifier, v.resolve(c), true)
                        }
                    }
                }
            }
        }
    }

    /**
     * 编制类型索引
     */
    fun indexType(){
        logger.debug("Generate Type Index...")
        //解析文件
        for (file in files) {
            //添加默认库的域
            if(!CompileSettings.ignoreStdLib){
                GlobalField.importedLibNamespaces["mcfpp.sys"] = GlobalField.libNamespaces["mcfpp.sys"]
            }
            try {
                file.indexType()
            } catch (e: IOException) {
                logger.error("Error while generate type index in file \"$file\"")
                errorCount++
                e.printStackTrace()
            }
            GlobalField.importedLibNamespaces.clear()
        }

    }

    /**
     * 编制函数索引
     */
    fun indexFunction() {
        logger.debug("Generate Function Index...")
        //解析文件
        for (file in files) {
            try {
                file.indexFunction()
            } catch (e: IOException) {
                logger.error("Error while generate function index in file \"$file\"")
                errorCount++
                e.printStackTrace()
            }
        }
    }

    /**
     * 编译工程
     */
    fun compile() {
        //工程文件编译
        //解析文件
        for (file in files) {
            LogProcessor.debug("Compiling mcfpp code in \"$file\"")
            //添加默认库域
            if(!CompileSettings.ignoreStdLib){
                GlobalField.importedLibNamespaces["mcfpp.sys"] = GlobalField.libNamespaces["mcfpp.sys"]
            }
            try {
                file.compile()
            } catch (e: IOException) {
                logger.error("Error while compiling file \"$file\"")
                errorCount++
                e.printStackTrace()
            }
        }
    }



    /**
     * 整理并优化工程
     */
    @InsertCommand
    fun optimization() {
        logger.debug("Optimizing...")
        logger.debug("Adding scoreboards declare in mcfpp:load function")
        //region load init command
        //向load函数中添加记分板初始化命令
        Function.currFunction = GlobalField.localNamespaces["mcfpp"]!!.getFunction("load", ArrayList(), ArrayList())!!
        for (scoreboard in GlobalField.scoreboards.values){
            Function.addCommand("scoreboard objectives add ${scoreboard.name} ${scoreboard.criterion}")
        }
        //向load函数中添加库初始化命令
        Function.addCommand("execute unless score math mcfpp_init matches 1 run function math:_init")
        //向load中添加类初始化命令
        for (n in GlobalField.localNamespaces.values){
            n.forEachClass { c->
                run {
                    c.classPreStaticInit.invoke(ArrayList(), callerClassP = null)
                }
            }
        }
        //浮点数临时marker实体
        Function.addCommand("summon marker 0 0 0 {Tags:[\"mcfpp:float_marker\"],UUID:${MCFloat.tempFloatEntityUUIDNBT}}")
        //endregion
        //浮点数的
        //寻找入口函数
        var hasEntrance = false
        for(field in GlobalField.localNamespaces.values){
            field.forEachFunction { f->
                run {
                    if (f.parent.size == 0 && f !is Native) {
                        //找到了入口函数
                        hasEntrance = true
                        f.commands.add(0, "data modify storage mcfpp:system $defaultNamespace.stack_frame prepend value {}")
                        f.commands.add("data remove storage mcfpp:system $defaultNamespace.stack_frame[0]")
                        logger.debug("Find entrance function: {} {}", f.tags, f.identifier)
                    }
                }
            }
        }
        if (!hasEntrance) {
            logger.warn("No valid entrance function in Project $defaultNamespace")
            warningCount++
        }
        logger.info("Complete compiling project " + root.name + " with [$errorCount] error and [$warningCount] warning")
    }

    /**
     * 生成库索引
     * 在和工程信息json文件的同一个目录下生成一个.mclib文件
     */
    fun genIndex() {
        LibWriter.write(root.absolutePathString())
    }

    /**
     * 获取文件列表
     * @param file 根目录
     * @return 这个根目录下包含的所有文件
     */
    private fun getFiles(file: File) : ArrayList<McfppFile> {
        val files = ArrayList<McfppFile>()
        if (!file.exists()) {
            logger.warn("Path \"" + file.absolutePath + "\" doesn't exist. Ignoring.")
            warningCount++
            return ArrayList()
        }
        val fs: Array<File> = file.listFiles() ?: return ArrayList()
        for (f in fs) {
            if (f.isDirectory) //若是目录，则递归打印该目录下的文件
                files += getFiles(f)
            if (f.isFile && f.name.substring(f.name.lastIndexOf(".") + 1) == "mcfpp") {
                if (!files.contains(McfppFile(f))) {
                    files.add(McfppFile(f))
                }
            }
        }
        return files
    }
}