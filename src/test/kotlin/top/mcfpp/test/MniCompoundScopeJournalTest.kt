package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.core.lang.MCInt
import top.mcfpp.mni.expand.CompoundScopeJournal
import top.mcfpp.mni.expand.FunctionStateJournal
import top.mcfpp.model.Member
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.Property
import top.mcfpp.model.scope.CompoundDataScope
import top.mcfpp.model.scope.IScope
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPGenericDataTemplateType
import top.mcfpp.type.MCFPPListType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.TypeId
import kotlin.test.*
import kotlin.test.Test

class MniCompoundScopeJournalTest {
    private fun scope() = CompoundDataScope(arrayListOf(null))

    private inline fun inFunction(action: (CompoundDataScope, Function) -> Unit) {
        Project.init()
        val previous = Function.currFunction
        val function = Function("scopeJournal", "test", null)
        Function.currFunction = function
        try {
            action(scope(), function)
            assertEquals(0, Project.errorCount)
        } finally {
            Function.currFunction = previous
        }
    }

    @Test fun privateMapsRestoreRealAliasKeysWithoutRebindingTheOriginalVariable() = inFunction { scope, _ ->
        val value = MCInt(3, "declarationName")
        val property = Property.buildSimpleProperty(value)
        scope.putVar("actual.alias", value)
        scope.putVar("second.alias", value)
        scope.putType("type.alias", MCFPPBaseType.Int)
        scope.putType("second.type.alias", MCFPPBaseType.Int)
        scope.putProperty("property.alias", property)
        scope.putProperty("second.property.alias", property)
        val symbol = value.symbol
        val before = CompoundScopeJournal.checkpoint(listOf(scope))
        scope.removeVar("actual.alias")
        scope.removeVar("second.alias")
        scope.putVar("new", MCInt(9, "unrelated"))
        scope.removeType("type.alias")
        scope.putType("second.type.alias", MCFPPBaseType.Bool, true)
        scope.putType("new.type", MCFPPBaseType.Bool)
        scope.removeProperty("property.alias")
        scope.putProperty("second.property.alias", Property.buildSimpleSetter("other"), true)
        scope.putProperty("new.property", Property.buildSimpleGetter("other"))
        before.restore()
        assertSame(value, scope.getVar("actual.alias"))
        assertSame(value, scope.getVar("second.alias"))
        assertSame(symbol, value.symbol)
        assertEquals("declarationName", value.identifier)
        assertNull(scope.getVar("new"))
        assertSame(MCFPPBaseType.Int, scope.getType("type.alias"))
        assertSame(MCFPPBaseType.Int, scope.getType("second.type.alias"))
        assertNull(scope.getType("new.type"))
        assertSame(property, scope.getProperty("property.alias"))
        assertSame(property, scope.getProperty("second.property.alias"))
        assertNull(scope.getProperty("new.property"))
        before.restore()
        assertSame(symbol, value.symbol)
    }

    @Test fun replacedFunctionAndOperatorTablesKeepDetachedNestedContainers() = inFunction { scope, _ ->
        val original = Function("declaration", "test", null)
        val changed = Function("changed", "test", null)
        val overloads = arrayListOf(original)
        val operatorEntries = hashMapOf<MCFPPType?, Function>(null to original, MCFPPBaseType.Int to original)
        val functions = scope.functions.apply { put("actual.method.alias", overloads) }
        val operators = scope.operators.apply { put("actual.operator.alias", operatorEntries) }
        val before = CompoundScopeJournal.checkpoint(listOf(scope))
        overloads.clear()
        overloads.add(changed)
        operatorEntries.clear()
        operatorEntries[MCFPPBaseType.Bool] = changed
        functions.clear()
        operators.clear()
        val publishedOverloads = arrayListOf(changed)
        val publishedFunctions = hashMapOf("published" to publishedOverloads)
        val publishedOperators = hashMapOf("published" to hashMapOf<MCFPPType?, Function>(null to changed))
        scope.functions = publishedFunctions
        scope.operators = publishedOperators
        val after = before.captureCurrent()
        before.restore()
        assertSame(functions, scope.functions)
        assertSame(operators, scope.operators)
        assertSame(overloads, functions.getValue("actual.method.alias"))
        assertSame(original, overloads.single())
        assertSame(operatorEntries, operators.getValue("actual.operator.alias"))
        assertSame(original, operatorEntries.getValue(null))
        assertSame(original, operatorEntries.getValue(MCFPPBaseType.Int))
        after.restore()
        assertSame(publishedFunctions, scope.functions)
        assertSame(publishedOperators, scope.operators)
        assertSame(publishedOverloads, scope.functions.getValue("published"))
        assertTrue(functions.isEmpty())
        assertTrue(operators.isEmpty())
        assertSame(changed, overloads.single())
        assertSame(changed, operatorEntries.getValue(MCFPPBaseType.Bool))
        assertEquals(1, operatorEntries.size)
        before.restore()
        assertSame(original, overloads.single())
        assertEquals(2, operatorEntries.size)
        assertSame(functions, scope.functions)
    }

