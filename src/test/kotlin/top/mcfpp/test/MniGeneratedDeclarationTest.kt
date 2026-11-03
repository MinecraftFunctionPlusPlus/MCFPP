package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.io.MCFPPFile
import top.mcfpp.lib.*
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPPrivateType
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

/**
 * Real source -> generated member -> live call index.
 *
 * The generated body is built by the public declaration API, detached, finally validated and
 * lowered through the production lowerer, then published through the real cumulative compiler
 * journal. Source lookup is a fresh [CompilerBindingEnvironment.prepareMemberCall], never a
 * manually inserted candidate or a forged signature.
 */
class MniGeneratedDeclarationTest {
    private val site = SourceSpan("generated-members.mcfpp", 10, 20, 2, 1, 2, 11)

    private fun isolated(action: () -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val current = Function.currFunction
        val nullFunction = Function.nullFunction
        val extra = Function.extraFunction
        val forced = Function.forcedField
        val template = DataTemplate.currTemplate
        val ignoreStdLib = CompileSettings.ignoreStdLib
        try {
            Project.config = ProjectConfig(version = "26.3")
            Project.init()
            MCFPPFile.currFile = null
            Function.forcedField = null
            DataTemplate.currTemplate = null
            CompileSettings.ignoreStdLib = true
            action()
        } finally {
            CompileSettings.ignoreStdLib = ignoreStdLib
            namespaces.restore()
            project.restore()
            Function.currFunction = current
            Function.nullFunction = nullFunction
            Function.extraFunction = extra
            Function.forcedField = forced
            DataTemplate.currTemplate = template
        }
    }

