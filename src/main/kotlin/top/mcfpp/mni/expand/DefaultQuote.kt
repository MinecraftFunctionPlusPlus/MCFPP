package top.mcfpp.mni.expand

import top.mcfpp.mni.api.*
import java.util.function.Consumer

/** Compiler-owned quote service; it depends only on immutable descriptors and arena allocation. */
internal class DefaultQuote(
    private val arena: CodeArena,
    private val types: TypeService,
    private val origin: Origin,
    private val scope: ScopeId = arena.rootScope,
    private val executionContext: ExecutionContextId? = null,
    private val minecraftFactory: ((CodeBuilder) -> MinecraftBuilder)? = null,
    private val access: BuilderAccess = BuilderAccess()
) : Quote {
    private fun parentScope(): ScopeId = access.active?.scope ?: scope
    private fun context(): ExecutionContextId? = access.active?.executionContext ?: executionContext

    private fun builder(parent: ScopeId, context: ExecutionContextId?, result: YieldTarget? = null): DefaultCodeBuilder = DefaultCodeBuilder(arena, types, origin, arena.childScope(parent), access, result, context, minecraftFactory)

    override fun block(body: Consumer<CodeBuilder>): Block {
        val builder = builder(parentScope(), context())
        builder.consume { body.accept(it) }
        return builder.finish()
    }

    override fun <T> expression(type: TypeRef<T>, body: Consumer<CodeBuilder>): Expr<T> {
        if ((type as ImmutableTypeRef<T>).kind == TypeKind.VOID)
            throw CodeModelException(origin.callSite, "An expression region cannot have void type")
        val parent = parentScope()
        val context = context()
        val region = arena.freshRegion()
        val builder = builder(parent, context, YieldTarget(type, region))
        builder.consume { body.accept(it) }
        val block = builder.finish(requireResult = true)
        return arena.expr(arena.metadata(parent, origin, builder.executionContext), type, ExprNode.ExprBlock(block, type, region))
    }
}
