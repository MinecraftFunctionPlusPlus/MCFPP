package top.mcfpp.model.function

import top.mcfpp.antlr.MCFPPExprVisitor
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
import java.util.*

open class DataTemplateConstructor(val data: DataTemplate, ctx: CurlBlockContext?): Function(
    "_init_" + data.identifier.lowercase(Locale.getDefault()) + "_" + data.constructors.size,
    data,
    ctx
) {

    lateinit var file: MCFPPFile

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
        val receiver = if (data is ObjectDataTemplate) StaticMemberView(data.getType())
            else target.scope.getVar("this") as DataTemplateObject
        for ((name, expression) in data.preInit) {
            val value = MCFPPExprVisitor().visitExpression(expression)
            val field = receiver.getMemberVar(name, Member.AccessModifier.PRIVATE).first!!
            val assigned = field.assignedBy(value)
            if (data is ObjectDataTemplate) {
                field.replacedBy(assigned)
                top.mcfpp.analysis.StorageAccess.materialize(assigned)
            }
        }
    }
}

