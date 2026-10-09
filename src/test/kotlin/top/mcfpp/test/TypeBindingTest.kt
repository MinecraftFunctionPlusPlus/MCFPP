package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.ParameterMatcher
import top.mcfpp.model.scope.NamespaceScope
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPType
import kotlin.test.*
import kotlin.test.Test

class TypeBindingTest {
    private fun function(type: MCFPPType) = Function("choose", context = null).appendNormalParam(type, "value")

    @Test fun typeQueriesSharePromotionRanksWithoutBuildingValuesOrChangingScopes() {
        val exact = function(MCFPPBaseType.Int)
        val promoted = function(MCFPPBaseType.Float)
        val opaque = function(MCFPPBaseType.Object)
        val erased = function(MCFPPBaseType.Any)
        val functions = listOf(erased, opaque, promoted, exact)
        val commands = Function.currFunction.commands.analyzeAll()
        val errors = Project.errorCount
        fun selected(candidates: List<Function>, source: MCFPPType) =
            assertIs<ParameterMatcher.TypeSelection.Selected>(ParameterMatcher.selectTypes(candidates, "choose", listOf(source))).function
        assertSame(exact, selected(functions, MCFPPBaseType.Int))
        assertSame(promoted, selected(functions - exact, MCFPPBaseType.Int))
        assertSame(promoted, selected(functions, MCFPPBaseType.Float))
        assertTrue(functions.all { it.scope.vars.isEmpty() })
        assertEquals(commands, Function.currFunction.commands.analyzeAll())
        assertEquals(errors, Project.errorCount)
    }

    @Test fun ambiguousLocalDefaultsDoNotFallThroughToAParentOverload() {
        val first = function(MCFPPBaseType.Int).appendNormalParam(MCFPPBaseType.Any, "extra").also {
            it.normalParams.last().hasDefault = true; it.normalParams.last().defaultVar = top.mcfpp.core.lang.MCInt(0)
        }
        val second = function(MCFPPBaseType.Int).appendNormalParam(MCFPPBaseType.Object, "extra").also {
            it.normalParams.last().hasDefault = true; it.normalParams.last().defaultVar = top.mcfpp.core.lang.MCInt(0)
        }
        val parent = NamespaceScope("parent").apply { addFunction(function(MCFPPBaseType.Any), false) }
        val local = NamespaceScope("local").apply {
            this.parent.clear(); this.parent.add(parent)
            addFunction(first, false); addFunction(second, false)
        }
        val errors = Project.errorCount
        val result = assertIs<ParameterMatcher.TypeSelection.Ambiguous>(local.getFunctionByTypes("choose", listOf(MCFPPBaseType.Int)))
        assertEquals(setOf(first.declarationId, second.declarationId), result.functions.map { it.declarationId }.toSet())
        assertEquals(errors, Project.errorCount)
    }

    @Test fun erasedAndObjectTypesDoNotBorrowConcreteOperationsAndRequiredArgumentsRemainRequired() {
        val concrete = function(MCFPPBaseType.Int)
        val erased = function(MCFPPBaseType.Any)
        for (source in listOf(MCFPPBaseType.Any, MCFPPBaseType.Object))
            assertSame(erased, assertIs<ParameterMatcher.TypeSelection.Selected>(
                ParameterMatcher.selectTypes(listOf(concrete, erased), "choose", listOf(source))).function)
        assertEquals(ParameterMatcher.TypeSelection.Missing, ParameterMatcher.selectTypes(listOf(concrete), "choose", emptyList()))
        assertEquals(ParameterMatcher.TypeSelection.Missing, ParameterMatcher.selectTypes(listOf(concrete), "choose", listOf(MCFPPBaseType.Bool)))
    }

    @Test fun parameterStorageUsesTheDeclarationNamespaceDuringForeignBodyCompilation() {
        val original = Project.currNamespace
        try {
            val declaration = function(MCFPPBaseType.Int).also { it.namespace = "library.source" }
            Project.currNamespace = "consumer.main"
            val parameter = MCInt(declaration, "value")
            assertEquals("library.source_func_choose_value", parameter.name)
            assertEquals("library.source_func_choose_", declaration.prefix)
        } finally { Project.currNamespace = original }
    }
}
