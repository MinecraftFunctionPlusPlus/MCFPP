package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.antlr.MCFPPFieldVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.UnionTypeVar
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.core.lang.obj.EnumVar
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.annotation.NoExternalWrites
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.Enum
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.ConstructorPlan
import top.mcfpp.model.function.DataTemplateConstructor
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.property.FunctionAccessor
import top.mcfpp.model.property.FunctionMutator
import top.mcfpp.model.property.NativeAccessor
import top.mcfpp.model.property.NativeMutator
import top.mcfpp.model.property.Property
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPEnumType
import top.mcfpp.type.MCFPPNBTType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPUnionType
import top.mcfpp.util.LogProcessor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.apache.logging.log4j.core.config.Property as LogProperty

/** Direct declaration models deliberately do not load the bundled library index. */
class DataInheritanceLayoutRestoreTest {
    @Test fun unionFieldTypesSurviveInheritanceSnapshotsAndFreshReceivingModels() {
        typedFieldRoundTrip({ MCFPPUnionType(MCFPPNBTType.IntArray, MCFPPNBTType.NBT) }) {
            assertIs<UnionTypeVar>(it)
        }
    }

    @Test fun enumFieldTypesSurviveInheritanceSnapshotsAndFreshReceivingModels() {
        typedFieldRoundTrip({
            Enum("Variant", "layout.model").apply {
                addMember("first")
                addMember("second")
            }.getType()
        }) { assertIs<EnumVar>(it) }
    }

    private fun typedFieldRoundTrip(typeFactory: () -> MCFPPType, checkAdapter: (Var<*>) -> Unit) {
        Project.init()
        fun root(type: MCFPPType) = model("TypedRoot").also {
            field(it, type).annotations.add(Marker())
        }
        fun verify(root: DataTemplate, descendants: List<DataTemplate>, expected: MCFPPType) {
            val declaration = root.scope.getVar("value")!!
            assertEquals(expected.typeId, declaration.type.typeId)
            checkAdapter(declaration)
            if (expected is MCFPPEnumType) assertSame(expected.enum, (declaration as EnumVar).enum)
            val fields = descendants.map { template ->
                val inherited = template.scope.getVar("value")!!
                checkAdapter(inherited)
                assertEquals(expected.typeId, inherited.type.typeId)
                assertSame(declaration.type, inherited.type)
                assertSame(root, inherited.declaredParentTemplate)
                val origin = template.scope.fieldOrigins.getValue("value").single()
                assertSame(root, origin.owner)
                assertEquals(expected.typeId, origin.typeId)
                assertTrue(template.scope.getProperty("value")!!.isFinal)
                assertEquals(listOf("middle-effect"), inherited.annotations.single().params)
                if (inherited is EnumVar) assertSame((declaration as EnumVar).enum, inherited.enum)
                inherited
            }
            val properties = (listOf(root) + descendants).map { it.scope.getProperty("value")!! }
            for ((index, field) in (listOf(declaration) + fields).withIndex()) {
                (listOf(declaration) + fields).drop(index + 1).forEach { other ->
                    assertNotSame(field, other)
                    assertNotSame(field.annotations.single(), other.annotations.single())
                }
                properties.drop(index + 1).forEach { other ->
                    assertNotSame(properties[index], other)
                    assertNotSame(properties[index].accessor, other.accessor)
                    assertNotSame(properties[index].mutator, other.mutator)
                }
            }
            assertTrue(declaration.annotations.single().params.isEmpty())
            assertFalse(properties.first().isFinal)
        }
        val originalType = typeFactory()
        val originalRoot = root(originalType)
        val middle = model("TypedMiddle", originalRoot).also(::complete)
        middle.scope.getVar("value")!!.annotations.single().params.add("middle-effect")
        middle.scope.getProperty("value")!!.isFinal = true
        val grand = model("TypedGrand", middle).also(::complete)
        val sibling = model("TypedSibling", middle).also(::complete)
        verify(originalRoot, listOf(middle, grand, sibling), originalType)
        val saved = middle.completedInheritedFields()
        val savedGrand = grand.completedInheritedFields()
        for (effects in listOf(saved, savedGrand)) {
            val effect = effects.single()
            checkAdapter(effect.field)
            assertEquals(originalType.typeId, effect.field.type.typeId)
            assertEquals(originalRoot.inheritanceIdentity, effect.origin)
        }
        assertEquals(0, Project.errorCount)

        Project.init()
        val freshType = typeFactory()
        val freshRoot = root(freshType)
        assertNotSame(originalRoot, freshRoot)
        val freshMiddle = model("TypedMiddle", freshRoot)
        freshMiddle.restoreInheritedFields(saved)
        // Registration owns a detached effect, including the annotation graph.
        saved.single().field.annotations.single().params.add("snapshot-only")
        complete(freshMiddle)
        val freshGrand = model("TypedGrand", freshMiddle)
        freshGrand.restoreInheritedFields(savedGrand)
        complete(freshGrand)
        val freshSibling = model("TypedSibling", freshMiddle).also(::complete)
        verify(freshRoot, listOf(freshMiddle, freshGrand, freshSibling), freshType)
        assertEquals(0, Project.errorCount)
        freshGrand.scope.getVar("value")!!.annotations.single().params.add("grand-only")
        assertEquals(listOf("middle-effect"), freshMiddle.scope.getVar("value")!!.annotations.single().params)
        assertEquals(listOf("middle-effect"), freshSibling.scope.getVar("value")!!.annotations.single().params)

        val incompatibleRoot = root(MCFPPBaseType.Int)
        val rejected = model("Rejected", incompatibleRoot)
        rejected.restoreInheritedFields(savedGrand)
        val messages = diagnostics { complete(rejected) }.joinToString("\n")
        assertTrue("Cannot restore inherited field 'value'" in messages && "incompatible typeId" in messages, messages)
        assertEquals(MCFPPBaseType.Int.typeId, rejected.scope.getVar("value")!!.type.typeId)
        assertSame(incompatibleRoot, rejected.scope.getVar("value")!!.declaredParentTemplate)
        assertFalse(rejected.scope.getProperty("value")!!.isFinal)
        assertTrue(Project.errorCount > 0)
    }

