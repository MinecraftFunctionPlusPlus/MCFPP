package top.mcfpp.model.generic

import top.mcfpp.antlr.mcfppParser
import top.mcfpp.Project
import top.mcfpp.antlr.McfppGenericClassFieldVisitor
import top.mcfpp.antlr.McfppGenericClassImVisitor
import top.mcfpp.lang.MCFPPTypeVar
import top.mcfpp.lang.Var
import top.mcfpp.lang.type.MCFPPClassType
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.model.Class
import top.mcfpp.model.CompiledGenericClass
import top.mcfpp.model.field.GlobalField

/**
 * 泛型类，即携带只读参数的类。mcfpp中的泛型借助只读参数实现，只读参数和函数的只读参数完全一致。通常来说，可以使用Class<类型 标识符>这样的形式
 *声明一个泛型类。
 *
 * 和泛型函数一样，泛型类将会在使用的时候根据传入的泛型参数的值即时编译。而泛型类本身不会被编译。编译后的泛型类将会被从0开始编号，以此进行区分
 *
 * 泛型类同样拥有“重载”的特性。例如，Test<int i>和Test<int i, int j>会被视作两个不同的泛型类。当然，两个重载的泛型类之间也是可以相互继承的。
 *诸如Test<int i, int j> : Test<i>是完全被允许的行为。
 *
 * 泛型类的只读参数是不能被继承的，它在编译的过程中即被确定，也不会作为成员储存在数据包中。
 */
class GenericClass : Class {

    val ctx : mcfppParser.ClassBodyContext

    val compiledClasses: HashMap<List<Var<*>>, Class> = HashMap()

    var index = 0

    val readOnlyParams: ArrayList<ClassParam> = ArrayList()

    override val namespaceID : String
        get() = "$namespace:${identifier}_${readOnlyParams.joinToString("_") { it.typeIdentifier }}"

    @get:Override
    override val prefix: String
        get() = "${namespace}_class_${identifier}_${readOnlyParams.joinToString("_") { it.typeIdentifier }}_"

    /**
     * 获取这个类指针对于的marker的tag
     */
    override val tag: String
        get() = "${namespace}_class_${identifier}_${readOnlyParams.joinToString("_") { it.typeIdentifier }}_pointer"

    /**
     * 生成一个类，它拥有指定的标识符和命名空间
     * @param identifier 类的标识符
     * @param namespace 类的命名空间
     */
    constructor(identifier: String, namespace: String = Project.currNamespace, ctx : mcfppParser.ClassBodyContext):super(identifier, namespace) {
        this.ctx = ctx
    }

    fun compile(readOnlyArgs: List<Var<*>>) : Class{
        val cls = CompiledGenericClass("${identifier}_${readOnlyParams.joinToString("_") { it.typeIdentifier }}_$index", namespace, this)
        cls.initialize()
        for (parent in this.parent){
            cls.extends(parent)
        }
        cls.isStaticClass = this.isStaticClass
        cls.isAbstract = this.isStaticClass

        //只读属性
        for (i in readOnlyParams.indices) {
            val r = readOnlyArgs[i].clone()
            r.isConst = true
            if(r is MCFPPTypeVar){
                cls.field.putType(readOnlyParams[i].identifier, r.value)
            }
            cls.field.putVar(readOnlyParams[i].identifier, r, false)
        }

        //注册
        val namespace = GlobalField.localNamespaces[namespace]!!
        namespace.field.addClass(cls.identifier, cls)
        Class.currClass = cls
        McfppGenericClassFieldVisitor(cls).visitClassDeclaration(ctx.parent as mcfppParser.ClassDeclarationContext)
        Class.currClass = cls
        McfppGenericClassImVisitor().visitClassBody(ctx)
        index ++

        cls.getType = {MCFPPClassType(cls, this.getType().parentType)}

        compiledClasses[readOnlyArgs] = cls

        return cls
    }

    fun isSelf(identifier: String, readOnlyParam: List<MCFPPType>): Boolean {
        if (this.identifier == identifier) {
            if (readOnlyParam.size != readOnlyParams.size) {
                return false
            }
            for (i in readOnlyParam.indices) {
                if (readOnlyParam[i].typeName != readOnlyParams[i].typeIdentifier) {
                    return false
                }
            }
            return true
        }
        return false
    }

    companion object {
        class Unknown
    }
}

class ClassParam(

    /**
     * 参数类型
     */
    var typeIdentifier: String,

    /**
     * 参数的名字
     */
    var identifier: String,
)