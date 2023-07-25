package top.mcfpp.lib

import org.jetbrains.annotations.Nullable
import top.mcfpp.lang.*
import java.util.*
import kotlin.collections.ArrayList

/**
 * 一个域。在编译过程中，编译器读取到的变量，函数等会以键值对的方式储存在其中。键为函数的id或者变量的
 * 标识符，而值则是这个函数或者变量的对象。
 *
 * 同一个域中的函数
 *
 * 域应该是一个链式的结构，主要分为如下几种情况：<br></br>
 * * 类的静态变量 ---> 类的成员变量 --> 函数 ---> 匿名内部函数<br></br>
 *
 * * 类的静态变量 ---> 静态函数 ---> 匿名内部函数<br></br>
 *
 * * 函数 ---> 匿名内部函数<br></br>
 * 寻找变量的时候，应当从当前的作用域开始寻找。<br></br>
 *
 * 变量和类都分别储存在一张哈希表中，键名即它在声明的时候的名字，而值则代表了它的对象。
 * 值得注意的是，变量声明时的名字虽然和最终编译出的名字有关，但不相同。
 *
 * 函数储存在一个列表中
 */
class NamespaceField: IFieldWithClass, IFieldWithFunction {
    /**
     * 变量
     */
    private val vars: HashMap<String, Var> = HashMap()

    /**
     * 函数
     */
    private var functions: ArrayList<Function> = ArrayList()

    fun forEachFunction(operation: (Function) -> Any?){
        for (function in functions){
            operation(function)
        }
    }

    /**
     * 类
     */
    private var classes: HashMap<String, Class> = HashMap()


    fun forEachClass(operation: (Class) -> Any?){
        for (`class` in classes.values){
            operation(`class`)
        }
    }


    /**
     * 父级域。命名空间的父级域应当是全局
     */
    @Nullable
    var parent = GlobalField

    /**
     * 这个缓存在哪一个容器中
     */
    @Nullable
    var container: FieldContainer? = GlobalField

    constructor()

    /**
     * 复制一个缓存。
     * @param cache 原来的缓存
     */
    constructor(cache: NamespaceField) {
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
     * @return 如果此缓存中存在这个函数，则返回这个函数的对象，否则返回null
     */
    @Nullable
    override fun getFunction(key: String, argsTypes: List<String>): Function? {
        for (f in functions) {
            if (f.name == key && f.params.size == argsTypes.size) {
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


    /**
     * 添加一个函数
     *
     * @param function
     */
    override fun addFunction(function: Function){
        functions.add(function)
    }

    override fun hasFunction(function: Function): Boolean{
        return functions.contains(function)
    }

    /**
     * 根据所给的id获取一个类
     *
     * @param identifier
     */
    override fun getClass(identifier: String): Class? {
        return classes[identifier]
    }

    override fun hasClass(cls: Class): Boolean{
        return classes.containsKey(cls.identifier)
    }

    override fun hasClass(identifier: String): Boolean{
        return classes.containsKey(identifier)
    }

    override fun addClass(identifier: String, cls: Class, force : Boolean): Boolean{
        return if (force){
            classes[identifier] = cls
            true
        }else{
            if(!classes.containsKey(identifier)){
                classes[identifier] = cls
                true
            }else{
                false
            }
        }
    }

    override fun removeClass(identifier: String): Boolean {
        return if(classes.containsKey(identifier)) {
            classes.remove(identifier)
            true
        }else{
            false
        }
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

    val allVars: Collection<Var>
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
    fun containVar(id: String): Boolean {
        return vars.containsKey(id)
    }

    /**
     * 移除缓存中的某个变量
     *
     * @param id 变量名
     * @return 若变量存在，则返回被移除的变量，否则返回空
     */
    fun removeVar(id : String): Var?{
        return vars.remove(id)
    }


}