    @Test fun completedEffectsReachGrandchildrenWithoutChangingParentsSiblingsOrTheSnapshot() {
        Project.init()
        val root = model("Root").also { field(it) }
        val middle = model("Middle", root)
        complete(middle)
        val marker = Marker().apply { parents.add(this) }
        value(middle).apply {
            isDataOnly = true
            annotations.add(marker)
            annotations.add(marker)
        }
        middle.scope.getProperty("value")!!.isFinal = true
        val saved = middle.completedInheritedFields()
        marker.params.add("writer-only")

        val freshRoot = model("Root").also { field(it) }
        val freshMiddle = model("Middle", freshRoot)
        freshMiddle.restoreInheritedFields(saved)
        // The two entries intentionally alias, including a parent cycle.
        saved.single().field.annotations.first().params.add("snapshot-only")
        complete(freshMiddle)
        val grand = model("Grand", freshMiddle).also(::complete)
        val sibling = model("Sibling", freshRoot).also(::complete)
        assertEquals(0, Project.errorCount)
        assertEquals(listOf(false, true, true, false), listOf(freshRoot, freshMiddle, grand, sibling).map { value(it).isDataOnly })
        for (template in listOf(freshMiddle, grand)) {
            val inherited = value(template)
            assertSame(freshRoot, inherited.declaredParentTemplate)
            assertSame(freshRoot, template.scope.fieldOrigins.getValue("value").single().owner)
            assertTrue(template.scope.getProperty("value")!!.isFinal)
            assertSame(freshRoot, template.scope.getProperty("value")!!.declaredParentTemplate)
            val annotation = inherited.annotations.first()
            assertSame(annotation, inherited.annotations.last())
            assertSame(annotation, annotation.parents.single())
            assertTrue(annotation.params.isEmpty())
        }
        assertNotSame(value(freshMiddle), value(grand))
        assertNotSame(value(freshMiddle).annotations.first(), value(grand).annotations.first())
        assertNotSame(saved.single().property, freshMiddle.scope.getProperty("value"))
        value(freshMiddle).annotations.first().params.add("middle-only")
        assertTrue(value(grand).annotations.first().params.isEmpty())
        assertTrue(value(freshRoot).annotations.isEmpty())
        assertTrue(value(sibling).annotations.isEmpty())
        assertFalse(freshRoot.scope.getProperty("value")!!.isFinal)
    }

