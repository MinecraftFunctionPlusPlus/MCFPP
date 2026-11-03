package top.mcfpp.test

import top.mcfpp.MacroExpansionConfig
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.newExpansionBudget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniExpansionConfigStateTest {
    private class Types : TypeService {
        private val int = ImmutableTypeRef<IntT>(TypeKey(917, 1), "int", kind = TypeKind.INT)
        private val bool = ImmutableTypeRef<BoolT>(TypeKey(917, 2), "bool", kind = TypeKind.BOOL)
        private val float = ImmutableTypeRef<FloatT>(TypeKey(917, 3), "float", kind = TypeKind.FLOAT)
        private val string = ImmutableTypeRef<StringT>(TypeKey(917, 4), "string", kind = TypeKind.STRING)
        private val void = ImmutableTypeRef<VoidT>(TypeKey(917, 5), "void", kind = TypeKind.VOID)
        override fun intType(): TypeRef<IntT> = int
        override fun boolType(): TypeRef<BoolT> = bool
        override fun floatType(): TypeRef<FloatT> = float
        override fun stringType(): TypeRef<StringT> = string
        override fun voidType(): TypeRef<VoidT> = void
        override fun lookup(name: String): TypeRef<*> = if (name == "int") int else error("Unknown type: $name")
        override fun function(name: String, argumentTypes: List<TypeRef<*>>): FunctionRef<*> = error("Unknown function: $name")
    }

    private fun span(offset: Int = 0) = SourceSpan("config.mcfpp", offset, offset + 1, 1, offset + 1, 1, offset + 2)

    @Test
    fun validatedConfigurationCreatesIndependentBudgetsUsedByRealSessions() {
        val defaults = MacroExpansionConfig()
        assertEquals(64, defaults.maxDepth)
        assertEquals(100_000, defaults.maxNodes)
        assertEquals(false, defaults.dump)
        assertFailsWith<IllegalArgumentException> { MacroExpansionConfig(maxDepth = 0) }
        assertFailsWith<IllegalArgumentException> { MacroExpansionConfig(maxDepth = -1) }
        assertFailsWith<IllegalArgumentException> { MacroExpansionConfig(maxNodes = -1) }
        assertFailsWith<IllegalArgumentException> { MacroExpansionConfig(maxNodes = 0) }
        assertEquals(Int.MAX_VALUE, MacroExpansionConfig(maxDepth = Int.MAX_VALUE, maxNodes = Int.MAX_VALUE).maxDepth)

        val config = ProjectConfig(macroExpansion = MacroExpansionConfig(maxDepth = 3, maxNodes = 2, dump = true))
        val firstBudget = config.newExpansionBudget()
        val secondBudget = config.newExpansionBudget()
        assertTrue(firstBudget !== secondBudget)
        val types = Types()
        val environment = ExpansionEnvironment(types)
        val span = span()
        val site = ExpansionSite(span, "config::limited()->void")
        val transaction = CompilerTransaction(
            CompilerTransactionBackend {
                object : PreparedCompilerCommit {
                    override fun apply() = Unit
                    override fun rollback() = Unit
                }
            }
        )
        ExpansionSession(site, firstBudget, environment, transaction).use { session ->
            session.context().quote().block(java.util.function.Consumer { builder -> builder.intLiteral(1) })
            assertEquals(2L, firstBudget.usedNodes)
            val error = assertFailsWith<ExpansionFailure> {
                session.context().quote().block(java.util.function.Consumer { builder -> builder.intLiteral(2) })
            }
            assertTrue(error.diagnostic.message.contains("2 generated nodes"))
        }
        assertEquals(3L, firstBudget.usedNodes)
        assertEquals(0L, secondBudget.usedNodes)

        val shallowBudget = ProjectConfig(macroExpansion = MacroExpansionConfig(maxDepth = 1)).newExpansionBudget()
        val shallowEnvironment = ExpansionEnvironment(Types())
        val shallowSession = ExpansionSession(
            site,
            shallowBudget,
            shallowEnvironment,
            CompilerTransaction(
                CompilerTransactionBackend {
                    object : PreparedCompilerCommit {
                        override fun apply() = Unit
                        override fun rollback() = Unit
                    }
                }
            )
        )
        val depthError = assertFailsWith<ExpansionFailure> { shallowSession.child(ExpansionSite(span(1), "config::nested()->void")) }
        assertTrue(depthError.diagnostic.message.contains("exceeds depth 1"))
        assertEquals(0, shallowBudget.depth)
        assertEquals(0L, shallowBudget.usedNodes)
    }

    @Test
    fun projectJournalRestoresMacroConfigurationAcrossMultipleGenerationsByIdentity() {
        val outer = ProjectStateJournal.checkpoint()
        try {
            Project.init()
            val originalConfig = ProjectConfig(macroExpansion = MacroExpansionConfig(5, 20, true))
            Project.config = originalConfig
            val originalLimits = originalConfig.macroExpansion
            val generationOne = ProjectStateJournal.checkpoint()

            originalConfig.macroExpansion = MacroExpansionConfig(2, 7, false)
            val generationTwo = generationOne.captureCurrent()
            originalConfig.macroExpansion = MacroExpansionConfig(9, 99, true)

            generationOne.restore()
            assertSame(originalConfig, Project.config)
            assertEquals(originalLimits, originalConfig.macroExpansion)

            generationTwo.restore()
            assertSame(originalConfig, Project.config)
            assertEquals(MacroExpansionConfig(2, 7, false), originalConfig.macroExpansion)

            generationOne.restore()
            assertEquals(originalLimits, originalConfig.macroExpansion)
        } finally {
            outer.restore()
        }
    }
}
