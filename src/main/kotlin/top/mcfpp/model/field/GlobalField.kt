package top.mcfpp.model.field

import org.jetbrains.annotations.Nullable
import top.mcfpp.Project
import top.mcfpp.lang.SbObject
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.model.*
import top.mcfpp.model.Annotation
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.generic.GenericClass
import java.util.*
import kotlin.collections.HashMap

/**
 * 全局域。
 *
 * 一个文件中，能被访问的域分为两种，一种是同命名空间下的类和函数，一种是通过import语句导入的库文件和库函数。
 * 根据import的先后顺序，应当是后导入的库优先于先导入的库，而同命名空间下的库永远是最高优先级的。
 *
 * 全局域只含有命名空间域作为内容，而命名空间域则又分为本地域和库域。本地域即编译时，源代码的域，包含了源代码的内容，而库域则是引用的mcfpp库的域，包含了库的内容
 */
object GlobalField : FieldContainer, IField {

    /**
     * 当前项目内声明的命名空间
     */
    val localNamespaces = Hashtable<String, Namespace>()

    /**
     * 每个文件中，使用import语句引用库以后的库命名空间，只作用于当前编译的文件
     */
    val importedLibNamespaces = Hashtable<String, Namespace>()

    /**
     * 库的命名空间域。在分析项目前，使用readLib函数读取所有在项目配置文件中被声明引用的库，并将所有的命名空间存放进去
     */
    val libNamespaces = Hashtable<String, Namespace>()

    /**
     * 函数的标签
     */
    var functionTags: HashMap<String, FunctionTag> = HashMap()

    /**
     * 记分板
     */
    var scoreboards: HashMap<String ,SbObject> = HashMap()

    /**
     * 注解
     *
     */
    var annotations = HashMap<String, java.lang.Class<out Annotation>>()

    fun init(): GlobalField {

        functionTags["minecraft:tick"] = FunctionTag.TICK
        functionTags["minecraft:load"] = FunctionTag.LOAD

        scoreboards[SbObject.MCFPP_boolean.name] = SbObject.MCFPP_boolean
        scoreboards[SbObject.MCFPP_default.name] = SbObject.MCFPP_default
        scoreboards[SbObject.MCFPP_INIT.name] = SbObject.MCFPP_INIT
        scoreboards[SbObject.MCFPP_TEMP.name] = SbObject.MCFPP_TEMP

        localNamespaces["default"] = Namespace("default")

        return this
    }

    fun getFunction(@Nullable namespace:String?, identifier: String, readOnlyParams: List<String>, normalParams : List<String>): Function {
        val rpls = readOnlyParams.map { MCFPPType.parseFromTypeName(it) }
        val npls = normalParams.map { MCFPPType.parseFromTypeName(it) }
        return getFunctionInner(namespace,identifier,rpls, npls)
    }

    /**
     * 从当前的全局域获取一个函数。若不存在，则返回null。
     *
     * 如果没有提供命名空间，则只会从import导入的库和本地命名空间中搜索。否则则在指定的命名空间中搜索。
     *
     * @param namespace 可选。这个函数的命名空间。如果为null，则会从当前所有的命名空间中寻找此函数。
     * @param identifier 函数的标识符
     *
     * @return 获取的函数。如果有多个相同函数（一般出现在命名空间未填写的情况下），则返回首先找到的那一个
     */
    fun getFunctionInner(@Nullable namespace:String?, identifier: String, readOnlyParams: List<MCFPPType>, normalParams : List<MCFPPType>): Function {
        if(namespace == null){
            val f = localNamespaces[Project.currNamespace]!!.field.getFunction(identifier, readOnlyParams, normalParams)
            if(f != null) return f
            for (n in importedLibNamespaces.values){
                val f1 = n.field.getFunction(identifier, readOnlyParams, normalParams)
                if(f1 != null) return f1
            }
            return UnknownFunction(identifier)
        }
        var np = localNamespaces[namespace]
        if(np == null){
            np = libNamespaces[namespace]
        }
        return np?.field?.getFunction(identifier, readOnlyParams, normalParams)?: UnknownFunction(identifier)
    }