    @Test fun localDeclarationsKeepTheirMetadataInitializersAndConstructorPlan() {
        Project.init()
        val root = model("Root").also { field(it) }
        root.preInit["value"] = parser("1").expression()
        val rootConstructor = DataTemplateConstructor(root, parser("{}").curlBlock())
        root.addMember(rootConstructor)
        val middle = model("Middle", root).also(::complete)
        value(middle).isDataOnly = true
        val saved = middle.completedInheritedFields()
        val local = model("Local", root)
        val own = field(local)
        own.annotations.add(Marker().apply { params.add("local") })
        val initializer = parser("9").expression()
        local.preInit["value"] = initializer
        val constructor = DataTemplateConstructor(local, parser("{}").curlBlock())
        local.addMember(constructor)
        local.restoreInheritedFields(saved)
        complete(local)
        assertEquals(0, Project.errorCount)
        assertSame(own, local.scope.getVar("value"))
        assertFalse(value(local).isDataOnly)
        assertEquals(listOf("local"), own.annotations.single().params)
        assertSame(local, own.declaredParentTemplate)
        assertSame(local, local.scope.fieldOrigins.getValue("value").single().owner)
        assertSame(initializer, local.preInit.getValue("value"))
        assertEquals("1", root.preInit.getValue("value").text)
        assertTrue(local.completedInheritedFields().isEmpty())
        val plan = assertNotNull(ConstructorPlan.build(local).plan)
        val stages = plan.stages(constructor)
        assertEquals(2, stages.size)
        assertSame(root, stages[0].owner)
        assertSame(rootConstructor, stages[0].constructor)
        assertSame(local, stages[1].owner)
        assertSame(constructor, stages[1].constructor)
        assertNull(rootConstructor.constructedReceiver)
        assertTrue(rootConstructor.commands.isEmpty())
        assertTrue(constructor.commands.isEmpty())
    }

    @Test fun canonicalOriginsAcceptFreshSameBindingsAndRejectDifferentBindingsWithTheSameDisplayName() {
        Project.init()
        val prototype = GenericDataTemplate(parser("{}").templateBody(), "Generic", "layout.model")
        fun bound(argument: MCFPPType) = CompiledGenericDataTemplate(
            "same",
            "layout.model",
            prototype,
            emptyList(),
            listOf(CompilerValue.TypeValue(argument.typeId))
        ).also { field(it) }
        val originalInt = bound(MCFPPBaseType.Int)
        val middle = model("Middle", originalInt).also(::complete)
        value(middle).isDataOnly = true
        val saved = middle.completedInheritedFields()
        val freshInt = bound(MCFPPBaseType.Int)
        assertNotSame(originalInt, freshInt)
        assertEquals(originalInt.inheritanceIdentity, freshInt.inheritanceIdentity)
        val accepted = model("Accepted", freshInt)
        accepted.restoreInheritedFields(saved)
        complete(accepted)
        assertEquals(0, Project.errorCount)
        assertTrue(value(accepted).isDataOnly)
        assertSame(freshInt, value(accepted).declaredParentTemplate)

        val freshBool = bound(MCFPPBaseType.Bool)
        assertEquals(freshInt.identifier, freshBool.identifier)
        assertFalse(freshInt.inheritanceIdentity == freshBool.inheritanceIdentity)
        val rejected = model("Rejected", freshBool)
        rejected.restoreInheritedFields(saved)
        val messages = diagnostics { complete(rejected) }.joinToString("\n")
        assertTrue(Project.errorCount > 0)
        assertTrue("Cannot restore inherited field 'value'" in messages && "origin" in messages, messages)
        assertFalse(value(rejected).isDataOnly)
        assertSame(freshBool, value(rejected).declaredParentTemplate)
    }

    @Test fun completedEffectsCannotResolveAmbiguousParentsOrRepairIncompatibleSlots() {
        Project.init()
        val root = model("Root").also { field(it) }
        val middle = model("Middle", root).also(::complete)
        value(middle).isDataOnly = true
        val saved = middle.completedInheritedFields()
        val other = model("Other").also { field(it) }
        val ambiguous = model("Ambiguous", root, other)
        ambiguous.restoreInheritedFields(saved)
        val ambiguity = diagnostics { complete(ambiguous) }.joinToString("\n")
        assertTrue("Ambiguous inherited field 'value'" in ambiguity, ambiguity)
        assertNull(ambiguous.scope.getVar("value"))
        assertNull(ambiguous.scope.getProperty("value"))

        val incompatible = model("Incompatible").also { field(it, MCFPPBaseType.Bool) }
        val invalid = model("Invalid", root, incompatible)
        val own = field(invalid)
        invalid.restoreInheritedFields(saved)
        val conflict = diagnostics { complete(invalid) }.joinToString("\n")
        assertTrue("Incompatible inherited field 'value'" in conflict && "typeId" in conflict, conflict)
        assertSame(own, invalid.scope.getVar("value"))
        assertFalse(value(invalid).isDataOnly)
        assertSame(invalid, own.declaredParentTemplate)
    }

