package top.mcfpp.syntax

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.antlr.DeclarationSyntax
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.TypeDataTemplate
import top.mcfpp.compiletime.CompileTimeFunction
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPConcreteType
import kotlin.test.*
import kotlin.test.Test

class DeclarationsTest {
    @Test fun acceptsColonDeclarationsAndKeepsAliasesCastsAndAnonymousParameters() {
        assertEquals(emptyList(), errors("""
            import example:Thing as Renamed
            typealias int as Integer
            data NumberView as int {}
            data Box<T, N: int> {
                var value: T
                const count: int = 1
                constructor(value: T) { this.value = value; }
            }
            const func fixed(value: int): int { return value; }
            func nativeValue<T, N: int>(value: T) = example.Native.value
            func accept(Box<int, 2>, static value: int) {}
            func main() {
                var typed: int
                var inferred = 1 as int
                const fixed: int
                dynamic runtime: int
                dynamic inferredRuntime = 2
                import var incoming: int
            }
        """.trimIndent()))
    }

    @Test fun rejectsOldDeclarationFormsWithoutRetiringExpressionAs() {
        for (source in listOf(
            "func main(){var value as int;}",
            "func main(){const var value=1;}",
            "func main(){dynamic var value=1;}",
            "func old(value as int){}",
            "func old<T as type>(){}",
            "data Old<T as type>{}",
            "data Old{var value as int;}",
            "data Old{const var value=1;}",
            "data Old{constructor(value as int){}}"
        )) assertTrue(errors(source).isNotEmpty(), source)
        assertEquals(emptyList(), errors("data NumberView as int {}\nfunc main(){var value=1 as int;}"))
    }

    @Test fun bareReadonlyIdentifiersAndNormalAnonymousTypesHaveDifferentMeanings() {
        val parser = parser("<\nT\n,\nN: int\n>(\nSomeClass\n, named: int\n)")
        val params = parser.functionParams()
        assertEquals(0, parser.numberOfSyntaxErrors)
        val readonly = params.readOnlyParams().parameterList().parameter()
        assertEquals("type", DeclarationSyntax.readonlyTypeName(readonly[0]))
        assertEquals("T", DeclarationSyntax.parameterName(readonly[0], true, "p0"))
        assertEquals("int", DeclarationSyntax.readonlyTypeName(readonly[1]))
        val anonymous = params.normalParams().parameterList().parameter()[0]
        assertEquals("SomeClass", anonymous.type().text)
        assertEquals("p0", DeclarationSyntax.parameterName(anonymous, false, "p0"))
    }

    @Test fun compilesMixedGenericFunctionsTemplatesConstructorsAndAnonymousValues() = isolated {
        compile("""
            namespace fixture.declarations
            data NumberView as int {}
            data Plain { var value: int; }
            data Box<T, N: int> {
                var value: T
                const count: int = N
                constructor(value: T) { this.value = value; }
            }
            func identity<T, N: int>(value: T): T { return value; }
            func anonymous(Plain) {}
            const func fixed(value: int): int { return value + 1; }
            func main() {
                var box = Box<int, 2>(3)
                var inferred = identity<int, 2>(4)
                var converted = inferred as int
                const local = 5
                anonymous(Plain())
            }
        """.trimIndent())
        val scope = GlobalScope.localNamespaces.getValue("fixture.declarations").scope
        assertIs<TypeDataTemplate>(scope.getTemplate("NumberView"))
        val template = assertIs<GenericDataTemplate>(scope.getTemplate("Box"))
        assertEquals(listOf("T", "N"), template.readOnlyParams.map { it.identifier })
        assertEquals(listOf(MCFPPConcreteType.Type, MCFPPBaseType.Int), template.readOnlyParams.map { it.type })
        val generic = assertIs<GenericFunction>(scope.functions.getValue("identity").single())
        assertEquals(listOf("T", "N"), generic.readOnlyParams.map { it.identifier })
        assertEquals("T", generic.readOnlyParams[0].toString())
        assertEquals("N: int", generic.readOnlyParams[1].toString())
        val anonymous = scope.functions.getValue("anonymous").single()
        assertEquals("p0", anonymous.normalParams.single().identifier)
        assertTrue(anonymous.normalParams.single().type.typeName.contains("fixture.declarations:Plain"))
        assertIs<CompileTimeFunction>(scope.functions.getValue("fixed").single())
        val main = scope.functions.getValue("main").single()
        assertEquals(MCFPPBaseType.Int, main.scope.getVar("inferred")!!.type)
        assertEquals(MCFPPBaseType.Int, main.scope.getVar("converted")!!.type)
        assertTrue(main.scope.getVar("local")!!.isConst)
    }