    @Test fun fieldProvenanceMapsAndMutableOriginListsRecoverActualKeysAndIdentity() = inFunction { scope, _ ->
        val owner = DataTemplate("Owner", "test")
        val value = MCInt(3, "field")
        val property = Property.buildSimpleProperty(value)
        val original = CompoundDataScope.FieldOrigin(owner, value, property)
        val changed = CompoundDataScope.FieldOrigin(owner, MCInt(9, "changed"), null)
        val origins = arrayListOf(original)
        val fixed = java.util.Arrays.asList(original, original)
        scope.fieldOrigins["actual.origin.alias"] = origins
        scope.fieldOrigins["fixed"] = fixed
        scope.fieldOrigins["immutable"] = listOf(original)
        val before = CompoundScopeJournal.checkpoint(listOf(scope))
        origins.clear()
        origins.add(changed)
        fixed[0] = changed
        scope.fieldOrigins.remove("actual.origin.alias")
        scope.fieldOrigins["new"] = listOf(changed)
        val after = before.captureCurrent()
        before.restore()
        assertSame(origins, scope.fieldOrigins.getValue("actual.origin.alias"))
        assertSame(original, origins.single())
        assertSame(fixed, scope.fieldOrigins.getValue("fixed"))
        assertSame(original, fixed[0])
        assertFalse("new" in scope.fieldOrigins)
        assertSame(value, before.elements.variables.single())
        assertSame(property, before.elements.properties.single())
        assertSame(owner, before.elements.originOwners.single())
        after.restore()
        assertFalse("actual.origin.alias" in scope.fieldOrigins)
        assertSame(changed, origins.single())
        assertSame(changed, fixed[0])
        assertTrue(after.elements.variables.any { it === value })
        assertTrue(after.elements.variables.any { it === changed.field })
        before.restore()
        assertSame(original, origins.single())
        assertSame(original, fixed[0])
    }

    @Test fun recursiveParentEnrollmentHandlesCyclesAndRestoresDetachedScopes() = inFunction { scope, _ ->
        val parent = this.scope()
        val parentValue = MCInt(4, "parent")
        parent.putVar("parent.alias", parentValue)
        scope.parent.clear()
        scope.parent.add(parent)
        parent.parent.clear()
        parent.parent.add(scope)
        val parents = scope.parent
        val parentParents = parent.parent
        val before = CompoundScopeJournal.checkpoint(listOf(scope))
        parents.clear()
        scope.parent = arrayListOf(null)
        parent.parent.clear()
        parent.removeVar("parent.alias")
        val detached = MCInt(8, "after")
        parent.putVar("new.alias", detached)
        val after = before.captureCurrent()
        before.restore()
        assertSame(parents, scope.parent)
        assertSame(parent, scope.parent.single())
        assertSame(parentParents, parent.parent)
        assertSame(scope, parent.parent.single())
        assertSame(parentValue, parent.getVar("parent.alias"))
        assertNull(parent.getVar("new.alias"))
        after.restore()
        assertNull(scope.parent.single())
        assertTrue(parents.isEmpty())
        assertTrue(parent.parent.isEmpty())
        assertNull(parent.getVar("parent.alias"))
        assertSame(detached, parent.getVar("new.alias"))
        before.restore()
        assertSame(scope, parent.parent.single())
        assertSame(parentValue, parent.getVar("parent.alias"))
    }

    @Test fun captureIsObservationalAndDoesNotMaterializeOrBindAdapters() = inFunction { scope, function ->
        val value = MCInt("unbound")
        scope.putVar("actual.alias", value)
        val symbol = value.symbol
        val path = value.nbtPath
        val commands = function.commands.size
        val before = CompoundScopeJournal.checkpoint(listOf(scope))
        val after = before.captureCurrent()
        before.restore()
        after.restore()
        before.restore()
        assertNull(value.storageBinding)
        assertSame(symbol, value.symbol)
        assertSame(path, value.nbtPath)
        assertEquals(commands, function.commands.size)
        assertSame(value, scope.getVar("actual.alias"))
    }