    @Test fun restoredEffectsMustPreserveEveryFieldSlotInvariant() {
        Project.init()
        val root = model("Root").also { field(it) }
        val source = model("Source", root).also(::complete)
        value(source).isDataOnly = true
        val saved = source.completedInheritedFields().single()
        val incompatible = listOf(
            "typeId" to MCFPPBaseType.Bool.buildUnConcrete("value"),
            "nullable" to MCInt("value").apply { nullable = true },
            "static" to MCInt("value").apply { isStatic = true },
            "const" to MCInt("value").apply { isConst = true }
        )
        for ((difference, field) in incompatible) {
            if (difference != "static") field.isStatic = false
            val target = model("Rejected_$difference", root)
            target.restoreInheritedFields(listOf(DataTemplate.CompletedInheritedField(saved.origin, field, saved.property)))
            val messages = diagnostics { complete(target) }.joinToString("\n")
            assertTrue("Cannot restore inherited field 'value'" in messages && difference in messages, messages)
            val inherited = value(target)
            assertSame(root, inherited.declaredParentTemplate)
            assertFalse(inherited.isDataOnly)
            assertFalse(inherited.nullable)
            assertFalse(inherited.isStatic)
            assertFalse(inherited.isConst)
        }
    }

    @Test fun completedHostAnnotationsAreNeverInvokedWhileGenuinelyPendingAnnotationsStillRunOnce() {
        Project.init()
        val root = model("Root").also { field(it) }
        val middle = model("Middle", root)
        val annotation = GeneratingAnnotation()
        middle.annotations.add(annotation)
        middle.pendingDeclarationAnnotations.add(annotation)
        complete(middle)
        assertEquals(1, annotation.applications)
        val saved = middle.completedInheritedFields()

        val freshRoot = model("Root").also { field(it) }
        val restored = model("Middle", freshRoot)
        // Completed generated members are ordinary local declarations in metadata.
        field(restored, name = "generated_1")
        val copied = Property.copyAnnotations(middle.annotations).single() as GeneratingAnnotation
        restored.annotations.add(copied)
        restored.restoreInheritedFields(saved)
        complete(restored)
        complete(restored)
        assertEquals(1, copied.applications)
        assertNotNull(restored.scope.getVar("generated_1"))
        assertNull(restored.scope.getVar("generated_2"))
        assertTrue(value(restored).isDataOnly)

        val pending = model("Pending", freshRoot)
        val pendingAnnotation = GeneratingAnnotation()
        pending.annotations.add(pendingAnnotation)
        pending.pendingDeclarationAnnotations.add(pendingAnnotation)
        complete(pending)
        complete(pending)
        assertEquals(1, pendingAnnotation.applications)
        assertNotNull(pending.scope.getVar("generated_1"))
        assertNull(pending.scope.getVar("generated_2"))
        assertTrue(pending.pendingDeclarationAnnotations.isEmpty())
        assertEquals(0, Project.errorCount)
    }

    @Test fun lateRegistrationAppliesOnceAndDoesNotOverwriteLaterDeclarationStateOnRepeatedCompletion() {
        Project.init()
        val root = model("Root").also { field(it) }
        val source = model("Source", root).also(::complete)
        value(source).isDataOnly = true
        source.scope.getProperty("value")!!.isFinal = true
        val saved = source.completedInheritedFields()
        val target = model("Target", root).also(::complete)
        assertFalse(value(target).isDataOnly)
        target.restoreInheritedFields(saved)
        target.flatExtends()
        assertTrue(value(target).isDataOnly)
        assertTrue(target.scope.getProperty("value")!!.isFinal)
        value(target).isDataOnly = false
        target.scope.getProperty("value")!!.isFinal = false
        complete(target)
        complete(target)
        assertFalse(value(target).isDataOnly)
        assertFalse(target.scope.getProperty("value")!!.isFinal)
        assertSame(root, value(target).declaredParentTemplate)
        assertEquals(0, Project.errorCount)
    }

