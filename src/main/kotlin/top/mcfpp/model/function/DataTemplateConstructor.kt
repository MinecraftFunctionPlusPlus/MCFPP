package top.mcfpp.model.function

import top.mcfpp.Project
import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.MCFPPFieldVisitor
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.TypeUsage
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
import top.mcfpp.model.compound.ObjectCompoundData
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
        if (ast == null && !needsActualBinding() && !bodyCompiled && !bodyBeingCompiled) compileBody()
        val result = super.invoke(normalArgs, caller)
        return if (caller is DataTemplateObject) caller else result
    }

    override fun prepareBody(target: Function) {
        super.prepareBody(target)
        target.bindIncomingParameters()
        val receiver = if (data is ObjectCompoundData) StaticMemberView(data.getType())
            else target.scope.getVar("this") as DataTemplateObject
        if (receiver is DataTemplateObject) target.constructedReceiver = receiver
        val initializers = linkedMapOf<String, Pair<DataTemplate, mcfppParser.ExpressionContext>>()
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<DataTemplate, Boolean>())
        fun collect(template: DataTemplate) {
            if (!visited.add(template)) return
            template.parent.filterIsInstance<DataTemplate>().forEach(::collect)
            for ((name, expression) in template.preInit) {
                if (data.scope.getVar(name)?.declaredParentTemplate === template)
                    initializers[name] = template to expression
            }
        }
        collect(data)
        for ((name, declaration) in initializers) {
            val (initializerOwner, expression) = declaration
            if (data !is ObjectCompoundData && name in data.deferredFields) continue
            val errors = Project.errorCount
            val value = target.withFieldInitializerOwner(initializerOwner) {
                MCFPPExprVisitor().visitExpression(expression, data.scope.getVar(name)?.type)
            }
            if (value is UnknownVar || value.isError || Project.errorCount != errors) continue
            TypeUsage.ordinaryDiagnostic(value.type, StorageAccess.snapshot(value))?.let {
                LogProcessor.error(it)
            }
            if (Project.errorCount != errors) {
                data.deferredFields.remove(name)
                continue
            }
            if (data is ObjectCompoundData) {
                val declaration = data.deferredFields[name]
                if (declaration != null && MCFPPFieldVisitor().completeTemplateField(data, declaration, value.type) == null) continue
            }
            val field = receiver.getMemberVar(name, Member.AccessModifier.PRIVATE).first ?: continue
            val assigned = field.assignedBy(value)
            if (assigned.isError || Project.errorCount != errors) continue
            if (data is ObjectCompoundData) {
                assigned.isConst = field.isConst
                assigned.isStatic = field.isStatic
                assigned.accessModifier = field.accessModifier
                assigned.declaredParentTemplate = field.declaredParentTemplate
                if (assigned !== field) assigned.annotations.addAll(field.annotations.filter { it !in assigned.annotations })
                assigned.bindDeclaration(previous = field)
                field.replacedBy(assigned)
                if (StorageAccess.hasRuntimeRepresentation(assigned)) StorageAccess.materialize(assigned)
            }
        }
    }
}

