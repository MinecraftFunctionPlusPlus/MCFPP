package top.mcfpp.model.function

import top.mcfpp.Project
import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.MCFPPFieldVisitor
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.ValueSnapshot
import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.antlr.mcfppParser.CurlBlockContext
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.core.lang.obj.StaticMemberView
import top.mcfpp.io.MCFPPFile
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Member
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectDataTemplate
import top.mcfpp.util.LogProcessor
import java.util.*

open class DataTemplateConstructor(val data: DataTemplate, ctx: CurlBlockContext?): Function(
    "_init_" + data.identifier.lowercase(Locale.getDefault()) + "_" + data.constructors.size,
    data,
    ctx
) {

    var file: MCFPPFile?
        get() = declarationFile
        set(value) { declarationFile = value }

    fun addParamsFromContext(ctx: mcfppParser.NormalParamsContext) {
        val n = ctx.parameterList()?:return
        for (param in n.parameter()) {
            val (p,v) = parseParam(param)
            normalParams.add(p)
            scope.putVar(p.identifier, v)
        }
    }

    override fun invoke(normalArgs: LinkedHashMap<String, Var<*>>, caller: CanSelectMember?): Var<*> {
        if (ast == null && !bodyCompiled && !bodyBeingCompiled) compileBody()
        val result = super.invoke(normalArgs, caller)
        return if (caller is DataTemplateObject) caller else result
    }

    override fun prepareBody(target: Function) {
        super.prepareBody(target)
        target.bindIncomingParameters()
        val receiver = if (data is ObjectDataTemplate) StaticMemberView(data.getType())
            else target.scope.getVar("this") as DataTemplateObject
        for ((name, expression) in data.preInit) {
            if (data !is ObjectDataTemplate && name in data.deferredFields) continue
            val errors = Project.errorCount
            val value = MCFPPExprVisitor().visitExpression(expression)
            if (value is UnknownVar || value.isError || Project.errorCount != errors) continue
            if (data is ObjectDataTemplate) {
                data.deferredFields[name]?.let { MCFPPFieldVisitor().completeTemplateField(data, it, value.type) }
            }
            val field = receiver.getMemberVar(name, Member.AccessModifier.PRIVATE).first ?: continue
            if (field.isConst && !StorageAccess.hasRuntimeRepresentation(value) && ValueSnapshot.of(value) == null) {
                LogProcessor.error("Compiler-only const field '$name' requires a complete value snapshot")
                continue
            }
            val assigned = field.assignedBy(value)
            if (assigned.isError || Project.errorCount != errors) continue
            if (data is ObjectDataTemplate) {
                assigned.isConst = field.isConst
                assigned.isStatic = field.isStatic
                assigned.accessModifier = field.accessModifier
                assigned.declaredParentTemplate = field.declaredParentTemplate
                if (assigned !== field) assigned.annotations.addAll(field.annotations)
                assigned.bindDeclaration(previous = field)
                field.replacedBy(assigned)
                if (StorageAccess.hasRuntimeRepresentation(assigned)) StorageAccess.materialize(assigned)
            }
        }
    }
}