    @Test fun elementEnrollmentCanComposeStorageAndFunctionJournalsForRealWrites() = inFunction { scope, function ->
        val value = MCInt(5, "field")
        scope.putVar("actual.alias", value)
        val property = Property.buildSimpleProperty(value)
        scope.putProperty("property.alias", property)
        val member = Function("member", "test", null)
        member.commands.add("say before")
        scope.functions["method.alias"] = arrayListOf(member)
        val structural = CompoundScopeJournal.checkpoint(listOf(scope))
        val functions = FunctionStateJournal.checkpoint(listOf(function) + structural.elements.functions, structural.elements.variables)
        val properties = StorageAccess.checkpointGraph(structural.elements.properties)
        scope.removeVar("actual.alias")
        scope.functions.clear()
        scope.removeProperty("property.alias")
        StorageAccess.write(value, MCInt(11))
        member.commands.single().build("changed")
        property.accessModifier = Member.AccessModifier.PRIVATE
        val afterStructural = structural.captureCurrent()
        val afterFunctions = functions.captureCurrent()
        val afterProperties = properties.captureCurrent()
        structural.restore()
        functions.restore()
        properties.restore()
        assertSame(value, scope.getVar("actual.alias"))
        assertSame(member, scope.functions.getValue("method.alias").single())
        assertSame(property, scope.getProperty("property.alias"))
        assertEquals(5, (StorageAccess.read(value) as MCInt).value)
        assertEquals("say before", member.commands.single().toString())
        assertEquals(Member.AccessModifier.PUBLIC, property.accessModifier)
        afterStructural.restore()
        afterFunctions.restore()
        afterProperties.restore()
        assertNull(scope.getVar("actual.alias"))
        assertTrue(scope.functions.isEmpty())
        assertNull(scope.getProperty("property.alias"))
        assertEquals(11, (StorageAccess.read(value) as MCInt).value)
        assertEquals("say before changed", member.commands.single().toString())
        assertEquals(Member.AccessModifier.PRIVATE, property.accessModifier)
        structural.restore()
        functions.restore()
        properties.restore()
        assertSame(value, scope.getVar("actual.alias"))
        assertEquals(5, (StorageAccess.read(value) as MCInt).value)
    }

    @Test fun elementProjectionIsImmutableAndDistinguishesEqualFunctionObjects() = inFunction { scope, _ ->
        val first = Function("equal", "test", null)
        val second = Function("equal", "test", null)
        assertEquals(first, second)
        scope.functions["alias"] = arrayListOf(first, second, first)
        val before = CompoundScopeJournal.checkpoint(listOf(scope))
        assertEquals(2, before.elements.functions.size)
        assertSame(first, before.elements.functions[0])
        assertSame(second, before.elements.functions[1])
        scope.functions.clear()
        val after = before.captureCurrent()
        assertTrue(after.elements.functions.any { it === first })
        assertTrue(after.elements.functions.any { it === second })
        @Suppress("UNCHECKED_CAST")
        val projection = before.elements.functions as MutableList<Function>
        assertFailsWith<UnsupportedOperationException> { projection.clear() }
        assertEquals(2, before.elements.functions.size)
    }

    @Test fun unknownFunctionMapIsRejectedBeforeItsEntriesAreRead() = inFunction { scope, _ ->
        var entryReads = 0
        scope.functions = object : HashMap<String, ArrayList<Function>>() {
            override val entries: MutableSet<MutableMap.MutableEntry<String, ArrayList<Function>>>
                get() {
                    entryReads++
                    return super.entries
                }
        }
        assertFailsWith<IllegalStateException> { CompoundScopeJournal.checkpoint(listOf(scope)) }
        assertEquals(0, entryReads)
    }

    @Test fun unknownNestedOperatorMapIsRejectedBeforeItsEntriesAreRead() = inFunction { scope, _ ->
        var reads = 0
        scope.operators["operator"] = object : HashMap<MCFPPType?, Function>() {
            override val entries: MutableSet<MutableMap.MutableEntry<MCFPPType?, Function>>
                get() {
                    reads++
                    return super.entries
                }
        }
        assertFailsWith<IllegalStateException> { CompoundScopeJournal.checkpoint(listOf(scope)) }
        assertEquals(0, reads)
    }

