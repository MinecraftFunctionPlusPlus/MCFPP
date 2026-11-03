package top.mcfpp.analysis

import org.antlr.v4.runtime.ParserRuleContext
import top.mcfpp.core.lang.Var
import top.mcfpp.library.compiler.LibraryBuiltinTypes
import top.mcfpp.library.compiler.ProjectionBindings
import top.mcfpp.library.syntax.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.StructuredBodyContexts
import top.mcfpp.type.*

/** Explicit session contracts; these compiler objects never enter library DTOs. */
sealed interface StructuredNameBinding {
    data class Value(val resolve: (Function) -> Var<*>?) : StructuredNameBinding
    data class Type(val type: MCFPPType) : StructuredNameBinding
    data class Functions(val candidates: List<Function>) : StructuredNameBinding
    data class Frame(val declaration: DeclarationId, val identifier: String) : StructuredNameBinding
    class Macros internal constructor(internal val candidates: top.mcfpp.mni.expand.FrozenMacroCandidates) : StructuredNameBinding
}

internal interface StructuredSessionBindings : ProjectionBindings {
    fun declaration(reference: DeclarationReference): StructuredNameBinding
    fun local(declaration: DeclarationId, frame: Function): Var<*>? = null
    /** Exact D directory entry for pure formal facts; never restore a prototype Var here. */
    fun formal(declaration: DeclarationId): top.mcfpp.model.function.FunctionParam? = null
    fun publishLocal(declaration: DeclarationId, frame: Function, value: Var<*>) {}
    fun closedConstant(reference: ConstantRef): DeclarationBinding.Bound? = null
    fun closedConstant(reference: ConstantRef, frame: Function): DeclarationBinding.Bound? = closedConstant(reference)
    fun functionTargets(reference: ConstantRef): top.mcfpp.core.lang.FunctionTargetResolver? = null
    fun memberFunctions(candidates: List<DeclarationReference>, receiver: Var<*>, frame: Function): List<Function> =
        memberFunctions(candidates, (receiver as? top.mcfpp.core.lang.obj.StaticMemberView)?.declaration as? MCFPPType
            ?: StorageAccess.actualType(receiver), frame)
    fun memberFunctions(candidates: List<DeclarationReference>, receiverType: MCFPPType, frame: Function): List<Function> =
        candidates.flatMap { reference ->
            (declaration(reference) as? StructuredNameBinding.Functions)?.candidates
                ?: throw DeclarationBinding.Failure("LIB_BODY_UNSUPPORTED: Member candidate is not an ordinary callable declaration")
        }
    fun boundType(reference: TypeRef, frame: Function): MCFPPType? = type(reference) as? MCFPPType
    fun dependentType(syntax: TypeSyntax, frame: Function): MCFPPType? = null
    fun dependentType(type: MCFPPType, frame: Function): MCFPPType? = null
}

/** Author bindings precede every display-token lookup. There is deliberately no consumer-import fallback. */
internal object StructuredBindings {
    private fun provider(site: ParserRuleContext) = StructuredBodyContexts.bindings(site)
    fun isBound(site: ParserRuleContext) = StructuredBodyContexts.origin(site)?.nameUse != null
    fun frame(function: Function): Function {
        var result = function
        while (!result.hasDeclarationBindingScope &&
            (result is top.mcfpp.model.function.NoStackFunction || result is top.mcfpp.model.function.InternalFunction))
            result = result.parent.singleOrNull() ?: break
        return result
    }

