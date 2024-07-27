package top.mcfpp.model

import top.mcfpp.lang.Var
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import java.io.Serializable


/**
 * 注解用于在编译阶段提供对函数和类的额外信息。同时注解也可以对函数和类进行某些额外的编译期间的操作。注解通常是这样的格式
 * ```java
 * @Annotation()
 * class xxx{
 *      @Annotation
 *      @AnnotationWithParam(param)
 *      func xxx(){
 *      }
 * }
 * ```
 * 注解可以拥有参数，或者没有参数。当注解没有参数的时候，其括号是可以省略的
 *
 * @constructor Create empty Annotation
 */
abstract class Annotation : Serializable {

    /**
     * 注解的标识符
     */
    val identifier : String

    /**
     * 注解的命名空间
     */
    val namespace : String

    /**
     * 注解的参数
     */
    val params = ArrayList<String>()

    constructor(identifier: String, namespace: String, param: ArrayList<String> = ArrayList()) {
        this.identifier = identifier
        this.namespace = namespace
        this.params.addAll(param)
    }

    abstract fun forClass(clazz: Class)

    abstract fun forFunction(function: Function)

    companion object {
        fun newInstance(clazz: java.lang.Class<out Annotation>, param: ArrayList<Var<*>>): Annotation {
            //比对参数
            try {
                val varType = Array(param.size) { i -> param[i]::class.java }
                val constructor = clazz.getConstructor(*varType)
                return constructor.newInstance(*param.toArray())
            }catch (e: NoSuchMethodException){
                LogProcessor.error("Cannot find constructor for annotation ${clazz.name} with param ${param.joinToString(",")}")
                throw e
            }catch (e: Exception){
                LogProcessor.error("Cannot create instance for annotation ${clazz.name}")
                throw e
            }
        }
    }
}

abstract class FunctionAnnotation(identifier: String, namespace: String) : Annotation(identifier,namespace) {
    override fun forClass(clazz: Class) {
        throw Exception("Cannot use function annotation on class")
    }
}

abstract class ClassAnnotation(identifier: String, namespace: String) : Annotation(identifier,namespace) {
    override fun forFunction(function: Function) {
        throw Exception("Cannot use class annotation on function")
    }
}