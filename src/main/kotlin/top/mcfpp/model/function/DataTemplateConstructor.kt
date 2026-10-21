package top.mcfpp.model.function

import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.TypeUsage
import top.mcfpp.antlr.MCFPPExprVisitor
import top.mcfpp.antlr.MCFPPFieldVisitor
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.antlr.mcfppParser.CurlBlockContext
import top.mcfpp.core.lang.UnknownVar
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

open class DataTemplateConstructor(val data: DataTemplate, ctx: CurlBlockContext?):
    Function(
        "_init_" + data.identifier.lowercase(Locale.getDefault()) + "_" + data.constructors.size,
        data,
        ctx
    ) {

    var file: MCFPPFile?
        get() = declarationFile
        set(value) {
            declarationFile = value
        }

    /** Selection for a later owner-stage executor; this does not invoke any parent body. */
    internal fun initializationPlan(): ConstructorPlan.Result = ConstructorPlan.build(data)

    fun addParamsFromContext(ctx: mcfppParser.NormalParamsContext) {
        val n = ctx.parameterList() ?: return
        for (param in n.parameter()) {
            val (p,v) = parseParam(param)
            normalParams.add(p)
            scope.putVar(p.identifier, v)
        }
    }

    override fun invoke(normalArgs: LinkedHashMap<String, Var<*>>, caller: CanSelectMember?): Var<*> {
        if (!data.canInstantiate()) {
            if (caller is Var<*>) caller.isError = true
            return UnknownVar("construction_${data.identifier}").apply { type = data.getType() }
        }
        val errors = Project.errorCount
        if (ast == null && !needsActualBinding() && !bodyCompiled && !bodyBeingCompiled) compileBody()
        if (Project.errorCount != errors || constructedReceiver?.isError == true) {
            if (caller is Var<*>) caller.isError = true
            return UnknownVar("construction_${data.identifier}").apply { type = data.getType() }
        }
        val result = super.invoke(normalArgs, caller)
        if (result is UnknownVar || Project.errorCount != errors) {
            if (caller is Var<*>) caller.isError = true
            return UnknownVar("construction_${data.identifier}").apply { type = data.getType() }
        }
        return if (caller is DataTemplateObject) caller else result
    }

    override fun prepareBody(target: Function) {
        super.prepareBody(target)
        target.bindIncomingParameters()
        val receiver = if (data is ObjectCompoundData) StaticMemberView(data.getType())
        else target.scope.getVar("this") as DataTemplateObject
        if (receiver is DataTemplateObject) target.constructedReceiver = receiver
        if (receiver is DataTemplateObject) {
            val plan = initializationPlan().plan ?: run {
                receiver.isError = true
                return
            }
            for (stage in plan.ancestors) {
                // An implicit owner with no declaration initializers has no work to emit.
                // In particular the universal DataObject ancestor needs no call target.
                if (stage.constructor == null && stage.owner.preInit.isEmpty()) continue
                if (!target.emitConstructorStage(stage.owner, stage.constructor, receiver) { body, shared ->
                        initializeOwnerFields(stage.owner, body, shared)
                    }
                ) {
                    receiver.isError = true
                    return
                }
            }
            initializeOwnerFields(data, target, receiver)
            return
        }
        // Static object initialization has its own receiver and publication path.
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

    private fun initializeOwnerFields(owner: DataTemplate, target: Function, receiver: DataTemplateObject) {
        for ((name, expression) in owner.preInit) {
            if (name in owner.deferredFields) continue
            val errors = Project.errorCount
            val value = target.withFieldInitializerOwner(owner) {
                MCFPPExprVisitor().visitExpression(expression, owner.scope.getVar(name)?.type)
            }
            if (value is UnknownVar || value.isError || Project.errorCount != errors) continue
            TypeUsage.ordinaryDiagnostic(value.type, StorageAccess.snapshot(value))?.let(LogProcessor::error)
            if (Project.errorCount != errors) continue
            val assigned = receiver.initializeDeclaredField(owner, name, value) ?: continue
            if (assigned.isError || Project.errorCount != errors) receiver.isError = true
        }
    }
}