    @Test fun explicitAccessorsResolveFreshDeclarationsAndKeepMutableWrappersIndependent() {
        Project.init()
        fun declarations(root: DataTemplate, result: Int): Pair<RecordingFunction, RecordingFunction> {
            field(root)
            val getter = RecordingFunction("get-value", root, result)
            val setter = RecordingFunction("set-value", root, result).apply { appendNormalParam(MCFPPBaseType.Int, "value") }
            root.scope.addFunction(getter, false)
            root.scope.addFunction(setter, false)
            root.scope.putProperty(
                "value",
                Property("value", FunctionAccessor(getter), FunctionMutator(setter)).apply {
                    declaredParentTemplate = root
                    isStatic = false
                },
                true
            )
            return getter to setter
        }
        val oldRoot = model("Root")
        val (oldGetter, oldSetter) = declarations(oldRoot, 1)
        val oldMiddle = model("Middle", oldRoot).also(::complete)
        val saved = oldMiddle.completedInheritedFields()
        val savedProperty = saved.single().property!!
        assertNotSame(oldMiddle.scope.getProperty("value")!!.accessor, savedProperty.accessor)
        assertNotSame(oldMiddle.scope.getProperty("value")!!.mutator, savedProperty.mutator)
        val poison = RecordingFunction("poison", oldRoot, -1)
        (oldMiddle.scope.getProperty("value")!!.accessor as FunctionAccessor).function = poison

        val freshRoot = model("Root")
        val (freshGetter, freshSetter) = declarations(freshRoot, 9)
        val middle = model("Middle", freshRoot)
        middle.restoreInheritedFields(saved)
        // Registration owns its wrappers even if the caller later edits the snapshot.
        (savedProperty.accessor as FunctionAccessor).function = poison
        complete(middle)
        val property = middle.scope.getProperty("value")!!
        val accessor = assertIs<FunctionAccessor>(property.accessor)
        val mutator = assertIs<FunctionMutator>(property.mutator)
        assertSame(freshGetter, accessor.function)
        assertSame(freshSetter, mutator.function)
        assertSame(freshRoot, accessor.function.owner)
        assertSame(freshRoot, mutator.function.owner)
        assertFalse(freshGetter.isStatic)
        assertFalse(freshSetter.isStatic)
        assertSame(freshRoot, value(middle).declaredParentTemplate)
        val receiver = DataTemplateObject(middle, "receiver")
        assertEquals(9, assertIs<MCInt>(property.getter(receiver, value(middle))).value)
        val argument = MCInt(42)
        assertSame(value(middle), property.setter(receiver, value(middle), argument))
        assertEquals(1, freshGetter.calls)
        assertEquals(1, freshSetter.calls)
        assertSame(receiver, freshGetter.lastReceiver)
        assertSame(receiver, freshSetter.lastReceiver)
        assertSame(argument, freshSetter.lastArgument)
        assertEquals(0, oldGetter.calls)
        assertEquals(0, oldSetter.calls)
        assertEquals(0, poison.calls)
        val grand = model("Grand", middle).also(::complete)
        val sibling = model("Sibling", middle).also(::complete)
        val properties = listOf(freshRoot, middle, grand, sibling).map { it.scope.getProperty("value")!! }
        for ((index, current) in properties.withIndex()) {
            assertSame(freshGetter, (current.accessor as FunctionAccessor).function)
            assertSame(freshSetter, (current.mutator as FunctionMutator).function)
            properties.drop(index + 1).forEach { other ->
                assertNotSame(current.accessor, other.accessor)
                assertNotSame(current.mutator, other.mutator)
            }
        }
        val grandProperty = properties[2]
        val siblingProperty = properties[3]
        (grandProperty.accessor as FunctionAccessor).function = poison
        assertSame(freshGetter, accessor.function)
        assertSame(freshGetter, (siblingProperty.accessor as FunctionAccessor).function)
        assertEquals(9, assertIs<MCInt>(siblingProperty.getter(DataTemplateObject(sibling), value(sibling))).value)
        accessor.function = oldGetter
        assertSame(poison, (grandProperty.accessor as FunctionAccessor).function)
        assertSame(freshGetter, (siblingProperty.accessor as FunctionAccessor).function)
        (siblingProperty.accessor as FunctionAccessor).function = oldSetter
        assertSame(oldGetter, accessor.function)
        assertSame(poison, (grandProperty.accessor as FunctionAccessor).function)
        assertSame(freshGetter, (properties[0].accessor as FunctionAccessor).function)
        assertEquals(0, Project.errorCount)
    }