    @Test fun preservesUninitializedConstRuntimeDynamicAndInferredDeclarations() = isolated {
        compile("""
            namespace fixture.variables
            func main() {
                var typed: int
                const fixed: int
                fixed = 2
                dynamic runtime: int
                runtime = 3
                var inferred = 4
                dynamic inferredRuntime = 5
            }
        """.trimIndent())
        val main = GlobalScope.localNamespaces.getValue("fixture.variables").scope.functions.getValue("main").single()
        assertTrue(main.scope.getVar("fixed")!!.isConst)
        assertTrue(main.scope.getVar("runtime")!!.symbol!!.forceRuntime)
        assertTrue(main.scope.getVar("inferredRuntime")!!.symbol!!.forceRuntime)
        assertTrue(main.commands.analyzeAll().any { "runtime" in it && it.endsWith(" 3") })
        assertTrue(main.commands.analyzeAll().any { "inferredRuntime" in it && it.endsWith(" 5") })
        assertEquals(MCFPPBaseType.Int, main.scope.getVar("inferred")!!.type)
        assertNotNull(StorageAccess.snapshot(main.scope.getVar("inferred")!!))
    }

    @Test fun nativeGenericDeclarationsRegisterTypeNamesBeforeNormalParameters() = isolated {
        compile("""
            namespace fixture.natives
            func nativeIdentity<T>(value: int): int = top.mcfpp.syntax.DeclarationsTest.nativeIdentity
            func main() { var result = nativeIdentity<int>(7); }
        """.trimIndent())
        val native = assertIs<NativeFunction>(GlobalScope.localNamespaces.getValue("fixture.natives").scope.functions.getValue("nativeIdentity").single())
        assertEquals(MCFPPConcreteType.Type, native.readOnlyParams.single().type)
        assertNotNull(native.scope.getType("T"))
    }

    companion object {
        @JvmStatic
        @MNIFunction(readOnlyParams = ["type"], normalParams = ["int"], returnType = "int")
        fun nativeIdentity(context: NativeCallContext) = context.withArguments { arguments ->
            context.publishResult(arguments.last())
        }
    }

    private fun parser(source: String): mcfppParser =
        mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))

    private fun errors(source: String): List<String> {
        val result = mutableListOf<String>()
        val listener = object : BaseErrorListener() {
            override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int,
                                     charPositionInLine: Int, msg: String?, e: RecognitionException?) {
                result += "$line:$charPositionInLine $msg"
            }
        }
        val lexer = mcfppLexer(CharStreams.fromString(source)).apply { removeErrorListeners(); addErrorListener(listener) }
        mcfppParser(CommonTokenStream(lexer)).apply { removeErrorListeners(); addErrorListener(listener) }.compilationUnit()
        return result
    }

    private fun compile(source: String) {
        assertEquals(emptyList(), errors(source), "Compiler fixtures must parse")
        MCFPPStringTest.readFromString(source, version = "26.3")
        assertEquals(0, Project.errorCount)
    }

    private fun isolated(action: () -> Unit) {
        val config = Project.config
        val ignore = CompileSettings.ignoreStdLib
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = true
            action()
        } finally {
            Project.config = config
            CompileSettings.ignoreStdLib = ignore
        }
    }
}