    private fun parser(text: String, file: String = "generated-members.mcfpp") = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, file))))

    private fun memberSyntax(text: String): P.VarWithSelectorContext = parser(text).let { parser ->
        parser.varWithSelector().also {
            assertEquals(0, parser.numberOfSyntaxErrors, "member syntax: $text")
            assertEquals(Token.EOF, parser.currentToken.type)
        }
    }

    private fun compileProject() {
        MCFPPStringTest.readFromString(
            """
            data Parent {
                func touch() {}
            }
            data Box: Parent {
                var value: int
                func bump(amount: int): int { this.value = this.value + amount; return this.value; }
            }
            func main() {}
            """.trimIndent(),
            version = "26.3"
        )
        assertEquals(0, Project.errorCount)
    }

    private fun boxTemplate(): DataTemplate = GlobalScope.localNamespaces.values
        .flatMap { it.scope.template.values }.single { it.identifier == "Box" && it.hasCompletedDeclaration }

    private fun bindTemplate(template: DataTemplate, name: String): DataTemplateObject {
        val value = DataTemplateObject(template, name)
        val root = Place(top.mcfpp.analysis.SymbolId.fresh())
        val data = StoredData(
            root,
            NBTPath(StorageSource("generated:global")).memberIndex(name),
            layout = StorageLayout.Nbt("storage generated:global", name)
        )
        value.symbol = top.mcfpp.analysis.Symbol(root.root, name, value.type.typeId, true)
        value.storageBinding = StorageBinding(data, root, data.path)
        data.types[value.type.typeId] = value.type
        data.declarations[root] = value.symbol!!
        data.facts.write(
            root,
            ValueFacts(
                TypeKnowledge.Exact(value.type.typeId),
                ValueKnowledge.Unknown,
                ValueState.INITIALIZED
            )
        )
        data.types[MCFPPBaseType.Int.typeId] = MCFPPBaseType.Int
        data.facts.write(
            root.field("value"),
            ValueFacts(
                TypeKnowledge.Exact(MCFPPBaseType.Int.typeId),
                ValueKnowledge.Unknown,
                ValueState.INITIALIZED
            )
        )
        return value
    }

    @Suppress("UNCHECKED_CAST")
    private inner class Fixture : AutoCloseable {
        val box: DataTemplate
        val file: MCFPPFile
        val caller: Function
        val receiver: DataTemplateObject
        lateinit var ready: CompilerCallPreparationResult.Ready
        lateinit var catalog: SourceTypeCatalog
        lateinit var owner: TypeRef<Any>
        lateinit var int: TypeRef<IntT>
        lateinit var void: TypeRef<VoidT>
        lateinit var noArgs: FunctionSignature<VoidT>
        lateinit var oneInt: FunctionSignature<VoidT>
        lateinit var valueField: FieldRef<IntT>
        lateinit var view: TemplateView
        private val opened = arrayListOf<PreparedDeclarationEdits>()

        init {
            compileProject()
            box = boxTemplate()
            file = assertNotNull(box.declarationFile)
            caller = Function("caller", file.namespace.identifier, null).apply {
                declarationFile = file
                Function.currFunction = this
            }
            file.namespace.scope.functions.getOrPut(caller.identifier) { arrayListOf() }.add(caller)
            receiver = bindTemplate(box, "box")
            caller.scope.vars["box"] = receiver
            file.field.vars["box"] = receiver
            Function.currFunction = caller
            val syntax = memberSyntax("box.bump(3)")
            val preparation = CompilerBindingEnvironment.prepareMemberCall(syntax, caller)
            val rejection = (preparation as? CompilerCallPreparationResult.Rejected)?.diagnostics
                ?.joinToString("; ") { "${it.code} at ${it.source}: ${it.message}" }
                ?: "prepareMemberCall returned ${preparation::class.simpleName}"
            ready = assertIs<CompilerCallPreparationResult.Ready>(preparation, rejection)
            catalog = ready.environment.binding.types
            owner = catalog.ref(box.getType()) as TypeRef<Any>
            int = catalog.ref(MCFPPBaseType.Int) as TypeRef<IntT>
            void = catalog.ref(MCFPPPrivateType.Void) as TypeRef<VoidT>
            noArgs = FunctionSignature.of(emptyList(), void)
            oneInt = FunctionSignature.of(listOf(ParameterView.of("amount", int, ParamMode.VALUE)), void)
            valueField = catalog.field(owner, "value") as FieldRef<IntT>
            val fields = owner.fields().map { field ->
                val key = (field as ImmutableFieldRef<*>).key
                val declaration = box.scope.getVar(field.name())
                ImmutableFieldDeclarationView(
                    field,
                    catalog.graph.type(key.owner),
                    declaration?.let { DeclarationAccess.valueOf(it.accessModifier.name) } ?: DeclarationAccess.PUBLIC,
                    declaration?.nullable ?: false
                )
            }
            view = ImmutableTemplateView(owner, fields, emptyList(), emptyList(), site)
        }

        fun healProcessor(): TemplateProcessor = TemplateProcessor { ctx, template ->
            assertSame(owner, template.type())
            val reset = ctx.declarations().method(owner, "heal", noArgs) { body ->
                val b = body.builder()
                b.assign(b.fieldRef(body.self(), valueField), b.intLiteral(7))
            }
            val write = ctx.declarations().method(owner, "heal", oneInt) { body ->
                @Suppress("UNCHECKED_CAST")
                val amount = body.parameters().single() as Value<IntT>
                val b = body.builder()
                b.assign(b.fieldRef(body.self(), valueField), amount.expr())
            }
            ctx.declarations().edits(listOf(reset, write))
        }

        fun install(processor: TemplateProcessor): Installed {
            Function.currFunction = caller
            val backend = CompilerCommitBackend()
            val validator = DeclarationEditValidator(
                ExpansionSite(site, "generated-declaration-test"),
                ExpansionBudget(),
                CompilerSourceBindings.expansionEnvironment(
                    ready.environment,
                    namespace = file.namespace.identifier
                ),
                backend,
                view
            )
            val prepared = validator.prepare(processor)
            opened += prepared
            Function.currFunction = caller
            val bindings = CompilerSourceBindings.capture(ready.environment, prepared.session)
            backend.attach(bindings)
            return Installed(prepared, bindings, GeneratedDeclarationCompiler(prepared, bindings))
        }

        fun prepare(text: String): CompilerCallPreparationResult.Ready {
            Function.currFunction = caller
            val preparation = CompilerBindingEnvironment.prepareMemberCall(memberSyntax(text), caller)
            val rejection = (preparation as? CompilerCallPreparationResult.Rejected)?.diagnostics
                ?.joinToString("; ") { "${it.code} at ${it.source}: ${it.message}" }
                ?: "prepareMemberCall returned ${preparation::class.simpleName}"
            return assertIs<CompilerCallPreparationResult.Ready>(preparation, rejection)
        }

        override fun close() {
            opened.forEach(PreparedDeclarationEdits::close)
        }
    }

    private class Installed(
        val prepared: PreparedDeclarationEdits,
        val bindings: CompilerSourceBindings,
        val compiler: GeneratedDeclarationCompiler
    ) : AutoCloseable {
        override fun close() = prepared.close()
    }

    @Test
    fun generatedBodiesAreLoweredPublishedAndVisibleToRealSourceLookup() = isolated {
        Fixture().use { f ->
            Function.currFunction = f.caller
            f.install(f.healProcessor()).use { installed ->
                val commit = installed.compiler.compile()
                assertEquals(2, commit.methods.size)
                val candidates = f.box.scope.getFunctionCandidates("heal")
                assertEquals(2, candidates.size, "Both generated overloads enter the common lookup index")
                commit.methods.forEach { method ->
                    assertTrue(method.function.bodyCompiled)
                    assertTrue(method.function.commands.isNotEmpty(), "The generated body must emit real commands")
                    assertTrue(method.plan.blocks.isNotEmpty(), "The generated body must be lowered to a plan")
                    assertTrue(candidates.any { it === method.function })
                }

                // The self root and the field leaf are separate levels: `self` is still Box while
                // the applied field step is int. The physical self slot keeps the original ABI.
                val noArgMethod = commit.methods.single { it.function.normalParams.isEmpty() }
                val self = noArgMethod.substitutions.values.single()
                assertEquals(
                    (f.owner as ImmutableTypeRef<*>).key,
                    self.type.key,
                    "The generated self root must keep its declared owner type"
                )
                assertTrue(
                    self.address.orEmpty().endsWith("stack_frame[0].this"),
                    "The generated self must address the original frame slot: ${self.address}"
                )
                val projected = noArgMethod.plan.blocks.flatMap { it.operations }
                    .filterIsInstance<PlanOperation.CaptureLocation>().single()
                assertEquals(
                    (f.int as ImmutableTypeRef<*>).key,
                    projected.location.type.key,
                    "The field projection must carry the selected field type, not the root type"
                )
                val field = assertIs<PlanLocationStep.Field>(projected.location.steps.single())
                assertEquals("value", field.field.name)
                assertTrue(
                    field.field.key in assertNotNull(noArgMethod.validated.environment.fieldCapabilities),
                    "The private self field write must stay inside the checked declaration capability set"
                )

                val oneArgMethod = commit.methods.single { it.function.normalParams.size == 1 }
                val parameter = oneArgMethod.substitutions.values.single {
                    it.type.key == (f.int as ImmutableTypeRef<*>).key
                }
                assertTrue(
                    parameter.address.orEmpty().endsWith("stack_frame[0].amount"),
                    "The generated parameter must address its original frame slot: ${parameter.address}"
                )

                val noArg = f.prepare("box.heal()")
                val oneArg = f.prepare("box.heal(3)")
                assertEquals("heal", noArg.call.target.name)
                assertEquals(0, noArg.call.arguments.size)
                assertEquals("heal", oneArg.call.target.name)
                assertEquals(1, oneArg.call.arguments.size)
                assertTrue(
                    oneArg.environment.compilerFunctions.values.any { function ->
                        commit.methods.any { it.function === function }
                    },
                    "Fresh source preparation must project the generated canonical Function"
                )
                assertEquals(0, Project.errorCount)

                // Exercise the ordinary member-call protocol, not only the lookup table.
                val generated = commit.methods.single { it.function.normalParams.isEmpty() }.function
                Function.currFunction = f.caller
                val before = f.caller.commands.size
                generated.invokeComplete(linkedMapOf(), f.receiver)
                assertTrue(
                    f.caller.commands.drop(before).any { it.analyze() == "function ${generated.namespaceID}" },
                    "The generated member must be callable through the real Function entry"
                )
                assertEquals(0, Project.errorCount)
            }
        }
    }

    @Test
    fun inheritedSignatureConflictIsRejectedBeforeAnyGeneratedMemberIsPublished() = isolated {
        Fixture().use { f ->
            Function.currFunction = f.caller
            val before = f.box.scope.functions.mapValues { it.value.toList() }
            f.install { ctx, _ ->
                ctx.declarations().edits(listOf(ctx.declarations().method(f.owner, "touch", f.noArgs) { }))
            }.use { installed ->
                val failure = assertFailsWith<CodeModelException> { installed.compiler.compile() }
                assertTrue(
                    failure.message.orEmpty().contains("conflicts with an existing signature"),
                    failure.message.orEmpty()
                )
                assertTrue(f.box.scope.getFunctionCandidates("touch").none { it.owner === f.box })
                assertEquals(before, f.box.scope.functions.mapValues { it.value.toList() })
                assertEquals(0, Project.errorCount)
            }
        }
    }

    @Test
    fun failureAfterTheFirstMemberIsInstalledRollsBackRegistrationAndCommands() = isolated {
        Fixture().use { f ->
            Function.currFunction = f.caller
            f.install { ctx, _ ->
                val first = ctx.declarations().method(f.owner, "first", f.noArgs) { body ->
                    val b = body.builder()
                    b.assign(b.fieldRef(body.self(), f.valueField), b.intLiteral(1))
                }
                val second = ctx.declarations().method(f.owner, "second", f.noArgs) { body ->
                    val b = body.builder()
                    b.assign(b.fieldRef(body.self(), f.valueField), b.intLiteral(2))
                }
                ctx.declarations().edits(listOf(first, second))
            }.use { installed ->
                val detached = installed.compiler.detach()
                val boxBefore = f.box.scope.functions.mapValues { it.value.toList() }
                val namespace = GlobalScope.localNamespaces.getValue(f.file.namespace.identifier)
                val namespaceBefore = namespace.scope.functions.mapValues { it.value.toList() }

                // Simulate a declaration that appeared between detach and publication: the second
                // member now claims the first member's live signature. The first one is still
                // installed and emitted before the second check fails.
                detached[1].function.identifier = detached[0].function.identifier
                val failure = assertFailsWith<ExpansionFailure> { installed.compiler.commit(detached) }
                assertTrue(
                    failure.message.orEmpty().contains("conflicts with an existing signature"),
                    failure.message.orEmpty()
                )

                assertTrue(
                    f.box.scope.functions.values.flatten().none {
                        it === detached[0].function || it === detached[1].function
                    }
                )
                assertEquals(boxBefore, f.box.scope.functions.mapValues { it.value.toList() })
                assertEquals(
                    namespaceBefore,
                    namespace.scope.functions.mapValues { it.value.toList() },
                    "Lowered block functions and the first member must not survive the failed publication"
                )
                assertEquals(0, Project.errorCount)
            }
        }
    }
}