    fun name(site: ParserRuleContext, frame: Function?): StructuredNameBinding? {
        val origin = StructuredBodyContexts.origin(site) ?: return null
        val use = origin.nameUse ?: return null
        val provider = provider(site) ?: error("Projected name has no author provider")
        val session = provider as? StructuredSessionBindings
        val boundMacro = origin.boundName?.binding as? StructuredNameBinding.Macros
        fun normalize(value: Any?): StructuredNameBinding? = when (value) {
            is StructuredNameBinding -> value
            is Function -> StructuredNameBinding.Functions(listOf(value))
            is MCFPPType -> StructuredNameBinding.Type(value)
            is Var<*> -> StructuredNameBinding.Value { value }
            is List<*> -> if (value.all { it is Function }) StructuredNameBinding.Functions(value.filterIsInstance<Function>()) else null
            else -> null
        }
        val result = when (use) {
            is NameUse.Lookup -> if (session != null) {
                val candidates = use.candidates.map(session::declaration)
                if (boundMacro != null) boundMacro
                else if (candidates.isNotEmpty() && candidates.all { it is StructuredNameBinding.Macros }) {
                    val macros = candidates.map { (it as StructuredNameBinding.Macros).candidates }
                    val preparedDirectory = macros.first().directory
                    check(macros.all { it.directory === preparedDirectory }) { "Frozen macro candidates require one explicit prepared directory" }
                    StructuredNameBinding.Macros(object : top.mcfpp.mni.expand.FrozenMacroCandidates {
                        override val directory = preparedDirectory
                        override fun bind(catalog: top.mcfpp.mni.expand.SourceTypeCatalog) = macros.flatMap { it.bind(catalog) }
                    })
                }
                else if (candidates.all { it is StructuredNameBinding.Functions })
                    StructuredNameBinding.Functions(candidates.flatMap { (it as StructuredNameBinding.Functions).candidates })
                else if (candidates.size == 1) candidates.single()
                else normalize(origin.boundName?.binding)
            } else normalize(origin.boundName?.binding)
            is NameUse.Declaration -> normalize(origin.boundName?.binding) ?: session?.declaration(use.target)
            is NameUse.Local -> normalize(origin.boundName?.binding)
                ?: StructuredNameBinding.Frame(use.target, requireNotNull(origin.boundName).name)
        } ?: throw DeclarationBinding.Failure("LIB_BODY_UNSUPPORTED: Name '$use' has no explicit session binding")
        if (frame != null && result is StructuredNameBinding.Functions) result.candidates.forEach { candidate ->
            (candidate.owner as? top.mcfpp.model.compound.DataTemplate)?.let { owner ->
                if (frame.accessTo(owner) < candidate.accessModifier)
                    throw DeclarationBinding.Failure("Cannot access declaration function '${candidate.identifier}'")
            }
        }
        return result
    }

    fun functions(site: ParserRuleContext, frame: Function?): List<Function>? = when (val binding = name(site, frame)) {
        null -> null
        is StructuredNameBinding.Macros -> null
        is StructuredNameBinding.Functions -> binding.candidates.distinctBy { it.declarationId }
        else -> emptyList()
    }

    fun memberCandidates(site: ParserRuleContext): List<DeclarationReference>? = when (val node = StructuredBodyContexts.origin(site)?.node) {
        is Expression.Member -> node.candidates
        is Expression.Call -> (node.callee as? Expression.Member)?.candidates
        else -> null
    }

    fun memberFunctions(site: ParserRuleContext, receiver: Var<*>, frame: Function): List<Function>? {
        val candidates = memberCandidates(site) ?: return null
        val session = provider(site) as? StructuredSessionBindings
            ?: throw DeclarationBinding.Failure("LIB_BODY_UNSUPPORTED: Frozen member has no author directory")
        val type = (receiver as? top.mcfpp.core.lang.obj.StaticMemberView)?.declaration as? MCFPPType
            ?: StorageAccess.actualType(receiver)
        return checkedMembers(session.memberFunctions(candidates, receiver, StructuredBindings.frame(frame)), type, frame)
    }

    fun memberFunctions(site: ParserRuleContext, receiverType: MCFPPType, frame: Function): List<Function>? {
        val candidates = memberCandidates(site) ?: return null
        val session = provider(site) as? StructuredSessionBindings
            ?: throw DeclarationBinding.Failure("LIB_BODY_UNSUPPORTED: Frozen member has no author directory")
        return checkedMembers(session.memberFunctions(candidates, receiverType, StructuredBindings.frame(frame)), receiverType, frame)
    }

