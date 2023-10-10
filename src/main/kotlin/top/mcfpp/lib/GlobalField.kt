package top.mcfpp.lib

import org.jetbrains.annotations.Nullable
import top.mcfpp.Project
import top.mcfpp.lang.SbObject
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
     * 命名空间
     */
    val localNamespaces = Hashtable<String, NamespaceField>()

    /**
     * import引用后的，来自库的命名空间。
     */
    val importedLibNamespaces = Hashtable<String, NamespaceField>()

    /**
     * 库的命名空间域。这个域中的内容是在编译时就已经确定的，不会随着代码的变化而变化。
     */
    val libNamespaces = Hashtable<String, NamespaceField>()

    /**
     * 函数的标签
     */
    var functionTags: HashMap<String, FunctionTag> = HashMap()

    /**
     * 记分板
     */
    var scoreboards: HashMap<String ,SbObject> = HashMap()

    fun init(): GlobalField {
        functionTags["minecraft:tick"] = FunctionTag.TICK
        functionTags["minecraft:load"] = FunctionTag.LOAD
        scoreboards[SbObject.MCS_boolean.name] = SbObject.MCS_boolean
        scoreboards[SbObject.MCS_default.name] = SbObject.MCS_default
        scoreboards[SbObject.MCFPP_INIT.name] = SbObject.MCFPP_INIT
        return this
    }

    /**
     * 从当前的全局域获取一个函数。若不存在，则返回null。
     *
     * 如果没有提供命名空间，则只会从import导入的库和本地命名空间中搜索。否则则在指定的命名空间中搜索。
     *
     * @param namespace 可选。这个函数的命名空间。如果为null，则会从当前所有的命名空间中寻找此函数。
     * @param identifier 函数的标识符
     * @param args 函数的参数
     *
     * @return 获取的函数。如果有多个相同函数（一般出现在命名空间未填写的情况下），则返回首先找到的那一个
     */
    fun getFunction(@Nullable namespace:String?, identifier: String, args : List<String>): Function?{
        if(namespace == null){
            val f = localNamespaces[Project.currNamespace]!!.getFunction(identifier, args)
            if(f != null) return f
            for (n in importedLibNamespaces.values){
                val f1 = n.getFunction(identifier, args)
                if(f1 != null) return f1
            }
            return null
        }
        var field = localNamespaces[namespace]
        if(field == null){
            field = libNamespaces[namespace]
        }
        return field?.getFunction(identifier, args)
    }

    /**
     * 从当前的全局域中获取一个类。若不存在，则返回null
     *
     * 如果没有提供命名空间，则只会从import导入的库和本地命名空间中搜索。否则则在指定的命名空间中搜索。
     * @param namespace 可选。这个类的命名空间。如果为null，则会从当前所有的命名空间中寻找此类。
     * @param identifier 类的标识符
     * @return 获取的类。如果有多个相同标识符的类（一般出现在命名空间未填写的情况下），则返回首先找到的那一个
     */
    fun getClass(@Nullable namespace: String? = null, identifier: String): Class?{
        if(namespace == null){
            var cls:Class?
            //命名空间为空，从全局寻找
            cls = localNamespaces[Project.currNamespace]!!.getClass(identifier)
            if(cls != null) return cls
            for (nsp in importedLibNamespaces.values){
                cls = nsp.getClass(identifier)
                if(cls != null) return cls
            }
            return null
        }
        //按照指定的命名空间寻找
        var field = localNamespaces[namespace]
        if(field == null){
            field = importedLibNamespaces[namespace]
        }
        return field?.getClass(identifier)
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
            var itf:Interface?
            //命名空间为空，从全局寻找
            itf = localNamespaces[Project.currNamespace]!!.getInterface(identifier)
            if(itf != null) return itf
            for (nsp in importedLibNamespaces.values){
                itf = nsp.getInterface(identifier)
                if(itf != null) return itf
            }
            return null
        }
        //按照指定的命名空间寻找
        var field = localNamespaces[namespace]
        if(field == null){
            field = importedLibNamespaces[namespace]
        }
        return field?.getInterface(identifier)
    }



    /**
     * 从当前的全局域中获取一个结构体。若不存在，则返回null
     *
     * 如果没有提供命名空间，则只会从import导入的库和本地命名空间中搜索。否则则在指定的命名空间中搜索。
     * @param namespace 可选。这个结构体的命名空间。如果为null，则会从当前所有的命名空间中寻找此结构体。
     * @param identifier 结构体的标识符
     * @return 获取的结构体。如果有多个相同标识符的结构体（一般出现在命名空间未填写的情况下），则返回首先找到的那一个
     */
    fun getStruct(@Nullable namespace: String?, identifier: String): Struct? {
        if(namespace == null){
            var struct:Struct?
            //命名空间为空，从全局寻找
            struct = localNamespaces[Project.currNamespace]!!.getStruct(identifier)
            if(struct != null) return struct
            for (nsp in importedLibNamespaces.values){
                struct = nsp.getStruct(identifier)
                if(struct != null) return struct
            }
            return null
        }
        //按照指定的命名空间寻找
        var field = localNamespaces[namespace]
        if(field == null){
            field = importedLibNamespaces[namespace]
        }
        return field?.getStruct(identifier)
    }
    @get:Override
    override val prefix: String
        get() = Project.defaultNamespace + "_global_"

    /**
     * TODO:DEBUG
     * 打印所有的函数和类
     */
    fun printAll() {
        for(field in localNamespaces.values){
            field.forEachFunction { s ->
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
            field.forEachClass { s ->
                run{
                    if (s is NativeClass) {
                        println("native class " + s.namespace + ":" + s.identifier + " -> " + s.cls.toString())
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
            field.forEachStruct { s ->
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
            field.forEachInterface { i ->
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