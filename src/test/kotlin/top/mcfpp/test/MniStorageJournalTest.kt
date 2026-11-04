package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.command.Command
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBool
import top.mcfpp.core.lang.nbt.MCString
import top.mcfpp.core.lang.nbt.NBTBasedData
import top.mcfpp.lib.*
import top.mcfpp.model.Member
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.property.Property
import top.mcfpp.nbt.tags.CompoundTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniStorageJournalTest {
    private fun path(name: String) = NBTPath(StorageSource("test:journal")).memberIndex(name)

    private inline fun inFunction(action: (Function) -> Unit) {
        Project.init()
        Project.config.version = "26.3"
        val previous = Function.currFunction
        val function = Function("journal", "test", null)
        Function.currFunction = function
        try {
            action(function)
            assertEquals(0, Project.errorCount)
        } finally {
            Function.currFunction = previous
        }
    }

    private fun runtimeInt(name: String): MCInt = MCInt(name).apply {
        nbtPath = path(name)
        StorageAccess.publishNbt(this)
    }

    @Test fun captureDoesNotBindAnUnboundAdapterAndRestoreRemovesItsPreparedBinding() = inFunction { function ->
        val value = MCInt("unbound")
        val originalPath = value.nbtPath
        val beforeCommands = function.commands.size
        val before = StorageAccess.checkpoint(listOf(value))
        assertNull(value.symbol)
        assertNull(value.storageBinding)
        assertEquals(beforeCommands, function.commands.size)
        StorageAccess.initializeLiteral(value, CompilerValue.Integral(9))
        StorageAccess.ensure(value)
        assertNotNull(value.storageBinding)
        before.restore()
        assertNull(value.symbol)
        assertNull(value.storageBinding)
        assertNull(value.storageReadVersion)
        assertSame(originalPath, value.nbtPath)
        before.restore()
        assertNull(value.storageBinding)
    }

    @Test fun nestedSavepointsRestoreActualAliasWritesWithoutReplacingAdapters() = inFunction { _ ->
        val value = MCInt(5, "value")
        value.bindDeclaration()
        val binding = StorageAccess.ensure(value)
        val alias = MCInt(value)
        val aliasBinding = assertNotNull(alias.storageBinding)
        val before = StorageAccess.checkpoint(listOf(value, alias))
        StorageAccess.write(alias, MCInt(7))
        assertEquals(7, (StorageAccess.read(value) as MCInt).value)
        val nested = before.captureCurrent()
        StorageAccess.write(value, MCInt(11))
        assertEquals(11, (StorageAccess.read(alias) as MCInt).value)
        nested.restore()
        assertEquals(7, (StorageAccess.read(value) as MCInt).value)
        assertEquals(7, (StorageAccess.read(alias) as MCInt).value)
        before.restore()
        assertSame(binding, value.storageBinding)
        assertSame(aliasBinding, alias.storageBinding)
        assertSame(binding.data, alias.storageBinding!!.data)
        assertEquals(5, (StorageAccess.read(alias) as MCInt).value)
        // The command journal discards the earlier prepare functions; use a fresh sink here.
        val continuation = Function("continuation", "test", null)
        Function.currFunction = continuation
        Function.addCommand("data modify storage mcfpp:system stack_frame prepend value {}")
        StorageAccess.materialize(alias)
        assertEquals(5, ScoreCommandExecutor(continuation.commands.analyzeAll()).read(value))
        StorageAccess.write(alias, MCInt(13))
        assertEquals(13, (StorageAccess.read(value) as MCInt).value)
        assertEquals(13, ScoreCommandExecutor(continuation.commands.analyzeAll()).read(value))
        before.restore()
        assertEquals(5, (StorageAccess.read(value) as MCInt).value)
    }

    @Test fun mergedSavepointsRetainDetachedPathsAndNewStorageAliases() = inFunction { _ ->
        val original = MCInt("original").apply { nbtPath = path("original") }
        val detachedPath = original.nbtPath
        val detachedSegments = detachedPath.pathList.toList()
        val before = StorageAccess.checkpoint(listOf(original))
        val replacementPath = path("replacement")
        original.nbtPath = replacementPath
        val added = MCInt(7, "added").apply { bindDeclaration() }
        val alias = MCInt(added)
        val binding = assertNotNull(added.storageBinding)
        val joined = before.captureCurrent(listOf(added, alias))
        detachedPath.pathList.clear()
        StorageAccess.write(alias, MCInt(9))
        joined.restore()
        assertSame(replacementPath, original.nbtPath)
        assertEquals(detachedSegments, detachedPath.pathList)
        assertSame(binding, added.storageBinding)
        assertSame(binding.data, alias.storageBinding!!.data)
        assertEquals(7, (StorageAccess.read(alias) as MCInt).value)

        val next = joined.captureCurrent()
        detachedPath.pathList.clear()
        StorageAccess.write(added, MCInt(11))
        next.restore()
        assertEquals(detachedSegments, detachedPath.pathList)
        assertEquals(7, (StorageAccess.read(alias) as MCInt).value)
        before.restore()
        assertSame(detachedPath, original.nbtPath)
    }

    @Test fun failedPreparationRestoresPendingWriterAndEmitsTheOriginalRuntimeValue() = inFunction { function ->
        val value = runtimeInt("pending")
        val binding = assertNotNull(value.storageBinding)
        var originalRuns = 0
        var replacementRuns = 0
        binding.data.refreshWriter {
            originalRuns++
            Function.addCommand("data modify storage test:journal pending set value 17")
        }
        val before = StorageAccess.checkpoint(listOf(value))
        val staging = Function("prepare", "test", null)
        assertFailsWith<IllegalArgumentException> {
            Function.currFunction = staging
            try {
                StorageAccess.intRegister(value)
                binding.data.relocate(path("wrong"), StorageLayout.Nbt("test:journal", "wrong")) {
                    replacementRuns++
                    Function.addCommand("data modify storage test:journal wrong set value 99")
                }
                throw IllegalArgumentException("prepare failed after materialization")
            } finally {
                Function.currFunction = function
                before.restore()
            }
        }
        assertEquals(1, originalRuns)
        assertTrue(function.commands.isEmpty())
        val register = StorageAccess.intRegister(value)
        assertEquals(2, originalRuns, "The discarded prepare must not consume the deferred writer")
        assertEquals(0, replacementRuns)
        val commands = function.commands.analyzeAll()
        assertFalse(commands.any { "wrong" in it })
        val machine = ScoreCommandExecutor(commands)
        assertEquals(17, machine.values["${register.player} ${register.objective}"])
        assertEquals(17, machine.read(value))
    }

    @Test fun preparedAfterStateRestoresRegistersAndMaterializationOwnership() = inFunction { function ->
        val value = runtimeInt("cached")
        val binding = assertNotNull(value.storageBinding)
        val before = StorageAccess.checkpoint(listOf(value))
        val firstRegister = StorageAccess.intRegister(value)
        assertEquals(1, function.commands.size)
        val after = before.captureCurrent()
        before.restore()
        val commandCount = function.commands.size
        val retryRegister = StorageAccess.intRegister(value)
        assertEquals(commandCount + 1, function.commands.size, "Failed preparation's load must be emitted again")
        assertNotEquals(firstRegister, retryRegister)
        after.restore()
        val countAfterRestore = function.commands.size
        assertEquals(firstRegister, StorageAccess.intRegister(value))
        assertEquals(countAfterRestore, function.commands.size, "Applying prepared state retains its valid load cache")
        val other = Function("other", "test", null)
        Function.currFunction = other
        try {
            assertEquals(firstRegister, StorageAccess.intRegister(value))
            assertEquals(1, other.commands.size, "A different owner must perform its own load")
        } finally {
            Function.currFunction = function
        }
        assertSame(binding, value.storageBinding)
    }

    @Test fun rollbackPreservesExistingLoadsButRestoresTheExactWriteClock() = inFunction { function ->
        val value = runtimeInt("versioned")
        val binding = assertNotNull(value.storageBinding)
        val register = StorageAccess.intRegister(value)
        val originalVersion = binding.data.versions.version(binding.place)
        val before = StorageAccess.checkpoint(listOf(value))
        binding.data.versions.invalidate(binding.place)
        val discardedVersion = binding.data.versions.version(binding.place)
        StorageAccess.intRegister(value)
        val afterMutationCount = function.commands.size
        before.restore()
        assertEquals(originalVersion, binding.data.versions.version(binding.place))
        assertEquals(register, StorageAccess.intRegister(value))
        assertEquals(afterMutationCount, function.commands.size, "Rollback must retain the preexisting owner cache")
        binding.data.versions.invalidate(binding.place)
        assertEquals(discardedVersion, binding.data.versions.version(binding.place))
        StorageAccess.intRegister(value)
        assertEquals(afterMutationCount + 1, function.commands.size)
    }

    @Test fun mutableLayoutListsAndChildLayoutsCannotCorruptSavedMaterializationKeys() = inFunction { function ->
        val value = runtimeInt("layouts")
        val binding = assertNotNull(value.storageBinding)
        val components = mutableListOf(StorageLayout.Scoreboard("sign", "s"), StorageLayout.Scoreboard("int", "i"))
        val expected = StorageLayout.LegacyFloat(components.toList())
        binding.data.layout = StorageLayout.LegacyFloat(components)
        val child = binding.place.field("child")
        binding.data.registerLayout(child, expected)
        binding.data.versions.materialize(binding.place, binding.data.layout, function)
        binding.data.versions.materialize(binding.place, binding.data.layout)
        val cached = binding.data.register(child, value.type.typeId, "original")
        val before = StorageAccess.checkpoint(listOf(value))
        components[0] = StorageLayout.Scoreboard("corrupt", "changed")
        binding.data.relocate(path("relocated"), StorageLayout.CompilerOnly, null)
        binding.data.registerLayout(child, StorageLayout.Constant)
        binding.data.register(child, value.type.typeId, "replacement")
        before.restore()
        assertEquals(expected, binding.data.layout)
        assertEquals(expected, binding.data.layoutAt(child))
        assertTrue(binding.data.versions.isMaterialized(binding.place, expected, function))
        assertTrue(binding.data.versions.isMaterialized(binding.place, expected))
        assertEquals(cached, binding.data.register(child, value.type.typeId, "unused"))
    }

    @Test fun dynamicPathOperandsAndTheirSourceGraphRestoreByIdentity() = inFunction { function ->
        val index = runtimeInt("index")
        val owner = runtimeInt("container")
        val ownerBinding = assertNotNull(owner.storageBinding)
        val indexBinding = assertNotNull(index.storageBinding)
        val address = ownerBinding.data.path.intIndex(index)
        val place = ownerBinding.place.unknownIndex()
        val binding = StorageBinding(ownerBinding.data, place, address)
        val element = MCInt("element").apply {
            storageBinding = binding
            nbtPath = address
        }
        ownerBinding.data.facts.refine(place, ValueFacts(TypeKnowledge.Exact(MCFPPBaseType.Int.typeId), ValueKnowledge.Unknown))
        // Cyclic retained sources are legal: capturing them must terminate without hash/equals calls.
        indexBinding.data.retainSource(ownerBinding.data)
        val before = StorageAccess.checkpoint(listOf(element))
        val originalSourcePath = indexBinding.data.path
        indexBinding.data.relocate(path("wrong_index"), StorageLayout.Nbt("test:journal", "wrong_index"), null)
        StorageAccess.initializeLiteral(index, CompilerValue.Integral(99))
        val extra = runtimeInt("extra").storageBinding!!.data
        ownerBinding.data.retainSource(extra)
        address.source = StorageSource("wrong:source")
        address.pathList.clear()
        before.restore()
        assertSame(binding, element.storageBinding)
        assertSame(indexBinding, index.storageBinding)
        assertSame(originalSourcePath, indexBinding.data.path)
        assertSame(indexBinding.data, ownerBinding.data.source(indexBinding.place))
        assertNull(ownerBinding.data.source(extra.root))
        assertSame(index, (address.pathList.last() as IntPath).value)
        Function.addCommand("data modify storage test:journal index set value 4")
        val register = StorageAccess.intRegister(index)
        val machine = ScoreCommandExecutor(function.commands.analyzeAll())
        assertEquals(4, machine.values["${register.player} ${register.objective}"])
        assertFalse(function.commands.any { "wrong_index" in it.toString() })
    }

    @Test fun pathListsMembersAndAdapterMetadataRestoreInPlace() = inFunction { function ->
        val value = runtimeInt("metadata")
        val binding = assertNotNull(value.storageBinding)
        val originalPath = value.nbtPath
        val originalSource = originalPath.source
        val originalMember = originalPath.pathList.single() as MemberPath
        val originalKey = originalMember.value
        val originalAnnotations = value.annotations
        val holder = object : ScoreHolder {
            override fun onScoreChange(score: MCInt) = Unit
            override fun replaceScore(score: MCInt) = Unit
        }
        value.holder = holder
        value.stackIndex = 3
        value.isDataOnly = true
        value.storageReadVersion = function to binding.data.versions.version(binding.place)
        val before = StorageAccess.checkpoint(listOf(value))
        originalMember.value = MCString(StringTag("corrupt"))
        originalPath.source = StorageSource("changed:source")
        originalPath.pathList.clear()
        value.nbtPath = path("different")
        value.identifier = "renamed"
        value.name = "different_score"
        value.sbObject = SbObject.MCFPP_TEMP
        value.holder = null
        value.isDataOnly = false
        value.stackIndex = 19
        value.isConst = true
        value.isStatic = true
        value.isFinal = true
        value.isTemp = true
        value.isError = true
        value.nullable = true
        value.parent = MCInt("different_parent")
        value.accessModifier = Member.AccessModifier.PRIVATE
        value.annotations = arrayListOf()
        value.type = MCFPPBaseType.Any
        value.storageReadVersion = null
        before.restore()
        assertSame(originalPath, value.nbtPath)
        assertSame(originalSource, originalPath.source)
        assertSame(originalMember, originalPath.pathList.single())
        assertSame(originalKey, originalMember.value)
        assertSame(originalAnnotations, value.annotations)
        assertEquals("metadata", value.identifier)
        assertEquals("metadata", value.name)
        assertSame(SbObject.MCFPP_default, value.sbObject)
        assertSame(holder, value.holder)
        assertTrue(value.isDataOnly)
        assertEquals(3, value.stackIndex)
        assertEquals(MCFPPBaseType.Int, value.type)
        assertFalse(value.isConst || value.isStatic || value.isFinal || value.isTemp || value.isError || value.nullable)
        assertNull(value.parent)
        assertEquals(Member.AccessModifier.PUBLIC, value.accessModifier)
        assertEquals(function to binding.data.versions.version(binding.place), value.storageReadVersion)
    }

    @Test fun relatedPropertyAndFloatAdaptersRetainTheirOriginalComponents() = inFunction { _ ->
        val field = runtimeInt("field")
        val caller = MCInt("caller")
        val property = Property.buildSimpleProperty(field)
        val wrapper = PropertyVar(property, field, caller)
        val float = MCFloat("float")
        val component = float.int0
        val bool = ScoreBool("bool")
        val objective = bool.boolObject
        val before = StorageAccess.checkpoint(listOf(wrapper, float, bool))
        wrapper.field = MCInt("replacement")
        field.storageBinding = null
        caller.isError = true
        property.parent = null
        float.int0 = MCInt("replacement_component")
        component.name = "corrupt_component"
        bool.name = "corrupt_bool"
        bool.boolObject = SbObject.MCFPP_TEMP
        bool.isDataOnly = true
        before.restore()
        assertSame(field, wrapper.field)
        assertSame(caller, property.parent)
        assertNotNull(field.storageBinding)
        assertFalse(caller.isError)
        assertSame(component, float.int0)
        assertEquals("float", component.name)
        assertEquals("bool", bool.name)
        assertSame(objective, bool.boolObject)
        assertFalse(bool.isDataOnly)
    }

    @Test fun factsShapeDeclarationsAndReachabilitySurviveRepeatedBeforeAfterApplication() = inFunction { _ ->
        val value = runtimeInt("facts")
        val binding = assertNotNull(value.storageBinding)
        val facts = binding.data.facts
        val types = binding.data.types
        val declarations = binding.data.declarations
        facts.setLength(binding.place, 2)
        val original = facts.fork()
        val before = StorageAccess.checkpoint(emptyList(), listOf(binding.data))
        facts.setLength(binding.place, 5)
        facts.reachable = false
        facts.refine(binding.place, ValueFacts(TypeKnowledge.Unknown, ValueKnowledge.Unknown, ValueState.ERROR))
        types.clear()
        declarations.clear()
        val after = before.captureCurrent()
        before.restore()
        assertSame(facts, binding.data.facts)
        assertSame(types, binding.data.types)
        assertSame(declarations, binding.data.declarations)
        assertEquals(original, facts)
        assertSame(MCFPPBaseType.Int, types[value.type.typeId])
        assertEquals(value.symbol, declarations[binding.place])
        after.restore()
        assertFalse(facts.reachable)
        assertEquals(5, facts.length(binding.place))
        assertEquals(ValueState.ERROR, facts.read(binding.place)?.state)
        assertTrue(types.isEmpty())
        assertTrue(declarations.isEmpty())
        before.restore()
        assertEquals(original, facts)
    }

    @Test fun sharedObjectivesAndDetachedDisplaysRestoreBeforeAfterBeforeByIdentity() = inFunction { function ->
        val text = PlainChatComponent("before")
        val hover = PlainChatComponent("before hover")
        val style = HoverEventShowTextStyle(hover)
        text.styles += style
        val display = ListChatComponent().apply { components += text }
        val objective = SbObject("journal_before", "dummy", display)
        val integer = MCInt(6, "integer").apply { sbObject = objective }
        val alias = MCInt(integer)
        val bool = ScoreBool(true, "bool").apply { boolObject = objective }
        val float = MCFloat(1f, "float").apply {
            sbObject = objective
            listOf(sign, int0, int1, exp).forEach { it.sbObject = objective }
        }
        val originalComponents = listOf(float.sign, float.int0, float.int1, float.exp)
        val displayComponents = display.components
        val textStyles = text.styles
        val before = StorageAccess.checkpoint(listOf(integer, alias, bool, float))
        objective.name = "journal_after"
        objective.criterion = "trigger"
        text.value = "after detached"
        hover.value = "after hover"
        text.styles.clear()
        val afterText = PlainChatComponent("after")
        val afterDisplay = ListChatComponent().apply { components += afterText }
        display.components.clear()
        display.components += afterText
        objective.display = afterDisplay
        val after = before.captureCurrent()

        repeat(2) {
            before.restore()
            assertEquals("journal_before", objective.name)
            assertEquals("dummy", objective.criterion)
            assertSame(display, objective.display)
            assertSame(displayComponents, display.components)
            assertSame(text, display.components.single())
            assertEquals("before", text.value)
            assertSame(textStyles, text.styles)
            assertSame(style, text.styles.single())
            assertEquals("before hover", hover.value)
            listOf(integer, alias, float).forEach { assertSame(objective, it.sbObject) }
            assertSame(objective, bool.boolObject)
            originalComponents.forEach { assertSame(objective, it.sbObject) }

            after.restore()
            assertEquals("journal_after", objective.name)
            assertEquals("trigger", objective.criterion)
            assertSame(afterDisplay, objective.display)
            assertSame(afterText, afterDisplay.components.single())
            assertEquals("after", afterText.value)
            assertSame(afterText, display.components.single(), "Detached original display still has observable aliases")
            assertEquals("after detached", text.value)
            assertTrue(text.styles.isEmpty())
            assertEquals("after hover", hover.value)
            // An application is reusable even when the currently installed graph has since changed.
            afterText.value = "discarded subsequent edit"
            text.value = "discarded detached edit"
        }
        before.restore()
        val score = StorageAccess.intRegister(integer)
        assertEquals("journal_before", score.objective)
        val machine = ScoreCommandExecutor(function.commands.analyzeAll())
        assertEquals(6, machine.values[score.player + " journal_before"])
    }

    @Test fun completeSupportedDisplayGraphRestoresNestedTextStylesAndReferencedAdapters() = inFunction { _ ->
        val objective = SbObject("graph_objective")
        val score = MCInt(8, "display_score").apply { sbObject = objective }
        val nbt = NBTBasedData(CompoundTag(), "display_nbt")
        val originalNbtBinding = nbt.storageBinding
        val originalScoreBinding = score.storageBinding
        val separator = PlainChatComponent("separator")
        val hoverName = PlainChatComponent("entity name")
        val text = PlainChatComponent("shared text")
        val arguments = arrayListOf<ChatComponent>(text)
        val translated = TranslatableChatComponent("test.key", "fallback", arguments)
        val nested = ListChatComponent().apply { components += text }
        val scored = ScoreChatComponent(score)
        val selected = SelectorChatComponent("@s", separator)
        val encoded = EncodedChatComponent("""{"text":"encoded"}""")
        val keybind = KeybindChatComponent("key.jump")
        val nbtComponent = NBTChatComponent(nbt, separator = separator)
        val display = ListChatComponent().apply {
            components.addAll(listOf(nested, translated, scored, selected, encoded, keybind, nbtComponent))
            styles.addAll(
                listOf(
                    ColorStyle.RED, BoldStyle(true), ItalicStyle(true), UnderlineStyle(true),
                    StrikethroughStyle(true), ObfuscatedStyle(true), InsertionStyle("insert"),
                    ClickEventStyle(ClickEventStyle.ClickEventAction.RUN_COMMAND, "/test"),
                    HoverEventShowTextStyle(text), HoverEventShowEntityStyle(hoverName, "minecraft:pig", nbt)
                )
            )
        }
        objective.display = display // score -> objective -> display -> score is an intentional identity cycle.
        val expectedScoreText = scored.toCommandPart().analyze()
        val expectedStyles = display.styles.toList()
        val before = StorageAccess.checkpoint(listOf(score))
        text.value = "mutated"
        separator.value = "mutated separator"
        hoverName.value = "mutated name"
        arguments.clear()
        arguments += PlainChatComponent("new argument")
        nested.components.clear()
        display.styles.clear()
        display.components.clear()
        score.name = "changed_score"
        score.sbObject = SbObject("changed_objective")
        score.storageBinding = null
        nbt.storageBinding = null
        before.restore()
        assertSame(objective, score.sbObject)
        assertEquals(expectedScoreText, scored.toCommandPart().analyze())
        assertSame(originalScoreBinding, score.storageBinding)
        assertSame(originalNbtBinding, nbt.storageBinding)
        assertSame(text, arguments.single())
        assertSame(text, nested.components.single())
        assertSame(translated, display.components[1])
        assertSame(scored, display.components[2])
        assertSame(selected, display.components[3])
        assertSame(encoded, display.components[4])
        assertSame(keybind, display.components[5])
        assertSame(nbtComponent, display.components[6])
        assertEquals("shared text", text.value)
        assertEquals("separator", separator.value)
        assertEquals("entity name", hoverName.value)
        assertEquals(expectedStyles.size, display.styles.size)
        expectedStyles.forEachIndexed { index, style -> assertSame(style, display.styles[index]) }
    }

    @Test fun sharedPropertyRestoresEveryAccessAndDeclarationFlagAcrossRepeatedApplication() = inFunction { _ ->
        val field = runtimeInt("property_field")
        val caller = MCInt("caller")
        val otherCaller = MCInt("other_caller")
        val originalTemplate = DataTemplate("PropertyBefore", "test")
        val afterTemplate = DataTemplate("PropertyAfter", "test")
        val property = Property.buildSimpleProperty(field).apply {
            parent = caller
            declaredParentTemplate = originalTemplate
            accessModifier = Member.AccessModifier.PROTECTED
            isFinal = true
            isStatic = false
            isInherited = true
        }
        val first = PropertyVar(property, field, caller)
        val second = PropertyVar(property, field, caller)
        val before = StorageAccess.checkpoint(listOf(first, second))
        property.parent = otherCaller
        property.declaredParentTemplate = afterTemplate
        property.accessModifier = Member.AccessModifier.PRIVATE
        property.isFinal = false
        property.isStatic = true
        property.isInherited = false
        val after = before.captureCurrent()
        repeat(2) {
            before.restore()
            assertSame(property, first.property)
            assertSame(property, second.property)
            assertSame(caller, property.parent)
            assertSame(originalTemplate, property.declaredParentTemplate)
            assertEquals(Member.AccessModifier.PROTECTED, property.accessModifier)
            assertTrue(property.isFinal)
            assertFalse(property.isStatic)
            assertTrue(property.isInherited)
            after.restore()
            assertSame(otherCaller, property.parent)
            assertSame(afterTemplate, property.declaredParentTemplate)
            assertEquals(Member.AccessModifier.PRIVATE, property.accessModifier)
            assertFalse(property.isFinal)
            assertTrue(property.isStatic)
            assertFalse(property.isInherited)
        }
        before.restore()
        assertSame(originalTemplate, property.declaredParentTemplate)
    }

    @Test fun translatableArgumentListsRestoreTheirOriginalElementsAndIdentity() = inFunction { _ ->
        val original = PlainChatComponent("original")
        val replacement = PlainChatComponent("replacement")
        val lists = listOf<MutableList<ChatComponent>>(
            arrayListOf(original),
            java.util.LinkedList<ChatComponent>(listOf(original)),
            java.util.Arrays.asList<ChatComponent>(original)
        )
        for ((index, arguments) in lists.withIndex()) {
            val translated = TranslatableChatComponent("key", args = arguments)
            val display = ListChatComponent().apply { components += translated }
            val value = MCInt("list_" + index).apply { sbObject = SbObject("args_" + index, "dummy", display) }
            val before = StorageAccess.checkpoint(listOf(value))
            arguments[0] = replacement
            before.restore()
            assertSame(arguments, translated.args)
            assertSame(original, arguments.single())
        }
        val immutable = listOf(
            emptyList<ChatComponent>(),
            listOf(original),
            java.util.List.of<ChatComponent>(original, replacement),
            java.util.List.of<ChatComponent>()
        )
        for ((index, arguments) in immutable.withIndex()) {
            val display = ListChatComponent().apply { components += TranslatableChatComponent("key", args = arguments) }
            val value = MCInt("immutable_" + index).apply { sbObject = SbObject("immutable_args_" + index, "dummy", display) }
            StorageAccess.checkpoint(listOf(value)).restore()
            assertSame(arguments, (display.components.single() as TranslatableChatComponent).args)
        }
    }

    @Test fun unknownDisplayShapesAreRejectedWithoutCallingHostRenderingCode() = inFunction { _ ->
        var rendered = 0
        val unknownComponent = object : ChatComponent() {
            override fun toCommandPart(): Command {
                rendered++
                error("Host renderer must not run")
            }
        }
        val unknownStyle = object : ChatComponentStyle {
            override fun toCommandPart(): Command {
                rendered++
                error("Host renderer must not run")
            }
        }
        fun value(display: ListChatComponent, name: String) = MCInt(name).apply {
            sbObject = SbObject(name + "_objective", "dummy", display)
        }
        val componentDisplay = ListChatComponent().apply { components += unknownComponent }
        assertTrue(
            assertFailsWith<IllegalStateException> {
                StorageAccess.checkpoint(listOf(value(componentDisplay, "unknown_component")))
            }.message!!.contains("chat component")
        )
        val styleDisplay = ListChatComponent().apply { styles += unknownStyle }
        assertTrue(
            assertFailsWith<IllegalStateException> {
                StorageAccess.checkpoint(listOf(value(styleDisplay, "unknown_style")))
            }.message!!.contains("chat style")
        )
        val backing = arrayListOf<ChatComponent>(PlainChatComponent("hidden backing"))
        val readOnlyView = java.util.Collections.unmodifiableList(backing)
        val argumentDisplay = ListChatComponent().apply {
            components += TranslatableChatComponent("key", args = readOnlyView)
        }
        assertTrue(
            assertFailsWith<IllegalStateException> {
                StorageAccess.checkpoint(listOf(value(argumentDisplay, "unknown_arguments")))
            }.message!!.contains("argument list")
        )
        val unknownArguments = object : AbstractList<ChatComponent>() {
            override val size: Int get() {
                rendered++
                error("Host list must not be inspected")
            }
            override fun get(index: Int): ChatComponent {
                rendered++
                error("Host list must not be inspected")
            }
        }
        val hostListDisplay = ListChatComponent().apply {
            components += TranslatableChatComponent("key", args = unknownArguments)
        }
        assertTrue(
            assertFailsWith<IllegalStateException> {
                StorageAccess.checkpoint(listOf(value(hostListDisplay, "host_arguments")))
            }.message!!.contains("argument list")
        )
        assertEquals(0, rendered)
    }
}
