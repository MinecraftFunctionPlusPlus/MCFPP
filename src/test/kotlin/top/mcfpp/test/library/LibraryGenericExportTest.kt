package top.mcfpp.test.library

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.io.MCFPPFile
import top.mcfpp.library.api.LibraryExport
import top.mcfpp.library.compiler.*
import top.mcfpp.library.format.*
import top.mcfpp.library.model.*
import top.mcfpp.library.syntax.*
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.TypeId
import java.nio.file.Files
import kotlin.test.*

class LibraryGenericExportTest {
    @Test fun sourceReadonlyTemplatesAndLambdaClosuresProduceStableBoundV1Files() = source("""
        namespace parameter.fixture
        data Parent<T> {
            var inherited:T
            constructor(initial:T){this.inherited=initial}
        }
        data Child<T,N:int=3>:Parent<T> {
            var token:T
            var count:int=N
            var inferred=N+1 {
                get=field
                set=value
            }
            constructor(initial:T,count:int=5){this.token=initial}
            func echo(value:T):T{return value}
        }
        func identity<T>(value:T):T{return value}
        func keepList<T>(value:list<T>):list<T>{return value}
        func keepChild<T>(value:Child<T>):Child<T>{return value}
        func genericDefault<T>(value:int=3):int{return value}
        func apply(callback:(int)->int,value:int):int{return callback(value)}
        func closure(seed:int=4):int {
            var callback:(int)->int={value:int -> var copy:int=value; copy+seed}
            return apply(callback,identity<int>(seed))
        }
        func implicit(seed:int):int {
            var callback:(int)->int={it+seed}
            return apply(callback,seed)
        }
        func nested(seed:int):int {
            var callback:(int)->int={outer:int ->
                var inner:(int)->int={value:int -> value+outer+seed}
                inner(outer)
            }
            return apply(callback,seed)
        }
        func main(){}
    """.trimIndent()) { file ->
        val scope = file.namespace.scope
        val roots = listOf("identity", "keepList", "keepChild", "genericDefault", "closure", "implicit", "nested")
            .map { scope.functions.getValue(it).single { function -> !function.actualCallBody } }
        val selection = OwnedLibrarySelection(LibraryIdentity(PackageKey("fixture:parameter-export", "1"), "main"),
            LibraryTarget("26.3"), roots, listOf(file), templates = listOf(scope.template.getValue("Child")))
        val directory = Files.createTempDirectory("mcfpp-parameter-export-")
        val output = directory.resolve("bin.mclib")
        try {
            val result = LibraryExport.write(selection, output)
            val bytes = Files.readAllBytes(output)
            assertEquals(bytes.size.toLong(), result.bytesWritten)
            assertEquals(1L, LibraryWire.decode(bytes).formatVersion)
            val codec = LibraryModelCodec(listOf(CompoundCodec))
            val model = codec.decode(bytes)
            val declarations = model.declarations.declarations
            fun declarationId(record: LibraryDeclaration) = DeclarationId(declarations.indexOf(record) + 1L)
            fun function(name: String) = declarations.filterIsInstance<FunctionDeclaration>().single { it.common.name == name }
            val identity = function("identity")
            val identityId = declarationId(identity)
            val readonly = model.declarations.declaration(identity.readonly.single()) as ValueDeclaration
            assertEquals(ValueRole.READONLY_PARAMETER, readonly.role)
            assertEquals(identityId, readonly.common.owner)
            assertEquals(0L, readonly.parameterIndex)
            val parameter = model.declarations.declaration(identity.normal.single()) as ValueDeclaration
            assertEquals(LibraryType.Parameter(DeclarationReference.Local(identityId), ParameterRole.READONLY, 0), model.tables.type(assertNotNull(parameter.type)))
            assertEquals(parameter.type, identity.result)
            assertEquals(KeyType.Parameter(0, ParameterRole.READONLY, 0), assertNotNull(identity.common.key?.signature).normal.single())
            val keepList = function("keepList")
            val listParameter = model.declarations.declaration(keepList.normal.single()) as ValueDeclaration
            val dependent = assertIs<LibraryType.Dependent>(model.tables.type(assertNotNull(listParameter.type)))
            val listTree = assertIs<TypeSyntax.Container>(model.syntax.node(dependent.syntax))
            assertEquals(NameUse.Local(keepList.readonly.single()), assertIs<TypeSyntax.Name>(listTree.argument).use)
            assertEquals(declarationId(keepList), dependent.owner)
            assertIs<KeyType.Dependent>(assertNotNull(function("keepChild").common.key?.signature).normal.single())

            val child = declarations.filterIsInstance<CompoundDeclaration>().single { it.common.name == "Child" }
            val childId = declarationId(child)
            val parent = assertIs<TypeSyntax.Apply>(model.syntax.node(assertNotNull(child.parents.single().expression)))
            assertNull(child.parents.single().type)
            assertEquals(NameUse.Local(child.readonly.first()), assertIs<Expression.Name>(parent.readonly.single()).use)
            val parentReference = assertIs<NameUse.Declaration>(assertIs<TypeSyntax.Name>(parent.base).use).target as DeclarationReference.Local
            assertEquals("Parent", model.declarations.declaration(parentReference.target).common.name)
            val count = declarations.filterIsInstance<ValueDeclaration>().single { it.common.owner == childId && it.common.name == "count" }
            val initializer = model.syntax.body(assertNotNull(count.defaultBody))
            assertEquals(BodyRole.INITIALIZER, initializer.role)
            assertEquals(NameUse.Local(child.readonly[1]), assertIs<Expression.Name>(model.syntax.node(initializer.root)).use)
            assertNotNull((model.declarations.declaration(child.readonly[1]) as ValueDeclaration).defaultBody)
            val inferred = declarations.filterIsInstance<ValueDeclaration>().single { it.common.owner == childId && it.common.name == "inferred" }
            val inferredType = assertIs<LibraryType.Dependent>(model.tables.type(assertNotNull(inferred.type)))
            val inferredSyntax = assertIs<TypeSyntax.Inferred>(model.syntax.node(inferredType.syntax))
            assertNull(inferred.declaredType)
            val inferredBody = model.syntax.body(assertNotNull(inferred.defaultBody))
            val inferredExpression = assertIs<Expression.Binary>(model.syntax.node(inferredBody.root))
            assertSame(inferredExpression, inferredSyntax.expression)
            assertEquals(NameUse.Local(child.readonly[1]), assertIs<Expression.Name>(inferredExpression.left).use)
            val property = declarations.filterIsInstance<PropertyDeclaration>().single { it.common.owner == childId && it.common.name == "inferred" }
            val getter = assertIs<LibraryAccessor.Function>(property.getter).target as DeclarationReference.Local
            val setter = assertIs<LibraryAccessor.Function>(property.setter).target as DeclarationReference.Local
            assertNotEquals(getter.target, setter.target)
            listOf(getter, setter).forEach { reference ->
                val accessor = assertIs<FunctionDeclaration>(model.declarations.declaration(reference.target))
                assertEquals(declarationId(property), accessor.common.owner)
                assertNotNull(accessor.body)
                assertNull(accessor.common.key)
            }
            assertEquals(KeyType.Parameter(1, ParameterRole.READONLY, 0), assertNotNull(function("echo").common.key?.signature).normal.single())
            val defaultParameter = model.declarations.declaration(function("genericDefault").normal.single()) as ValueDeclaration
            assertEquals(BodyRole.DEFAULT, model.syntax.body(assertNotNull(defaultParameter.defaultBody)).role)

            val lambdas = model.syntax.nodes.filterIsInstance<Expression.Lambda>()
            assertEquals(4, lambdas.size)
            val implicit = lambdas.single { !it.explicitParameters }
            val implicitParameter = model.declarations.declaration(implicit.parameters.single()) as ValueDeclaration
            assertEquals("it", implicitParameter.common.name)
            assertNull(implicitParameter.type)
            lambdas.forEach { lambda ->
                val synthetic = model.declarations.declaration(lambda.declaration) as FunctionDeclaration
                val body = model.syntax.body(lambda.body)
                assertEquals(lambda.body, synthetic.body)
                assertEquals(lambda.declaration, body.owner)
                assertEquals(BodyRole.LAMBDA, body.role)
                assertNotNull(lambda.tail)
                assertTrue(body.captures.isNotEmpty())
                lambda.parameters.forEachIndexed { index, id ->
                    val value = model.declarations.declaration(id) as ValueDeclaration
                    assertEquals(ValueRole.LAMBDA_PARAMETER, value.role)
                    assertEquals(lambda.declaration, value.common.owner)
                    assertEquals(index.toLong(), value.parameterIndex)
                }
                val root = assertIs<Statement.Block>(model.syntax.node(body.root))
                assertTrue(root.statements.none { it is Statement.Evaluate && it.value === lambda.tail }, "A lambda tail is not repeated as an executable statement")
            }
            val nestedCapture = lambdas.single { lambda -> model.syntax.body(lambda.body).captures.size == 2 }
            val captured = model.syntax.body(nestedCapture.body).captures.map { (it as DeclarationReference.Local).target }
            assertEquals(setOf("outer", "seed"), captured.map { model.declarations.declaration(it).common.name }.toSet())
            assertTrue(model.syntax.nodes.filterIsInstance<Expression.Call>().any { it.readonly.isNotEmpty() })
            assertTrue(model.localReferencesValidated)
            assertTrue(model.syntax.externalReferencesValidated)
            assertNull(model.debug)
            repeat(3) { TypeId.fresh() }
            assertContentEquals(bytes, codec.encode(LibraryExporter.capture(selection.copy(functions = roots.reversed()))))
        } finally {
            Files.deleteIfExists(output)
            Files.deleteIfExists(directory)
        }
    }

    private fun source(code: String, action: (MCFPPFile) -> Unit) {
        val configuration = Project.config
        val settings = listOf(CompileSettings.ignoreStdLib, CompileSettings.isDebug, CompileSettings.isLib, CompileSettings.printAll)
        try {
            Project.config = ProjectConfig()
            MCFPPStringTest.readFromString(code, arrayOf("-ignoreStdLib"), version = "26.3")
            assertEquals(0, Project.errorCount)
            val main = GlobalScope.localNamespaces.getValue("parameter.fixture").scope.functions.getValue("main").single()
            action(assertNotNull(main.declarationFile))
        } finally {
            MCFPPFile.currFile = null
            Project.config = configuration
            CompileSettings.ignoreStdLib = settings[0]; CompileSettings.isDebug = settings[1]
            CompileSettings.isLib = settings[2]; CompileSettings.printAll = settings[3]
        }
    }
}
