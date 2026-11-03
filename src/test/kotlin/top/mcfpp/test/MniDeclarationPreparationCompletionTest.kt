package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.*
import top.mcfpp.antlr.ReadonlyParamsDeclarationSyntax
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.DataTemplateParam
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.property.Property
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import top.mcfpp.util.LogProcessor
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

/** Exercises H06 through real preparation, registered host annotations and the Project barrier. */
class MniDeclarationPreparationCompletionTest {
    companion object {
        private var annotationCalls = 0
        private var failCompletion = false
        private var affectedReady: Function? = null
        private var affectedValue: MCInt? = null
        private var affectedReceiver: MCFPPListType? = null
        private var affectedFile: MCFPPFile? = null
    }

    class GenerateField : Annotation("GenerateField", "completion.actual") {
        override fun forFunction(function: Function) = error("Template annotation applied to a function")
        override fun forField(field: Var<*>) = error("Template annotation applied to a field")
        override fun forDataTemplate(data: DataTemplate) {
            annotationCalls++
            val generated = MCInt(23, "generated").apply { declaredParentTemplate = data }
            data.scope.putVar("generated", generated, true)
            data.scope.putProperty("generated", Property.buildSimpleProperty(generated).apply { declaredParentTemplate = data })
            if (failCompletion) {
                data.identifier = "failed_name"
                data.isFinal = true
                data.parent.clear()
                data.scope.parent.clear()
                affectedReady!!.identifier = "failed_ready"
                affectedReady!!.scope.putVar("escaped", MCInt(8, "escaped"), true)
                StorageAccess.initializeLiteral(affectedValue!!, CompilerValue.Integral(99))
                affectedReceiver!!.generic[0] = MCFPPBaseType.Bool
                affectedFile!!.namespace = Namespace("completion.replaced")
                affectedFile!!.unsolvedImports["completion.escaped"] = "*"
                GlobalScope.localNamespaces["completion.escaped"] = Namespace("completion.escaped")
                Function.currFunction.commands.add(Command("say failed annotation"))
                LogProcessor.error("registered completion annotation failed")
                error("completion failure after declaration effects")
            }
        }
    }