    @Test fun nativeAccessorsResolveFreshPropertyDeclarationsWithoutAFunctionRegistryEntry() {
        Project.init()
        nativeCalls.clear()
        fun declarations(root: DataTemplate, getterMethod: String, setterMethod: String): Pair<NativeFunction, NativeFunction> {
            field(root)
            fun function(name: String, method: String) = NativeFunction(
                name,
                root.namespace,
                DataInheritanceLayoutRestoreTest::class.java.getMethod(method, NativeCallContext::class.java)
            ).apply {
                owner = root
                caller = root.getType()
                returnType = MCFPPBaseType.Int
            }
            val getter = function("get_value", getterMethod)
            val setter = function("set_value", setterMethod).apply { appendNormalParam(MCFPPBaseType.Int, "value") }
            root.scope.putProperty(
                "value",
                Property("value", NativeAccessor(getter), NativeMutator(setter)).apply {
                    declaredParentTemplate = root
                    isStatic = false
                },
                true
            )
            return getter to setter
        }
        val oldRoot = model("Root")
        // A fresh model owns new declarations of the same host targets.
        val (oldGetter, _) = declarations(oldRoot, "freshNativeGetter", "freshNativeSetter")
        val saved = model("Middle", oldRoot).also(::complete).completedInheritedFields()
        val freshRoot = model("Root")
        val (getter, setter) = declarations(freshRoot, "freshNativeGetter", "freshNativeSetter")
        val middle = model("Middle", freshRoot)
        middle.restoreInheritedFields(saved)
        (saved.single().property!!.mutator as NativeMutator).function = oldGetter
        complete(middle)
        val property = middle.scope.getProperty("value")!!
        assertSame(getter, (property.accessor as NativeAccessor).function)
        assertSame(setter, (property.mutator as NativeMutator).function)
        assertSame(freshRoot, getter.owner)
        assertSame(freshRoot, setter.owner)
        assertFalse(getter.isStatic)
        assertFalse(setter.isStatic)
        assertNotSame(saved.single().property!!.accessor, property.accessor)
        assertNotSame(saved.single().property!!.mutator, property.mutator)
        val grand = model("Grand", middle).also(::complete)
        val sibling = model("Sibling", middle).also(::complete)
        val properties = listOf(freshRoot, middle, grand, sibling).map { it.scope.getProperty("value")!! }
        for ((index, current) in properties.withIndex()) {
            assertSame(getter, (current.accessor as NativeAccessor).function)
            assertSame(setter, (current.mutator as NativeMutator).function)
            properties.drop(index + 1).forEach { other ->
                assertNotSame(current.accessor, other.accessor)
                assertNotSame(current.mutator, other.mutator)
            }
        }
        val previous = Function.currFunction
        Function.currFunction = Function("test-accessors", "layout.model", null)
        try {
            val receiver = DataTemplateObject(middle, "receiver")
            StorageAccess.initializeLiteral(receiver, CompilerValue.Record(mapOf("value" to CompilerValue.Integral(3))))
            assertEquals(9, assertIs<MCInt>(property.getter(receiver, value(middle))).value)
            assertEquals(42, assertIs<MCInt>(property.setter(receiver, value(middle), MCInt(42))).value)
            val grandMutator = properties[2].mutator as NativeMutator
            val middleMutator = property.mutator as NativeMutator
            val siblingMutator = properties[3].mutator as NativeMutator
            grandMutator.function = oldGetter
            assertSame(setter, middleMutator.function)
            assertSame(setter, siblingMutator.function)
            middleMutator.function = getter
            assertSame(oldGetter, grandMutator.function)
            assertSame(setter, siblingMutator.function)
            val siblingReceiver = DataTemplateObject(sibling, "sibling")
            StorageAccess.initializeLiteral(siblingReceiver, CompilerValue.Record(mapOf("value" to CompilerValue.Integral(4))))
            assertEquals(9, assertIs<MCInt>(properties[3].getter(siblingReceiver, value(sibling))).value)
            assertEquals(43, assertIs<MCInt>(properties[3].setter(siblingReceiver, value(sibling), MCInt(43))).value)
            siblingMutator.function = oldGetter
            assertSame(getter, middleMutator.function)
            assertSame(oldGetter, grandMutator.function)
            assertSame(setter, (properties[0].mutator as NativeMutator).function)
        } finally {
            Function.currFunction = previous
        }
        assertEquals(listOf("fresh-get", "fresh-set:42", "fresh-get", "fresh-set:43"), nativeCalls)
        assertEquals(0, Project.errorCount)
    }

