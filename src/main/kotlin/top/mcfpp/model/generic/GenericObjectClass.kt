package top.mcfpp.model.generic

import top.mcfpp.Project
import top.mcfpp.antlr.MCFPPGenericClassImVisitor
import top.mcfpp.antlr.MCFPPGenericObjectClassFieldVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.`var`.lang.MCFPPTypeVar
import top.mcfpp.`var`.lang.Var
import top.mcfpp.type.MCFPPClassType
import top.mcfpp.type.MCFPPType
import top.mcfpp.model.Class
import top.mcfpp.model.CompiledGenericObjectClass
import top.mcfpp.model.ObjectClass
import top.mcfpp.model.field.GlobalField


class GenericObjectClass : ObjectClass {

    val ctx : mcfppParser.ClassBodyContext

    val compiledClasses: HashMap<List<Var<*>>, ObjectClass> = HashMap()

    var index = 0

    val readOnlyParams: ArrayList<ClassParam> = ArrayList()

    override val namespaceID : String
        get() = "$namespace:${identifier}_${readOnlyParams.joinToString("_") { it.typeIdentifier }}"

    @get:Override
    override val prefix: String
        get() = "${namespace}_object_class_${identifier}_${readOnlyParams.joinToString("_") { it.typeIdentifier }}_"

    /**
     * 获取这个类指针对于的marker的tag
     */
    override val tag: String
        get() = "${namespace}_object_class_${identifier}_${readOnlyParams.joinToString("_") { it.typeIdentifier }}_pointer"

    /**
     * 生成一个类，它拥有指定的标识符和命名空间
     * @param identifier 类的标识符
     * @param namespace 类的命名空间
     */
    constructor(identifier: String, namespace: String = Project.currNamespace, ctx : mcfppParser.ClassBodyContext):super(identifier, namespace) {
        this.ctx = ctx
    }

    fun compile(readOnlyArgs: List<Var<*>>) : Class {
        val cls = CompiledGenericObjectClass("${identifier}_${readOnlyParams.joinToString("_") { it.typeIdentifier }}_$index", namespace, this)
        for (parent in this.parent){
            cls.extends(parent)
        }
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
        namespace.field.addObject(cls.identifier, cls)
        Class.currClass = cls
        MCFPPGenericObjectClassFieldVisitor(cls).visitClassDeclaration(ctx.parent as mcfppParser.ClassDeclarationContext)
        Class.currClass = cls
        MCFPPGenericClassImVisitor().visitClassBody(ctx)
        index ++

        cls.getType = { MCFPPClassType(cls, this.getType().parentType) }

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
}