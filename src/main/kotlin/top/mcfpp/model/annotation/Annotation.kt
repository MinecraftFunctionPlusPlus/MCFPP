package top.mcfpp.model.annotation

import top.mcfpp.core.lang.Var
import top.mcfpp.model.compound.Class
import top.mcfpp.model.compound.DataTemplate
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
 * 可以通过继承此类来实现一个注解。抽象方法[forClass]，[forDataTemplate]，[forFunction]分别表示注解对类、数据模板和函数的操作。
 *
 * 使用MNI实现注解的时候，注解需要拥有一个私有的构造函数，并且此构造函数的参数列表和在mcfpp调用注解的时候注解的参数列表的参数类型和顺序相同。在执行注解的时候，将会把传入注解的mcfpp变量转换为jvm变量传入注解的构造函数中构建一个新的注解实例。
 *
 * 继承[ClassAnnotation]，[DataTemplateAnnotation]，[FunctionAnnotation]来实现注解，让注解只能适用于类、数据模板和函数。如果直接继承此类，请注意在[forClass]，[forFunction]，[forDataTemplate]对应的情况中抛出异常
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

    val parents = ArrayList<Annotation>()

    @Suppress("ConvertSecondaryConstructorToPrimary")
    internal constructor(identifier: String, namespace: String, param: ArrayList<String> = ArrayList()) {
        this.identifier = identifier
        this.namespace = namespace
        this.params.addAll(param)
    }

    internal fun on(clazz: Class){
        forClass(clazz)
        parents.forEach { it.on(clazz) }
    }

    abstract fun forClass(clazz: Class)

    internal fun on(function: Function){
        forFunction(function)
        parents.forEach { it.on(function) }
    }

    abstract fun forFunction(function: Function)

    internal fun on(data: DataTemplate){
        forDataTemplate(data)
        parents.forEach { it.on(data) }
    }

    abstract fun forDataTemplate(data: DataTemplate)

    internal fun on(field: Var<*>){
        forField(field)
        parents.forEach { it.on(field) }
    }

    abstract fun forField(field: Var<*>)

    @JvmName("extends_")
    fun extends(annotation: Annotation){
        parents.add(annotation)
    }

    companion object {
        fun newInstance(clazz: java.lang.Class<out Annotation>, args: ArrayList<Any>): Annotation? {
            //比对参数
            try {
                val varType = Array(args.size) { i -> args[i]::class.java }
                val constructor = clazz.getDeclaredConstructor(*varType)
                constructor.isAccessible = true
                return constructor.newInstance(*args.toArray())
            }catch (e: NoSuchMethodException){
                LogProcessor.error("Cannot find constructor for annotation ${clazz.name} with param ${args.joinToString(",")}")
                return null
            }catch (e: Exception){
                LogProcessor.error("Cannot create instance for annotation ${clazz.name}")
                return null
            }
        }
    }
}