    @Test fun unknownOverloadAndParentListsAreRejectedBeforeHostCallbacks() = inFunction { scope, _ ->
        var reads = 0
        scope.functions["method"] = object : ArrayList<Function>() {
            override val size: Int get() {
                reads++
                return super.size
            }
            override fun toArray(): Array<Any?> {
                reads++
                return super.toArray()
            }
            override fun iterator(): MutableIterator<Function> {
                reads++
                return super.iterator()
            }
        }
        assertFailsWith<IllegalStateException> { CompoundScopeJournal.checkpoint(listOf(scope)) }
        assertEquals(0, reads)
        scope.functions.clear()
        scope.parent = object : ArrayList<IScope?>() {
            override val size: Int get() {
                reads++
                return super.size
            }
            override fun toArray(): Array<Any?> {
                reads++
                return super.toArray()
            }
            override fun iterator(): MutableIterator<IScope?> {
                reads++
                return super.iterator()
            }
        }
        assertFailsWith<IllegalStateException> { CompoundScopeJournal.checkpoint(listOf(scope)) }
        assertEquals(0, reads)
    }

    @Test fun unknownOriginListIsRejectedBeforeSizeOrIteration() = inFunction { scope, _ ->
        var reads = 0
        scope.fieldOrigins["field"] = object : AbstractList<CompoundDataScope.FieldOrigin>() {
            override val size: Int get() {
                reads++
                return 0
            }
            override fun get(index: Int): CompoundDataScope.FieldOrigin {
                reads++
                error("Host list was read")
            }
        }
        assertFailsWith<IllegalStateException> { CompoundScopeJournal.checkpoint(listOf(scope)) }
        assertEquals(0, reads)
    }

    @Test fun erasedFunctionRegistrationKeysAreRejectedWithoutHashingThroughEitherEntryPoint() = inFunction { scope, _ ->
        var hashes = 0
        val key = object {
            override fun hashCode(): Int {
                hashes++
                return 7
            }
        }
        @Suppress("UNCHECKED_CAST")
        val erased = scope.functions as MutableMap<Any?, Any?>
        erased[key] = arrayListOf(Function("member", "test", null))
        hashes = 0
        assertFailsWith<IllegalStateException> { scope.checkpointRelations() }
        assertEquals(0, hashes)
        val template = DataTemplate("Combined", "test").apply { this.scope = scope }
        assertFailsWith<IllegalStateException> { template.checkpointDeclarationState() }
        assertEquals(0, hashes)
    }

    @Test fun anOrdinaryTypedOperatorKeyCannotInvokeAnUnknownTypeGetterDuringCapture() = inFunction { scope, _ ->
        var reads = 0
        val unknown = object : MCFPPType() {
            override val typeId: TypeId get() {
                reads++
                return TypeId.Builtin("host")
            }
        }
        scope.operators["+"] = hashMapOf<MCFPPType?, Function>(unknown to Function("operator", "test", null))
        reads = 0
        assertFailsWith<IllegalStateException> { scope.checkpointRelations() }
        val template = DataTemplate("CombinedOperator", "test").apply { this.scope = scope }
        assertFailsWith<IllegalStateException> { template.checkpointDeclarationState() }
        assertEquals(0, reads)
    }

    @Test fun knownGenericOperatorKeysCheckNestedTypesBeforeComputingTheirIdentity() = inFunction { scope, _ ->
        var reads = 0
        val unknown = object : MCFPPType() {
            override val typeId: TypeId get() {
                reads++
                return TypeId.Builtin("nested-host")
            }
        }
        val key = MCFPPListType(MCFPPBaseType.Int)
        scope.operators["+"] = hashMapOf<MCFPPType?, Function>(key to Function("operator", "test", null))
        key.generic[0] = unknown
        reads = 0
        assertFailsWith<IllegalStateException> { scope.checkpointRelations() }
        val template = DataTemplate("CombinedNested", "test").apply { this.scope = scope }
        assertFailsWith<IllegalStateException> { template.checkpointDeclarationState() }
        assertEquals(0, reads)
    }