    /**
     * 从当前的全局域中获取一个泛型类。若不存在，则返回null
     *
     * 如果没有提供命名空间，则会从import导入的库和本地命名空间中搜索。否则则在指定的命名空间中搜索。
     * @param namespace 可选。这个类的命名空间。如果为null，则会从当前所有的命名空间中寻找此类。
     * @param identifier 类的标识符
     * @return 获取的类。如果有多个相同标识符的类（一般出现在命名空间未填写的情况下），则返回首先找到的那一个
     */
    fun getClass(@Nullable namespace: String? = null, identifier: String, readOnlyParams: List<MCFPPType>): Class?{
        if(namespace == null){
            var cls: Class?
            //命名空间为空，从全局寻找
            cls = localNamespaces[Project.currNamespace]!!.field.getClass(identifier, readOnlyParams)
            if(cls != null) return cls
            for (nsp in importedLibNamespaces.values){
                cls = nsp.field.getClass(identifier, readOnlyParams)
                if(cls != null) return cls
            }
            return null
        }
        //按照指定的命名空间寻找
        var np = localNamespaces[namespace]
        if(np == null){
            np = importedLibNamespaces[namespace]
        }
        return np?.field?.getClass(identifier, readOnlyParams)
    }

    /**
     * 从当前的全局域中获取一个类。若不存在，则返回null
     *
     * 如果没有提供命名空间，则会从import导入的库和本地命名空间中搜索。否则则在指定的命名空间中搜索。
     * @param namespace 可选。这个类的命名空间。如果为null，则会从当前所有的命名空间中寻找此类。
     * @param identifier 类的标识符
     * @return 获取的类。如果有多个相同标识符的类（一般出现在命名空间未填写的情况下），则返回首先找到的那一个
     */
    fun getClass(@Nullable namespace: String? = null, identifier: String): Class?{
        if(namespace == null){
            var cls: Class?
            //命名空间为空，从全局寻找
            cls = localNamespaces[Project.currNamespace]!!.field.getClass(identifier)
            if(cls != null) return cls
            for (nsp in importedLibNamespaces.values){
                cls = nsp.field.getClass(identifier)
                if(cls != null) return cls
            }
            return null
        }
        //按照指定的命名空间寻找
        var np = localNamespaces[namespace]
        if(np == null){
            np = importedLibNamespaces[namespace]
        }
        return np?.field?.getClass(identifier)
    }

    /**
     * 从当前的全局域中获取一个接口。若不存在，则返回null
     *
     * 如果没有提供命名空间，则只会从import导入的库和本地命名空间中搜索。否则则在指定的命名空间中搜索。
     * @param namespace 可选。这个接口的命名空间。如果为null，则会从当前所有的命名空间中寻找此类。
     * @param identifier 接口的标识符
     * @return 获取的接口。如果有多个相同标识符的接口（一般出现在命名空间未填写的情况下），则返回首先找到的那一个
     */
    fun getInterface(@Nullable namespace: String? = null, identifier: String): Interface?{
        if(namespace == null){
            var itf: Interface?
            //命名空间为空，从全局寻找
            itf = localNamespaces[Project.currNamespace]!!.field.getInterface(identifier)
            if(itf != null) return itf
            for (nsp in importedLibNamespaces.values){
                itf = nsp.field.getInterface(identifier)
                if(itf != null) return itf
            }
            return null
        }
        //按照指定的命名空间寻找
        var np = localNamespaces[namespace]
        if(np == null){
            np = importedLibNamespaces[namespace]
        }
        return np?.field?.getInterface(identifier)
    }



    /**
     * 从当前的全局域中获取一个结构体。若不存在，则返回null
     *
     * 如果没有提供命名空间，则只会从import导入的库和本地命名空间中搜索。否则则在指定的命名空间中搜索。
     * @param namespace 可选。这个结构体的命名空间。如果为null，则会从当前所有的命名空间中寻找此结构体。
     * @param identifier 结构体的标识符
     * @return 获取的结构体。如果有多个相同标识符的结构体（一般出现在命名空间未填写的情况下），则返回首先找到的那一个
     */
    fun getTemplate(@Nullable namespace: String?, identifier: String): Template? {
        if(namespace == null){
            var struct: Template?
            //命名空间为空，从全局寻找
            struct = localNamespaces[Project.currNamespace]!!.field.getTemplate(identifier)
            if(struct != null) return struct
            for (nsp in importedLibNamespaces.values){
                struct = nsp.field.getTemplate(identifier)
                if(struct != null) return struct
            }
            return null
        }
        //按照指定的命名空间寻找
        var np = localNamespaces[namespace]
        if(np == null){
            np = importedLibNamespaces[namespace]
        }
        return np?.field?.getTemplate(identifier)
    }
    @get:Override
    override val prefix: String
        get() = Project.config.defaultNamespace + "_global_"