    @Test fun nativeAccessorsIgnoreSameSignatureFunctionsWithDifferentHostTargetsOrCallers() {
        for (differentHost in listOf(true, false)) {
            Project.init()
            nativeCalls.clear()
            fun function(root: DataTemplate, setter: Boolean, method: String) = NativeFunction(
                if (setter) "set_value" else "get_value",
                root.namespace,
                DataInheritanceLayoutRestoreTest::class.java.getMethod(method, NativeCallContext::class.java)
            ).apply {
                owner = root
                caller = root.getType()
                returnType = MCFPPBaseType.Int
                if (setter) appendNormalParam(MCFPPBaseType.Int, "value")
            }
            fun declarations(root: DataTemplate): Pair<NativeFunction, NativeFunction> {
                field(root)
                val getter = function(root, false, "oldNativeGetter")
                val setter = function(root, true, "oldNativeSetter")
                root.scope.putProperty(
                    "value",
                    Property("value", NativeAccessor(getter), NativeMutator(setter)).apply {
                        declaredParentTemplate = root
                        isStatic = false
                    },
                    true
                )
                return getter to setter
            }
            val oldRoot = model("Root")
            val (oldGetter, oldSetter) = declarations(oldRoot)
            val saved = model("Middle", oldRoot).also(::complete).completedInheritedFields()
            val root = model("Root")
            val (getter, setter) = declarations(root)
            val decoyGetter = function(root, false, if (differentHost) "freshNativeGetter" else "oldNativeGetter")
            val decoySetter = function(root, true, if (differentHost) "freshNativeSetter" else "oldNativeSetter")
            if (!differentHost) {
                decoyGetter.caller = MCFPPPrivateType.Void
                decoySetter.caller = MCFPPPrivateType.Void
            }
            assertTrue(root.scope.addFunction(decoyGetter, false))
            assertTrue(root.scope.addFunction(decoySetter, false))
            val middle = model("Middle", root)
            middle.restoreInheritedFields(saved)
            complete(middle)
            assertEquals(0, Project.errorCount, "differentHost=$differentHost")
            val grand = model("Grand", middle).also(::complete)
            val sibling = model("Sibling", middle).also(::complete)
            val properties = listOf(root, middle, grand, sibling).map { it.scope.getProperty("value")!! }
            for ((index, property) in properties.withIndex()) {
                val restoredGetter = (property.accessor as NativeAccessor).function
                val restoredSetter = (property.mutator as NativeMutator).function
                assertSame(getter, restoredGetter)
                assertSame(setter, restoredSetter)
                assertSame(root, restoredGetter.owner)
                assertSame(root, restoredSetter.owner)
                assertNotSame(oldGetter, restoredGetter)
                assertNotSame(oldSetter, restoredSetter)
                assertNotSame(decoyGetter, restoredGetter)
                assertNotSame(decoySetter, restoredSetter)
                assertNotSame(saved.single().property!!.accessor, property.accessor)
                assertNotSame(saved.single().property!!.mutator, property.mutator)
                properties.drop(index + 1).forEach { other ->
                    assertNotSame(property.accessor, other.accessor)
                    assertNotSame(property.mutator, other.mutator)
                }
            }
            val previous = Function.currFunction
            Function.currFunction = Function("native-target-test", "layout.model", null)
            try {
                val receiver = DataTemplateObject(middle, "receiver")
                StorageAccess.initializeLiteral(receiver, CompilerValue.Record(mapOf("value" to CompilerValue.Integral(3))))
                val property = properties[1]
                assertEquals(1, assertIs<MCInt>(property.getter(receiver, value(middle))).value)
                assertEquals(42, assertIs<MCInt>(property.setter(receiver, value(middle), MCInt(42))).value)
            } finally {
                Function.currFunction = previous
            }
            assertEquals(listOf("old-get", "old-set"), nativeCalls, "differentHost=$differentHost")
            assertEquals(0, Project.errorCount)
        }
    }