    private fun checkedMembers(candidates: List<Function>, receiverType: MCFPPType, frame: Function): List<Function> {
        val receiver = (receiverType as? MCFPPDataTemplateType)?.template
        fun dispatch(target: Function): Function {
            val owner = target.owner as? top.mcfpp.model.compound.DataTemplate ?: return target
            if (receiver == null || !receiver.isSubOf(owner)) return target
            val templates = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<top.mcfpp.model.compound.DataTemplate, Boolean>())
            fun collect(value: top.mcfpp.model.compound.DataTemplate) {
                if (!templates.add(value)) return
                value.parent.filterIsInstance<top.mcfpp.model.compound.DataTemplate>().forEach(::collect)
            }
            collect(receiver)
            fun overridesTarget(value: Function): Boolean {
                val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Function, Boolean>())
                var current: Function? = value
                while (current != null && seen.add(current)) {
                    if (current === target) return true
                    current = current.superFunction
                }
                return false
            }
            val implementations = templates.flatMap { it.scope.functions.values.flatten() }.filter { !it.actualCallBody && overridesTarget(it) }.distinctBy { it.declarationId }
            val effective = implementations.filter { base -> implementations.none { derived ->
                val baseOwner = base.owner as? top.mcfpp.model.compound.DataTemplate
                val derivedOwner = derived.owner as? top.mcfpp.model.compound.DataTemplate
                derived !== base && baseOwner != null && derivedOwner != null && derivedOwner !== baseOwner &&
                    derivedOwner.isSubOf(baseOwner) && !baseOwner.isSubOf(derivedOwner)
            } }
            if (effective.size > 1) throw DeclarationBinding.Failure("Frozen member has ambiguous declaration overrides")
            return effective.singleOrNull() ?: target
        }
        return candidates.map(::dispatch).distinctBy { it.declarationId }.onEach { function ->
            (function.owner as? top.mcfpp.model.compound.DataTemplate)?.let { owner ->
                if (frame.accessTo(owner) < function.accessModifier) throw DeclarationBinding.Failure("Cannot access bound member '${function.identifier}'")
            }
        }
    }

    fun variable(site: ParserRuleContext, frame: Function): Var<*>? {
        val result = when (val binding = name(site, frame)) {
        is StructuredNameBinding.Value -> binding.resolve(StructuredBindings.frame(frame))
            ?: throw DeclarationBinding.Failure("Bound value is unavailable in this invocation")
        is StructuredNameBinding.Frame -> (provider(site) as? StructuredSessionBindings)?.local(binding.declaration, StructuredBindings.frame(frame))
            ?: throw DeclarationBinding.Failure("Bound local ${binding.declaration} is unavailable in this invocation")
        else -> null
        }
        val value = result ?: return null
        value.declaredParentTemplate?.let { owner ->
            if (frame.accessTo(owner) < value.accessModifier)
                throw DeclarationBinding.Failure("Cannot access bound declaration '${value.identifier}'")
        }
        return value
    }

    fun publish(site: ParserRuleContext, frame: Function, value: Var<*>) {
        val declaration = StructuredBodyContexts.declaration(site) ?: return
        (provider(site) as? StructuredSessionBindings)?.publishLocal(declaration, StructuredBindings.frame(frame), value)
    }

    fun constant(site: ParserRuleContext, frame: Function? = Function.currFunction): DeclarationBinding.Bound? {
        val reference = StructuredBodyContexts.origin(site)?.constantReference ?: return null
        val provider = requireNotNull(provider(site))
        val session = provider as? StructuredSessionBindings
        (if (frame == null) session?.closedConstant(reference) else session?.closedConstant(reference, StructuredBindings.frame(frame)))?.let { return it }
        val descriptors = linkedMapOf<TypeId, MCFPPType>()
        val active = hashSetOf<ConstantRef>()
        fun type(reference: TypeRef): MCFPPType = (frame?.let { session?.boundType(reference, StructuredBindings.frame(it)) }
            ?: (provider.type(reference) as? MCFPPType)
            ?: provider.builtinKey(reference)?.let(LibraryBuiltinTypes::restore)
            ?: throw DeclarationBinding.Failure("LIB_TYPE_UNSUPPORTED: Constant type $reference is unbound"))
            .also { descriptors[it.typeId] = it }
        fun restore(reference: ConstantRef): Pair<MCFPPType, CompilerValue> {
            if (!active.add(reference)) throw DeclarationBinding.Failure("Cyclic semantic constant")
            val result: Pair<MCFPPType, CompilerValue> = when (val value = provider.constant(reference)) {
                ConstantValue.Null -> MCFPPPrivateType.Null to CompilerValue.NullValue
                is ConstantValue.Integral -> (if (value.value in Int.MIN_VALUE..Int.MAX_VALUE) MCFPPBaseType.Int else MCFPPNBTType.Long) to CompilerValue.Integral(value.value)
                is ConstantValue.FloatBits -> MCFPPBaseType.Float to CompilerValue.FloatBits(value.bits)
                is ConstantValue.DoubleBits -> MCFPPNBTType.Double to CompilerValue.DoubleBits(value.bits)
                is ConstantValue.Bool -> MCFPPBaseType.Bool to CompilerValue.Bool(value.value)
                is ConstantValue.Text -> MCFPPBaseType.String to CompilerValue.Text(value.value)
                is ConstantValue.TypeValue -> MCFPPConcreteType.Type to CompilerValue.TypeValue(type(value.value).typeId)
                is ConstantValue.Nbt -> MCFPPNBTType.NBT to CompilerValue.Nbt(value.snbt)
                is ConstantValue.Sequence -> MCFPPListType(MCFPPBaseType.Any) to CompilerValue.Sequence(value.elements.map { restore(it).second })
                is ConstantValue.Record -> MCFPPDictType(MCFPPBaseType.Any) to CompilerValue.Record(value.fields.associate { it.name to restore(it.value).second })
                is ConstantValue.Typed -> type(value.type).let { declared -> declared to CompilerValue.Typed(declared.typeId, restore(value.value).second) }
                is ConstantValue.FunctionValue -> throw DeclarationBinding.Failure("LIB_BODY_UNSUPPORTED: Function constants require the callable session provider")
            }
            active.remove(reference)
            descriptors[result.first.typeId] = result.first
            return result
        }
        val (type, value) = restore(reference)
        return DeclarationBinding.Bound(type, if (value is CompilerValue.Typed) value else CompilerValue.Typed(type.typeId, value), descriptors = descriptors)
    }

    fun literal(site: ParserRuleContext): Var<*>? = constant(site)?.let { fact ->
        val value = StorageAccess.literal(fact.type, requireNotNull(fact.constant), types = fact.descriptors)
        val binding = value.storageBinding
        val callable = binding != null && binding.data.callableTargets.keys.any { place ->
            place.root == binding.place.root && place.path.take(binding.place.path.size) == binding.place.path
        }
        if (callable) {
            val reference = requireNotNull(StructuredBodyContexts.origin(site)?.constantReference)
            val resolver = (provider(site) as? StructuredSessionBindings)?.functionTargets(reference)
                ?: throw DeclarationBinding.Failure("LIB_BODY_UNSUPPORTED: Callable constant has no author session target resolver")
            StorageAccess.bindFunctionTargets(value, resolver)
        }
        value
    }

    fun type(site: ParserRuleContext, frame: Function? = Function.currFunction,
             localType: ((DeclarationId) -> MCFPPType?)? = null): MCFPPType? {
        val origin = StructuredBodyContexts.origin(site) ?: return null
        val syntax = origin.node as? TypeSyntax ?: return null
        val provider = requireNotNull(provider(site))
        fun resolve(node: TypeSyntax): MCFPPType {
            frame?.let { (provider as? StructuredSessionBindings)?.dependentType(node, StructuredBindings.frame(it)) }?.let { return it }
            if (node is TypeSyntax.Name && node.use is NameUse.Local)
                localType?.invoke(node.use.target)?.let { return it }
            val resolved = when (node) { is TypeSyntax.Name -> node.resolved; is TypeSyntax.Apply -> node.resolved; else -> null }
            resolved?.let { reference ->
                (frame?.let { (provider as? StructuredSessionBindings)?.boundType(reference, StructuredBindings.frame(it)) }
                    ?: (provider.type(reference) as? MCFPPType) ?: provider.builtinKey(reference)?.let(LibraryBuiltinTypes::restore))?.let { return it }
            }
            return when (node) {
                is TypeSyntax.Name -> {
                    val binding = provider.name(node.use, NameRole.TYPE).binding
                    when (binding) {
                        is MCFPPType -> binding
                        is StructuredNameBinding.Type -> binding.type
                        else -> {
                            val target = (node.use as? NameUse.Declaration)?.target
                                ?: (node.use as? NameUse.Local)?.target?.let { DeclarationReference.Local(it) }
                            ((provider as? StructuredSessionBindings)?.let { target?.let(it::declaration) } as? StructuredNameBinding.Type)?.type
                                ?: throw DeclarationBinding.Failure("LIB_TYPE_UNSUPPORTED: Type name ${node.use} has no author binding")
                        }
                    }
                }
                is TypeSyntax.Container -> when (node.kind) {
                    ContainerKind.LIST -> MCFPPListType(resolve(node.argument))
                    ContainerKind.IMMUTABLE_LIST -> MCFPPImmutableListType(resolve(node.argument))
                    ContainerKind.MAP -> MCFPPMapType(resolve(node.argument))
                    ContainerKind.DICT -> MCFPPDictType(resolve(node.argument))
                }
                is TypeSyntax.Vector -> {
                    if (node.dimensions > Int.MAX_VALUE) throw DeclarationBinding.Failure("Unsupported vector dimension")
                    MCFPPVectorType(node.dimensions.toInt())
                }
                is TypeSyntax.Union -> MCFPPUnionType(*node.alternatives.map(::resolve).toTypedArray())
                is TypeSyntax.Function -> MCFPPFunctionType(node.parameters.map(::resolve), resolve(node.result), when (node.execution) {
                    FunctionExecution.NORMAL -> MCFPPFunctionType.ExecutionCategory.NORMAL
                    FunctionExecution.PAUSED -> MCFPPFunctionType.ExecutionCategory.PAUSED
                    FunctionExecution.GENERATOR -> MCFPPFunctionType.ExecutionCategory.GENERATOR
                })
                is TypeSyntax.Void -> MCFPPPrivateType.Void
                is TypeSyntax.AnonymousData -> ((provider as? StructuredSessionBindings)?.declaration(DeclarationReference.Local(node.declaration)) as? StructuredNameBinding.Type)?.type
                    ?: throw DeclarationBinding.Failure("Anonymous type has no author declaration")
                else -> throw DeclarationBinding.Failure("LIB_TYPE_UNSUPPORTED: Dependent type requires its structural session resolver")
            }
        }
        return resolve(syntax)
    }
}