    private val source = SourceSpan("declaration-completion.mcfpp", 0, 1, 1, 1, 1, 2)
    private fun parser(text: String) = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, source.file))))

    private fun isolated(action: () -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val function = Function.currFunction
        val nullFunction = Function.nullFunction
        val extraFunction = Function.extraFunction
        val forced = Function.forcedField
        val template = DataTemplate.currTemplate
        try {
            Project.config = ProjectConfig(version = "26.3")
            Project.init()
            MCFPPFile.currFile = null
            DataTemplate.currTemplate = null
            Function.currFunction = Function("ambient", "completion.ambient", null).apply { declarationFile = null }
            Function.forcedField = null
            annotationCalls = 0
            failCompletion = false
            affectedReady = null
            affectedValue = null
            affectedReceiver = null
            affectedFile = null
            action()
        } finally {
            affectedReady = null
            affectedValue = null
            affectedReceiver = null
            affectedFile = null
            namespaces.restore()
            project.restore()
            Function.nullFunction = nullFunction
            Function.extraFunction = extraFunction
            Function.currFunction = function
            Function.forcedField = forced
            DataTemplate.currTemplate = template
        }
    }

    private fun file(namespace: String, link: Boolean = true): MCFPPFile {
        val owner = Namespace(namespace)
        GlobalScope.localNamespaces[namespace] = owner
        return MCFPPFile(owner).also { if (link) owner.scope.journalFileFields.add(it.field) }
    }

    private fun prototype(file: MCFPPFile): GenericDataTemplate {
        val parser = parser(
            """
            data Box<T>{
                var value:T;
                func identity(value:T):T{return value}
                constructor(value:T){var bodyOnly=17;}
            }
            """.trimIndent()
        )
        val declaration = parser.templateDeclaration()
        assertEquals(0, parser.numberOfSyntaxErrors)
        assertEquals(Token.EOF, parser.currentToken.type)
        val head = declaration.compoundDeclaration()
        return GenericDataTemplate(declaration.templateBody(), "Box", file.namespace.identifier).apply {
            declarationFile = file
            head.declarationName().readOnlyParams().readOnlyParameterList().readOnlyParameter().forEachIndexed { ordinal, parameter ->
                readOnlyParams.add(
                    DataTemplateParam(
                        ReadonlyParamsDeclarationSyntax.readonlyTypeName(parameter),
                        ReadonlyParamsDeclarationSyntax.readonlyParameterName(parameter),
                        MCFPPConcreteType.Type
                    )
                )
            }
            file.namespace.scope.template[identifier] = this
            file.namespace.scope.annotations["GenerateField"] = GenerateField::class.java
            recordDeclarationAnnotations(listOf(GenerateField()))
        }
    }

    private fun target(file: MCFPPFile, name: String, type: MCFPPType) = Function(name, file.namespace.identifier, null).apply {
        declarationFile = file
        normalParams.add(FunctionParam(type, "input", this))
    }

    private fun prepare(request: SignaturePreparationRequest): SignaturePreparationResult = SignaturePreparationSession().prepare(request)
    private fun pending(request: SignaturePreparationRequest): SignaturePreparationResult.NeedsCompletion {
        val result = prepare(request)
        return assertIs<SignaturePreparationResult.NeedsCompletion>(
            result,
            (result as? SignaturePreparationResult.Rejected)?.diagnostics?.joinToString { it.message }.orEmpty()
        )
    }
    private fun completed(result: DeclarationCompletionResult): DeclarationCompletionResult.Completed = assertIs<DeclarationCompletionResult.Completed>(
        result,
        (result as? DeclarationCompletionResult.Rejected)?.diagnostics?.joinToString { it.message }.orEmpty()
    )

    @Test fun purePreparationCompletesTheSameCanonicalShellOnceWithoutCompilingBodies() = portableCompletion { it }

    internal fun portableCompletion(roundTrip: (GenerateField) -> GenerateField) = isolated {
        val file = file("completion.actual")
        val prototype = prototype(file)
        prototype.annotations[0] = roundTrip(prototype.annotations.single() as GenerateField)
        assertEquals(0, annotationCalls, "Restoring a pending annotation must not complete its declaration")
        val target = target(file, "target", UnresolvedType("Box<int>"))
        val request = SignaturePreparationRequest(listOf(target), emptyList(), source)
        val ambient = Function.currFunction
        ambient.commands.add(Command("say before declaration completion"))
        val commands = ambient.commands.toList()
        val work = pending(request)
        val reservation = work.staged.headers.single()
        val shell = reservation.model
        assertEquals(0, annotationCalls)
        assertFalse(Project.templateDeclarationsReady)
        assertFalse(reservation.isPublished)
        assertTrue(shell.scope.allVars.isEmpty())
        assertTrue(shell.constructors.isEmpty())
        assertEquals(0, prototype.index)
        assertEquals(commands, ambient.commands.toList())

        val result = completed(CompilerDeclarationPreparation().complete(work.staged, work.requests))
        result.revision.requireCurrent()
        assertEquals(1, annotationCalls)
        assertEquals(HeaderPhase.COMPLETE, reservation.phase)
        assertTrue(shell.hasCompletedDeclaration)
        assertSame(shell, prototype.compiledTemplates[reservation.key])
        assertSame(MCFPPBaseType.Int, shell.scope.getVar("value")!!.type)
        assertEquals(23, assertIs<MCInt>(shell.scope.getVar("generated")).value)
        val method = shell.scope.functions.getValue("identity").single { it.owner === shell }
        val constructor = shell.constructors.single { it.ast != null }
        assertFalse(method.bodyCompiled)
        assertFalse(constructor.bodyCompiled)
        assertTrue(method.commands.isEmpty())
        assertTrue(constructor.commands.isEmpty())
        assertTrue(constructor.scope.allVars.none { it.identifier == "bodyOnly" })
        assertSame(shell, prototype.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Int))))
        assertIs<SignaturePreparationResult.Ready>(prepare(request))
        assertIs<DeclarationCompletionResult.Rejected>(CompilerDeclarationPreparation().complete(work.staged))
        assertEquals(1, annotationCalls)
        assertEquals(1, prototype.index)
        assertEquals(commands, ambient.commands.toList())
        assertEquals(0, Project.errorCount)
    }

    @Test fun aFailedRegisteredAnnotationRestoresTheWholeInputDomainAndFreshCompletionCanRetry() = isolated {
        val file = file("completion.actual")
        val prototype = prototype(file)
        val pendingCandidate = target(file, "pending", UnresolvedType("Box<int>"))
        val readyFile = MCFPPFile(Namespace("completion.detached"))
        val ready = target(readyFile, "ready", MCFPPBaseType.Int)
        val value = MCInt(7, "saved")
        ready.scope.putVar("saved", value, true)
        val receiver = MCFPPListType(MCFPPBaseType.Int)
        val request = SignaturePreparationRequest(
            listOf(pendingCandidate, ready),
            emptyList(),
            source,
            mapOf("IndependentReceiver" to receiver),
            ready
        )
        affectedReady = ready
        affectedValue = value
        affectedReceiver = receiver
        affectedFile = readyFile
        val ambient = Function.currFunction
        ambient.commands.add(Command("say original"))
        val commands = ambient.commands.toList()
        val namespace = readyFile.namespace
        val work = pending(request)
        val reservation = work.staged.headers.single()
        failCompletion = true

        val failure = assertIs<DeclarationCompletionResult.Rejected>(CompilerDeclarationPreparation().complete(work.staged))
        assertTrue(failure.diagnostics.any { "registered completion annotation failed" in it.message })
        assertEquals(1, annotationCalls)
        assertTrue(work.staged.consumed)
        assertFalse(reservation.isPublished)
        assertEquals(HeaderPhase.RESERVED, reservation.phase)
        assertEquals("<prepared>", reservation.model.identifier)
        assertFalse(reservation.model.isFinal)
        assertFalse(reservation.model.hasDeclarationCompletionFailure)
        assertTrue(reservation.model.scope.allVars.isEmpty())
        assertTrue(reservation.model.constructors.isEmpty())
        assertTrue(reservation.model.parent.isEmpty())
        assertTrue(prototype.compiledTemplates.isEmpty())
        assertEquals(0, prototype.index)
        assertEquals("ready", ready.identifier)
        assertNull(ready.scope.getVar("escaped"))
        assertEquals(7, value.value)
        assertSame(MCFPPBaseType.Int, receiver.generic.single())
        assertSame(namespace, readyFile.namespace)
        assertTrue(readyFile.unsolvedImports.isEmpty())
        assertFalse(GlobalScope.localNamespaces.containsKey("completion.escaped"))
        assertEquals(commands, ambient.commands.toList())
        assertSame(ambient, Function.currFunction)
        assertEquals(0, Project.errorCount)
        work.staged.domainStamps().forEach { it.requireCurrent() }

        failCompletion = false
        val retry = pending(request)
        val result = completed(CompilerDeclarationPreparation().complete(retry.staged))
        result.revision.requireCurrent()
        val shell = retry.staged.headers.single().model
        assertEquals(2, annotationCalls)
        assertEquals(1, shell.scope.allVars.count { it.identifier == "generated" })
        assertSame(shell, prototype.compiledTemplates.values.single())
        assertIs<SignaturePreparationResult.Ready>(prepare(request))
        assertEquals(2, annotationCalls)
        ready.identifier = "changed_after_completion"
        assertFailsWith<PreparationFactFailure> { result.revision.requireCurrent() }
    }

    @Test fun unresolvedAliasesCompleteInTheirOriginalSourceEnvironment() = isolated {
        val origin = file("completion.alias", link = false)
        val consumer = file("completion.consumer", link = false)
        val parser = parser("typealias Local as Chosen;")
        val syntax = parser.compilationUnit()
        assertEquals(0, parser.numberOfSyntaxErrors)
        Project.trees[origin] = syntax
        val alias = MCFPPTypeAliasType(syntax.typealiasDeclaration().single().type())
        origin.namespace.scope.putType("Chosen", alias)
        origin.field.putResolvedAlias("Local", MCFPPBaseType.Int)
        consumer.field.putResolvedAlias("Local", MCFPPBaseType.Bool)
        origin.namespace.scope.journalFileFields.add(origin.field)
        consumer.namespace.scope.journalFileFields.add(consumer.field)
        consumer.unsolvedImports[origin.namespace.identifier] = "*"
        val target = target(consumer, "target", UnresolvedType("completion.alias:Chosen"))
        val request = SignaturePreparationRequest(listOf(target), emptyList(), source)
        val work = pending(request)
        assertSame(alias, assertIs<CompletionTarget.Alias>(work.requests.single().target).alias)
        assertNull(alias.cachedTarget)
        MCFPPFile.currFile = consumer
        Project.currNamespace = consumer.namespace.identifier

        val result = completed(CompilerDeclarationPreparation().complete(work.staged))
        assertSame(MCFPPBaseType.Int, alias.cachedTarget)
        assertSame(consumer, MCFPPFile.currFile)
        assertEquals(consumer.namespace.identifier, Project.currNamespace)
        result.revision.requireCurrent()
        val ready = assertIs<SignaturePreparationResult.Ready>(prepare(request))
        assertSame(MCFPPBaseType.Int, ready.declarations.signatures.single().normal.single().type)
        assertEquals(0, Project.errorCount)
    }
}
