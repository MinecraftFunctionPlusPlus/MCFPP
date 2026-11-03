package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.type.*
import kotlin.test.*
import kotlin.test.Test

class MniDeclarationEditTest {
    private val site = SourceSpan("declarations.mcfpp", 20, 40, 2, 1, 2, 21)

    /** Uses the production canonical source projection, not independently forged type handles. */
    private inner class Fixture {
        val base = DataTemplate("Base", "declaration.protocol")
        val declaration = DataTemplate("Owner", "declaration.protocol").apply { extends(base) }
        val alien = DataTemplate("Other", "declaration.protocol")
        val sourceOwner = declaration.getType()
        val sourceBase = base.getType()
        val sourceAlien = alien.getType()
        val declarations = listOf(
            SourceFieldDeclaration(SourceFieldId(sourceOwner.typeId, "hp"), sourceOwner.typeId, "hp", MCFPPBaseType.Int.typeId, true, site),
            SourceFieldDeclaration(SourceFieldId(sourceOwner.typeId, "fixed"), sourceOwner.typeId, "fixed", MCFPPBaseType.Int.typeId, false, site),
            SourceFieldDeclaration(SourceFieldId(sourceOwner.typeId, "compilerSecret"), sourceOwner.typeId, "compilerSecret", MCFPPBaseType.Int.typeId, true, site),
            SourceFieldDeclaration(SourceFieldId(sourceBase.typeId, "secret"), sourceBase.typeId, "secret", MCFPPBaseType.Int.typeId, true, site),
            SourceFieldDeclaration(SourceFieldId(sourceBase.typeId, "inherited"), sourceBase.typeId, "inherited", MCFPPBaseType.Int.typeId, true, site),
            SourceFieldDeclaration(SourceFieldId(sourceAlien.typeId, "foreign"), sourceAlien.typeId, "foreign", MCFPPBaseType.Int.typeId, true, site)
        )
        val sourceTypes = SourceTypeCatalog.preparedDomain(
            listOf(
                sourceOwner,
                sourceBase,
                sourceAlien,
                MCFPPBaseType.Int,
                MCFPPBaseType.Bool,
                MCFPPBaseType.Float,
                MCFPPBaseType.String,
                MCFPPPrivateType.Void
            )
        )
        val catalog = SourceTypeCatalog.snapshot(
            sourceTypes,
            declarations,
            declarations.flatMap { field ->
                listOf(SourceFieldView(field.owner, field.declaration)) +
                    if (field.owner == sourceBase.typeId) listOf(SourceFieldView(sourceOwner.typeId, field.declaration)) else emptyList()
            }
        )
        @Suppress("UNCHECKED_CAST")
        val owner = catalog.ref(sourceOwner) as TypeRef<Any>
        val parent = catalog.ref(sourceBase)
        @Suppress("UNCHECKED_CAST")
        val int = catalog.ref(MCFPPBaseType.Int) as TypeRef<IntT>
        @Suppress("UNCHECKED_CAST")
        val bool = catalog.ref(MCFPPBaseType.Bool) as TypeRef<BoolT>
        @Suppress("UNCHECKED_CAST")
        val void = catalog.ref(MCFPPPrivateType.Void) as TypeRef<VoidT>
        val noArgs = FunctionSignature.of(emptyList(), void)
        val budget = ExpansionBudget()
        var publishes = 0
        // Publication is outside D_PROTOCOL. An accidental publication must fail this test.
        val backend = CompilerTransactionBackend {
            publishes++
            error("Detached declaration validation attempted publication")
        }
        val types = object : TypeService {
            override fun intType(): TypeRef<IntT> = int
            override fun boolType(): TypeRef<BoolT> = bool
            @Suppress("UNCHECKED_CAST")
            override fun floatType(): TypeRef<FloatT> = catalog.ref(MCFPPBaseType.Float) as TypeRef<FloatT>
            @Suppress("UNCHECKED_CAST")
            override fun stringType(): TypeRef<StringT> = catalog.ref(MCFPPBaseType.String) as TypeRef<StringT>
            override fun voidType(): TypeRef<VoidT> = void
            override fun lookup(name: String): TypeRef<*> = catalog.resolve(name)
                ?: throw CodeModelException(site, "Unknown type '$name' in completed declaration catalog")
            override fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*> = throw CodeModelException(site, "No accessible function '$name' in this declaration snapshot")
        }
        val environment = ExpansionEnvironment(types)
        fun field(name: String): FieldRef<IntT> {
            @Suppress("UNCHECKED_CAST")
            return catalog.field(owner, name) as FieldRef<IntT>
        }
        fun view(methods: List<MethodView> = emptyList(), annotations: List<AnnotationView> = emptyList()): TemplateView = ImmutableTemplateView(
            owner,
            owner.fields().map { field ->
                val key = (field as ImmutableFieldRef<*>).key
                ImmutableFieldDeclarationView(
                    field,
                    catalog.graph.type(key.owner),
                    when (field.name()) {
                        "secret", "hp" -> DeclarationAccess.PRIVATE
                        "compilerSecret" -> DeclarationAccess.COMPILE_PRIVATE
                        "inherited" -> DeclarationAccess.PROTECTED
                        else -> DeclarationAccess.PUBLIC
                    },
                    false,
                    listOf(ImmutableAnnotationView("fixture:Field", mapOf("name" to ConstantValue.Text(field.name())), site))
                )
            },
            methods,
            annotations,
            site
        )
        fun method(
            name: String,
            owner: TypeRef<*> = this.owner,
            result: TypeRef<*> = int,
            access: DeclarationAccess = DeclarationAccess.PUBLIC,
            readonly: List<ParameterView> = emptyList()
        ) = ImmutableMethodView(
            FunctionKey((this.owner as ImmutableTypeRef<*>).key.universe, "${owner.name()}/$name/${readonly.size}"),
            name, owner, FunctionSignature.of(emptyList(), result), readonly, access,
            abstractMember = true, finalMember = true,
            annotations = listOf(ImmutableAnnotationView("fixture:Method", emptyMap(), site)), span = site
        )
        fun validator(view: TemplateView = view(), whitelist: Map<String, DeclarationMetadataRule> = emptyMap()) = DeclarationEditValidator(ExpansionSite(site, "processor:fixture"), budget, environment, backend, view, whitelist)
        fun rejects(
            message: String,
            view: TemplateView = view(),
            whitelist: Map<String, DeclarationMetadataRule> = emptyMap(),
            body: (DeclarationContext, TemplateView) -> DeclarationEdits
        ) {
            validator(view, whitelist).use { validator ->
                val failure = assertFailsWith<ExpansionFailure> { validator.prepare(TemplateProcessor(body)) }
                assertEquals(site, failure.diagnostic.source)
                assertContains(failure.diagnostic.message, message)
                assertEquals(0, budget.depth)
                assertEquals(0, publishes)
            }
        }
    }