    /**
     * TODO:DEBUG
     * 打印所有的函数和类
     */
    fun printAll() {
        for(namespace in localNamespaces.values){
            namespace.field.forEachFunction { s ->
                run {
                    if (s is NativeFunction) {
                        println("native " + s.namespaceID + " -> " + s.javaClassName +  "." + s.javaMethodName )
                    } else {
                        val n = StringBuilder("")
                        for(tag in s.tags){
                            n.append("@${tag.namespaceID}")
                        }
                        println("$n ${s.namespaceID}")
                        for (c in s.commands) {
                            println("\t" + c)
                        }
                    }
                }
            }
            namespace.field.forEachClass { s ->
                run{
//                    if (s is NativeClass) {
//                        println("native class " + s.namespace + ":" + s.identifier + " -> " + s.cls.toString())
//                        return@run
//                    }
                    if(s is GenericClass){
                        println("generic class " + s.namespaceID)
                        return@run
                    }
                    println("class " + s.identifier)
                    println("\tconstructors:")
                    for (c in s.constructors) {
                        if (c is NativeConstructor) {
                            println("\t\tnative " + c.namespaceID)
                        } else {
                            println("\t\t" + c.namespaceID)
                            for (d in c.commands) {
                                println("\t\t\t" + d)
                            }
                        }
                    }
                    println("\tfunctions:")
                    s.field.forEachFunction {f ->
                        run {
                            if (f is NativeFunction) {
                                println(
                                    "\t\t" + f.accessModifier.name
                                        .lowercase(Locale.getDefault()) + " native " + f.namespaceID
                                )
                            } else {
                                println(
                                    "\t\t" + f.accessModifier.name
                                        .lowercase(Locale.getDefault()) + " " + f.namespaceID
                                )
                                for (d in f.commands) {
                                    println("\t\t\t" + d)
                                }
                            }
                        }
                    }
                    println("\tattributes:")
                    for (v in s.field.allVars.toList()) {
                        println(
                            "\t\t" + v.accessModifier.name
                                .lowercase(Locale.getDefault()) + " " + v.type + " " + v.identifier
                        )
                    }
                    println("\tstatic functions:")
                    s.staticField.forEachFunction { f ->
                        run {
                            if (f is NativeFunction) {
                                println(
                                    "\t\t" + f.accessModifier.name
                                        .lowercase(Locale.getDefault()) + " native " + "static" + f.namespaceID
                                )
                            } else {
                                println(
                                    "\t\t" + f.accessModifier.name
                                        .lowercase(Locale.getDefault()) + " " + "static" + f.namespaceID
                                )
                                for (d in f.commands) {
                                    println("\t\t\t" + d)
                                }
                            }
                        }
                    }
                    println("\tstatic attributes:")
                    for (v in s.staticField.allVars) {
                        println(
                            "\t\t" + v.accessModifier.name
                                .lowercase(Locale.getDefault()) + " " + "static " + v.type + " " + v.identifier
                        )
                    }
                }
            }
            namespace.field.forEachTemplate { s ->
                run {
                    println("struct " + s.identifier)
                    println("\tconstructors:")
                    for (c in s.constructors) {
                        println("\t\t" + c.namespaceID)
                        for (d in c.commands) {
                            println("\t\t\t" + d)
                        }
                    }
                    println("\tfunctions:")
                    s.field.forEachFunction {f ->
                        run {
                            println(
                                "\t\t" + f.accessModifier.name
                                    .lowercase(Locale.getDefault()) + " " + f.namespaceID
                            )
                            for (d in f.commands) {
                                println("\t\t\t" + d)
                            }
                        }
                    }
                    println("\tattributes:")
                    for (v in s.field.allVars.toList()) {
                        println(
                            "\t\t" + v.accessModifier.name
                                .lowercase(Locale.getDefault()) + " " + v.type + " " + v.identifier
                        )
                    }
                    println("\tstatic functions:")
                    s.staticField.forEachFunction { f ->
                        run {
                            println(
                                "\t\t" + f.accessModifier.name
                                    .lowercase(Locale.getDefault()) + " " + "static" + f.namespaceID
                            )
                            for (d in f.commands) {
                                println("\t\t\t" + d)
                            }
                        }
                    }
                    println("\tstatic attributes:")
                    for (v in s.staticField.allVars) {
                        println(
                            "\t\t" + v.accessModifier.name
                                .lowercase(Locale.getDefault()) + " " + "static " + v.type + " " + v.identifier
                        )
                    }
                }
            }
            namespace.field.forEachInterface { i ->
                run {
                    println("interface " + i.identifier)
                    println("\tfunctions:")
                    i.field.forEachFunction {f ->
                        run {
                            println(
                                "\t\t" + f.accessModifier.name
                                    .lowercase(Locale.getDefault()) + " " + f.namespaceID
                            )
                            for (d in f.commands) {
                                println("\t\t\t" + d)
                            }
                        }
                    }
                }
            }
        }
    }
}