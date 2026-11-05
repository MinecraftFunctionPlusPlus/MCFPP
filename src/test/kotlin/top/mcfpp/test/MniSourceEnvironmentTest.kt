@file:Suppress("UNUSED_PARAMETER")

package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.*
import top.mcfpp.annotations.MNIMacro
import top.mcfpp.antlr.ReadonlyParamsDeclarationSyntax
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.DataTemplateParam
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.property.Property
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

/** Uses original compiler declarations and syntax through the production pre-eager entry. */
class MniSourceEnvironmentTest {
    companion object {
        private var hostCalls = 0
        private var annotations = 0
        @JvmStatic fun legacy(context: NativeCallContext) {
            hostCalls++
            error("native executed during source preparation")
        }
    }

    class Entries {
        companion object {
            @JvmStatic
            @MNIMacro(identifier = "kotlinMacro", normalParams = ["int"], returnType = "int")
            fun expression(context: MacroContext, value: Value<IntT>): Expr<IntT> {
                hostCalls++
                error("Kotlin macro executed during source preparation")
            }
        }
    }

    class GenerateField : Annotation("GenerateField", "source.refresh") {
        override fun forFunction(function: Function) = error("wrong annotation target")
        override fun forField(field: Var<*>) = error("wrong annotation target")
        override fun forDataTemplate(data: DataTemplate) {
            annotations++
            val value = MCInt(23, "generated").apply { declaredParentTemplate = data }
            data.scope.putVar(value.identifier, value, true)
            data.scope.putProperty(value.identifier, Property.buildSimpleProperty(value).apply { declaredParentTemplate = data })
        }
    }

