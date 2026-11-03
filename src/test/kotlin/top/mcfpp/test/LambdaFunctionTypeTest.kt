package top.mcfpp.test

import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPFunctionType
import top.mcfpp.type.MCFPPFunctionType.ExecutionCategory
import top.mcfpp.type.MCFPPGenericParamType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPUnionType
import top.mcfpp.type.TypeRelations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LambdaFunctionTypeTest {

    private fun function(
        parameters: List<MCFPPType>,
        result: MCFPPType,
        category: ExecutionCategory = ExecutionCategory.NORMAL
    ) = MCFPPFunctionType(parameters, result, category)

    @Test
    fun identicalSignaturesAreCompatibleWithoutIncidentalObjectIdentity() {
        val first = function(listOf(MCFPPBaseType.Int, MCFPPBaseType.String), MCFPPBaseType.Bool)
        val second = function(listOf(MCFPPBaseType.Int, MCFPPBaseType.String), MCFPPBaseType.Bool)

        assertNotSame(first, second)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(first.typeId, second.typeId)
        assertTrue(TypeRelations.isSubtype(first, second))
        assertTrue(TypeRelations.isSubtype(second, first))
        assertEquals(TypeRelations.Conversion.EXACT, TypeRelations.resolveImplicitConversion(first, second))
        assertEquals("(int, string) -> bool", first.typeName)
        assertEquals(first, MCFPPFunctionType.fromIdentity(first.typeId))
    }

    @Test
    fun parameterOrderAndArityRemainPartOfTheSignature() {
        val intThenString = function(listOf(MCFPPBaseType.Int, MCFPPBaseType.String), MCFPPBaseType.Bool)
        val stringThenInt = function(listOf(MCFPPBaseType.String, MCFPPBaseType.Int), MCFPPBaseType.Bool)
        val oneParameter = function(listOf(MCFPPBaseType.Int), MCFPPBaseType.Bool)

        assertNotEquals(intThenString, stringThenInt)
        assertFalse(TypeRelations.isSubtype(intThenString, stringThenInt))
        assertFalse(TypeRelations.isSubtype(intThenString, oneParameter))
        assertNull(TypeRelations.resolveImplicitConversion(intThenString, oneParameter))
    }

    @Test
    fun parametersAreContravariantAndResultsCovariant() {
        val intToInt = function(listOf(MCFPPBaseType.Int), MCFPPBaseType.Int)
        val widerParameter = function(listOf(MCFPPBaseType.Object), MCFPPBaseType.Int)
        val widerResult = function(listOf(MCFPPBaseType.Int), MCFPPBaseType.Object)

        // Accepting object is more permissive than accepting int, so it can replace it.
        assertTrue(TypeRelations.isSubtype(widerParameter, intToInt))
        assertFalse(TypeRelations.isSubtype(intToInt, widerParameter))
        // Returning int can satisfy a caller that expects object.
        assertTrue(TypeRelations.isSubtype(intToInt, widerResult))
        assertFalse(TypeRelations.isSubtype(widerResult, intToInt))

        assertEquals(TypeRelations.Conversion.NOMINAL, TypeRelations.resolveImplicitConversion(widerParameter, intToInt))
        assertEquals(TypeRelations.Conversion.NOMINAL, TypeRelations.resolveImplicitConversion(intToInt, widerResult))
        assertNull(TypeRelations.resolveImplicitConversion(intToInt, widerParameter))
        assertNull(TypeRelations.resolveImplicitConversion(widerResult, intToInt))
    }

    @Test
    fun executionCategoriesAreDistinctAndNeverInterchangeImplicitly() {
        val normal = function(listOf(MCFPPBaseType.Int), MCFPPBaseType.Int, ExecutionCategory.NORMAL)
        val paused = function(listOf(MCFPPBaseType.Int), MCFPPBaseType.Int, ExecutionCategory.PAUSED)
        val generator = function(listOf(MCFPPBaseType.Int), MCFPPBaseType.Int, ExecutionCategory.GENERATOR)

        assertEquals(3, setOf(normal.typeId, paused.typeId, generator.typeId).size)
        assertNotEquals(normal, paused)
        assertFalse(TypeRelations.isSubtype(normal, paused))
        assertFalse(TypeRelations.isSubtype(paused, normal))
        assertFalse(TypeRelations.isSubtype(normal, generator))
        assertFalse(TypeRelations.isSubtype(generator, normal))
        assertNull(TypeRelations.resolveImplicitConversion(normal, paused))
        assertNull(TypeRelations.resolveImplicitConversion(paused, generator))
        assertNull(TypeRelations.resolveImplicitConversion(generator, normal))
        assertTrue(TypeRelations.isSubtype(paused, paused))
        assertEquals("paused (int) -> int", paused.typeName)
        assertEquals(paused, MCFPPFunctionType.fromIdentity(paused.typeId))
    }

    @Test
    fun functionTypesDoNotBecomeStructuralOrdinaryObjects() {
        val plain = function(listOf(MCFPPBaseType.Int), MCFPPBaseType.Int)

        assertFalse(TypeRelations.isSubtype(plain, MCFPPBaseType.Object))
        assertFalse(TypeRelations.isSubtype(MCFPPBaseType.Object, plain))
        assertFalse(TypeRelations.isSubtype(plain, MCFPPBaseType.Any))
        assertNull(TypeRelations.resolveImplicitConversion(plain, MCFPPBaseType.Object))
        assertNull(TypeRelations.resolveImplicitConversion(plain, MCFPPBaseType.Any))

        // Ordinary nominal subtyping keeps its existing behavior.
        assertTrue(TypeRelations.isSubtype(MCFPPBaseType.Int, MCFPPBaseType.Object))
        assertEquals(
            TypeRelations.Conversion.OBJECT,
            TypeRelations.resolveImplicitConversion(MCFPPBaseType.Int, MCFPPBaseType.Object)
        )
    }

    @Test
    fun identityIsImmutableAndUnionsOnlyAcceptMatchingFunctions() {
        val parameterTypes = mutableListOf<MCFPPType>(MCFPPBaseType.Int)
        val plain = MCFPPFunctionType(parameterTypes, MCFPPBaseType.Bool)
        parameterTypes += MCFPPBaseType.String
        assertEquals(listOf(MCFPPBaseType.Int), plain.parameters)

        val nested = function(listOf(plain), MCFPPBaseType.String)
        assertNotEquals(plain, nested)
        assertEquals(nested, MCFPPFunctionType.fromIdentity(nested.typeId))

        val union = MCFPPUnionType(plain, MCFPPBaseType.String)
        assertTrue(TypeRelations.isSubtype(plain, union))
        assertFalse(TypeRelations.isSubtype(nested, union))
        assertNull(TypeRelations.resolveImplicitConversion(nested, union))
    }

    @Test
    fun genericParametersSubstituteThroughParametersAndResult() {
        val generic = MCFPPGenericParamType("T", arrayListOf())
        val template = function(listOf(generic), generic)

        assertEquals(
            function(listOf(MCFPPBaseType.Int), MCFPPBaseType.Int),
            template.replaceGenericParam(mapOf("T" to MCFPPBaseType.Int))
        )
    }

    @Test
    fun modelConstructsRuntimeFunctionValuesWithoutAThrowsContract() {
        val plain = function(listOf(MCFPPBaseType.Int), MCFPPBaseType.Int)

        assertTrue(plain.isValueType)
        assertTrue(plain.hasRuntimeRepresentation)
        kotlin.test.assertIs<top.mcfpp.core.lang.FunctionValue>(plain.buildUnConcrete("callback"))
        assertFalse(plain.typeName.contains("throws"))
    }

    @Test
    fun arrowTypeStringsRoundTripForNestedParametersAndResults() {
        val callback = function(listOf(MCFPPBaseType.Int), MCFPPBaseType.Int)
        val nested = function(listOf(callback), MCFPPBaseType.Int)
        val returned = function(listOf(MCFPPBaseType.Int), function(emptyList(), MCFPPBaseType.Int))
        val scope = top.mcfpp.model.scope.SimpleScopeWithType.getTypeScope()
        assertEquals("((int) -> int) -> int", nested.typeName)
        assertEquals("(int) -> () -> int", returned.typeName)
        assertEquals(nested, MCFPPType.parseFromString(nested.typeName, scope))
        assertEquals(returned, MCFPPType.parseFromString(returned.typeName, scope))
    }
}
