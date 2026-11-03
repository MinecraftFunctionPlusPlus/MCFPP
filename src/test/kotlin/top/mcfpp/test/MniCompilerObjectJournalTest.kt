package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.core.lang.obj.TypeDataTemplateObject
import top.mcfpp.lib.*
import top.mcfpp.mni.annotation.NoExternalWrites
import top.mcfpp.mni.expand.CompilerObjectJournal
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Member
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.TypeDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.property.*
import top.mcfpp.model.scope.CompoundDataScope
import top.mcfpp.model.scope.FunctionScope
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import kotlin.test.*
import kotlin.test.Test

class MniCompilerObjectJournalTest {
    private inline fun inFunction(action: (Function) -> Unit) {
        Project.init()
        Project.config.version = "26.3"
        val previous = Function.currFunction
        val forced = Function.forcedField
        val function = Function("objectJournal", "test", null)
        Function.currFunction = function
        try {
            action(function)
            assertEquals(0, Project.errorCount)
        } finally {
            Function.currFunction = previous
            Function.forcedField = forced
        }
    }

    private fun schema(name: String = "Schema"): DataTemplate = DataTemplate(name, "test").apply {
        val value = MCInt(3, "field")
        scope.putVar("field", value)
        scope.putProperty("field", Property.buildSimpleProperty(value))
    }

    @Test fun lazyCaptureDoesNotConstructFieldsOrAllocateStorage() = inFunction { function ->
        val value = DataTemplateObject(schema(), "object")
        val commands = function.commands.size
        val before = CompilerObjectJournal.checkpoint(listOf(value))
        assertNull(value.journalInstanceField())
        assertNull(value.storageBinding)
        assertNull(value.symbol)
        assertEquals(commands, function.commands.size)
        val cache = value.instanceField
        val field = assertNotNull(cache.getVar("field"))
        val after = before.captureCurrent()
        before.restore()
        assertNull(value.journalInstanceField())
        after.restore()
        assertSame(cache, value.journalInstanceField())
        assertSame(field, cache.getVar("field"))
        before.restore()
        assertNull(value.journalInstanceField())
        assertNull(value.storageBinding)
    }

    @Test fun publishedCacheRestoresAliasesMetadataAndActualStoredValues() = inFunction { _ ->
        val value = DataTemplateObject(schema(), "object")
        val cache = value.instanceField
        val field = assertIs<MCInt>(cache.getVar("field"))
        val property = assertNotNull(cache.getProperty("field"))
        cache.putVar("real.alias", field)
        cache.putProperty("property.alias", property)
        val symbol = field.symbol
        val before = CompilerObjectJournal.checkpoint(listOf(value))
        cache.removeVar("field")
        cache.removeVar("real.alias")
        cache.removeProperty("field")
        cache.removeProperty("property.alias")
        field.identifier = "prepared"
        field.parent = null
        property.parent = null
        property.isFinal = true
        property.accessModifier = Member.AccessModifier.PRIVATE
        StorageAccess.write(field, MCInt(19))
        value.instanceField = CompoundDataScope(arrayListOf(null))
        val replacement = value.instanceField
        val after = before.captureCurrent()
        before.restore()
        assertSame(cache, value.journalInstanceField())
        assertSame(field, cache.getVar("field"))
        assertSame(field, cache.getVar("real.alias"))
        assertSame(symbol, field.symbol)
        assertEquals("field", field.identifier)
        assertSame(value, field.parent)
        assertSame(property, cache.getProperty("property.alias"))
        assertSame(value, property.parent)
        assertFalse(property.isFinal)
        assertEquals(Member.AccessModifier.PUBLIC, property.accessModifier)
        assertEquals(3, (StorageAccess.read(field) as MCInt).value)
        after.restore()
        assertSame(replacement, value.journalInstanceField())
        assertNull(cache.getVar("field"))
        assertNull(cache.getProperty("field"))
        assertEquals("prepared", field.identifier)
        assertNull(field.parent)
        assertTrue(property.isFinal)
        assertEquals(19, (StorageAccess.read(field) as MCInt).value)
        before.restore()
        assertSame(field, cache.getVar("field"))
        assertEquals(3, (StorageAccess.read(field) as MCInt).value)
    }