    @Test fun unresolvedAccessorDeclarationsRejectTheWholeEffectBeforeChangingTheReceivingField() {
        Project.init()
        val oldRoot = model("Root").also { field(it) }
        val missing = RecordingFunction("get-missing", oldRoot, 1)
        oldRoot.scope.putProperty(
            "value",
            Property("value", FunctionAccessor(missing), null).apply {
                declaredParentTemplate = oldRoot
            },
            true
        )
        val source = model("Middle", oldRoot).also(::complete)
        value(source).isDataOnly = true
        val saved = source.completedInheritedFields()
        val freshRoot = model("Root").also { field(it) }
        val target = model("Middle", freshRoot)
        target.restoreInheritedFields(saved)
        val messages = diagnostics { complete(target) }.joinToString("\n")
        assertTrue(
            "Cannot restore inherited field 'value'" in messages &&
                "get-missing" in messages &&
                "no unique receiving declaration" in messages,
            messages
        )
        assertFalse(value(target).isDataOnly)
        assertSame(freshRoot, value(target).declaredParentTemplate)
        assertEquals(0, missing.calls)
    }

    internal fun model(name: String, vararg parents: DataTemplate) = DataTemplate(name, "layout.model").also { template ->
        parents.forEach { template.extends(it) }
    }

    internal fun field(template: DataTemplate, type: MCFPPType = MCFPPBaseType.Int, name: String = "value"): Var<*> {
        val field = type.buildUnConcrete(name).apply {
            isStatic = false
            declaredParentTemplate = template
            bindDeclaration()
        }
        assertTrue(template.addMember(field))
        assertTrue(template.addMember(Property.buildSimpleProperty(field).apply { declaredParentTemplate = template }))
        return field
    }

    private fun value(template: DataTemplate) = assertIs<MCInt>(template.scope.getVar("value"))

    internal fun complete(template: DataTemplate) {
        template.flatExtends()
        MCFPPFieldVisitor().completeTemplateFields(template)
        template.applyDeclarationAnnotations()
    }

    private fun parser(source: String) = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(source))))

    private fun diagnostics(action: () -> Unit): List<String> {
        val logger = assertIs<Logger>(LogProcessor.logger)
        val messages = arrayListOf<String>()
        val appender = object : AbstractAppender("layout-restore-${java.util.UUID.randomUUID()}", null, null, false, LogProperty.EMPTY_ARRAY) {
            override fun append(event: LogEvent) {
                messages.add(event.message.formattedMessage)
            }
        }
        appender.start()
        logger.addAppender(appender)
        try {
            action()
        } finally {
            logger.removeAppender(appender)
            appender.stop()
        }
        return messages
    }

    class Marker : Annotation("Marker", "layout.model") {
        override fun forFunction(function: Function) = Unit
        override fun forDataTemplate(data: DataTemplate) = Unit
        override fun forField(field: Var<*>) = Unit
    }

    class RecordingFunction(name: String, owner: DataTemplate, private val result: Int) : Function(name, owner, null) {
        var calls = 0
        var lastReceiver: CanSelectMember? = null
        var lastArgument: Var<*>? = null
        init {
            returnType = MCFPPBaseType.Int
        }
        override fun invoke(normalArgs: LinkedHashMap<String, Var<*>>, caller: CanSelectMember?): Var<*> {
            calls++
            lastReceiver = caller
            lastArgument = normalArgs.values.singleOrNull()
            return MCInt(result)
        }
    }

    companion object {
        val nativeCalls = arrayListOf<String>()
        @JvmStatic @NoExternalWrites
        fun oldNativeGetter(context: NativeCallContext) {
            nativeCalls.add("old-get")
            context.publishResult(MCInt(1))
        }
        @JvmStatic @NoExternalWrites
        fun oldNativeSetter(context: NativeCallContext) {
            nativeCalls.add("old-set")
            context.withArguments { context.publishResult(it.single()) }
        }
        @JvmStatic @NoExternalWrites
        fun freshNativeGetter(context: NativeCallContext) {
            nativeCalls.add("fresh-get")
            context.publishResult(MCInt(9))
        }
        @JvmStatic @NoExternalWrites
        fun freshNativeSetter(context: NativeCallContext) {
            context.withArguments {
                nativeCalls.add("fresh-set:${(it.single() as MCInt).value}")
                context.publishResult(it.single())
            }
        }
    }

    class GeneratingAnnotation : Annotation("Generate", "layout.model") {
        var applications = 0
        override fun forFunction(function: Function) = Unit
        override fun forField(field: Var<*>) = Unit
        override fun forDataTemplate(data: DataTemplate) {
            applications++
            val generated = MCInt("generated_$applications").apply {
                isStatic = false
                declaredParentTemplate = data
                bindDeclaration()
            }
            check(data.addMember(generated))
            check(data.addMember(Property.buildSimpleProperty(generated).apply { declaredParentTemplate = data }))
            (data.scope.getVar("value") as MCInt).isDataOnly = true
        }
    }
}
