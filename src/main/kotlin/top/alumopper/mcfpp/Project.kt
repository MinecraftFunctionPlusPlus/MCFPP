package top.alumopper.mcfpp

import com.alibaba.fastjson2.*
import org.apache.logging.log4j.*
import top.alumopper.mcfpp.io.McfppFileReader
import top.alumopper.mcfpp.lib.*
import top.alumopper.mcfpp.lib.Function
import java.io.*

/**
 * 一个工程。工程文件包含了这个mcfpp工程编译需要的所有信息。编译器将会以这个文件为入口开始编译。
 * 同时，这个工程文件的名字也是此文件编译生成的数据包的命名空间。
 */
object Project {
    var logger: Logger = LogManager.getLogger("mcfpp")
//TODO config

    /**
     * 工程的根目录
     */
    lateinit var root: File
    /**
     * 工程包含的所有文件。以绝对路径保存
     */
    var files: ArrayList<String> = ArrayList()

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
     * 编译时，当前编译的文件
     */
    lateinit var currFile: File

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
    var errorCount = 0

    /**
     * 工程中的总警告数量
     */
    var warningCount = 0

    /**
     * 全局缓存
     */
    var global: Global = Global()

    /**
     * 初始化
     */
    fun init() {
        //全局缓存初始化
        global.init()
        //初始化mcfpp的tick和load函数
        val mcfppTick = Function("tick","mcfpp")
        val mcfppLoad = Function("load","mcfpp")
        global.cache.functions.add(mcfppLoad)
        global.cache.functions.add(mcfppTick)
    }

    /**
     * 读取工程
     * @param path 工程的json文件的路径
     */
    fun readProject(path: String) {
        //工程信息读取
        try {
            logger.debug("Reading project from file \"$path\"")
            val reader = BufferedReader(FileReader(path))
            val qwq = File(path)
            root = qwq.parentFile
            name = qwq.name.substring(0, qwq.name.lastIndexOf('.'))
            val json: StringBuilder = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                json.append(line)
            }
            val jsonObject: JSONObject = JSONObject.parse(json.toString()) as JSONObject
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
                    File(root.absolutePath + s)
                }
                logger.info("Finding file in \"" + r.absolutePath + "\"")
                getFiles(r, files)
            }
            version = jsonObject.getString("version")
            if (version == null) {
                version = "1.20"
            }
            description = jsonObject.getString("description")
            if (description == null) {
                description = "A datapack compiled by MCFPP"
            }
            defaultNamespace = if(jsonObject.getString("namespace") != null){
                jsonObject.getString("namespace")
            }else{
                "default"
            }
            val includesJson: JSONArray = jsonObject.getJSONArray("includes")?: JSONArray()
            for (i in 0 until includesJson.size) {
                includes.add(includesJson.getString(i))
            }
        } catch (e: Exception) {
            logger.error("Error while reading project from file \"$path\"")
            errorCount++
            e.printStackTrace()
        }
    }

    /**
     * 解析工程
     */
    fun analyse() {
        logger.debug("Analysing project...")
        //解析文件
        for (file in files) {
            try {
                McfppFileReader(file).analyse()
            } catch (e: IOException) {
                logger.error("Error while analysing file \"$file\"")
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
            logger.debug("Compiling mcfpp code in \"$file\"")
            try {
                McfppFileReader(file).compile()
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
    fun optimization() {
        logger.debug("Optimizing...")
        //寻找入口函数
        var hasEntrance = false
        for (f in global.cache.functions) {
            if (f.parent.size == 0 && f !is Native) {
                //找到了入口函数
                hasEntrance = true
                f.commands.add(0, "data modify storage mcfpp:system $defaultNamespace.stack_frame prepend value {}")
                f.commands.add("data remove storage mcfpp:system $defaultNamespace.stack_frame[0]")
                logger.debug("Find entrance function:${f.tags} ${f.name}")
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
        try {
            val writer = BufferedWriter(FileWriter(root.absolutePath + "/.mclib"))
            writer.write("[function]\n")
            for (f in global.cache.functions) {
                writer.write(f.namespaceID + "\n")
            }
            writer.write("[class]\n")
            for (c in global.cache.classes.values) {
                writer.write(c.namespace.toString() + ":" + c.identifier + "\n")
            }
            writer.write("[end]\n")
            writer.flush()
            writer.close()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    /**
     * 获取文件列表
     * @param file 根目录
     * @param files 储存文件用的数组
     */
    private fun getFiles(file: File, files: ArrayList<String>?) {
        if (!file.exists()) {
            logger.warn("Path \"" + file.absolutePath + "\" doesn't exist. Ignoring.")
            warningCount++
            return
        }
        val fs: Array<File> = file.listFiles() ?: return
        for (f in fs) {
            if (f.isDirectory) //若是目录，则递归打印该目录下的文件
                getFiles(f, files)
            if (f.isFile && f.name.substring(f.name.lastIndexOf(".") + 1) == "mcfpp") {
                if (!files!!.contains(f.absolutePath)) {
                    files.add(f.absolutePath)
                }
            }
        }
    }

    /**
     * 获取数据包版本
     * @param version 版本字符串
     * @return 版本编号
     */
    fun getVersion(version: String): Int {
        return when (version) {
            "1.20" -> {
                13
            }

            "1.19.4" -> {
                12
            }

            "23w03a" -> {
                11
            }

            "1.19.3", "1.19.2", "1.19.1", "1.19" -> {
                10
            }

            "1.18.2" -> {
                9
            }

            "1.18.1", "1.18" -> {
                8
            }

            "1.17.1", "1.17" -> {
                7
            }

            "1.16.5", "1.16.4", "1.16.3", "1.16.2" -> {
                6
            }

            "1.16.1", " 1.16", "1.15.2", "1.15.1", "1.15" -> {
                5
            }

            "1.14.4", "1.14.3", "1.14.2", "1.14.1", "1.14", "1.13.2", "1.13.1", "1.13" -> {
                4
            }

            "17w43a" -> {
                3
            }

            else -> {
                logger.warn("Unknown version: \"$version\". Using 1.20 (13)")
                13
            }
        }
    }
}