    @Test fun nestedObjectsAndSharedCachesKeepIdentityAfterDisconnection() = inFunction { _ ->
        val template = schema()
        val outer = DataTemplateObject(template, "outer")
        val child = DataTemplateObject(template, "child").apply { parent = outer }
        val shared = child.instanceField
        outer.instanceField = CompoundDataScope(arrayListOf(null)).apply {
            putVar("first", child)
            putVar("second", child)
        }
        val outerCache = outer.instanceField
        val field = assertIs<MCInt>(shared.getVar("field"))
        val before = CompilerObjectJournal.checkpoint(listOf(child))
        outerCache.removeVar("first")
        outerCache.removeVar("second")
        child.parent = null
        shared.removeVar("field")
        field.identifier = "detached"
        child.instanceField = outerCache
        val after = before.captureCurrent()
        before.restore()
        assertSame(outer, child.parent)
        assertSame(child, outerCache.getVar("first"))
        assertSame(child, outerCache.getVar("second"))
        assertSame(shared, child.journalInstanceField())
        assertSame(field, shared.getVar("field"))
        after.restore()
        assertSame(outerCache, child.journalInstanceField())
        assertNull(child.parent)
        assertNull(outerCache.getVar("first"))
        assertNull(shared.getVar("field"))
        assertEquals("detached", field.identifier)
        before.restore()
        assertSame(shared, child.journalInstanceField())
        assertEquals("field", field.identifier)
    }

    @Test fun typeAliasObjectsDoNotEvaluateTheirComputedDelegateDuringCapture() = inFunction { function ->
        val value = TypeDataTemplateObject(TypeDataTemplate(MCFPPBaseType.Int, "Counter", "test"), "counter")
        val commands = function.commands
        val originalCommands = commands.toList()
        val originalText = originalCommands.map { it.toString() }
        val before = CompilerObjectJournal.checkpoint(listOf(value), listOf(function))
        assertNull(value.storageBinding)
        assertNull(value.symbol)
        assertSame(commands, function.commands)
        assertEquals(originalText, commands.map { it.toString() })
        StorageAccess.write(value.delegateVar, MCInt(8))
        assertNotNull(value.storageBinding)
        assertTrue(function.commands.size > originalCommands.size, "The explicit delegate write must emit real commands")
        before.restore()
        assertNull(value.storageBinding)
        assertNull(value.symbol)
        assertSame(commands, function.commands)
        assertEquals(originalText, commands.map { it.toString() })
        originalCommands.forEachIndexed { index, command -> assertSame(command, function.commands[index]) }
    }

    @Test fun accessorFunctionReplacementAndDetachedBodiesRestoreTogether() = inFunction { _ ->
        val value = DataTemplateObject(schema(), "object")
        val getter = Function("getter", "test", null)
        val replacement = Function("replacement", "test", null)
        val accessor = FunctionAccessor(getter)
        val native = NativeFunction("setter")
        val changedNative = NativeFunction("changedSetter")
        val mutator = NativeMutator(native)
        val property = Property("custom", accessor, mutator).apply { parent = value }
        value.instanceField.putProperty("custom.alias", property)
        val before = CompilerObjectJournal.checkpoint(listOf(value))
        accessor.function = replacement
        mutator.function = changedNative
        getter.commands.add("say detached")
        replacement.commands.add("say replacement")
        native.identifier = "detachedSetter"
        changedNative.identifier = "preparedSetter"
        value.instanceField.removeProperty("custom.alias")
        val after = before.captureCurrent()
        before.restore()
        assertSame(getter, accessor.function)
        assertTrue(getter.commands.isEmpty())
        assertSame(native, mutator.function)
        assertEquals("setter", native.identifier)
        assertSame(property, value.instanceField.getProperty("custom.alias"))
        after.restore()
        assertSame(replacement, accessor.function)
        assertEquals("say detached", getter.commands.single().toString())
        assertSame(changedNative, mutator.function)
        assertEquals("preparedSetter", changedNative.identifier)
        assertNull(value.instanceField.getProperty("custom.alias"))
        before.restore()
        assertSame(getter, accessor.function)
        assertTrue(getter.commands.isEmpty())
        assertSame(native, mutator.function)
    }

