package top.mcfpp.antlr

import top.mcfpp.Project.withCompilationContext
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.DeclarationBinding
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.Var
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.IScopeWithType
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.type.MCFPPEnumType
import top.mcfpp.type.MCFPPType

/** Readonly expressions use the declaration binder and immutable values, never executable bodies. */
class MCFPPReadonlyExprVisitor(
    private val enumType: MCFPPEnumType? = null,
    private val lookupTypeScope: IScopeWithType? = null,
    private val lexicalCaller: Function? = null
) : mcfppParserBaseVisitor<Var<*>?>() {
    override fun visitExpression(ctx: mcfppParser.ExpressionContext): Var<*>? = withCompilationContext(ctx) {
        val caller = lexicalCaller ?: Function.currFunction
        val scope = lookupTypeScope ?: caller.scope
        val members = enumType?.let { type ->
            type.enum.members.mapValues { (_, member) ->
                DeclarationBinding.Bound(type, CompilerValue.Typed(type.typeId, CompilerValue.Record(mapOf(
                    "ordinal" to CompilerValue.Integral(member.value.toLong()),
                    "data" to CompilerValue.Nbt(Tag.toSNBT(member.data))
                ))))
            }
        }.orEmpty()
        val bound = try {
            DeclarationBinding(caller, emptyMap(), members, lookupScope = scope, captureValues = true)
                .expression(ctx)
        } catch (_: DeclarationBinding.Failure) {
            return@withCompilationContext null
        }
        val snapshot = bound.constant ?: return@withCompilationContext null
        val types = bound.descriptors.toMutableMap().apply { put(bound.type.typeId, bound.type) }
        MCFPPType.registerSnapshotTypes(snapshot, types)
        StorageAccess.restore(bound.type, snapshot, "readonly", types)
    }
}
