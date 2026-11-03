package top.mcfpp.mni.expand

import top.mcfpp.Project
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.StorageLayout
import top.mcfpp.analysis.TypeKnowledge
import top.mcfpp.analysis.ValueFacts
import top.mcfpp.analysis.ValueKnowledge
import top.mcfpp.analysis.ValueState
import top.mcfpp.antlr.MCFPPFieldVisitor
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.lib.NBTPath
import top.mcfpp.mni.api.*
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.ObjectCompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import java.util.function.Consumer

/**
 * One detached generated member. Construction, final validation and lowering have already
 * happened; nothing is visible to source lookup until [GeneratedDeclarationCompiler.commit]
 * publishes the whole batch inside the production compiler journal.
 */
internal class DetachedGeneratedMethod internal constructor(
    val edit: MethodEdit,
    val template: DataTemplate,
    val function: Function,
    internal val validated: ValidatedCode,
    val plan: LoweringPlan,
    internal val substitutions: Map<SymbolId, CompilerPlanLocation>
)

/** One checked whitelist metadata fact, still unpublished until the batch commit. */
internal class DetachedGeneratedMetadata internal constructor(
    val edit: MetadataEdit,
    val template: DataTemplate,
    val repeatable: Boolean
)

/** Published generated members. The functions are canonical and already participate in lookup. */
internal class GeneratedDeclarationCommit(
    methods: List<DetachedGeneratedMethod>,
    metadata: List<DetachedGeneratedMetadata>
) {
    val methods: List<DetachedGeneratedMethod> = immutableList(methods)
    val metadata: List<DetachedGeneratedMetadata> = immutableList(metadata)
}

/**
 * Converts the read-only [PreparedDeclarationEdits] into real template member functions.
 *
 * The detached phase performs every canonical owner, inheritance/overload, final-body and
 * lowering check without publishing compiler state. The commit phase only registers those
 * already checked members and emits their detached plans. Registration and emission share one
 * [CompilerJournalParticipant]; the production backend wraps it in the single cumulative
 * declaration/command/namespace journal, so a failure after the first member has been installed
 * still restores the complete before state.
 *
 * This is the callable integration entry for the successor D_DECLARATION_STAGE. Annotation
 * metadata publication and the multi-file processor barrier remain the stage's responsibility.
 */