    @Test fun standardAccessorsAndOpaqueNativeClosuresAreCapturedWithoutInvocation() = inFunction { _ ->
        val value = DataTemplateObject(schema(), "object")
        val field = assertNotNull(value.instanceField.getVar("field"))
        val setter = Function("setter", "test", null)
        val native = NativeFunction("getter")
        var calls = 0
        val anonymous = AnonymousNativeMutator { _, argument ->
            calls++
            StorageAccess.write(field, argument)
        }
        val closureProperty = Property("closure", SimpleAccessor(), anonymous).apply { parent = value }
        value.instanceField.putProperty("closure", closureProperty)
        value.instanceField.putProperty("native", Property("native", NativeAccessor(native), FunctionMutator(setter)))
        val before = CompilerObjectJournal.checkpoint(listOf(value))
        assertEquals(0, calls)
        val prepared = before.prepare {
            closureProperty.setter(value, field, MCInt(14))
            setter.commands.add("say changed")
        }
        assertEquals(1, calls)
        assertEquals(3, (StorageAccess.read(field) as MCInt).value)
        assertTrue(setter.commands.isEmpty())
        prepared.apply()
        assertEquals(14, (StorageAccess.read(field) as MCInt).value)
        assertEquals("say changed", setter.commands.single().toString())
        prepared.rollback()
        prepared.apply()
        prepared.rollback()
        assertEquals(1, calls)
        assertSame(anonymous, closureProperty.mutator)
        assertEquals(3, (StorageAccess.read(field) as MCInt).value)
    }

    @Test fun objectsReachedThroughFunctionCommandsAndDisplayAdaptersAreEnrolled() = inFunction { function ->
        val value = DataTemplateObject(schema(), "object")
        val field = assertNotNull(value.instanceField.getVar("field"))
        val displayValue = MCInt(1, "display").apply { parent = value }
        val display = ListChatComponent().apply { components.add(ScoreChatComponent(displayValue)) }
        val root = MCInt(2, "root").apply { sbObject = SbObject("journalDisplay", "dummy", display) }
        val command = Command("say").buildMacro(root)
        function.commands.add(command)
        val before = CompilerObjectJournal.checkpoint(functions = listOf(function))
        value.instanceField.removeVar("field")
        value.instanceField = CompoundDataScope(arrayListOf(null))
        StorageAccess.write(field, MCInt(17))
        before.restore()
        assertSame(field, value.instanceField.getVar("field"))
        assertEquals(3, (StorageAccess.read(field) as MCInt).value)
        assertSame(command, function.commands.single())
        assertSame(value, displayValue.parent)
    }

    @Test fun failedNativePreparationRestoresCacheStorageCommandsAndCurrentFunction() = inFunction { function ->
        val value = DataTemplateObject(schema(), "object")
        val cache = value.instanceField
        val field = assertIs<MCInt>(cache.getVar("field"))
        val command = Command("before", "slot").also { function.commands.add(it) }
        function.scope = FunctionScope(FunctionScope(null).apply { vars["external"] = field })
        val native = NativeFunction("mutate", javaMethod = ObjectJournalNative::class.java.getMethod("mutate"))
        ObjectJournalNative.action = {
            command.replace("slot" to "prepared")
            StorageAccess.write(field, MCInt(23))
            cache.removeVar("field")
            value.instanceField = CompoundDataScope(arrayListOf(null))
        }
        ObjectJournalNative.calls = 0
        EffectAnalysis.withActualEffects(function) {
            val before = CompilerObjectJournal.checkpoint(listOf(value), listOf(function, native))
            val failure = assertFailsWith<IllegalArgumentException> {
                before.prepare {
                    assertFalse(native.invoke(emptyList(), emptyList(), null).isError)
                    Function.currFunction = Function("stray", "test", null)
                    Function.forcedField = null
                    throw IllegalArgumentException("abort")
                }
            }
            assertEquals("abort", failure.message)
            assertEquals(1, ObjectJournalNative.calls)
            assertSame(function, Function.currFunction)
            assertSame(cache, value.journalInstanceField())
            assertSame(field, cache.getVar("field"))
            assertEquals(3, (StorageAccess.read(field) as MCInt).value)
            assertEquals("before", command.toString())
            assertSame(command, function.commands.single())
            function.bodyCompiled = true
        }
        assertSame(Effect.Pure, function.runtimeEffect, "A failed native cache write must not leak its external-write effect")
    }