    private fun parser(text: String, file: String = "source-environment.mcfpp") = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, file))))
    private fun call(text: String): P.FunctionCallContext {
        val parser = parser(text)
        return parser.functionCall().also {
            assertEquals(0, parser.numberOfSyntaxErrors, text)
            assertEquals(Token.EOF, parser.currentToken.type, text)
        }
    }
    private fun file(namespace: String): MCFPPFile {
        val owner = Namespace(namespace)
        GlobalScope.localNamespaces[namespace] = owner
        return MCFPPFile(owner).also { owner.scope.journalFileFields.add(it.field) }
    }
    private fun importFile(file: MCFPPFile, dependency: MCFPPFile) {
        file.unsolvedImports[dependency.namespace.identifier] = "*"
        file.field.importedNamespaceField[dependency.namespace.identifier] = dependency.namespace.scope
    }
    private fun function(
        file: MCFPPFile,
        name: String,
        parameters: List<MCFPPType> = emptyList(),
        result: MCFPPType = MCFPPBaseType.Int,
        body: String = "{}"
    ) = Function(name, file.namespace.identifier, parser(body, "$name-declaration.mcfpp").curlBlock()).apply {
        declarationFile = file
        returnType = result
        parameters.forEachIndexed { index, type -> normalParams.add(FunctionParam(type, "p$index", this)) }
        file.namespace.scope.functions.getOrPut(name) { arrayListOf() }.add(this)
    }
    private fun generic(file: MCFPPFile, name: String) = GenericFunction(name, file.namespace.identifier, parser("{}", "$name-declaration.mcfpp").curlBlock()).apply {
        declarationFile = file
        readOnlyParams.add(FunctionParam(MCFPPConcreteType.Type, "T", this, isReadOnly = true))
        file.namespace.scope.functions.getOrPut(name) { arrayListOf() }.add(this)
    }
    private fun ready(syntax: P.FunctionCallContext, caller: Function): CompilerCallPreparationResult.Ready {
        val result = CompilerBindingEnvironment.prepareCall(syntax, caller)
        return assertIs<CompilerCallPreparationResult.Ready>(
            result,
            (result as? CompilerCallPreparationResult.Rejected)?.diagnostics?.joinToString { it.message }.orEmpty()
        )
    }
    private fun isolated(action: () -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val current = Function.currFunction
        val nullFunction = Function.nullFunction
        val extra = Function.extraFunction
        val forced = Function.forcedField
        val template = DataTemplate.currTemplate
        val loader = Project.classLoader
        try {
            Project.config = ProjectConfig(version = "26.3")
            Project.init()
            MCFPPFile.currFile = null
            Function.forcedField = null
            DataTemplate.currTemplate = null
            Function.currFunction = Function("ambient", "source.ambient", null).apply { declarationFile = null }
            hostCalls = 0
            annotations = 0
            action()
        } finally {
            Project.classLoader = loader
            namespaces.restore()
            project.restore()
            Function.currFunction = current
            Function.nullFunction = nullFunction
            Function.extraFunction = extra
            Function.forcedField = forced
            DataTemplate.currTemplate = template
        }
    }

    @Test fun importsDefaultsAndStaticBodiesUseTheOriginalDeclarationAndVisiblePublicOverloads() = isolated {
        val declaration = file("source.original")
        val dependency = file("source.dependency")
        val consumer = file("source.consumer")
        importFile(declaration, dependency)
        importFile(consumer, declaration)
        val shared = MCInt(7, "shared")
        declaration.field.vars["shared"] = shared
        val helper = function(dependency, "helper")
        val hidden = function(declaration, "entry", listOf(UnresolvedType("AbsentPrivateType"))).apply {
            accessModifier = AccessModifier.PRIVATE
        }
        val original = function(declaration, "entry", listOf(MCFPPBaseType.Int), body = "{return shared;}")
        original.normalParams.single().apply {
            hasDefault = true
            defaultContext = parser("9", "entry-declaration.mcfpp").value()
        }
        val caller = Function("caller", consumer.namespace.identifier, null).apply {
            declarationFile = consumer
            scope.vars["shared"] = MCInt(99, "shared")
        }
        val result = ready(call("entry()"), caller)
        val environment = result.environment
        val target = result.call.target
        assertSame(original, environment.compilerFunctions[(target.function as ImmutableFunctionRef<*>).key])
        assertEquals(ConstantValue.Integral(9), target.normalDefaultExpressions.getValue(0).constant)
        assertEquals(listOf(target), environment.binding.functions("entry"))
        assertEquals(listOf(target), environment.binding.functions("source.original:entry"))
        assertTrue(environment.compilerFunctions.values.none { it === hidden })
        val originalEnvironment = environment.declarationEnvironments.getValue(target.declarationIdentity)
        assertSame(environment.binding.types, originalEnvironment.types)
        assertSame(shared, environment.compilerSymbols.getValue(originalEnvironment.symbol("shared")!!.symbol))
        assertTrue(
            originalEnvironment.functions("helper").any {
                environment.compilerFunctions[(it.function as ImmutableFunctionRef<*>).key] === helper
            }
        )
        val signature = FunctionSignature.of(target.function.parameters(), target.function.returnType())
        val callback = SourceCallbackSyntax(
            emptyList(),
            signature,
            assertNotNull(environment.binding.types.inlineType(signature)),
            "entry",
            result.call.source,
            target
        )
        val body = SnapshotSourceBodyBinder().bind(callback, environment.binding, environment.declarationEnvironments)
        val read = assertIs<BoundExprNode.Read>(assertIs<BoundSourceStatement.Return>(body.body.statements.single()).value!!.node)
        assertEquals(originalEnvironment.symbol("shared")!!.symbol, read.location.root.symbol)
        assertFalse(original.bodyCompiled)
        assertFalse(helper.bodyCompiled)
        assertEquals(0, hostCalls)
        assertEquals(0, Project.errorCount)
        environment.requireCurrent()
    }

    @Test fun oneOriginalTreeKeepsIndependentReadonlyBindingsAndItsOmittedDefaultAfterEnvironmentCopies() = isolated {
        val file = file("source.readonly")
        val copy = generic(file, "copy").apply {
            readOnlyParams.single().apply {
                hasDefault = true
                defaultValue = CompilerValue.TypeValue(MCFPPBaseType.Int.typeId)
                defaultTypes = mapOf(MCFPPBaseType.Int.typeId to MCFPPBaseType.Int)
            }
            normalParams.add(FunctionParam(MCFPPGenericParamType("T", arrayListOf()), "value", this))
            returnType = UnresolvedType("T")
        }
        function(file, "combine", listOf(MCFPPBaseType.Int, MCFPPBaseType.Float, MCFPPBaseType.Int))
        val caller = Function("caller", file.namespace.identifier, null).apply { declarationFile = file }
        val syntax = call("combine(copy<int>(1),copy<float>(2.0f),copy(3))")
        val result = ready(syntax, caller)
        fun targets(call: SourceCall) = call.arguments.map { argument ->
            assertIs<BoundExprNode.Call>(assertIs<BoundCallArgument.Value>(argument).expression.node).sourceCall.target
        }
        val targets = targets(result.call)
        assertEquals(1, targets.map { it.declarationIdentity }.distinct().size)
        assertSame(targets[0], targets[2])
        assertNotSame(targets[0], targets[1])
        assertSame(result.environment.binding.types.ref(MCFPPBaseType.Int), targets[0].function.returnType())
        assertSame(result.environment.binding.types.ref(MCFPPBaseType.Float), targets[1].function.returnType())
        assertTrue(targets.all { result.environment.compilerFunctions[(it.function as ImmutableFunctionRef<*>).key] === copy })
        val original = result.environment.binding
        val local = BindingEnvironment(
            original.types,
            original.symbols.values,
            original.functions,
            original.fields.values,
            original.sourceText,
            sourceFile = original.sourceFile
        )
        assertEquals(targets, targets(SnapshotSourceCodeBinder().call(syntax, local)))
        assertEquals(original.functions("source.readonly:copy"), local.functions("source.readonly:copy"))
        assertTrue(original.functions("missing", result.call.source).isEmpty())
        val changedName = call("missing(copy<int>(1),copy<float>(2.0f),copy(3))")
        assertEquals(preparationSpan(syntax), preparationSpan(changedName))
        assertFailsWith<SourceBindingFailure> { SnapshotSourceCodeBinder().call(changedName, original) }
        assertFailsWith<SourceBindingFailure> { SnapshotSourceCodeBinder().call(call("copy<int>(4)"), local) }
        val variants = targets.take(2)
        assertFailsWith<SourceBindingFailure> {
            BindingEnvironment(original.types, emptyList(), variants, callCandidates = emptyMap())
        }
        assertFailsWith<SourceBindingFailure> {
            BindingEnvironment(original.types, emptyList(), variants, callCandidates = mapOf(result.call.source to variants))
        }
        val ownDeclarations = original.functions.filter { it.function.name().startsWith("${file.namespace.identifier}:") }
            .map { it.declarationIdentity }.toSet()
        val environments = result.environment.declarationEnvironments
        assertTrue(environments.filterKeys { it in ownDeclarations }.isNotEmpty())
        assertTrue(
            environments.filterKeys { it in ownDeclarations }.values.all { declaration ->
                declaration.functions("copy", targets[0].preparedCallSites.first()).single() === targets[0]
            }
        )
        val standardEnvironments = environments.filterKeys { it !in ownDeclarations }.values
        assertTrue(standardEnvironments.isNotEmpty(), "Implicit standard functions must be prepared")
        assertTrue(standardEnvironments.all { declaration ->
            declaration.functions.none { it.compilerDeclarationId == copy.declarationId.value }
        }, "Standard declarations must not inherit the caller's functions")
        assertTrue(copy.compiledFunctions.isEmpty())
        assertFalse(copy.bodyCompiled)
        assertEquals(0, hostCalls)
        assertEquals(0, Project.errorCount)
    }

    @Test fun readonlyTextWithTheSamePrintedTupleRetainsDistinctPreparedTargetKeys() = isolated {
        val file = file("source.textkeys")
        val text = generic(file, "bindText").apply {
            readOnlyParams.clear()
            listOf("left", "right").forEach { readOnlyParams.add(FunctionParam(MCFPPBaseType.String, it, this, isReadOnly = true)) }
            returnType = MCFPPBaseType.Int
        }
        function(file, "pair", List(2) { MCFPPBaseType.Int })
        val marker = "PAYLOAD_MARKER"
        fun typed(value: String) = CompilerValue.Typed(MCFPPBaseType.String.typeId, CompilerValue.Text(value))
        val printed = typed(marker).toString()
        val prefix = printed.substringBefore(marker)
        val suffix = printed.substringAfter(marker)
        val first = listOf("a$suffix, ${prefix}b", "c")
        val second = listOf("a", "b$suffix, ${prefix}c")
        assertNotEquals(first, second)
        assertEquals(first.map(::typed).joinToString(), second.map(::typed).joinToString())
        fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        fun invoke(values: List<String>) = "bindText<" + values.joinToString(",", transform = ::quote) + ">()"
        val caller = Function("caller", file.namespace.identifier, null).apply { declarationFile = file }
        val result = ready(call("pair(${invoke(first)},${invoke(second)})"), caller)
        val targets = result.call.arguments.map {
            assertIs<BoundExprNode.Call>(assertIs<BoundCallArgument.Value>(it).expression.node).sourceCall.target
        }
        assertEquals(targets[0].declarationIdentity, targets[1].declarationIdentity)
        assertNotEquals((targets[0].function as ImmutableFunctionRef<*>).key, (targets[1].function as ImmutableFunctionRef<*>).key)
        assertTrue(targets.all { result.environment.compilerFunctions[(it.function as ImmutableFunctionRef<*>).key] === text })
        assertTrue(text.compiledFunctions.isEmpty())
        assertFalse(text.bodyCompiled)
        assertEquals(0, hostCalls)
        assertEquals(0, Project.errorCount)
    }

    @Test fun javaKotlinOrdinaryAndNativeShareTheCatalogAndFirstAnnotationRebuildsTheWholeDomain() = portableSourceEnvironment { it }

    internal fun portableSourceEnvironment(roundTrip: (GenerateField) -> GenerateField) = isolated {
        val directory = Files.createTempDirectory("mcfpp-source-java")
        try {
            val javaSource = directory.resolve("EnvironmentJavaMacro.java")
            Files.writeString(
                javaSource,
                """
                import top.mcfpp.annotations.*;
                import top.mcfpp.mni.api.*;
                public class EnvironmentJavaMacro {
                    @MNIMacro(identifier="javaMacro", normalParams ={"int"}, returnType="int")
                    public static Expr<IntT> expression(MacroContext context, Value<IntT> value) {
                        throw new AssertionError("Java macro executed during source preparation");
                    }
                }
                """.trimIndent()
            )
            val classpath = listOf(MNIMacro::class.java, MacroContext::class.java, kotlin.Unit::class.java)
                .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }.distinct().joinToString(java.io.File.pathSeparator)
            assertEquals(
                0,
                ToolProvider.getSystemJavaCompiler().run(
                    null, null, null,
                    "-proc:none", "-classpath", classpath, "-d", directory.toString(), javaSource.toString()
                )
            )
            URLClassLoader(arrayOf(directory.toUri().toURL()), Project.classLoader).use { loader ->
                Project.classLoader = loader
                val file = file("source.refresh")
                file.namespace.declareMniFixtures(Entries::class.java)
                file.namespace.declareMniFixtures("EnvironmentJavaMacro", loader)
                val ordinary = function(file, "ordinary", listOf(MCFPPBaseType.Int))
                val native = NativeFunction(
                    "legacy",
                    file.namespace.identifier,
                    MniSourceEnvironmentTest::class.java.getMethod("legacy", NativeCallContext::class.java)
                ).apply {
                    declarationFile = file
                    normalParams.add(FunctionParam(MCFPPBaseType.Int, "value", this))
                    returnType = MCFPPBaseType.Int
                }
                file.namespace.scope.functions["legacy"] = arrayListOf(native)
                function(file, "combine", List(4) { MCFPPBaseType.Int })
                val templateSyntax = parser("data Box<T>{var value:T;func identity(v:T):T{return v;}constructor(v:T){var bodyOnly=17;}}")
                    .templateDeclaration()
                val prototype = GenericDataTemplate(templateSyntax.templateBody(), "Box", file.namespace.identifier).apply {
                    declarationFile = file
                    templateSyntax.compoundDeclaration().declarationName().readOnlyParams().readOnlyParameterList().readOnlyParameter().forEachIndexed { index, parameter ->
                        readOnlyParams.add(
                            DataTemplateParam(
                                ReadonlyParamsDeclarationSyntax.readonlyTypeName(parameter),
                                ReadonlyParamsDeclarationSyntax.readonlyParameterName(parameter),
                                MCFPPConcreteType.Type
                            )
                        )
                    }
                    recordDeclarationAnnotations(listOf(roundTrip(GenerateField())))
                }
                file.namespace.scope.template["Box"] = prototype
                file.namespace.scope.annotations["GenerateField"] = GenerateField::class.java
                val producer = generic(file, "produce").apply { returnType = UnresolvedType("Box<T>") }
                val accept = generic(file, "accept").apply { normalParams.add(FunctionParam(UnresolvedType("Box<T>"), "value", this)) }
                val caller = Function("caller", file.namespace.identifier, null).apply { declarationFile = file }
                val before = ready(call("combine(kotlinMacro(1),javaMacro(2),legacy(3),ordinary(4))"), caller)
                val nested = before.call.arguments.map { assertIs<BoundExprNode.Call>(assertIs<BoundCallArgument.Value>(it).expression.node).sourceCall }
                assertEquals(
                    listOf(FunctionKind.MACRO, FunctionKind.MACRO, FunctionKind.NATIVE, FunctionKind.ORDINARY),
                    nested.map { (it.target.function as ImmutableFunctionRef<*>).kind }
                )
                assertSame(before.environment.binding.types, before.environment.macros.types)
                assertEquals(ParamMode.VALUE, nested.last().target.function.parameters().single().mode())
                assertEquals(0, annotations)
                assertEquals(0, prototype.index)
                val syntax = call("accept<int>(produce<int>())")
                val after = ready(syntax, caller)
                assertEquals(1, annotations)
                assertEquals(1, prototype.index)
                val shell = prototype.compiledTemplates.values.single()
                assertTrue(shell.hasCompletedDeclaration)
                assertSame(MCFPPBaseType.Int, shell.scope.getVar("value")!!.type)
                val actual = assertIs<BoundExprNode.Call>(assertIs<BoundCallArgument.Value>(after.call.arguments.single()).expression.node).sourceCall.target.function.returnType()
                val generated = assertNotNull(after.environment.binding.field(actual, "generated"))
                assertTrue((generated.canonical as ImmutableFieldRef<*>).key in after.environment.fieldCapabilities)
                assertNotSame(before.environment.binding.types, after.environment.binding.types)
                assertNotSame(
                    before.environment.macros.namespaces.getValue(file.namespace.identifier).find("kotlinMacro").single().reference,
                    after.environment.macros.namespaces.getValue(file.namespace.identifier).find("kotlinMacro").single().reference
                )
                assertFails { before.environment.requireCurrent() }
                after.environment.requireCurrent()
                ready(syntax, caller)
                assertEquals(1, annotations)
                assertTrue(listOf(ordinary, native, producer, accept).all { !it.bodyCompiled && it.commands.isEmpty() })
                assertTrue(producer.compiledFunctions.isEmpty())
                assertTrue(accept.compiledFunctions.isEmpty())
                assertTrue(shell.scope.functions.values.flatten().all { !it.bodyCompiled })
                assertTrue(shell.constructors.all { !it.bodyCompiled })
                caller.scope.vars["runtime"] = MCInt("runtime")
                // Ordinary VALUE arguments remain runtime reads. Static-source restrictions
                // belong to INLINE bodies, not to a normally typed ordinary call.
                val dynamic = ready(call("ordinary(runtime)"), caller)
                val argument = assertIs<BoundCallArgument.Value>(dynamic.call.arguments.single())
                assertEquals(ParamMode.VALUE, argument.mode)
                assertNull(argument.expression.constant, "A runtime argument must not be rebound as a static constant")
                assertEquals("runtime", assertIs<BoundExprNode.Read>(argument.expression.node).location.root.name)
                assertFalse(ordinary.bodyCompiled)
                assertTrue(ordinary.commands.isEmpty())
                assertEquals(0, hostCalls)
                assertEquals(0, Project.errorCount)
            }
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }
}