    private fun fixture(): Fixture {
        Project.init()
        return Fixture()
    }

    @Test fun completedViewProjectsCanonicalFieldsMethodsAnnotationsAndSourcesWithoutMutableCollections() {
        val f = fixture()
        val annotations = arrayListOf<AnnotationView>(ImmutableAnnotationView("fixture:Owner", mapOf("enabled" to ConstantValue.Bool(true)), site))
        val members = arrayListOf<MethodView>(f.method("existing", readonly = listOf(ParameterView.of("T", f.int, ParamMode.CONST))))
        val view = f.view(members, annotations)
        members.clear()
        annotations.clear()
        assertEquals(5, view.fields().size)
        assertSame(f.field("hp"), view.fields().single { it.name() == "hp" })
        assertEquals(DeclarationAccess.PRIVATE, view.fieldDeclarations().single { it.field().name() == "secret" }.access())
        assertEquals("fixture:Field", view.fieldDeclarations().first().annotations().single().name())
        val member = view.methods().single()
        assertTrue(member.isAbstract())
        assertTrue(member.isFinal())
        assertFalse(member.isStatic())
        assertEquals("T", member.readonlyParameters().single().name())
        assertEquals("fixture:Method", member.annotations().single().name())
        assertEquals(site, member.source())
        assertEquals(site, view.source())
        assertEquals(ConstantValue.Bool(true), view.annotations().single().arguments()["enabled"])
        assertFailsWith<UnsupportedOperationException> { (view.fields() as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (view.methods() as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (view.annotations().single().arguments() as MutableMap<*, *>).clear() }
        assertEquals(0, Project.errorCount)
    }

    @Test fun generatedBodiesHaveExplicitSelfValueParametersSeparateScopesAndMethodResultRegions() {
        val f = fixture()
        val contexts = arrayListOf<MethodBodyContext<Any>>()
        var callbacks = 0
        f.validator().use { validator ->
            validator.prepare { ctx, template ->
                assertSame(f.owner, template.type())
                val reset = ctx.declarations().method(f.owner, "reset", f.noArgs) { body ->
                    callbacks++
                    contexts += body
                    val b = body.builder()
                    b.assign(b.fieldRef(body.self(), f.field("hp")), b.intLiteral(0))
                    b.ifThen(b.boolLiteral(true)) { child ->
                        child.assign(child.fieldRef(body.self(), f.field("inherited")), child.intLiteral(1))
                    }
                }
                val signature = FunctionSignature.of(listOf(ParameterView.of("value", f.int, ParamMode.VALUE)), f.int)
                val first = ctx.declarations().method(f.owner, "first", signature) { body ->
                    callbacks++
                    contexts += body
                    assertEquals(1, body.parameters().size)
                    @Suppress("UNCHECKED_CAST")
                    val parameter = body.parameters().single() as Value<IntT>
                    body.builder().yield(parameter.expr())
                }
                val second = ctx.declarations().method(f.owner, "second", FunctionSignature.of(emptyList(), f.int)) { body ->
                    callbacks++
                    contexts += body
                    val expression = ctx.quote().expression(f.int) { it.yield(it.intLiteral(2)) }
                    body.builder().yield(expression)
                }
                ctx.declarations().edits(listOf(reset, first, second))
            }.use { plan ->
                assertEquals(3, callbacks)
                assertEquals(3, plan.methods.map { (it.edit.body() as OwnedCode).metadata.scopeId }.distinct().size)
                val results = plan.methods.mapNotNull { it.code.environment.resultRegion }
                assertEquals(2, results.map { it.id }.distinct().size)
                val reset = plan.methods.first()
                val assignment = assertIs<StmtNode.Assign>((reset.edit.body().statements().first() as ImmutableStmt).node)
                assertEquals((reset.self as ImmutableRef<*>).location.root, (assignment.destination as ImmutableRef<*>).location.root)
                plan.methods.forEach { method ->
                    assertSame(method.self, method.code.environment.externalBindings.first())
                    CodeValidator().validate(method.code.code, method.code.environment, ValidationPhase.FINAL)
                }
                assertEquals(0, f.publishes)
                contexts.forEach { assertFailsWith<CodeModelException> { it.self() } }
            }
        }
        assertEquals(0, f.budget.depth)
        assertEquals(0, Project.errorCount)
    }

    @Test fun canonicalOwnerCannotBeReplacedByAnotherTypeOrAnotherSnapshotOfTheSameDeclaration() {
        val f = fixture()
        val foreign = SourceTypeCatalog.snapshot(f.sourceTypes).ref(f.sourceOwner)
        listOf(f.catalog.ref(f.sourceAlien), foreign).forEach { owner ->
            f.rejects(if (owner === foreign) "canonical domain" else "current canonical template") { ctx, _ ->
                ctx.declarations().edits(listOf(ctx.declarations().method(owner, "bad", f.noArgs) { }))
            }
        }
    }

    @Test fun duplicateSignaturesCannotBeDistinguishedByReturnTypeAndFailurePublishesNoPartialEdit() {
        val f = fixture()
        f.rejects("Duplicate generated method signature") { ctx, _ ->
            ctx.resources().reserve(ResourceKind.OBJECTIVE, "pending", ResourceData.of(ConstantValue.Record(mapOf("criteria" to ConstantValue.Text("dummy")))))
            val first = ctx.declarations().method(f.owner, "same", FunctionSignature.of(emptyList(), f.int)) { it.builder().yield(it.builder().intLiteral(1)) }
            val second = ctx.declarations().method(f.owner, "same", FunctionSignature.of(emptyList(), f.bool)) { it.builder().yield(it.builder().boolLiteral(true)) }
            ctx.declarations().edits(listOf(first, second))
        }
    }

    @Test fun completedLocalAndVisibleInheritedSignatureSlotsRejectReplacementWhileParentPrivateDoesNot() {
        val f = fixture()
        listOf(f.method("occupied"), f.method("occupied", f.parent)).forEach { existing ->
            f.rejects("conflicts with an existing", f.view(listOf(existing))) { ctx, _ ->
                ctx.declarations().edits(listOf(ctx.declarations().method(f.owner, "occupied", f.noArgs) { }))
            }
        }
        f.validator(f.view(listOf(f.method("occupied", f.parent, access = DeclarationAccess.PRIVATE)))).use { validator ->
            validator.prepare { ctx, _ -> ctx.declarations().edits(listOf(ctx.declarations().method(f.owner, "occupied", f.noArgs) { })) }.use {
                assertEquals(1, it.methods.size)
            }
        }
    }

    @Test fun differentParameterTypesFormRealOverloadsButReadonlyParentArityDoesNotCreateAFalseCollision() {
        val f = fixture()
        val existing = f.method("reset", readonly = listOf(ParameterView.of("T", f.int, ParamMode.CONST)))
        f.validator(f.view(listOf(existing))).use { validator ->
            validator.prepare { ctx, _ ->
                val a = ctx.declarations().method(f.owner, "reset", f.noArgs) { }
                val b = ctx.declarations().method(f.owner, "reset", FunctionSignature.of(listOf(ParameterView.of("n", f.int, ParamMode.VALUE)), f.void)) { }
                val c = ctx.declarations().method(f.owner, "reset", FunctionSignature.of(listOf(ParameterView.of("b", f.bool, ParamMode.VALUE)), f.void)) { }
                ctx.declarations().edits(listOf(a, b, c))
            }.use { assertEquals(3, it.methods.size) }
        }
    }

    @Test fun generatedSelfFieldsRespectInheritedPrivateReadonlyAndCanonicalMembership() {
        val f = fixture()
        listOf(
            "secret" to "Field capability is not authorized",
            "compilerSecret" to "Field capability is not authorized",
            "fixed" to "read-only",
            "foreign" to "does not belong"
        ).forEach { (name, message) ->
            f.rejects(message) { ctx, _ ->
                val field = if (name == "foreign") {
                    @Suppress("UNCHECKED_CAST")
                    (f.catalog.field(f.catalog.ref(f.sourceAlien), name) as FieldRef<IntT>)
                } else f.field(name)
                val edit = ctx.declarations().method(f.owner, "bad", f.noArgs) { body ->
                    body.builder().assign(body.builder().fieldRef(body.self(), field), body.builder().intLiteral(1))
                }
                ctx.declarations().edits(listOf(edit))
            }
        }
    }

    @Test fun selfAndParametersCannotCrossMethodScopes() {
        val f = fixture()
        for (captureSelf in listOf(true, false)) f.rejects("outside its lexical scope") { ctx, _ ->
            var self: Ref<Any>? = null
            var parameter: Value<IntT>? = null
            val signature = FunctionSignature.of(listOf(ParameterView.of("p", f.int, ParamMode.VALUE)), f.void)
            val first = ctx.declarations().method(f.owner, "first", signature) { body ->
                self = body.self()
                @Suppress("UNCHECKED_CAST")
                val value = body.parameters().single() as Value<IntT>
                parameter = value
            }
            val second = ctx.declarations().method(f.owner, "second", f.noArgs) { body ->
                if (captureSelf) body.builder().evaluate(self!!.load()) else body.builder().evaluate(parameter!!.expr())
            }
            ctx.declarations().edits(listOf(first, second))
        }
    }

    @Test fun selfCannotBeCapturedByAnInternalHelper() {
        val f = fixture()
        f.rejects("outside its lexical scope") { ctx, _ ->
            val edit = ctx.declarations().method(f.owner, "bad", f.noArgs) { body ->
                val self = body.self()
                ctx.helpers().define("capture", f.noArgs) { b, _ -> b.evaluate(self.load()) }
            }
            ctx.declarations().edits(listOf(edit))
        }
    }

    @Test fun internalDeclarationsAreActualSessionOwnedPrevalidatedHelpersAndCallbacksAreConsumedOnce() {
        val f = fixture()
        var calls = 0
        f.validator().use { validator ->
            validator.prepare { ctx, _ ->
                val helper = ctx.declarations().internalFunction("implementation", f.noArgs) { _, _ -> calls++ }
                val member = ctx.declarations().method(f.owner, "invoke", f.noArgs) { body ->
                    body.builder().callVoid(helper.function(), emptyList())
                }
                ctx.declarations().edits(listOf(helper, member))
            }.use { plan ->
                assertEquals(1, calls)
                assertEquals(1, plan.helpers.size)
                assertSame(plan.helpers.single().function, plan.edits.all().filterIsInstance<InternalDeclarationEdit<*>>().single().function())
                assertIs<Block>(plan.helpers.single().code.code)
                assertSame(plan.session.arena, plan.helpers.single().code.environment.arena)
                CodeValidator().validate(plan.methods.single().code.code, plan.methods.single().code.environment, ValidationPhase.FINAL)
                assertEquals(0, f.publishes)
            }
        }
    }

    @Test fun compilerWhitelistRequiresExactImmutableMetadataShapeAndDoesNotReplayTheProcessor() {
        val f = fixture()
        val rules = linkedMapOf("fixture:Generated" to DeclarationMetadataRule(mapOf("label" to DeclarationMetadataShape.Scalar(ConstantShape.STRING))))
        val args = linkedMapOf<String, ConstantValue>("label" to ConstantValue.Text("before"))
        var calls = 0
        f.validator(whitelist = rules).use { validator ->
            rules.clear()
            validator.prepare { ctx, _ ->
                calls++
                val edit = ctx.declarations().metadata(f.owner, "fixture:Generated", args)
                args["label"] = ConstantValue.Text("after")
                ctx.declarations().edits(listOf(edit))
            }.use { plan ->
                assertEquals(1, calls)
                val metadata = plan.edits.all().filterIsInstance<MetadataEdit>().single().annotation()
                assertEquals(ConstantValue.Text("before"), metadata.arguments()["label"])
                assertFailsWith<UnsupportedOperationException> { (metadata.arguments() as MutableMap<*, *>).clear() }
            }
        }
        val policy = mapOf("fixture:Generated" to DeclarationMetadataRule(mapOf("label" to DeclarationMetadataShape.Scalar(ConstantShape.STRING))))
        for (values in listOf(
            emptyMap(),
            mapOf("label" to ConstantValue.Bool(true)),
            mapOf("label" to ConstantValue.Text("x"), "extra" to ConstantValue.Text("y"))
        )) {
            f.rejects("whitelisted shape", whitelist = policy) { ctx, _ ->
                ctx.declarations().edits(listOf(ctx.declarations().metadata(f.owner, "fixture:Generated", values)))
            }
        }
        f.rejects("not whitelisted") { ctx, _ -> ctx.declarations().edits(listOf(ctx.declarations().metadata(f.owner, "fixture:Unknown", emptyMap()))) }
    }

    @Test fun nonrepeatableMetadataCannotDuplicateCompletedAnnotationsOrAnotherEdit() {
        val f = fixture()
        val policy = mapOf("fixture:Marker" to DeclarationMetadataRule(emptyMap()))
        val existing = f.view(annotations = listOf(ImmutableAnnotationView("fixture:Marker", emptyMap(), site)))
        f.rejects("Duplicate annotation metadata", existing, policy) { ctx, _ ->
            ctx.declarations().edits(listOf(ctx.declarations().metadata(f.owner, "fixture:Marker", emptyMap())))
        }
        f.rejects("Duplicate annotation metadata", whitelist = policy) { ctx, _ ->
            ctx.declarations().edits(
                listOf(
                    ctx.declarations().metadata(f.owner, "fixture:Marker", emptyMap()),
                    ctx.declarations().metadata(f.owner, "fixture:Marker", emptyMap())
                )
            )
        }
    }

    @Test fun processorFailureDiscardsResourcesHelpersAndRevokesEveryRetainedConstructionCapability() {
        val f = fixture()
        var captured: DeclarationContext? = null
        var resource: ResourceRef? = null
        f.rejects("intentional processor failure") { ctx, _ ->
            captured = ctx
            resource = ctx.resources().reserve(ResourceKind.STORAGE, "pending", ResourceData.of(ConstantValue.Record(mapOf("initial" to ConstantValue.Integral(0)))))
            ctx.declarations().internalFunction("pending", f.noArgs) { _, _ -> }
            ctx.declarations().method(f.owner, "pending", f.noArgs) { }
            error("intentional processor failure")
        }
        assertFailsWith<CodeModelException> { captured!!.resources() }
        assertFailsWith<ExpansionFailure> { resource!!.name() }
        assertEquals(0, f.publishes)
        assertEquals(0, Project.errorCount)
    }

    @Test fun editsAreSingleInvocationCapabilitiesAndCannotBeDuplicatedOrSilentlyOmitted() {
        val f = fixture()
        lateinit var foreign: DeclarationEdits
        f.validator().use { validator ->
            validator.prepare { ctx, _ -> ctx.declarations().edits(emptyList()).also { foreign = it } }.use { }
            assertFailsWith<IllegalStateException> { validator.prepare { _, _ -> foreign } }
        }
        f.rejects("another processor invocation") { _, _ -> foreign }
        f.rejects("returned twice") { ctx, _ ->
            val edit = ctx.declarations().method(f.owner, "once", f.noArgs) { }
            ctx.declarations().edits(listOf(edit, edit))
        }
        f.rejects("Every constructed declaration edit") { ctx, _ ->
            ctx.declarations().method(f.owner, "omitted", f.noArgs) { }
            ctx.declarations().edits(emptyList())
        }
    }

    @Test fun runtimeMethodsRejectMacroParameterConventionsAndMissingResultAtTheDeclarationSpan() {
        val f = fixture()
        for (mode in listOf(ParamMode.REF, ParamMode.CONST, ParamMode.INLINE)) f.rejects("only VALUE parameters") { ctx, _ ->
            val edit = ctx.declarations().method(f.owner, "bad", FunctionSignature.of(listOf(ParameterView.of("p", f.int, mode)), f.void)) { }
            ctx.declarations().edits(listOf(edit))
        }
        f.rejects("Every normal exit must yield") { ctx, _ ->
            ctx.declarations().edits(listOf(ctx.declarations().method(f.owner, "bad", FunctionSignature.of(emptyList(), f.int)) { }))
        }
    }

    @Test fun nestedMetadataChecksIntegerBoundsCanonicalTypeValuesAndExplicitRepeatability() {
        val f = fixture()
        val policy = mapOf(
            "fixture:Nested" to DeclarationMetadataRule(
                mapOf(
                    "entries" to DeclarationMetadataShape.Sequence(
                        DeclarationMetadataShape.Record(
                            mapOf(
                                "type" to DeclarationMetadataShape.Scalar(ConstantShape.TYPE),
                                "count" to DeclarationMetadataShape.Scalar(ConstantShape.INT32)
                            )
                        )
                    )
                ),
                repeatable = true
            )
        )
        fun arguments(type: TypeRef<*>, count: Long) = mapOf(
            "entries" to ConstantValue.Sequence(
                listOf(
                    ConstantValue.Record(mapOf("type" to ConstantValue.TypeValue(type), "count" to ConstantValue.Integral(count)))
                )
            )
        )
        f.validator(whitelist = policy).use { validator ->
            validator.prepare { ctx, _ ->
                ctx.declarations().edits(
                    listOf(
                        ctx.declarations().metadata(f.owner, "fixture:Nested", arguments(f.owner, 1)),
                        ctx.declarations().metadata(f.owner, "fixture:Nested", arguments(f.int, 2))
                    )
                )
            }.use { assertEquals(2, it.edits.all().size) }
        }
        val foreign = SourceTypeCatalog.snapshot(f.sourceTypes).ref(f.sourceOwner)
        for (args in listOf(arguments(foreign, 1), arguments(f.owner, Int.MAX_VALUE.toLong() + 1))) {
            f.rejects("whitelisted shape", whitelist = policy) { ctx, _ ->
                ctx.declarations().edits(listOf(ctx.declarations().metadata(f.owner, "fixture:Nested", args)))
            }
        }
    }

    @Test fun helperConstructionCannotMutateTheSuspendedOuterMethodBuilderOrHidePrivateFieldAccess() {
        val f = fixture()
        f.rejects("Use the active child builder") { ctx, _ ->
            val method = ctx.declarations().method(f.owner, "bad", f.noArgs) { body ->
                val outer = body.builder()
                ctx.helpers().define("nested", f.noArgs) { _, _ -> outer.evaluate(body.self().load()) }
            }
            ctx.declarations().edits(listOf(method))
        }
        f.rejects("Field capability is not authorized") { ctx, _ ->
            val signature = FunctionSignature.of(listOf(ParameterView.of("receiver", f.owner, ParamMode.VALUE)), f.void)
            val helper = ctx.declarations().internalFunction("hidden_read", signature) { builder, parameters ->
                @Suppress("UNCHECKED_CAST")
                val receiver = parameters.single() as Value<Any>
                builder.evaluate(builder.field(receiver.expr(), f.field("secret")))
            }
            ctx.declarations().edits(listOf(helper))
        }
    }

    @Test fun methodNamesMustBeExactLexerIdentifiersIncludingKeywordAndVectorTokenPriority() {
        val f = fixture()
        for (name in listOf("if", "this", "int", "Type", "ref", "vec", "vec3", "vec42", "valid trailing", "valid!", " valid", "valid/*comment*/")) {
            f.rejects("Invalid generated member name '$name'") { ctx, _ ->
                ctx.declarations().edits(listOf(ctx.declarations().method(f.owner, name, f.noArgs) { }))
            }
        }
    }

    @Test fun parameterNamesUseTheSameLexerIdentifierRulesAsMethodNames() {
        val f = fixture()
        for (name in listOf("if", "this", "int", "ref", "vec3", "vec42", "value!", "value ")) {
            f.rejects("Invalid generated member name '$name'") { ctx, _ ->
                val signature = FunctionSignature.of(listOf(ParameterView.of(name, f.int, ParamMode.VALUE)), f.void)
                ctx.declarations().edits(listOf(ctx.declarations().method(f.owner, "validName", signature) { }))
            }
        }
    }

    @Test fun unicodeMethodAndParameterIdentifiersBuildAndValidateRealMethodBodies() {
        val f = fixture()
        val names = listOf("entrée", "μέθοδος", "méthode١", "_entrée٢")
        f.validator().use { validator ->
            validator.prepare { ctx, _ ->
                val edits = names.map { name ->
                    val signature = FunctionSignature.of(listOf(ParameterView.of("entrée٢", f.int, ParamMode.VALUE)), f.int)
                    ctx.declarations().method(f.owner, name, signature) { body ->
                        @Suppress("UNCHECKED_CAST")
                        val parameter = body.parameters().single() as Value<IntT>
                        body.builder().yield(parameter.expr())
                    }
                }
                ctx.declarations().edits(edits)
            }.use { plan ->
                assertEquals(names, plan.methods.map { it.edit.name() })
                plan.methods.forEach { method ->
                    assertEquals("entrée٢", method.edit.signature().parameters().single().name())
                    CodeValidator().validate(method.code.code, method.code.environment, ValidationPhase.FINAL)
                }
                assertEquals(0, f.publishes)
            }
        }
        assertEquals(0, f.budget.depth)
        assertEquals(0, Project.errorCount)
    }
}