    @Test fun nativeAfterStateAppliesOnceAndStillProducesTheExpectedMaterializedValue() = inFunction { function ->
        val value = DataTemplateObject(schema(), "object")
        val cache = value.instanceField
        val field = assertIs<MCInt>(cache.getVar("field"))
        function.scope = FunctionScope(FunctionScope(null).apply { vars["external"] = field })
        val native = NativeFunction("mutate", javaMethod = ObjectJournalNative::class.java.getMethod("mutate"))
        ObjectJournalNative.calls = 0
        ObjectJournalNative.action = {
            StorageAccess.write(field, MCInt(29))
            cache.putVar("alias", field)
        }
        EffectAnalysis.withActualEffects(function) {
            val prepared = CompilerObjectJournal.checkpoint(listOf(value), listOf(function, native)).prepare {
                assertFalse(native.invoke(emptyList(), emptyList(), null).isError)
            }
            assertEquals(1, ObjectJournalNative.calls)
            assertNull(cache.getVar("alias"))
            prepared.apply()
            assertSame(field, cache.getVar("alias"))
            assertEquals(29, (StorageAccess.read(field) as MCInt).value)
            StorageAccess.materialize(field)
            assertEquals(
                29,
                ScoreCommandExecutor(
                    listOf("data modify storage mcfpp:system stack_frame prepend value {}") +
                        function.commands.map { it.analyze() }
                ).read(field)
            )
            prepared.rollback()
            prepared.apply()
            prepared.rollback()
            assertEquals(1, ObjectJournalNative.calls)
            assertNull(cache.getVar("alias"))
            assertEquals(3, (StorageAccess.read(field) as MCInt).value)
            function.bodyCompiled = true
        }
        assertSame(Effect.Pure, function.runtimeEffect, "Repeated native after-state application and rollback must restore effect classification")
    }

    @Test fun failedWriteRestoresTheActiveEffectCollectorToPure() = inFunction { function ->
        val external = MCInt(5, "external").apply { bindDeclaration() }
        val parent = FunctionScope(null).apply { vars["external"] = external }
        function.scope = FunctionScope(parent)
        EffectAnalysis.withActualEffects(function) {
            val before = CompilerObjectJournal.checkpoint()
            assertFailsWith<IllegalStateException> {
                before.prepare {
                    StorageAccess.write(external, MCInt(11))
                    error("abort effect")
                }
            }
            assertEquals(5, (StorageAccess.read(external) as MCInt).value)
            function.bodyCompiled = true
        }
        assertSame(Effect.Pure, function.runtimeEffect)
    }

    @Test fun applyingPreparedWritesRestoresExternalAndLocalEffectClassification() = inFunction { function ->
        val external = MCInt(5, "external").apply { bindDeclaration() }
        val local = MCInt(7, "local").apply { bindDeclaration() }
        val parent = FunctionScope(null).apply { vars["external"] = external }
        function.scope = FunctionScope(parent).apply { vars["local"] = local }
        EffectAnalysis.withActualEffects(function) {
            val prepared = CompilerObjectJournal.checkpoint().prepare {
                StorageAccess.write(external, MCInt(11))
                StorageAccess.write(local, MCInt(13))
            }
            assertEquals(5, (StorageAccess.read(external) as MCInt).value)
            prepared.apply()
            assertEquals(11, (StorageAccess.read(external) as MCInt).value)
            prepared.rollback()
            prepared.apply()
            function.bodyCompiled = true
        }
        val effect = assertIs<Effect.Writes>(function.runtimeEffect)
        assertEquals(setOf(StorageAccess.ensure(external).place), effect.places)
        assertFalse(StorageAccess.ensure(local).place in effect.places)
    }

    @Test fun rollbackRemovesPreparedLocalClassificationWithoutDiscardingTheAdapter() = inFunction { function ->
        val leaked = MCInt(5, "notInScope").apply { bindDeclaration() }
        val place = StorageAccess.ensure(leaked).place
        EffectAnalysis.withActualEffects(function) {
            val before = CompilerObjectJournal.checkpoint(listOf(leaked))
            before.prepare {
                EffectAnalysis.declared(place)
                StorageAccess.write(leaked, MCInt(7))
            }
            StorageAccess.write(leaked, MCInt(9))
            function.bodyCompiled = true
        }
        assertSame(Effect.Unknown, function.runtimeEffect, "A rolled-back declaration must not prove a later write local")
    }