    @Test fun nestedImmutableIdentityPayloadsAreCheckedBeforeTheMapCanHashTheirErasedHost() = inFunction { scope, _ ->
        var hashes = 0
        val host = object {
            override fun hashCode(): Int {
                hashes++
                return 3
            }
        }
        @Suppress("UNCHECKED_CAST")
        val payload = CompilerValue.Sequence(listOf(host) as List<CompilerValue>)
        val identity = TypeId.Specialized(TypeId.Declaration("template", "test", "Specialized"), listOf(payload))
        val key = MCFPPGenericDataTemplateType(DataTemplate("Specialized", "test"), arrayListOf(), identity)
        scope.operators["+"] = hashMapOf<MCFPPType?, Function>(key to Function("operator", "test", null))
        hashes = 0
        assertFailsWith<IllegalStateException> { scope.checkpointRelations() }
        assertEquals(0, hashes)
    }

    @Test fun operatorKeyRootsCanComposeTypeRestorationBeforeEveryRepeatedMapRehash() = inFunction { scope, _ ->
        val key = MCFPPListType(MCFPPBaseType.Int)
        val original = Function("original", "test", null)
        val changed = Function("changed", "test", null)
        val originalEntries = hashMapOf<MCFPPType?, Function>(key to original)
        val originalOperators = scope.operators.apply { put("actual.alias", originalEntries) }
        val before = scope.checkpointRelations()
        assertTrue(before.elements.types.any { it === key })
        assertTrue(before.elements.types.any { it === MCFPPBaseType.Int })
        originalEntries.clear()
        originalOperators.clear()
        key.generic[0] = MCFPPBaseType.Bool
        val changedEntries = hashMapOf<MCFPPType?, Function>(key to changed)
        val changedOperators = hashMapOf("published" to changedEntries)
        scope.operators = changedOperators
        val after = before.captureCurrent()
        assertTrue(after.elements.types.any { it === MCFPPBaseType.Int })
        assertTrue(after.elements.types.any { it === MCFPPBaseType.Bool })
        repeat(3) {
            // Type internals belong to the composing declaration journal and precede relation restoration.
            key.generic[0] = MCFPPBaseType.Int
            before.restore()
            assertSame(originalOperators, scope.operators)
            assertSame(originalEntries, scope.operators.getValue("actual.alias"))
            assertSame(original, originalEntries[MCFPPListType(MCFPPBaseType.Int)])
            key.generic[0] = MCFPPBaseType.Bool
            after.restore()
            assertSame(changedOperators, scope.operators)
            assertSame(changedEntries, scope.operators.getValue("published"))
            assertSame(changed, changedEntries[MCFPPListType(MCFPPBaseType.Bool)])
            assertTrue(originalEntries.isEmpty())
            assertTrue(originalOperators.isEmpty())
        }
    }

    @Test fun restoreRechecksAllKeyDependenciesBeforeChangingAnyScopeRelationship() = inFunction { scope, _ ->
        var reads = 0
        val unknown = object : MCFPPType() {
            override val typeId: TypeId get() {
                reads++
                return TypeId.Builtin("later-host")
            }
        }
        val key = MCFPPListType(MCFPPBaseType.Int)
        val member = Function("operator", "test", null)
        scope.operators["+"] = hashMapOf<MCFPPType?, Function>(key to member)
        val before = scope.checkpointRelations()
        val published = hashMapOf("new" to arrayListOf(member))
        scope.functions = published
        key.generic[0] = unknown
        assertFailsWith<IllegalStateException> { before.restore() }
        assertSame(published, scope.functions)
        assertEquals(0, reads)
        key.generic[0] = MCFPPBaseType.Int
        before.restore()
        assertTrue(scope.functions.isEmpty())
        assertSame(member, scope.operators.getValue("+")[MCFPPListType(MCFPPBaseType.Int)])
    }

    @Test fun cyclicGenericHashDependenciesAreRejectedBeforeRecursiveTypeIdentityEvaluation() = inFunction { scope, _ ->
        val key = MCFPPListType(MCFPPBaseType.Int)
        scope.operators["+"] = hashMapOf<MCFPPType?, Function>(key to Function("operator", "test", null))
        key.generic[0] = key
        val failure = assertFailsWith<IllegalStateException> { scope.checkpointRelations() }
        assertTrue(failure.message!!.contains("cyclic generic"))
    }

