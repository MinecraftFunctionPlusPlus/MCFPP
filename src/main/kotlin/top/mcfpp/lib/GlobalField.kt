package top.mcfpp.lib

import top.mcfpp.Project
import top.mcfpp.lang.SbObject
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * 全局域。
 *
 * 一个文件中，能被访问的域分为两种，一种是同命名空间下的类和函数，一种是通过import语句导入的库文件和库函数。
 * 根据import的先后顺序，应当是后导入的库优先于先导入的库，而同命名空间下的库永远是最高优先级的。
 */
object GlobalField : FieldContainer, IField {

    /**
     * 全局函数和类
     */
    //lateinit var field: Field

    /**
     * 命名空间
     */
    val localNamespaces = Hashtable<String, NamespaceField>()

    /**
     * 来自库的命名空间
     */
    val libNamespaces = Hashtable<String, NamespaceField>()

    /**
     * 函数的标签
     */
    var functionTags: HashMap<String, FunctionTag> = HashMap()

    /**
     * 记分板
     */
    var scoreboards: ArrayList<SbObject> = ArrayList()

    fun init(): GlobalField {
        //field = Field(null, this)
        functionTags["minecraft:tick"] = FunctionTag.TICK
        functionTags["minecraft:load"] = FunctionTag.LOAD
        scoreboards.add(SbObject.MCS_boolean)
        scoreboards.add(SbObject.MCS_default)
        return this
    }

    /**
     * 从当前的全局域获取一个函数。若不存在，则返回null
     *
     * @param namespace
     * @param identifier
     * @param args
     */
    fun getFunction(namespace:String? = Project.currNamespace, identifier: String, args : List<String>): Function?{
        if(namespace == null){
            for (n in localNamespaces.values){
                val f = n.getFunction(identifier, args)
                if(f != null) return f
            }
            for (n in libNamespaces.values){
                val f = n.getFunction(identifier, args)
                if(f != null) return f
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
     * @param namespace
     * @param identifier
     * @return
     */
    fun getClass(namespace: String? = null, identifier: String): Class?{
        if(namespace == null){
            var cls:Class?
            //命名空间为空，从全局寻找
            for (nsp in localNamespaces.values){
                cls = nsp.getClass(identifier)
                if(cls != null) return cls
            }
            for (nsp in libNamespaces.values){
                cls = nsp.getClass(identifier)
                if(cls != null) return cls
            }
            return null
        }
        //按照指定的命名空间寻找
        var field = localNamespaces[namespace]
        if(field == null){
            field = libNamespaces[namespace]
        }
        return field?.getClass(identifier)
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
                    field.forEachFunction {f ->
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
                    println("\tfunctions:")
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
                    println("\tattributes:")
                    for (v in s.staticField.allVars) {
                        println(
                            "\t\t" + v.accessModifier.name
                                .lowercase(Locale.getDefault()) + " " + "static " + v.type + " " + v.identifier
                        )
                    }
                }
            }
        }
    }
}