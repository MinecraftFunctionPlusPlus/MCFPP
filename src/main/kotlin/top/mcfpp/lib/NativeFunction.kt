package top.mcfpp.lib

import top.mcfpp.exception.IllegalFormatException
import top.mcfpp.lang.CanSelectMember
import top.mcfpp.lang.ClassBase
import top.mcfpp.lang.ClassPointer
import top.mcfpp.lang.Var
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.Class

/**
 * 表示了一个native方法
 */
class NativeFunction(name: String, javaMethod: mcfppParser.JavaReferContext?) : Function(name), Native {
    /**
     * native方法引用的java方法的词法上下文
     */
    var javaReferContext: mcfppParser.JavaReferContext?

    /**
     * 要调用的java方法
     */
    var javaMethod: Method

    /**
     * 引用的java方法的类名。包含包路径
     */
    var javaClassName: String

    /**
     * 引用的java方法的参数名。
     */
    var javaMethodName: String

    init {
        javaReferContext = javaMethod
        val strs: List<mcfppParser.StringNameContext> = javaMethod!!.stringName()
        try {
            javaMethodName = strs[strs.size - 1].text
            javaClassName = javaMethod.text.substring(0, javaMethod.text.lastIndexOf(javaMethodName) - 1)
        } catch (e: StringIndexOutOfBoundsException) {
            throw IllegalFormatException(javaReferContext!!.text)
        }
        try{
            val cls: Class<*> = Class.forName(javaClassName)
            this.javaMethod = cls.getMethod(javaMethodName, Array<Var>::class.java, ClassPointer::class.java)
        } catch (e: NoSuchMethodException) {
            throw NoSuchMethodException(javaMethodName)
        } catch (e: ClassNotFoundException) {
            throw ClassNotFoundException(javaClassName)
        }
    }

    @Override
    override fun invoke(args: ArrayList<Var>, cls: ClassBase?) {
        val argsArray = arrayOfNulls<Var>(args.size)
        args.toArray(argsArray)
        try {
            javaMethod.invoke(null, argsArray, cls)
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        } catch (e: InvocationTargetException) {
            throw RuntimeException(e)
        }
    }
}