    @Test fun invalidErasedRegistrationValuesAreRejectedWithoutCallingTheHost() = inFunction { scope, _ ->
        var callbacks = 0
        val host = object {
            override fun hashCode(): Int {
                callbacks++
                return 1
            }
            override fun toString(): String {
                callbacks++
                return "host"
            }
        }
        @Suppress("UNCHECKED_CAST")
        val functions = scope.functions as MutableMap<Any?, Any?>
        functions["method"] = host
        assertFailsWith<IllegalStateException> { scope.checkpointRelations() }
        functions["method"] = arrayListOf(host)
        assertFailsWith<IllegalStateException> { scope.checkpointRelations() }
        assertEquals(0, callbacks)
    }

    @Test fun oneMapCannotUseAnOverloadValidationToBypassItsOperatorRole() = inFunction { scope, _ ->
        scope.functions["method"] = arrayListOf(Function("member", "test", null))
        @Suppress("UNCHECKED_CAST")
        val aliased = scope.functions as HashMap<MCFPPType?, Function>
        scope.operators["+"] = aliased
        val failure = assertFailsWith<IllegalStateException> { scope.checkpointRelations() }
        assertTrue(failure.message!!.contains("invalid operator key"))
        val template = DataTemplate("AliasedMap", "test").apply { this.scope = scope }
        val combined = assertFailsWith<IllegalStateException> { template.checkpointDeclarationState() }
        assertTrue(combined.message!!.contains("invalid operator key"))
        assertSame<Any>(scope.functions, scope.operators.getValue("+"))
    }

    @Test fun oneListCannotUseAFunctionValidationToBypassItsFieldOriginRole() = inFunction { scope, _ ->
        val overloads = arrayListOf(Function("member", "test", null))
        scope.functions["method"] = overloads
        @Suppress("UNCHECKED_CAST")
        val aliased = overloads as List<CompoundDataScope.FieldOrigin>
        scope.fieldOrigins["field"] = aliased
        val failure = assertFailsWith<IllegalStateException> { scope.checkpointRelations() }
        assertTrue(failure.message!!.contains("invalid ORIGINS list element"))
        val template = DataTemplate("AliasedList", "test").apply { this.scope = scope }
        val combined = assertFailsWith<IllegalStateException> { template.checkpointDeclarationState() }
        assertTrue(combined.message!!.contains("invalid ORIGINS list element"))
        assertSame<Any>(overloads, scope.fieldOrigins.getValue("field"))
    }

    @Test fun repeatedSameRoleAliasesRestoreOriginalAndDetachedContainersAcrossGenerations() = inFunction { scope, _ ->
        val original = Function("original", "test", null)
        val changed = Function("changed", "test", null)
        val overloads = arrayListOf(original)
        val operators = hashMapOf<MCFPPType?, Function>(null to original)
        val functionTable = scope.functions.apply {
            put("first", overloads)
            put("second", overloads)
        }
        val operatorTable = scope.operators.apply {
            put("+", operators)
            put("-", operators)
        }
        val before = scope.checkpointRelations()
        assertEquals(1, before.elements.functions.size)
        assertSame(original, before.elements.functions.single())
        overloads.clear()
        overloads.add(changed)
        operators.clear()
        operators[MCFPPBaseType.Int] = changed
        functionTable.clear()
        operatorTable.clear()
        val publishedFunctions = hashMapOf("published" to overloads)
        val publishedOperators = hashMapOf("published" to operators)
        scope.functions = publishedFunctions
        scope.operators = publishedOperators
        val after = before.captureCurrent()
        assertEquals(2, after.elements.functions.size)
        assertTrue(after.elements.functions.any { it === original })
        assertTrue(after.elements.functions.any { it === changed })
        repeat(3) {
            before.restore()
            assertSame(functionTable, scope.functions)
            assertSame(operatorTable, scope.operators)
            assertSame(overloads, functionTable.getValue("first"))
            assertSame(overloads, functionTable.getValue("second"))
            assertSame(operators, operatorTable.getValue("+"))
            assertSame(operators, operatorTable.getValue("-"))
            assertSame(original, overloads.single())
            assertSame(original, operators.getValue(null))
            after.restore()
            assertSame(publishedFunctions, scope.functions)
            assertSame(publishedOperators, scope.operators)
            assertSame(overloads, scope.functions.getValue("published"))
            assertSame(operators, scope.operators.getValue("published"))
            assertTrue(functionTable.isEmpty())
            assertTrue(operatorTable.isEmpty())
            assertSame(changed, overloads.single())
            assertSame(changed, operators.getValue(MCFPPBaseType.Int))
            assertEquals(1, operators.size)
        }
        before.restore()
        assertSame(original, overloads.single())
        assertSame(original, operators.getValue(null))
    }
}