    @Test fun nestedCollectorsKeepTheirParentAndReturnToTheOriginalActiveFrame() = inFunction { outer ->
        val inner = Function("inner", "test", null)
        EffectAnalysis.withActualEffects(outer) {
            EffectAnalysis.recordEffect(Effect.ReadsRuntime)
            EffectAnalysis.withActualEffects(inner) {
                val before = CompilerObjectJournal.checkpoint()
                assertFailsWith<IllegalArgumentException> {
                    before.prepare {
                        EffectAnalysis.recordEffect(Effect.Unknown)
                        EffectAnalysis.withActualEffects(Function("third", "test", null)) {
                            EffectAnalysis.recordEffect(Effect.Unknown)
                        }
                        throw IllegalArgumentException("abort nested")
                    }
                }
                inner.bodyCompiled = true
            }
            outer.bodyCompiled = true
        }
        assertSame(Effect.Pure, inner.runtimeEffect)
        assertSame(Effect.ReadsRuntime, outer.runtimeEffect)
    }

    @Test fun detachedEffectFramesAndPublishedMutableWriteSetsRestoreByIdentity() = inFunction { function ->
        val first = MCInt(1, "first").apply { bindDeclaration() }
        val second = MCInt(2, "second").apply { bindDeclaration() }
        function.scope = FunctionScope(
            FunctionScope(null).apply {
                vars["first"] = first
                vars["second"] = second
            }
        )
        lateinit var before: CompilerObjectJournal
        EffectAnalysis.withActualEffects(function) {
            StorageAccess.write(first, MCInt(3))
            StorageAccess.write(second, MCInt(4))
            before = CompilerObjectJournal.checkpoint()
            function.bodyCompiled = true
        }
        val published = assertIs<Effect.Writes>(function.runtimeEffect)
        val original = published.places.toSet()
        val changed = Place(SymbolId.fresh())
        @Suppress("UNCHECKED_CAST")
        val places = published.places as MutableSet<Place>
        places.clear()
        places.add(changed)
        val after = before.captureCurrent()
        try {
            before.restore()
            assertEquals(original, places)
            after.restore()
            assertSame(published, function.runtimeEffect)
            assertSame(places, published.places)
            assertEquals(setOf(changed), places)
            before.restore()
            assertEquals(original, places)
        } finally {
            // before was captured inside the collector; after was captured with no active frame.
            after.restore()
        }
    }

    @Test fun inactiveFunctionSummariesComposeWithoutAnActiveCollector() = inFunction { function ->
        val first = Place(SymbolId.fresh())
        val changed = Place(SymbolId.fresh())
        val places = linkedSetOf(first)
        val contents = linkedSetOf(first)
        val effect = Effect.Writes(places, contents)
        function.runtimeEffect = effect
        val before = CompilerObjectJournal.checkpoint(functions = listOf(function))
        places.clear()
        places.add(changed)
        contents.clear()
        function.runtimeEffect = Effect.Unknown
        val after = before.captureCurrent()
        before.restore()
        assertSame(effect, function.runtimeEffect)
        assertSame(places, effect.places)
        assertSame(contents, effect.contents)
        assertEquals(setOf(first), places)
        assertEquals(setOf(first), contents)
        after.restore()
        assertSame(Effect.Unknown, function.runtimeEffect)
        assertEquals(setOf(changed), places)
        assertTrue(contents.isEmpty())
        before.restore()
        assertSame(effect, function.runtimeEffect)
        assertEquals(setOf(first), places)
        assertEquals(setOf(first), contents)
    }