internal class GeneratedDeclarationCompiler internal constructor(
    private val prepared: PreparedDeclarationEdits,
    private val bindings: CompilerSourceBindings
) {
    private var detached: List<DetachedGeneratedMethod>? = null
    private var detachedMetadata: List<DetachedGeneratedMetadata>? = null

    init {
        require(prepared.session === bindings.session) {
            "Generated declarations require the compiler bindings captured from their own prepared session"
        }
    }

    fun compile(): GeneratedDeclarationCommit {
        val methods = detach()
        return commit(methods)
    }


    /**
     * Builds the detached declaration half of a generated instance member. This mirrors the
     * source member setup (owner scope, explicit parameter values and the `this` receiver bound to
     * the callee frame) but never registers the function or emits a command. The declaration
     * compiler publishes it only after the live inheritance/overload contract and body checks pass.
     */
    internal fun buildGeneratedMethodDeclaration(
        template: DataTemplate, name: String, returnType: MCFPPType, parameters: List<Pair<String, MCFPPType>>
    ): Function {
        val function = Function(name, template, null)
        function.accessModifier = AccessModifier.PUBLIC
        function.returnType = returnType
        parameters.forEach { (parameterName, type) -> function.appendNormalParam(type, parameterName) }
        function.declarationFile = template.declarationFile
        function.bodyCompiled = true
        function.buildParamVar()
        val receiver = (FunctionParam(template.getType(), "this", function).buildVar() as DataTemplateObject).apply {
            nbtPath = NBTPath.stack.intIndex(0).memberIndex("this")
            storageBinding = null
            StorageAccess.bindIncomingParameter(this)
        }
        function.scope.putVar("this", receiver, true)
        return function
    }

    /**
     * Step one: turn every permitted edit into a detached declaration and prove it can be lowered.
     * A failure here publishes nothing and therefore cannot leave a half member.
     */
    fun detach(): List<DetachedGeneratedMethod> {
        bindings.requireCurrent()
        if (prepared.methods.size != prepared.edits.all().count { it is MethodEdit })
            throw CodeModelException(
                prepared.edits.all().filterIsInstance<MethodEdit>().firstOrNull()?.source()
                    ?: SourceSpan.unknown(),
                "Every generated method edit must have one detached prepared body"
            )

        val result = arrayListOf<DetachedGeneratedMethod>()
        val claimed = linkedSetOf<Pair<String, List<TypeId>>>()
        prepared.methods.forEach { method ->
            val edit = method.edit
            val template = template(edit.owner(), edit.source())
            if (template is ObjectCompoundData)
                throw CodeModelException(edit.source(), "Generated instance methods require a data declaration, not an object template")
            if (!template.hasCompletedDeclaration)
                throw CodeModelException(edit.source(), "Generated member owner has not completed its declaration")

            val returnType = bindings.compilerType(edit.signature().returnType())
            val parameterTypes = edit.signature().parameters().map { bindings.compilerType(it.type()) }
            val function = buildGeneratedMethodDeclaration(
                template,
                edit.name(),
                returnType,
                edit.signature().parameters().mapIndexed { index, parameter ->
                    parameter.name() to parameterTypes[index]
                }
            )
            template.generatedMethodSlotConflict(function)?.let { existing ->
                throw CodeModelException(edit.source(), "Generated method '${edit.name()}' conflicts with an existing signature from ${existing.namespaceID}")
            }
            val identity = edit.name() to parameterTypes.map { it.typeId }
            if (!claimed.add(identity))
                throw CodeModelException(edit.source(), "Duplicate generated method signature '${edit.name()}'")

            // FINAL here is deliberate: a declaration body cannot enter the call index with a
            // residual macro/inline node or with an invalid result/field/scope proof.
            val final = CodeValidator().validate(
                method.code.code,
                method.code.environment,
                ValidationPhase.FINAL
            ) as ValidatedCode
            val ownerKey = (edit.owner() as ImmutableTypeRef<*>).key
            // The detached FINAL body carries the execution context it was actually built in.
            // The lowering target must name that exact context; substituting null would fail the
            // CodeLowerer guard for any body that requested a frame (for example score access).
            val context = (final.code as OwnedCode).metadata.executionContextId
            val target = LoweringTarget(
                FunctionKey(
                    ownerKey.universe,
                    "generated:${template.namespaceID}:${edit.name()}:${identity.second.joinToString(",")}"
                ),
                context,
                bindings.loweringTarget.domain
            )
            val plan = CodeLowerer().lower(final, target)
            bindings.requireDomain(plan.domain)
            result += DetachedGeneratedMethod(
                edit,
                template,
                function,
                final,
                plan,
                substitutions(method, edit)
            )
        }
        val metadata = prepared.edits.all().filterIsInstance<MetadataEdit>().map { edit ->
            val template = template(edit.owner(), edit.source())
            if (!template.hasCompletedDeclaration)
                throw CodeModelException(edit.source(), "Generated metadata owner has not completed its declaration")
            val rule = prepared.metadataPolicy[edit.annotation().name()]
                ?: throw CodeModelException(edit.source(), "Annotation metadata '${edit.annotation().name()}' is not whitelisted")
            DetachedGeneratedMetadata(edit, template, rule.repeatable)
        }
        val snapshot = immutableList(result)
        detached = snapshot
        detachedMetadata = immutableList(metadata)
        return snapshot
    }

    private fun template(owner: TypeRef<*>, source: SourceSpan): DataTemplate {
        val compilerType = bindings.compilerType(owner)
        return when (compilerType) {
            is MCFPPDataTemplateType -> compilerType.template
            else -> throw CodeModelException(source, "Generated member owner is not a data declaration")
        }
    }

    /**
     * Steps two and three: publish the checked signatures, emit the already lowered bodies and
     * expose them to ordinary source lookup. The whole batch is one journaled participant.
     */
    fun commit(methods: List<DetachedGeneratedMethod>): GeneratedDeclarationCommit {
        require(detached != null && detached == methods) {
            "Generated declarations can commit only the detached batch produced by the same compiler"
        }
        val metadata = requireNotNull(detachedMetadata)
        bindings.requireCurrent()
        val empty = prepared.session.context().quote().block(Consumer { })
        prepared.session.stage(empty)
        prepared.session.finalizeHelpers()
        val final = CodeValidator().validate(
            empty,
            prepared.session.validationEnvironment(),
            ValidationPhase.FINAL
        ) as ValidatedCode
        prepared.session.complete(final, Publication(methods, metadata))
        return GeneratedDeclarationCommit(methods, metadata)
    }

    /** Physical frame positions of the method's own `this` and explicit VALUE parameters. */
    private fun substitutions(
        method: PreparedMethodDeclaration,
        edit: MethodEdit
    ): Map<SymbolId, CompilerPlanLocation> {
        val external = method.code.environment.externalBindings
        if (external.size != method.parameters.size + 1)
            throw CodeModelException(edit.source(), "Generated method body lost its self/parameter bindings")
        val signature = edit.signature()
        if (method.parameters.size != signature.parameters().size)
            throw CodeModelException(edit.source(), "Generated method parameter bindings differ from its declared signature")
        if (!method.self.type().sameType(edit.owner()))
            throw CodeModelException(edit.source(), "Generated method self type differs from its declared owner")
        val result = linkedMapOf<SymbolId, CompilerPlanLocation>()
        val frameNames = linkedSetOf<String>()
        fun put(reference: Ref<*>, name: String, writable: Boolean) {
            if (!frameNames.add(name))
                throw CodeModelException(edit.source(), "Generated method has duplicate physical frame name '$name'")
            val root = when (reference) {
                is ImmutableRef<*> -> reference.location.root
                is ImmutableLocal<*> -> reference.location.root
                else -> throw CodeModelException(edit.source(), "Unsupported generated method binding '${reference.javaClass.name}'")
            }
            if (result.put(root, stackLocation(name, reference.type(), writable)) != null)
                throw CodeModelException(edit.source(), "Generated method has duplicate self/parameter identities")
        }
        put(method.self, "this", true)
        method.parameters.zip(signature.parameters()).forEach { (reference, parameter) ->
            // The substitution addresses the actual declared frame slot, not a positional guess.
            if (!reference.type().sameType(parameter.type()))
                throw CodeModelException(
                    edit.source(),
                    "Generated method parameter '${parameter.name()}' physical type differs from its declared signature"
                )
            put(reference, parameter.name(), false)
        }
        external.forEach { reference ->
            val root = when (reference) {
                is ImmutableRef<*> -> reference.location.root
                is ImmutableLocal<*> -> reference.location.root
                else -> throw CodeModelException(edit.source(), "Unsupported generated method capture '${reference.javaClass.name}'")
            }
            if (root !in result)
                throw CodeModelException(edit.source(), "Generated method body captured a value outside its self/parameter scope")
        }
        return immutableMap(result)
    }

    private fun stackLocation(name: String, type: TypeRef<*>, writable: Boolean): CompilerPlanLocation {
        val address = NBTPath.stack.intIndex(0).memberIndex(name).toCommandPart().analyze()
        return CompilerPlanLocation(
            address,
            StorageLayout.Nbt("mcfpp:system", address.substringAfter("storage mcfpp:system ")),
            PlanType.from(type),
            writable = writable
        )
    }

    /** Publication is the only mutation. It never re-runs a processor or a source visitor. */
    private inner class Publication(
        private val methods: List<DetachedGeneratedMethod>,
        private val metadata: List<DetachedGeneratedMetadata>
    ) : CompilerJournalParticipant {
        override val domains: Set<CompilerDomain> = CompilerDomain.entries.toSet()

        override fun prepare(): PreparedCompilerCommit {
            bindings.requireCurrent()
            val roots = arrayListOf<Any>(bindings.target)
            methods.forEach {
                roots += it.template
                roots += it.function
            }
            metadata.forEach { roots += it.template }
            return CompilerStateJournal.checkpoint(roots).prepare { publish() }
        }

        override fun generation(): (() -> Unit)? = { publish() }

        private fun publish() {
            methods.forEach { method ->
                val conflict = method.template.generatedMethodSlotConflict(method.function)
                if (conflict != null)
                    throw CodeModelException(method.edit.source(), "Generated method '${method.function.identifier}' conflicts with an existing signature from ${conflict.namespaceID}")
                if (!method.template.publishGeneratedMethod(method.function))
                    throw CodeModelException(method.edit.source(), "Generated method '${method.function.identifier}' is not a permitted new member slot")
                val errors = Project.errorCount
                initializeReturn(method.function)
                if (Project.errorCount != errors)
                    throw CodeModelException(method.edit.source(), "Generated method result slot could not be prepared")
                val reservation = prepared.session.reserveCompilerName("method_plan_${method.function.identifier}", "backend")
                val blocks = method.plan.blocks.associate { block ->
                    block.id to prepared.session.reserveCompilerName(
                        "method_block_${method.function.identifier}_${block.id.index}",
                        "block"
                    )
                }
                val returnSlot = if (method.function.returnType == MCFPPPrivateType.Void) null
                else NBTPath.stack.intIndex(0).memberIndex("return").toCommandPart().analyze()
                val emitter = CompilerPlanEmitter(
                    bindings,
                    method.plan,
                    reservation,
                    blocks,
                    method.function,
                    returnSlot,
                    method.substitutions
                )
                // The detached body was checked against this exact accessible-field set. Add it to
                // the emitter's source capabilities instead of widening the check or trusting any
                // field that happens to appear in the plan.
                val checkedFields = method.validated.environment.fieldCapabilities
                    ?: throw CodeModelException(method.edit.source(), "Generated method body lost its checked field capabilities")
                emitter.locations.authorize(checkedFields)
                emitter.generate()
                if (Project.errorCount != errors)
                    throw CodeModelException(method.edit.source(), "Generated method body reported compiler errors")
            }
            metadata.forEach { fact ->
                if (!fact.template.publishGeneratedMetadata(fact.edit.annotation(), fact.repeatable))
                    throw CodeModelException(
                        fact.edit.source(),
                        "Generated annotation metadata '${fact.edit.annotation().name()}' conflicts with a published fact"
                    )
            }
        }

        /** A lowered plan proves every reachable exit writes the result slot; publish that producer. */
        private fun initializeReturn(function: Function) {
            if (function.returnType == MCFPPPrivateType.Void) return
            val binding = StorageAccess.ensure(function.returnVar)
            binding.data.write(
                binding.place,
                ValueFacts(
                    TypeKnowledge.Exact(function.returnType.typeId),
                    ValueKnowledge.Unknown,
                    ValueState.INITIALIZED
                )
            )
        }
    }

    companion object {
        fun compile(prepared: PreparedDeclarationEdits, bindings: CompilerSourceBindings): GeneratedDeclarationCommit = GeneratedDeclarationCompiler(prepared, bindings).compile()
    }
}
