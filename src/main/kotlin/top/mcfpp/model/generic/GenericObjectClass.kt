package top.mcfpp.model.generic

import top.mcfpp.Project
import top.mcfpp.antlr.MCFPPGenericClassImVisitor
import top.mcfpp.antlr.MCFPPGenericObjectClassFieldVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.ClassPointer
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCFPPValue
import top.mcfpp.core.lang.Var
import top.mcfpp.model.compound.Class
import top.mcfpp.model.compound.CompiledGenericClass
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.MCUUID

open class GenericObjectClass : GenericClass {

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
    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(identifier: String, namespace: String = Project.currNamespace, ctx : mcfppParser.ClassBodyContext):super(identifier, namespace, ctx)

    override fun compile(readonlyArgs: List<Var<*>>) : CompiledGenericClass {
        val cls = CompiledGenericObjectClass(
            "${identifier}_${readOnlyParams.joinToString("_") { it.typeIdentifier }}_$index",
            namespace,
            this,
            readonlyArgs.map { it as MCFPPValue<*> }
        )
        for (parent in this.parent){
            cls.extends(parent)
        }
        //只读属性
        for (i in readOnlyParams.indices) {
            val r = readonlyArgs[i].clone()
            r.isConst = true
            if(r is MCFPPTypeVar){
                cls.field.putType(readOnlyParams[i].identifier, r.value)
            }
            cls.field.putVar(readOnlyParams[i].identifier, r, false)
        }

        //注册
        currClass = cls
        MCFPPGenericObjectClassFieldVisitor(cls).visitClassDeclaration(ctx.parent as mcfppParser.ClassDeclarationContext)
        currClass = cls
        MCFPPGenericClassImVisitor().visitClassBody(ctx)
        index ++

        compiledClasses[readonlyArgs] = cls

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

class CompiledGenericObjectClass(
    identifier: String,
    namespace: String = Project.currNamespace,
    originClass: GenericObjectClass,
    args: List<MCFPPValue<*>>
) : CompiledGenericClass(identifier, namespace, originClass, args) {

    var mcuuid: MCUUID = MCUUID.genFromString("$namespace:$identifier")

    var normalClass: Class? = null

    override fun newPointer(): ClassPointer {
        LogProcessor.error("Cannot instantiate an object class")
        return ClassPointer(this, "error_object_instance")
    }
}