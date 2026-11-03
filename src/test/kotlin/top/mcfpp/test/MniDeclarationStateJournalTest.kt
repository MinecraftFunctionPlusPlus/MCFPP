package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.command.Command
import top.mcfpp.command.CommandList
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.NBTDictionary
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.io.info.DataTemplateInfo
import top.mcfpp.io.info.InheritedPropertyOwnerInfo
import top.mcfpp.lib.EntitySelector
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.annotation.Name
import top.mcfpp.mni.expand.DeclarationStateJournal
import top.mcfpp.mni.expand.NamespaceStateJournal
import top.mcfpp.mni.expand.ProjectStateJournal
import top.mcfpp.model.Member
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.*
import top.mcfpp.model.function.DataTemplateConstructor
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionContext
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.property.FunctionAccessor
import top.mcfpp.model.property.Property
import top.mcfpp.model.scope.CompoundDataScope
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.model.scope.SimpleLibScope
import top.mcfpp.type.*
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniDeclarationStateJournalTest {
    private inline fun isolated(action: (Function) -> Unit) {
        val project = ProjectStateJournal.checkpoint()
        val namespaces = NamespaceStateJournal.checkpoint()
        val current = Function.currFunction
        val forced = Function.forcedField
        val ignore = CompileSettings.ignoreStdLib
        try {
            Project.config = ProjectConfig(version = "26.3")
            Project.init()
            val sink = Function("journal", "deep.test", null)
            Function.currFunction = sink
            action(sink)
            assertEquals(0, Project.errorCount)
            assertEquals(0, Project.warningCount)
        } finally {
            namespaces.restore()
            project.restore()
            Function.currFunction = current
            Function.forcedField = forced
            CompileSettings.ignoreStdLib = ignore
        }
    }

    private fun parser(text: String) = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString(text))))
    private fun model(name: String) = DataTemplate(name, "deep.test").apply { declarationFile = null }
    private fun generic() = GenericDataTemplate(parser("{}").templateBody(), "Box", "deep.test").apply { declarationFile = null }
    private fun field(owner: DataTemplate, name: String = "value") = MCInt(name).also {
        it.declaredParentTemplate = owner
        owner.scope.putVar(name, it)
        owner.scope.putProperty(name, Property.buildSimpleProperty(it))
    }
    private fun key(owner: GenericDataTemplate, n: Long) = SpecializationKey(
        owner.declarationId,
        listOf(SpecializationArgument.Constant(CompilerValue.Integral(n))),
        GenerationOptions("26.3", 0)
    )

    @Test fun cyclicDeclarationsAndTypeParentsRetainTheirOriginalListsAndIdentities() = isolated { _ ->
        val first = model("First")
        val second = model("Second")
        first.parent.add(second)
        second.parent.add(first)
        val aParents = arrayListOf<MCFPPType>()
        val bParents = arrayListOf<MCFPPType>()
        val a = MCFPPType(aParents)
        val b = MCFPPType(bParents)
        aParents.add(b)
        bParents.add(a)
        first.commonType = a
        second.commonType = b
        val parents = first.parent
        val typeParents = a.parentType
        val before = DeclarationStateJournal.checkpoint(listOf(first))
        first.identifier = "changed"
        first.parent = arrayListOf()
        parents.clear()
        a.parentType = arrayListOf()
        typeParents.clear()
        second.annotations.add(Name("later"))
        repeat(2) {
            before.restore()
            assertEquals("First", first.identifier)
            assertSame(parents, first.parent)
            assertSame(second, parents.single())
            assertSame(first, second.parent.single())
            assertSame(typeParents, a.parentType)
            assertSame(b, typeParents.single())
            assertSame(a, b.parentType.single())
            assertTrue(second.annotations.isEmpty())
        }
        assertTrue(before.elements.any { it === second })
        assertFailsWith<UnsupportedOperationException> { (before.elements as MutableList<Any>).clear() }
    }

    @Test fun nestedGenericArgumentsAndDetachedUnionArraysRestoreInEveryGeneration() = isolated { _ ->
        val leaf = model("Leaf")
        val leafType = MCFPPDataTemplateType(leaf, arrayListOf())
        val inner = MCFPPListType(leafType)
        val outer = MCFPPListType(inner)
        val union = MCFPPUnionType(outer, MCFPPBaseType.Int)
        val original = union.types
        val identity = outer.typeId
        val before = DeclarationStateJournal.checkpoint(listOf(union))
        inner.generic[0] = MCFPPBaseType.Bool
        leaf.identifier = "Detached"
        union.types = arrayOf(MCFPPBaseType.String)
        original[0] = MCFPPBaseType.Float
        val after = before.captureCurrent()
        val replacement = union.types
        leaf.identifier = "OlderDetached"
        replacement[0] = MCFPPBaseType.Bool
        val latest = after.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(original, union.types)
            assertSame(outer, original[0])
            assertEquals(identity, outer.typeId)
            assertSame(leafType, inner.generic.single())
            assertEquals("Leaf", leaf.identifier)
            after.restore()
            assertSame(replacement, union.types)
            assertSame(MCFPPBaseType.String, replacement.single())
            assertSame(MCFPPBaseType.Float, original[0], "The detached original union array must retain its captured after state")
            assertEquals("Detached", leaf.identifier)
            latest.restore()
            assertSame(MCFPPBaseType.Bool, replacement.single())
            assertEquals("OlderDetached", leaf.identifier)
        }
    }

    @Test fun annotationCyclesAndDetachedParameterListsAreDeepRoots() = isolated { _ ->
        val owner = model("Owner")
        val annotation = Name("first").apply { params.add("before") }
        val parent = Name("parent").apply { params.add("parent_before") }
        annotation.parents.add(parent)
        parent.parents.add(annotation)
        owner.annotations.add(annotation)
        val before = DeclarationStateJournal.checkpoint(listOf(owner))
        owner.annotations.clear()
        annotation.params[0] = "after"
        parent.params[0] = "parent_after"
        val after = before.captureCurrent()
        annotation.params[0] = "latest"
        annotation.parents.clear()
        val latest = after.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(annotation, owner.annotations.single())
            assertEquals("before", annotation.params.single())
            assertEquals("parent_before", parent.params.single())
            assertSame(parent, annotation.parents.single())
            assertSame(annotation, parent.parents.single())
            after.restore()
            assertTrue(owner.annotations.isEmpty())
            assertEquals("after", annotation.params.single())
            latest.restore()
            assertEquals("latest", annotation.params.single())
            assertTrue(annotation.parents.isEmpty())
        }
    }

    @Test fun replacedScopesKeepRemovedMemberMetadataAndFunctionBodiesAlive() = isolated { _ ->
        val owner = model("Owner")
        val value = field(owner)
        val annotation = Name("field").apply { params.add("initial") }
        value.annotations.add(annotation)
        val function = Function("read", "deep.test", parser("{}").curlBlock()).apply {
            this.owner = owner
            returnType = MCFPPListType(MCFPPBaseType.Int)
            commands.add(Command("say").build("before", "text"))
        }
        owner.scope.addFunction(function, true)
        val property = Property("custom", FunctionAccessor(function), null).apply { declaredParentTemplate = owner }
        owner.scope.putProperty("custom", property, true)
        val original = owner.scope
        val before = DeclarationStateJournal.checkpoint(listOf(owner))
        owner.scope = CompoundDataScope(arrayListOf())
        value.isFinal = true
        annotation.params[0] = "after"
        property.accessModifier = Member.AccessModifier.PRIVATE
        assertEquals(1, function.commands.single().replace("text" to "after"))
        (function.returnType as MCFPPListType).generic[0] = MCFPPBaseType.Bool
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(original, owner.scope)
            assertSame(value, owner.scope.getVar("value"))
            assertFalse(value.isFinal)
            assertEquals("initial", annotation.params.single())
            assertEquals(Member.AccessModifier.PUBLIC, property.accessModifier)
            assertSame(MCFPPBaseType.Int, (function.returnType as MCFPPListType).generic.single())
            assertTrue(function.commands.single().toString().contains("before"))
            after.restore()
            assertTrue(owner.scope.allVars.isEmpty())
            assertTrue(value.isFinal)
            assertEquals("after", annotation.params.single())
            assertTrue(function.commands.single().toString().contains("after"))
            assertEquals(Member.AccessModifier.PRIVATE, property.accessModifier)
        }
    }

    @Test fun pendingAnnotationsAndRestoredInheritedEffectsKeepTheirDeepMembers() = isolated { _ ->
        val parent = model("Parent")
        val declared = field(parent)
        val annotation = Name("saved").apply { params.add("before") }
        declared.annotations.add(annotation)
        val child = model("Child").apply {
            extends(parent)
            flatExtends()
        }
        val inherited = assertNotNull(child.scope.getVar("value"))
        val effects = child.completedInheritedFields()
        val target = model("Target").apply {
            extends(parent)
            restoreInheritedFields(effects)
        }
        val pending = Name("pending").apply { params.add("pending_before") }
        target.pendingFieldAnnotations["value"] = arrayListOf(pending)
        val before = DeclarationStateJournal.checkpoint(listOf(target))
        val ownedEffect = before.elements.filterIsInstance<DataTemplate.CompletedInheritedField>().single()
        assertNotSame(effects.single().field, ownedEffect.field)
        target.pendingFieldAnnotations.clear()
        pending.params[0] = "pending_after"
        ownedEffect.field.annotations.single().params[0] = "effect_after"
        target.flatExtends()
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertNull(target.scope.getVar("value"))
            assertSame(pending, target.pendingFieldAnnotations.getValue("value").single())
            assertEquals("pending_before", pending.params.single())
            assertEquals("before", ownedEffect.field.annotations.single().params.single())
            after.restore()
            assertNotNull(target.scope.getVar("value"))
            assertTrue(target.pendingFieldAnnotations.isEmpty())
            assertEquals("effect_after", ownedEffect.field.annotations.single().params.single())
            assertEquals("effect_after", target.scope.getVar("value")!!.annotations.single().params.single())
        }
        assertEquals("before", effects.single().field.annotations.single().params.single())
        assertSame(parent, inherited.declaredParentTemplate)
    }

    @Test fun genericHeadersParametersAndDetachedBoundInstancesComposeWithDeepState() = isolated { _ ->
        val generic = generic()
        val parameter = DataTemplateParam("type", "T", MCFPPConcreteType.Type)
        generic.readOnlyParams.add(parameter)
        val first = CompiledGenericDataTemplate("Box_0", "deep.test", generic, emptyList(), listOf(CompilerValue.Integral(1)))
        val value = field(first)
        val boundKey = key(generic, 1)
        generic.compiledTemplates[boundKey] = first
        val before = DeclarationStateJournal.checkpoint(listOf(generic))
        generic.compiledTemplates.clear()
        generic.index = 7
        parameter.identifier = "Changed"
        value.isDataOnly = true
        first.identifier = "detached"
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(first, generic.compiledTemplates[boundKey])
            assertSame(parameter, generic.readOnlyParams.single())
            assertEquals("T", parameter.identifier)
            assertEquals(0, generic.index)
            assertFalse(value.isDataOnly)
            assertEquals("Box_0", first.identifier)
            after.restore()
            assertTrue(generic.compiledTemplates.isEmpty())
            assertEquals(7, generic.index)
            assertEquals("Changed", parameter.identifier)
            assertTrue(value.isDataOnly)
            assertEquals("detached", first.identifier)
        }
        assertTrue(after.elements.any { it === first })
        assertTrue(after.elements.any { it === value })
    }

    @Test fun constructorParametersAndTheirDefaultDescriptorsRestoreThroughFunctionJournal() = isolated { _ ->
        val owner = model("Constructed")
        val constructor = DataTemplateConstructor(owner, null)
        val descriptor = MCFPPListType(MCFPPBaseType.Int)
        val parameter = FunctionParam(descriptor, "items", constructor, hasDefault = true).apply {
            defaultTypes = linkedMapOf(descriptor.typeId to descriptor)
            defaultValue = CompilerValue.Typed(descriptor.typeId, CompilerValue.Sequence(emptyList()))
        }
        constructor.normalParams.add(parameter)
        owner.constructors.add(constructor)
        val before = DeclarationStateJournal.checkpoint(listOf(owner))
        owner.constructors.clear()
        parameter.identifier = "changed"
        parameter.hasDefault = false
        descriptor.generic[0] = MCFPPBaseType.Bool
        constructor.commands.add(Command("say changed"))
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(constructor, owner.constructors.single())
            assertEquals("items", parameter.identifier)
            assertTrue(parameter.hasDefault)
            assertSame(MCFPPBaseType.Int, descriptor.generic.single())
            assertTrue(constructor.commands.isEmpty())
            assertSame(descriptor, parameter.defaultTypes.values.single())
            after.restore()
            assertTrue(owner.constructors.isEmpty())
            assertEquals("changed", parameter.identifier)
            assertSame(MCFPPBaseType.Bool, descriptor.generic.single())
            assertEquals(1, constructor.commands.size)
        }
    }

    @Test fun genericHeaderCompletionFlagsCanBeRestoredWithoutReplayingItsBody() = isolated { _ ->
        val generic = generic()
        val parameter = DataTemplateParam("type", "T")
        generic.readOnlyParams.add(parameter)
        val before = DeclarationStateJournal.checkpoint(listOf(generic))
        generic.prepareHeader()
        assertSame(MCFPPConcreteType.Type, parameter.type)
        val after = before.captureCurrent()
        before.restore()
        assertNull(parameter.type)
        assertTrue(generic.parent.isEmpty())
        parameter.typeIdentifier = "int"
        generic.prepareHeader()
        assertSame(MCFPPBaseType.Int, parameter.type, "Restored headerPrepared/varianceChecked must allow genuine header preparation again")
        after.restore()
        assertEquals("type", parameter.typeIdentifier)
        assertSame(MCFPPConcreteType.Type, parameter.type)
        assertTrue(generic.compiledTemplates.isEmpty())
        assertEquals(0, generic.index)
    }

    @Test fun deepTypesRestoreBeforeOperatorAndNamespaceGenericKeysAreRehashed() = isolated { _ ->
        val namespace = Namespace("deep.keys")
        GlobalScope.localNamespaces[namespace.identifier] = namespace
        val owner = model("Operators")
        val leaf = model("Leaf")
        val generic = generic()
        val leafType = MCFPPDataTemplateType(leaf, arrayListOf())
        val inner = MCFPPListType(leafType)
        val outer = MCFPPListType(inner)
        val arguments = arrayListOf<MCFPPType>(outer)
        val key = "Box" to arguments
        namespace.scope.genericTemplate[key] = generic
        val function = Function("plus", "deep.test", null)
        owner.scope.operators["+"] = hashMapOf(outer to function)
        val registry = NamespaceStateJournal.checkpoint()
        val before = DeclarationStateJournal.checkpoint(listOf(owner, generic, outer))
        val oldIdentity = outer.typeId
        leaf.identifier = "AfterLeaf"
        inner.generic[0] = MCFPPBaseType.Bool
        assertNotEquals(oldIdentity, outer.typeId)
        val registryAfter = registry.captureCurrent()
        val after = before.captureCurrent()
        repeat(3) {
            before.restore()
            registry.restore()
            assertEquals(oldIdentity, outer.typeId)
            assertSame(leafType, inner.generic.single())
            assertSame(function, owner.scope.operators.getValue("+")[outer])
            assertSame(generic, namespace.scope.genericTemplate["Box" to listOf(MCFPPListType(MCFPPListType(leafType)))])
            assertSame(arguments, namespace.scope.genericTemplate.keys.single().second)
            after.restore()
            registryAfter.restore()
            assertSame(MCFPPBaseType.Bool, inner.generic.single())
            assertSame(function, owner.scope.operators.getValue("+")[outer])
            assertSame(generic, namespace.scope.genericTemplate["Box" to listOf(outer)])
        }
    }

    @Test fun aliasStateIsCapturedWithoutResolvingAndResolvedTargetsRemainDeepRoots() = isolated { _ ->
        val alias = MCFPPTypeAliasType(parser("int").type())
        val before = DeclarationStateJournal.checkpoint(listOf(alias))
        assertNull(alias.cachedTarget)
        assertSame(MCFPPBaseType.Int, alias.resolve(SimpleLibScope()))
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertNull(alias.cachedTarget)
            after.restore()
            assertSame(MCFPPBaseType.Int, alias.cachedTarget)
        }
    }

    @Test fun anObjectWithAnUninitializedInfoSlotIsNeverResolvedDuringCapture() = isolated { _ ->
        val unresolved = UnsolvedTemplate("Input", "deep.test", false, false)
        val instance = DataTemplateObject(unresolved, "instance")
        val type = assertIsTemplate(instance.type)
        val before = DeclarationStateJournal.checkpoint(listOf(instance))
        assertFailsWith<UninitializedPropertyAccessException> { unresolved.info }
        assertNull(instance.journalInstanceField())
        val info = DataTemplateInfo.from(model("Input"))
        unresolved.info = info
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertFailsWith<UninitializedPropertyAccessException> { unresolved.info }
            assertSame(unresolved, type.checkpointPrivateState().elements.first())
            after.restore()
            assertSame(info, unresolved.info)
            assertNull(instance.journalInstanceField())
        }
        assertTrue(after.elements.any { it === info })
    }

    private fun assertIsTemplate(type: MCFPPType) = kotlin.test.assertIs<MCFPPDataTemplateType>(type)

    @Test fun compilerOwnedCompoundTypesPublishTheirRawOwnerAndNativeSignature() = isolated { _ ->
        val owner = CompoundData("Owner", "deep.test")
        val function = NativeFunction("native", "deep.test", javaClass.getMethod("nativeBody", NativeCallContext::class.java))
        val caller = owner.getType()
        function.caller = caller
        owner.scope.addFunction(function, true)
        val before = DeclarationStateJournal.checkpoint(listOf(caller))
        owner.identifier = "changed"
        function.returnType = MCFPPBaseType.Bool
        before.restore()
        assertEquals("Owner", owner.identifier)
        assertSame(owner, CompoundData.ownedCompoundDataTypeOwner(caller))
        assertSame(caller, function.caller)
        assertTrue(before.elements.any { it === function })
    }

    @Test fun bothInheritedPropertyOwnerLocatorShapesRetainDeepDeclarationMembers() = isolated { _ ->
        for (objectOwner in listOf(false, true)) {
            val identity = TypeId.Specialized(TypeId.Declaration("template", "deep.test", "Owner"), listOf(CompilerValue.Integral(1)))
            val owner = InheritedPropertyOwnerInfo(identity, objectOwner).reference()
            val value = field(owner as DataTemplate)
            val before = DeclarationStateJournal.checkpoint(listOf(owner))
            value.isFinal = true
            owner.namespace = "changed"
            val after = before.captureCurrent()
            before.restore()
            assertFalse(value.isFinal)
            assertEquals("<metadata>", owner.namespace)
            assertSame(identity, InheritedPropertyOwnerInfo.ownedReferenceIdentity(owner))
            after.restore()
            assertTrue(value.isFinal)
            assertEquals("changed", owner.namespace)
        }
    }

    @Test fun selectorTypeValuesAcceptTheirOwnedNonemptyIdentityListsInStoredDescriptors() = isolated { _ ->
        val entities = arrayListOf("minecraft:pig")
        val selector = MCFPPEntityType(types = entities)
        val value = MCFPPTypeVar(selector, "T")
        val identity = selector.typeId
        assertEquals(TypeId.Builtin("entity"), identity)
        assertEquals(listOf("minecraft:pig"), selector.types)
        val store = assertNotNull(value.storageBinding).data
        assertSame(selector, store.types[identity])
        val before = DeclarationStateJournal.checkpoint(listOf(value))
        entities[0] = "minecraft:cow"
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertEquals(listOf("minecraft:pig"), selector.types)
            assertEquals(identity, selector.typeId)
            assertSame(selector, value.value)
            assertSame(selector, store.types[identity])
            after.restore()
            assertEquals(listOf("minecraft:cow"), selector.types)
        }
        before.restore()
        assertSame(entities, selector.types)
    }

    @Test fun inheritedOwnerLocatorsAcceptSpecializedSelectorTypeValueIdentities() = isolated { _ ->
        val selector = MCFPPEntityType(types = arrayListOf("minecraft:pig", "minecraft:cow"))
        val selectorIdentity = selector.typeId
        assertEquals(TypeId.Builtin("entity"), selectorIdentity)
        assertEquals(2, assertNotNull(selector.types).size)
        val argument = CompilerValue.Typed(MCFPPConcreteType.Type.typeId, CompilerValue.TypeValue(selectorIdentity))
        val identity = TypeId.Specialized(TypeId.Declaration("template", "deep.test", "Selected"), listOf(argument))
        for (objectOwner in listOf(false, true)) {
            val owner = InheritedPropertyOwnerInfo(identity, objectOwner).reference() as DataTemplate
            val member = field(owner)
            val before = DeclarationStateJournal.checkpoint(listOf(owner))
            member.isFinal = true
            val after = before.captureCurrent()
            repeat(2) {
                before.restore()
                assertFalse(member.isFinal)
                assertSame(identity, InheritedPropertyOwnerInfo.ownedReferenceIdentity(owner))
                after.restore()
                assertTrue(member.isFinal)
            }
            assertTrue(after.elements.any { it === selectorIdentity })
        }
    }

    @Test fun actualAllEntityAndScoresRelationshipsAreCapturedThroughOwnedRawShapes() = isolated { _ ->
        CompileSettings.ignoreStdLib = false
        Project.readProject()
        assertEquals(0, Project.errorCount)
        assertEquals(0, Project.warningCount)
        val selected = SelectorVar(EntitySelector('e'), "selected")
        val address = kotlin.test.assertIs<top.mcfpp.core.lang.entity.EntityDataAddress>(selected.getMemberVar("dat", Member.AccessModifier.PUBLIC).first)
        assertNotSame(selected, address.target)
        assertEquals(StorageAccess.snapshot(selected), StorageAccess.snapshot(address.target))
        assertEquals(selected.entityFacts, kotlin.test.assertIs<SelectorVar>(address.target).entityFacts)
        val declaration = assertIsTemplate(address.type).checkpointPrivateState().elements.first() as DataTemplate
        val instance = DataTemplateObject(declaration, "instance")
        assertTrue(SelectorVar.isOwnedDeclaration(declaration))
        val scores = kotlin.test.assertIs<NBTDictionary>(declaration.scope.getVar("scores"))
        val slot = MCFPPEntityType.checkpointScores(scores)
        try {
            val before = DeclarationStateJournal.checkpoint(listOf(instance, scores))
            val selectedSlot = scores as SelectorVar.SelectorParamMap
            selectedSlot.selector = selected
            val after = before.captureCurrent()
            before.restore()
            after.restore()
            assertSame(selected, selectedSlot.selector)
            assertSame(scores, MCFPPEntityType.scoresTypeOwner(scores.type))
            assertTrue(after.elements.any { it === scores.type })
            assertNull(instance.journalInstanceField())
        } finally {
            slot.restore()
        }
    }

    @Test fun prepareCapturesActualAliasWritesAndFunctionChangesWithoutReexecutingTheAction() = isolated { function ->
        val owner = model("Owner")
        val value = MCInt(3, "stored").apply { bindDeclaration() }
        owner.scope.putVar("stored", value)
        val alias = MCInt(value)
        function.scope.vars["stored"] = value
        function.scope.vars["alias"] = alias
        val originalBinding = value.storageBinding
        var calls = 0
        val prepared = DeclarationStateJournal.checkpoint(listOf(owner, function)).prepare {
            calls++
            StorageAccess.write(alias, MCInt(9))
            assertEquals(9, (StorageAccess.read(value) as MCInt).value)
            owner.identifier = "Prepared"
            function.commands.add(Command("say prepared"))
            Function.currFunction = Function("other", "deep.test", null)
        }
        assertEquals(1, calls)
        assertSame(function, Function.currFunction)
        repeat(2) {
            assertEquals(3, (StorageAccess.read(value) as MCInt).value)
            assertEquals("Owner", owner.identifier)
            prepared.apply()
            assertEquals(9, (StorageAccess.read(alias) as MCInt).value)
            assertEquals("Prepared", owner.identifier)
            assertTrue(function.commands.any { it.toString() == "say prepared" })
            prepared.rollback()
            assertSame(originalBinding, value.storageBinding)
            assertEquals(3, (StorageAccess.read(alias) as MCInt).value)
        }
        assertEquals(1, calls)
    }

    @Test fun failingPreparationRestoresDeepMetadataActualStorageAndCommands() = isolated { function ->
        val owner = model("Owner")
        val value = MCInt(4, "stored").apply { bindDeclaration() }
        owner.scope.putVar("stored", value)
        function.scope.vars["stored"] = value
        val annotation = Name("annotation").apply { params.add("before") }
        owner.annotations.add(annotation)
        val commands = function.commands.toList()
        val failure = assertFailsWith<IllegalArgumentException> {
            DeclarationStateJournal.checkpoint(listOf(owner, function)).prepare {
                StorageAccess.write(value, MCInt(8))
                assertEquals(8, (StorageAccess.read(value) as MCInt).value)
                owner.identifier = "failed"
                annotation.params[0] = "failed"
                function.commands.add(Command("say failed"))
                throw IllegalArgumentException("abort deep")
            }
        }
        assertEquals("abort deep", failure.message)
        assertEquals("Owner", owner.identifier)
        assertEquals("before", annotation.params.single())
        assertEquals(commands, function.commands.toList())
        assertEquals(4, (StorageAccess.read(value) as MCInt).value)
    }

    private class UnknownType : MCFPPType() {
        var callbacks = 0
        override val typeId: TypeId get() {
            callbacks++
            return TypeId.Builtin("host")
        }
        override var parentType: ArrayList<out MCFPPType>
            get() {
                callbacks++
                error("host parent getter")
            }
            set(@Suppress("UNUSED_PARAMETER") value) { }
    }

    private class UnknownAdapter : MCInt("host") {
        var callbacks = 0
        override var type: MCFPPType
            get() {
                callbacks++
                error("host adapter type getter")
            }
            set(@Suppress("UNUSED_PARAMETER") value) { }
    }

    @Test fun detachedCommandListsArePreflightedBeforeLowerJournalsReadTheirNewMacroAdapters() = isolated { function ->
        val original = function.commands
        val before = DeclarationStateJournal.checkpoint(listOf(function))
        function.commands = CommandList()
        val unknown = UnknownAdapter()
        original.add(Command("say").buildMacro(unknown))
        unknown.callbacks = 0
        assertFailsWith<IllegalStateException> { before.captureCurrent() }
        assertEquals(0, unknown.callbacks)
        before.restore()
        assertSame(original, function.commands)
        assertTrue(original.isEmpty())
    }

    @Test fun detachedFunctionContextsArePreflightedBeforeLowerJournalsReadTheirNewCaller() = isolated { function ->
        val original = function.context
        val before = DeclarationStateJournal.checkpoint(listOf(function))
        function.context = FunctionContext()
        val unknown = UnknownAdapter()
        original.caller = unknown
        unknown.callbacks = 0
        assertFailsWith<IllegalStateException> { before.captureCurrent() }
        assertEquals(0, unknown.callbacks)
        before.restore()
        assertSame(original, function.context)
        assertNull(original.caller)
    }

    @Test fun detachedFunctionContainersPreserveTheirNewRelationsAcrossThreeGenerations() = isolated { function ->
        val originalCommands = function.commands
        val originalContext = function.context
        val before = DeclarationStateJournal.checkpoint(listOf(function))
        val replacementCommands = CommandList()
        val replacementContext = FunctionContext()
        function.commands = replacementCommands
        function.context = replacementContext
        val first = MCInt(5, "first")
        val command = Command("say").buildMacro(first)
        originalCommands.add(command)
        originalContext.caller = first
        val after = before.captureCurrent()
        val second = MCInt(6, "second")
        originalCommands.add(Command("say latest"))
        originalContext.caller = second
        val latest = after.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(originalCommands, function.commands)
            assertSame(originalContext, function.context)
            assertTrue(originalCommands.isEmpty())
            assertNull(originalContext.caller)
            after.restore()
            assertSame(replacementCommands, function.commands)
            assertSame(replacementContext, function.context)
            assertSame(command, originalCommands.single())
            assertSame(first, originalContext.caller)
            assertEquals(5, (StorageAccess.read(first) as MCInt).value)
            latest.restore()
            assertEquals(2, originalCommands.size)
            assertSame(second, originalContext.caller)
            assertEquals(6, (StorageAccess.read(second) as MCInt).value)
        }
        assertTrue(latest.elements.any { it === originalContext })
    }

    private class UnknownScope : SimpleLibScope() {
        var callbacks = 0
        override fun getType(key: String): MCFPPType? {
            callbacks++
            error("host scope lookup")
        }
        override fun hashCode(): Int {
            callbacks++
            return 43
        }
    }

    @Test fun everyRawScopeParentIsCheckedIncludingParentsAddedToADetachedScope() = isolated { _ ->
        val owner = model("Owner")
        val original = owner.scope
        val external = SimpleLibScope()
        original.parent.add(external)
        val before = DeclarationStateJournal.checkpoint(listOf(owner))
        val unknown = UnknownScope()
        original.parent.add(unknown)
        assertFailsWith<IllegalStateException> { DeclarationStateJournal.checkpoint(listOf(owner)) }
        assertEquals(0, unknown.callbacks)
        owner.scope = CompoundDataScope(arrayListOf())
        assertFailsWith<IllegalStateException> { before.captureCurrent() }
        assertEquals(0, unknown.callbacks)
        before.restore()
        assertSame(original, owner.scope)
        assertSame(external, original.parent.single())
    }

    @Test fun unknownNestedTypesAreRejectedBeforeOperatorKeysCanInvokeHostHashOrGetters() = isolated { _ ->
        val owner = model("Owner")
        val unknown = UnknownType()
        unknown.callbacks = 0
        unknown.hashCode()
        assertEquals(1, unknown.callbacks, "The final compiler hash must reach the observable host typeId getter")
        val nested = MCFPPListType(MCFPPListType(unknown))
        owner.scope.operators["+"] = hashMapOf(nested to Function("plus", "deep.test", null))
        unknown.callbacks = 0
        assertFailsWith<IllegalStateException> { DeclarationStateJournal.checkpoint(listOf(owner)) }
        assertEquals(0, unknown.callbacks)
    }

    private class UnknownDeclaration : CompoundData("host", "deep.test") {
        var callbacks = 0
        override fun getType(): MCFPPType {
            callbacks++
            error("host getType")
        }
        override fun hashCode(): Int {
            callbacks++
            return 17
        }
    }

    @Test fun unknownDeclarationsBehindOwnedTypeReferencesAreRejectedWithoutHostCalls() = isolated { _ ->
        val unknown = UnknownDeclaration()
        val type = MCFPPDataTemplateType(model("Known"), arrayListOf())
        assertFailsWith<IllegalStateException> { DeclarationStateJournal.checkpoint(listOf(unknown)) }
        assertEquals(0, unknown.callbacks)
        val unknownType = UnknownType()
        type.parentType = arrayListOf(unknownType)
        unknownType.callbacks = 0
        assertFailsWith<IllegalStateException> { DeclarationStateJournal.checkpoint(listOf(type)) }
        assertEquals(0, unknownType.callbacks)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun theSamePhysicalListMustPassEveryConsumerRole() = isolated { _ ->
        val owner = model("Owner")
        val value = field(owner)
        owner.parentID.add("ParentInput")
        value.annotations = owner.parentID as ArrayList<Annotation>
        assertFailsWith<IllegalStateException> { DeclarationStateJournal.checkpoint(listOf(owner)) }
    }

    private class HostList : ArrayList<MCFPPType>() {
        var callbacks = 0
        override fun iterator(): MutableIterator<MCFPPType> {
            callbacks++
            error("host iterator")
        }
        override fun toArray(): Array<Any?> {
            callbacks++
            error("host array")
        }
    }

    @Test fun unknownContainerShapesAreRejectedBeforeReadingTheirContents() = isolated { _ ->
        val type = MCFPPType()
        val list = HostList()
        type.parentType = list
        assertFailsWith<IllegalStateException> { DeclarationStateJournal.checkpoint(listOf(type)) }
        assertEquals(0, list.callbacks)
    }

    private class UnknownAnnotation : Annotation("host", "deep.test") {
        var callbacks = 0
        override fun forFunction(function: Function) {
            callbacks++
        }
        override fun forDataTemplate(data: DataTemplate) {
            callbacks++
        }
        override fun forField(field: Var<*>) {
            callbacks++
        }
        override fun hashCode(): Int {
            callbacks++
            return 31
        }
    }

    @Test fun unknownAnnotationInstancesAreRejectedWithoutInvokingAnnotationOrHashCode() = isolated { _ ->
        val owner = model("Owner")
        val annotation = UnknownAnnotation()
        owner.pendingDeclarationAnnotations.add(annotation)
        assertFailsWith<IllegalStateException> { DeclarationStateJournal.checkpoint(listOf(owner)) }
        assertEquals(0, annotation.callbacks)
    }

    private class HostKey {
        var callbacks = 0
        override fun hashCode(): Int {
            callbacks++
            return 37
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun functionNameSetsAreValidatedBeforeTheFunctionJournalCopiesAndHashesThem() = isolated { function ->
        val key = HostKey()
        (function.scope.fieldVarSet as MutableSet<Any>).add(key)
        key.callbacks = 0
        assertFailsWith<IllegalStateException> { DeclarationStateJournal.checkpoint(listOf(function)) }
        assertEquals(0, key.callbacks)
    }

    @Test fun borrowedUnmodifiableViewsAreRejectedInsteadOfPretendingTheirBackingStateIsImmutable() = isolated { _ ->
        val backing = arrayListOf("minecraft:pig")
        val selector = MCFPPEntityType(types = Collections.unmodifiableList(backing))
        assertFailsWith<IllegalStateException> { DeclarationStateJournal.checkpoint(listOf(selector)) }
        assertEquals("minecraft:pig", backing.single())
    }

    companion object {
        @JvmStatic fun nativeBody(@Suppress("UNUSED_PARAMETER") context: NativeCallContext) { }
    }
}
