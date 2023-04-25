package top.alumopper.mcfpp.lib

import org.jetbrains.annotations.Nullable
import top.alumopper.mcfpp.Project
import top.alumopper.mcfpp.lang.*
import java.util.*

/**
 * 一个缓存。在编译过程中，编译器读取到的变量，函数等会以键值对的方式储存在其中。键为函数的id或者变量的
 * 标识符，而值则是这个函数或者变量的对象。
 * 缓存应该是一个链式的结构，主要分为如下几种情况：<br></br>
 * 类的静态变量 ---> 类的成员变量 --> 函数 ---> 匿名内部函数<br></br>
 * 类的静态变量 ---> 静态函数 ---> 匿名内部函数<br></br>
 * 函数 ---> 匿名内部函数<br></br>
 * 寻找变量的时候，应当从当前的作用域开始寻找。<br></br>
 *
 *
 * 变量和类都分别储存在一张哈希表中，键名即它在声明的时候的名字，而值则代表了它的对象。
 * 值得注意的是，变量声明时的名字虽然和最终编译出的名字有关，但不相同。
 *
 *
 *
 * 函数储存在一个列表中
 *
 */
class Cache {
    /**
     * 变量缓存
     */
    private val vars: HashMap<String, Var> = HashMap()

    /**
     * 函数缓存
     */
    var functions: ArrayList<Function> = ArrayList()

    /**
     * 类缓存
     */
    var classes: HashMap<String, Class> = HashMap()

    /**
     * 父级缓存。函数的父级缓存可能是类，也可能是全局。类的父级缓存总是全局，而全局则没有父级缓存
     */
    @Nullable
    var parent: Cache?

    /**
     * 这个缓存在哪一个容器中
     */
    @Nullable
    var container: CacheContainer? = null

    /**
     * 创建一个缓存，并指定它的父级
     * @param parent 父级缓存。若没有则设置为null
     * @param cacheContainer 此缓存所在的容器
     */
    constructor(parent: Cache?, cacheContainer: CacheContainer?) {
        this.parent = parent
        container = cacheContainer
    }

    /**
     * 复制一个缓存。
     * @param cache 原来的缓存
     */
    constructor(cache: Cache) {
        parent = cache.parent
        //变量复制
        for (key in cache.vars.keys) {
            val `var`: Var? = cache.vars[key]
            vars[key] = `var`!!.clone() as Var
        }
        functions.addAll(cache.functions)
    }

    /**
     * 根据所给的函数名和参数获取一个函数
     * @param key 函数名
     * @param argsTypes 参数类型
     * @param namespace 命名空间
     * @return 如果此缓存中存在这个函数，则返回这个函数的对象，否则返回null
     */
    @Nullable
    fun getFunction(namespace: String, key: String, argsTypes: List<String>): Function? {
        for (f in functions) {
            if (f.namespace == namespace && f.name == key && f.params.size == argsTypes.size) {
                if (f.params.size == 0) {
                    return f
                }
                var hasFoundFunc = true
                //参数比对
                for (i in argsTypes.indices) {
                    if (argsTypes[i] != f.params[i].type) {
                        hasFoundFunc = false
                        break
                    }
                }
                if (hasFoundFunc) {
                    return f
                }
            }
        }
        return null
    }
    //region Var
    /**
     * 向此缓存中添加一个新的变量键值对。如果已存在此对象，将不会进行覆盖。
     * @param key 变量的标识符
     * @param var 变量的对象
     * @return 如果缓存中已经存在此对象，则返回false，否则返回true。
     */
    fun putVar(key: String, `var`: Var): Boolean {
        return if (vars.containsKey(key)) {
            false
        } else {
            vars[key] = `var`
            true
        }
    }

    /**
     * 从缓存中取出一个变量。如果此缓存中没有，则从父缓存中寻找。
     * @param key 变量的标识符
     * @return 变量的对象。若不存在，则返回null。
     */
    fun getVar(key: String): Var? {
        return vars.getOrDefault(key, null)
    }

    val allVars: Collection<Any>
        /**
         * 获取此缓存中的全部变量。不会从父缓存搜索。
         * @return 一个包含了此缓存全部变量的集合。
         */
        get() = vars.values

    /**
     * 缓存中是否包含某个变量
     * @param id 变量名
     * @return 如果包含则返回true，否则返回false
     */
    fun containVar(id: String?): Boolean {
        return vars.containsKey(id)
    }

    companion object {
        //endregion
        /**
         * TODO:DEBUG
         * 打印所有的函数和类
         */
        fun printAll() {
            for (s in Project.global.cache.functions) {
                if (s is NativeFunction) {
                    println("native " + s.namespace + " -> " + s.javaReferContext!!.text)
                } else {
                    println(s.namespaceID)
                    for (c in s.commands) {
                        println("\t" + c)
                    }
                }
            }
            for (s in Project.global.cache.classes.values) {
                if (s is NativeClass) {
                    println("native class " + s.namespace + ":" + s.identifier + " -> " + s.cls.toString())
                    continue
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
                for (f in s.cache!!.functions) {
                    if (f is NativeFunction) {
                        println(
                            "\t\t" + f.accessModifier.name
                                .lowercase(Locale.getDefault()) + " native " + (if (f.isStatic) "static" else "") + f.namespaceID
                        )
                    } else {
                        println(
                            "\t\t" + f.accessModifier.name
                                .lowercase(Locale.getDefault()) + " " + (if (f.isStatic) "static" else "") + f.namespaceID
                        )
                        for (d in f.commands) {
                            println("\t\t\t" + d)
                        }
                    }
                }
                println("\tattributes:")
                for (v in s.cache!!.vars.values) {
                    println(
                        "\t\t" + v.accessModifier.name
                            .lowercase(Locale.getDefault()) + " " + (if (v.isStatic) "static " else "") + v.type + " " + v.key
                    )
                }
            }
        } //public static Object getKey(Map<?,?> map, Object value){
        //    List<Object> keyList = new ArrayList<>();
        //    for(Object key: map.keySet()){
        //        if(map.get(key).equals(value)){
        //            keyList.add(key);
        //        }
        //    }
        //    return keyList.get(0);
        //}
    }
}