    @Test fun replacingAnActiveEffectRetainsItsDetachedPublishedPlacesAndContents() = inFunction { function ->
        val first = MCInt(1, "first").apply { bindDeclaration() }
        val second = MCInt(2, "second").apply { bindDeclaration() }
        function.scope = FunctionScope(
            FunctionScope(null).apply {
                vars["first"] = first
                vars["second"] = second
            }
        )
        val originals = setOf(StorageAccess.ensure(first).place, StorageAccess.ensure(second).place)
        lateinit var before: CompilerObjectJournal
        EffectAnalysis.withActualEffects(function) {
            EffectAnalysis.recordEffect(Effect.Writes(originals, originals))
            before = CompilerObjectJournal.checkpoint()
            function.bodyCompiled = true
        }
        val published = assertIs<Effect.Writes>(function.runtimeEffect)
        val places = assertIs<MutableSet<Place>>(published.places)
        val contents = assertIs<MutableSet<Place>>(published.contents)
        val inactive = EffectAnalysis.checkpointActualEffects()
        try {
            before.restore()
            // No function summary now retains the published Writes object. Only the frame journal does.
            assertSame(Effect.Unknown, function.runtimeEffect)
            EffectAnalysis.recordEffect(Effect.Unknown)
            val changed = Place(SymbolId.fresh())
            places.clear()
            places.add(changed)
            contents.clear()
            contents.add(changed)
            val after = before.captureCurrent()
            repeat(2) {
                before.restore()
                assertSame(places, published.places)
                assertSame(contents, published.contents)
                assertEquals(originals, places)
                assertEquals(originals, contents)
                after.restore()
                assertSame(Effect.Unknown, function.runtimeEffect)
                assertEquals(setOf(changed), places)
                assertEquals(setOf(changed), contents)
            }
            before.restore()
            assertEquals(originals, places)
            assertEquals(originals, contents)
        } finally {
            inactive.restore()
        }
    }

    @Test fun unknownAccessorIsRejectedBeforeGetterSetterOrPreparationRuns() = inFunction { _ ->
        val value = DataTemplateObject(schema(), "object")
        var callbacks = 0
        val accessor = object : AbstractAccessor() {
            override fun getter(caller: CanSelectMember, field: Var<*>): Var<*> {
                callbacks++
                return field
            }
        }
        value.instanceField.putProperty("unknown", Property("unknown", accessor, SimpleMutator()))
        val failure = assertFailsWith<IllegalStateException> {
            CompilerObjectJournal.checkpoint(listOf(value)).prepare { callbacks++ }
        }
        assertTrue(failure.message.orEmpty().contains("accessor"))
        assertEquals(0, callbacks)
    }

    @Test fun unknownAdapterIsRejectedBeforeReadingOverriddenCompilerProperties() = inFunction { _ ->
        var reads = 0
        val value = object : MCInt("unknown") {
            override var parent: CanSelectMember?
                get() {
                    reads++
                    return null
                }
                set(value) {
                    reads++
                }
        }
        reads = 0
        assertFailsWith<IllegalStateException> { CompilerObjectJournal.checkpoint(listOf(value)) }
        assertEquals(0, reads)
    }

    @Test fun unknownRootContainerIsRejectedBeforeIteratorSizeOrCopyCallbacks() = inFunction { _ ->
        var reads = 0
        val roots = object : ArrayList<Var<*>>() {
            override val size: Int get() {
                reads++
                return super.size
            }
            override fun iterator(): MutableIterator<Var<*>> {
                reads++
                return super.iterator()
            }
            override fun toArray(): Array<Any?> {
                reads++
                return super.toArray()
            }
        }
        roots.add(MCInt(1))
        reads = 0
        assertFailsWith<IllegalStateException> { CompilerObjectJournal.checkpoint(roots) }
        assertEquals(0, reads)
    }

    @Test fun unknownAnnotationContainerIsRejectedBeforeStorageReadsItsContents() = inFunction { _ ->
        var reads = 0
        val value = MCInt(1, "value")
        value.annotations = object : ArrayList<top.mcfpp.model.annotation.Annotation>() {
            override val size: Int get() {
                reads++
                return super.size
            }
            override fun iterator(): MutableIterator<top.mcfpp.model.annotation.Annotation> {
                reads++
                return super.iterator()
            }
            override fun toArray(): Array<Any?> {
                reads++
                return super.toArray()
            }
        }
        assertFailsWith<IllegalStateException> { CompilerObjectJournal.checkpoint(listOf(value)) }
        assertEquals(0, reads)
    }
}

object ObjectJournalNative {
    var action: () -> Unit = {}
    var calls = 0
    @JvmStatic @NoExternalWrites
    fun mutate() {
        calls++
        action()
    }
}
