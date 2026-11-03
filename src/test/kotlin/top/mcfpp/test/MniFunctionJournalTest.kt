package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.command.*
import top.mcfpp.core.lang.MCInt
import top.mcfpp.doc.AuthorTag
import top.mcfpp.doc.Document
import top.mcfpp.mni.annotation.NoExternalWrites
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.mni.expand.CompilerStateJournal
import top.mcfpp.mni.expand.FunctionStateJournal
import top.mcfpp.model.Member
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.FunctionScope
import top.mcfpp.model.scope.InternalFunctionScope
import top.mcfpp.model.scope.NoStackFunctionScope
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPPrivateType
import kotlin.test.*
import kotlin.test.Test

class MniFunctionJournalTest {
    private inline fun inFunction(action: (Function) -> Unit) {
        Project.init()
        Project.config.version = "26.3"
        val previous = Function.currFunction
        val forced = Function.forcedField
        val function = Function("journal", "test", null)
        Function.currFunction = function
        try {
            action(function)
            assertEquals(0, Project.errorCount)
        } finally {
            Function.currFunction = previous
            Function.forcedField = forced
        }
    }

    @Test fun analyzedCommandsRecoverReplacementPointsAndOriginalListIdentity() = inFunction { function ->
        val command = Command("before", "slot").build("tail")
        val comment = Comment("# original", CommentLevel.WARN)
        function.commands.add(command)
        function.commands.add(comment)
        val commands = function.commands
        val before = FunctionStateJournal.checkpoint(listOf(function))
        assertEquals(1, command.replace("slot" to "after"))
        command.analyze()
        command.isCompleted = true
        comment.build("extra")
        function.commands = CommandList().apply { add("replacement") }
        before.restore()
        assertSame(commands, function.commands)
        assertSame(command, function.commands[0])
        assertSame(comment, function.commands[1])
        assertFalse(command.isCompleted)
        assertEquals("before tail", command.toString())
        assertEquals(1, command.replace("slot" to "continued"))
        assertEquals("continued tail", command.toString())
        before.restore()
        assertEquals("before tail", command.toString())
        assertEquals("# original", comment.toString())
    }

    @Test fun sharedMacroPartsKeepTheSameAdapterAcrossNestedSavepoints() = inFunction { function ->
        val value = MCInt(3, "source")
        value.bindDeclaration()
        val command = Command("say").buildMacro(value)
        val shared = Command().build(command)
        function.commands.add(command)
        function.commands.add(shared)
        val before = FunctionStateJournal.checkpoint(listOf(function))
        value.identifier = "prepared"
        command.build("suffix")
        val after = before.captureCurrent()
        before.restore()
        assertEquals("say $(source)", command.toString())
        assertEquals("say $(source)", shared.toString())
        after.restore()
        assertEquals("say $(prepared) suffix", command.toString())
        assertEquals("say $(prepared)", shared.toString())
        before.restore()
        assertEquals("source", value.identifier)
    }

    @Test fun commandTagsAndCompletionStateAreRestoredInPlace() = inFunction { function ->
        val command = Command("say original")
        // Legacy host code can access this private metadata; the production snapshot uses no reflection.
        val field = Command::class.java.getDeclaredField("tags").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val tags = field.get(command) as MutableList<String>
        tags.add("original")
        function.commands.add(command)
        val before = FunctionStateJournal.checkpoint(listOf(function))
        tags.clear()
        tags.add("prepared")
        command.isCompleted = true
        val after = before.captureCurrent()
        before.restore()
        assertSame(tags, field.get(command))
        assertTrue(command.hasTag("original"))
        assertFalse(command.hasTag("prepared"))
        assertFalse(command.isCompleted)
        after.restore()
        assertTrue(command.hasTag("prepared"))
        assertTrue(command.isCompleted)
        before.restore()
        assertEquals(listOf("original"), tags)
    }

    @Test fun returnsParametersAndMutableMetadataRecoverTheirOriginalObjects() = inFunction { function ->
        function.returnType = MCFPPBaseType.Int
        val returnVar = function.returnVar
        val param = FunctionParam(MCFPPBaseType.Int, "input", function)
        param.defaultVar = MCInt(4)
        param.hasDefault = true
        function.normalParams.add(param)
        val params = function.normalParams
        val defaultTypes = param.defaultTypes
        val context = function.context
        val document = function.document.apply { tags.add(AuthorTag("before")) }
        val indices = function.excludedArgIndex.apply { add(1) }
        val before = FunctionStateJournal.checkpoint(listOf(function))
        function.returnType = MCFPPBaseType.Bool
        function.normalParams = arrayListOf()
        param.type = MCFPPBaseType.Bool
        param.identifier = "changed"
        param.typeName = "lie"
        param.isReadOnly = true
        param.hasDefault = false
        param.defaultVar = MCInt(99)
        function.context = FunctionContext()
        context.caller = MCInt(9)
        document.tags.clear()
        function.document = Document()
        indices.clear()
        indices.add(2)
        function.excludedArgIndex = hashSetOf(3)
        function.identifier = "changed"
        function.namespace = "other"
        function.isReturned = true
        function.isEnded = true
        function.isAbstract = true
        function.hasReturnStatement = true
        function.isFinal = true
        function.isOverride = true
        function.accessModifier = Member.AccessModifier.PRIVATE
        function.bodyCompiled = true
        function.bodyBeingCompiled = true
        function.actualCallBody = true
        function.accessorField = "field"
        function.boundReadonlyNames = setOf("readonly")
        before.restore()
        assertSame(returnVar, function.returnVar)
        assertSame(MCFPPBaseType.Int, function.returnType)
        assertSame(params, function.normalParams)
        assertSame(param, params.single())
        assertEquals("input", param.identifier)
        assertSame(MCFPPBaseType.Int, param.type)
        assertEquals(4, (param.defaultVar as MCInt).value)
        assertSame(defaultTypes, param.defaultTypes)
        assertFalse(param.isReadOnly)
        assertTrue(param.hasDefault)
        assertSame(context, function.context)
        assertNull(context.caller)
        assertSame(document, function.document)
        assertEquals(1, document.tags.size)
        assertEquals("before", assertIs<AuthorTag>(document.tags.single()).content)
        assertSame(indices, function.excludedArgIndex)
        assertEquals(setOf<Byte>(1), indices)
        assertEquals("journal", function.identifier)
        assertEquals("test", function.namespace)
        assertFalse(function.isReturned)
        assertFalse(function.isEnded)
        assertFalse(function.isAbstract)
        assertFalse(function.hasReturnStatement)
        assertFalse(function.isFinal)
        assertFalse(function.isOverride)
        assertEquals(Member.AccessModifier.PUBLIC, function.accessModifier)
        assertFalse(function.bodyCompiled)
        assertFalse(function.bodyBeingCompiled)
        assertFalse(function.actualCallBody)
        assertNull(function.accessorField)
        assertTrue(function.boundReadonlyNames.isEmpty())
    }

    @Test fun scopeReplacementAndInheritedAliasWritesRestoreWithoutCloning() = inFunction { function ->
        val parent = FunctionScope(null)
        val original = MCInt(5, "value")
        parent.putVar("value", original)
        val binding = StorageAccess.ensure(original)
        val scope = InternalFunctionScope(parent)
        function.scope = scope
        val parents = scope.parent
        val before = FunctionStateJournal.checkpoint(listOf(function))
        val alias = MCInt(original)
        StorageAccess.write(alias, MCInt(19))
        parent.vars.clear()
        parent.fieldVarSet.clear()
        parent.types["new"] = MCFPPBaseType.Bool
        scope.parent.clear()
        scope.parent = arrayListOf(null)
        function.scope = FunctionScope(null)
        before.restore()
        assertSame(scope, function.scope)
        assertSame(parents, scope.parent)
        assertSame(parent, parents.single())
        assertSame(original, parent.vars.getValue("value"))
        assertTrue("value" in parent.fieldVarSet)
        assertFalse("new" in parent.types)
        assertSame(binding, original.storageBinding)
        assertEquals(5, (StorageAccess.read(scope.getVar("value")!!) as MCInt).value)
        StorageAccess.write(original, MCInt(8))
        assertEquals(8, (StorageAccess.read(alias) as MCInt).value)
    }

    @Test fun callGraphCyclesTagsAndDetachedFunctionsRestoreTheirOriginalMembership() = inFunction { function ->
        val callee = Function("callee", "test", null)
        function.child.add(callee)
        callee.parent.add(function)
        function.innerFunction.add(callee)
        function.superFunction = callee
        val tag = FunctionTag("test", "tag").apply { functions.add(function) }
        function.tags.add(tag)
        val before = FunctionStateJournal.checkpoint(listOf(function))
        function.child.clear()
        function.innerFunction.clear()
        function.tags.clear()
        function.superFunction = null
        callee.parent.clear()
        callee.commands.add("say prepared")
        tag.namespace = "other"
        tag.identifier = "changed"
        tag.functions = arrayListOf(callee)
        val after = before.captureCurrent()
        before.restore()
        assertSame(callee, function.child.single())
        assertSame(function, callee.parent.single())
        assertSame(callee, function.innerFunction.single())
        assertSame(callee, function.superFunction)
        assertSame(tag, function.tags.single())
        assertEquals("test:tag", tag.namespaceID)
        assertSame(function, tag.functions.single())
        assertTrue(callee.commands.isEmpty())
        after.restore()
        assertTrue(function.child.isEmpty())
        assertTrue(callee.parent.isEmpty())
        assertEquals("say prepared", callee.commands.single().toString())
        before.restore()
        assertTrue(callee.commands.isEmpty())
        assertSame(function, callee.parent.single())
    }

    @Test fun nativeMetadataAndReadonlyParameterStateRestoreTogether() = inFunction { _ ->
        val method = FunctionJournalNative::class.java.getMethod("mutate")
        val native = NativeFunction("native", javaMethod = method)
        native.appendReadOnlyParam(MCFPPBaseType.Int, "constant")
        val param = native.readOnlyParams.single()
        val before = FunctionStateJournal.checkpoint(listOf(native))
        native.javaMethod = FunctionJournalNative::class.java.getMethod("other")
        native.javaMethodName = "other"
        native.caller = MCFPPBaseType.Int
        native.returnsConstWhenArgsConst = true
        native.readOnlyParams.clear()
        param.identifier = "changed"
        before.restore()
        assertSame(method, native.javaMethod)
        assertEquals("native", native.javaMethodName)
        assertSame(MCFPPPrivateType.Void, native.caller)
        assertFalse(native.returnsConstWhenArgsConst)
        assertSame(param, native.readOnlyParams.single())
        assertEquals("constant", param.identifier)
    }

    @Test fun genericSpecializationsStaticAliasesAndReadonlyParamsRecoverTogether() = inFunction { _ ->
        val generic = GenericFunction("generic", "test", top.mcfpp.antlr.mcfppParser.CurlBlockContext(null, 0))
        val parameter = FunctionParam(MCFPPBaseType.Int, "constant", generic, isReadOnly = true)
        generic.readOnlyParams.add(parameter)
        val specialized = Function("specialized", "test", null)
        val key = SpecializationKeys.forArguments(generic, listOf(MCInt(3)))
        generic.compiledFunctions[key] = specialized
        val value = MCInt(5, "reference").apply { bindDeclaration() }
        generic.staticRefValue["reference"] = value
        val names = linkedSetOf("constant")
        generic.boundReadonlyNames = names
        val before = FunctionStateJournal.checkpoint(listOf(generic))
        generic.compiledFunctions.clear()
        specialized.commands.add("say prepared")
        generic.readOnlyParams.clear()
        parameter.identifier = "changed"
        generic.staticRefValue.clear()
        StorageAccess.write(value, MCInt(9))
        names.add("changed")
        val after = before.captureCurrent()
        before.restore()
        assertSame(specialized, generic.compiledFunctions.getValue(key))
        assertTrue(specialized.commands.isEmpty())
        assertSame(parameter, generic.readOnlyParams.single())
        assertEquals("constant", parameter.identifier)
        assertSame(value, generic.staticRefValue.getValue("reference"))
        assertEquals(5, (StorageAccess.read(value) as MCInt).value)
        assertSame(names, generic.boundReadonlyNames)
        assertEquals(setOf("constant"), names)
        after.restore()
        assertTrue(generic.compiledFunctions.isEmpty())
        assertTrue(generic.staticRefValue.isEmpty())
        assertEquals("say prepared", specialized.commands.single().toString())
        assertEquals(9, (StorageAccess.read(value) as MCInt).value)
        before.restore()
        assertSame(specialized, generic.compiledFunctions.getValue(key))
        assertEquals(setOf("constant"), names)
    }

    @Test fun actualLegacyNativeIsExecutedOnceAndItsPreparedWritesCanBeAppliedAndRolledBack() = inFunction { function ->
        val value = MCInt(5, "value").apply { bindDeclaration() }
        val alias = MCInt(value)
        function.scope.vars["value"] = value
        val command = Command("before", "slot")
        function.commands.add(command)
        val native = NativeFunction("mutate", javaMethod = FunctionJournalNative::class.java.getMethod("mutate"))
        FunctionJournalNative.command = command
        FunctionJournalNative.value = alias
        FunctionJournalNative.calls = 0
        val before = FunctionStateJournal.checkpoint(listOf(function, native), listOf(alias))
        val prepared = before.prepare { assertFalse(native.invoke(emptyList(), emptyList(), null).isError) }
        assertEquals(1, FunctionJournalNative.calls)
        assertEquals("before", command.toString())
        assertEquals(5, (StorageAccess.read(value) as MCInt).value)
        prepared.apply()
        assertEquals("prepared", command.toString())
        assertEquals(17, (StorageAccess.read(value) as MCInt).value)
        StorageAccess.materialize(value)
        assertEquals(
            17,
            ScoreCommandExecutor(
                listOf("data modify storage mcfpp:system stack_frame prepend value {}") +
                    function.commands.drop(1).map { it.analyze() }
            ).read(value)
        )
        prepared.rollback()
        assertEquals("before", command.toString())
        assertEquals(5, (StorageAccess.read(alias) as MCInt).value)
        prepared.apply()
        prepared.rollback()
        assertEquals(1, FunctionJournalNative.calls)
        assertEquals(5, (StorageAccess.read(value) as MCInt).value)
    }

    @Test fun failedPreparationRestoresExistingCommandsStorageAndCurrentFunction() = inFunction { function ->
        val value = MCInt(4, "value").apply { bindDeclaration() }
        function.scope.vars["value"] = value
        val command = Command("before", "slot").also { function.commands.add(it) }
        val scope = function.scope
        Function.forcedField = scope
        val before = FunctionStateJournal.checkpoint(listOf(function))
        val failure = assertFailsWith<IllegalArgumentException> {
            before.prepare {
                command.replace("slot" to "failed")
                command.analyze()
                StorageAccess.write(value, MCInt(99))
                Function.currFunction = Function("stray", "test", null)
                Function.forcedField = null
                throw IllegalArgumentException("prepare failed")
            }
        }
        assertEquals("prepare failed", failure.message)
        assertSame(function, Function.currFunction)
        assertSame(scope, Function.forcedField)
        assertEquals("before", command.toString())
        assertEquals(1, command.replace("slot" to "still works"))
        assertEquals(4, (StorageAccess.read(value) as MCInt).value)
    }

    @Test fun detachedCommandsAndVariablesRemainEnrolledInTheAfterSnapshot() = inFunction { function ->
        val command = Command("before", "slot").also { function.commands.add(it) }
        val value = MCInt(5, "value").apply { bindDeclaration() }
        function.scope.vars["value"] = value
        val before = FunctionStateJournal.checkpoint(listOf(function))
        function.commands.clear()
        function.scope.vars.clear()
        command.replace("slot" to "after")
        StorageAccess.write(value, MCInt(9))
        val preparedCommands = function.commands
        val preparedCommandObjects = preparedCommands.toList()
        val preparedCommandText = preparedCommandObjects.map { it.toString() }
        val preparedScope = function.scope
        val preparedVars = preparedScope.vars
        val preparedEntries = preparedVars.entries.map { it.key to it.value }
        assertTrue(preparedCommandObjects.isNotEmpty(), "The actual write must exercise generated commands")
        assertFalse(preparedCommandObjects.any { it === command })
        assertFalse(preparedEntries.any { it.second === value })
        val after = before.captureCurrent()
        before.restore()
        assertSame(command, function.commands.single())
        assertSame(value, function.scope.vars.getValue("value"))
        assertEquals(5, (StorageAccess.read(value) as MCInt).value)
        after.restore()
        assertSame(preparedCommands, function.commands)
        assertEquals(preparedCommandObjects.size, function.commands.size)
        preparedCommandObjects.forEachIndexed { index, expected ->
            assertSame(expected, function.commands[index])
            assertEquals(preparedCommandText[index], function.commands[index].toString())
        }
        assertSame(preparedScope, function.scope)
        assertSame(preparedVars, function.scope.vars)
        assertEquals(preparedEntries.map { it.first }, function.scope.vars.keys.toList())
        preparedEntries.forEach { (name, expected) -> assertSame(expected, function.scope.vars.getValue(name)) }
        assertFalse(function.commands.any { it === command })
        assertFalse(function.scope.vars.values.any { it === value })
        assertEquals("after", command.toString())
        assertEquals(9, (StorageAccess.read(value) as MCInt).value)
        before.restore()
        assertSame(command, function.commands.single())
        assertSame(value, function.scope.vars.getValue("value"))
        assertEquals("before", command.toString())
        assertEquals(5, (StorageAccess.read(value) as MCInt).value)
    }

    @Test fun replacedListsAndSetsRetainTheirDetachedAfterStateAndOriginalIdentity() = inFunction { function ->
        val command = Command("before")
        val parameter = FunctionParam(MCFPPBaseType.Int, "before", function)
        function.commands.add(command)
        function.normalParams.add(parameter)
        val commands = function.commands
        val parameters = function.normalParams
        val parents = function.scope.parent
        val originalParents = parents.toList()
        val indices = function.excludedArgIndex.apply { add(1) }
        val before = FunctionStateJournal.checkpoint(listOf(function))
        commands.clear()
        commands.add("detached after")
        parameters.clear()
        parameters.add(FunctionParam(MCFPPBaseType.Bool, "detached", function))
        val detachedParent = FunctionScope(null)
        parents.clear()
        parents.add(detachedParent)
        indices.clear()
        indices.add(2)
        val publishedCommands = CommandList().apply { add("published after") }
        val publishedParameters = arrayListOf<FunctionParam>()
        val publishedParents = arrayListOf<top.mcfpp.model.scope.IScope?>(null)
        function.commands = publishedCommands
        function.normalParams = publishedParameters
        function.scope.parent = publishedParents
        function.excludedArgIndex = hashSetOf(3)
        val after = before.captureCurrent()
        before.restore()
        assertSame(commands, function.commands)
        assertSame(command, commands.single())
        assertSame(parameters, function.normalParams)
        assertSame(parameter, parameters.single())
        assertSame(parents, function.scope.parent)
        assertEquals(originalParents, parents)
        assertSame(indices, function.excludedArgIndex)
        assertEquals(setOf<Byte>(1), indices)
        after.restore()
        assertSame(publishedCommands, function.commands)
        assertEquals("detached after", commands.single().toString())
        assertSame(publishedParameters, function.normalParams)
        assertEquals("detached", parameters.single().identifier)
        assertSame(publishedParents, function.scope.parent)
        assertSame(detachedParent, parents.single())
        assertEquals(setOf<Byte>(2), indices)
        assertEquals(setOf<Byte>(3), function.excludedArgIndex)
        before.restore()
        assertSame(command, commands.single())
        assertSame(parameter, parameters.single())
        assertEquals(originalParents, parents)
        assertEquals(setOf<Byte>(1), indices)
    }

    @Test fun unknownCompilerExtensionsAreRejectedBeforePreparationCanRun() = inFunction { function ->
        var calls = 0
        val unsupported = object : Command("host") {}
        function.commands.add(unsupported)
        assertFailsWith<IllegalStateException> {
            FunctionStateJournal.checkpoint(listOf(function)).prepare { calls++ }
        }
        assertEquals(0, calls)
        function.commands.clear()
        function.scope = object : FunctionScope(null) {}
        assertFailsWith<IllegalStateException> { FunctionStateJournal.checkpoint(listOf(function)) }
        val unknown = UnknownFunction("unknown").apply { data = StringBuilder("mutable") }
        assertFailsWith<IllegalStateException> { FunctionStateJournal.checkpoint(listOf(unknown)) }
        val custom = object : Function("custom", "test", null) {}
        assertFailsWith<IllegalStateException> { FunctionStateJournal.checkpoint(listOf(custom)) }
    }

    @Test fun unsupportedAfterStateStillRollsBackTheOriginalGraph() = inFunction { function ->
        val command = Command("before", "slot").also { function.commands.add(it) }
        val before = FunctionStateJournal.checkpoint(listOf(function))
        var calls = 0
        assertFailsWith<IllegalStateException> {
            before.prepare {
                calls++
                command.replace("slot" to "after")
                function.commands.add(object : Command("unknown") {})
            }
        }
        assertEquals(1, calls)
        assertSame(command, function.commands.single())
        assertEquals("before", command.toString())
        assertEquals(1, command.replace("slot" to "usable"))
    }

    @Test fun unknownReadonlySetIsRejectedWithoutCallingItsSizeOrIterator() = inFunction { function ->
        var sizeReads = 0
        var iterations = 0
        function.boundReadonlyNames = object : AbstractSet<String>() {
            override val size: Int get() {
                sizeReads++
                return 1
            }
            override fun iterator(): Iterator<String> {
                iterations++
                return listOf("host").iterator()
            }
        }
        assertFailsWith<IllegalStateException> { FunctionStateJournal.checkpoint(listOf(function)) }
        assertEquals(0, sizeReads)
        assertEquals(0, iterations)
    }

    @Test fun unknownParameterTypeMapIsRejectedWithoutReadingItsEntries() = inFunction { function ->
        var entryReads = 0
        val parameter = FunctionParam(MCFPPBaseType.Int, "parameter", function)
        parameter.defaultTypes = object : AbstractMap<top.mcfpp.type.TypeId, top.mcfpp.type.MCFPPType>() {
            override val entries: Set<Map.Entry<top.mcfpp.type.TypeId, top.mcfpp.type.MCFPPType>>
                get() {
                    entryReads++
                    return emptySet()
                }
        }
        function.normalParams.add(parameter)
        assertFailsWith<IllegalStateException> { FunctionStateJournal.checkpoint(listOf(function)) }
        assertEquals(0, entryReads)
    }

    @Test fun unknownTagFunctionListIsRejectedWithoutSizeArrayOrIteratorCallbacks() = inFunction { function ->
        var sizeReads = 0
        var arrayReads = 0
        var iterations = 0
        val tag = FunctionTag("test", "host")
        tag.functions = object : ArrayList<Function>() {
            override val size: Int get() {
                sizeReads++
                return super.size
            }
            override fun toArray(): Array<Any?> {
                arrayReads++
                return super.toArray()
            }
            override fun iterator(): MutableIterator<Function> {
                iterations++
                return super.iterator()
            }
        }
        tag.functions.add(function)
        function.tags.add(tag)
        assertFailsWith<IllegalStateException> { FunctionStateJournal.checkpoint(listOf(function)) }
        assertEquals(0, sizeReads)
        assertEquals(0, arrayReads)
        assertEquals(0, iterations)
    }

    @Test fun inactiveFunctionWriteSummaryRestoresOriginalSetIdentityAndContents() = inFunction { function ->
        val first = Place(SymbolId.fresh())
        val second = Place(SymbolId.fresh())
        val changed = Place(SymbolId.fresh())
        val places = linkedSetOf(first, second)
        val contents = linkedSetOf(first)
        val effect = Effect.Writes(places, contents)
        function.runtimeEffect = effect
        val before = FunctionStateJournal.checkpoint(listOf(function))
        places.clear()
        places.add(changed)
        contents.clear()
        contents.add(changed)
        val after = before.captureCurrent()
        before.restore()
        assertSame(effect, function.runtimeEffect)
        assertSame(places, effect.places)
        assertSame(contents, effect.contents)
        assertEquals(setOf(first, second), places)
        assertEquals(setOf(first), contents)
        after.restore()
        assertSame(effect, function.runtimeEffect)
        assertEquals(setOf(changed), places)
        assertEquals(setOf(changed), contents)
        before.restore()
        assertEquals(setOf(first, second), places)
        assertEquals(setOf(first), contents)
    }

    @Test fun detachedSummariesRetainSharedSetAliasesAcrossRepeatedRestoration() = inFunction { function ->
        val other = Function("other", "test", null)
        val first = Place(SymbolId.fresh())
        val changed = Place(SymbolId.fresh())
        val shared = linkedSetOf(first)
        val original = Effect.Writes(shared, shared)
        val alias = Effect.Writes(shared, shared)
        function.runtimeEffect = original
        other.runtimeEffect = alias
        val before = FunctionStateJournal.checkpoint(listOf(function, other))
        shared.clear()
        shared.add(changed)
        function.runtimeEffect = Effect.Pure
        other.runtimeEffect = Effect.Unknown
        val after = before.captureCurrent()
        before.restore()
        assertSame(original, function.runtimeEffect)
        assertSame(alias, other.runtimeEffect)
        assertSame(shared, original.places)
        assertSame(shared, original.contents)
        assertSame(shared, alias.places)
        assertSame(shared, alias.contents)
        assertEquals(setOf(first), shared)
        after.restore()
        assertSame(Effect.Pure, function.runtimeEffect)
        assertSame(Effect.Unknown, other.runtimeEffect)
        assertEquals(setOf(changed), shared)
        before.restore()
        assertSame(original, function.runtimeEffect)
        assertSame(alias, other.runtimeEffect)
        assertEquals(setOf(first), shared)
    }

    @Test fun preparedSummaryChangesRollbackAndApplyWithoutRepeatingHostCode() = inFunction { function ->
        val first = Place(SymbolId.fresh())
        val changed = Place(SymbolId.fresh())
        val places = linkedSetOf(first)
        val contents = linkedSetOf(first)
        val original = Effect.Writes(places, contents)
        val replacement = Effect.Writes(linkedSetOf(changed), linkedSetOf(changed))
        function.runtimeEffect = original
        val before = FunctionStateJournal.checkpoint(listOf(function))
        assertFailsWith<IllegalArgumentException> {
            before.prepare {
                places.clear()
                contents.clear()
                function.runtimeEffect = Effect.Unknown
                throw IllegalArgumentException("abort summary")
            }
        }
        assertSame(original, function.runtimeEffect)
        assertEquals(setOf(first), places)
        assertEquals(setOf(first), contents)
        var calls = 0
        val prepared = before.prepare {
            calls++
            places.clear()
            places.add(changed)
            contents.clear()
            function.runtimeEffect = replacement
        }
        assertSame(original, function.runtimeEffect)
        assertEquals(setOf(first), contents)
        repeat(2) {
            prepared.apply()
            assertSame(replacement, function.runtimeEffect)
            assertEquals(setOf(changed), places)
            assertTrue(contents.isEmpty())
            // New after-state roots must also survive mutations between applications.
            assertEquals(setOf(changed), replacement.places)
            assertEquals(setOf(changed), replacement.contents)
            (replacement.places as MutableSet<Place>).clear()
            (replacement.contents as MutableSet<Place>).clear()
            prepared.rollback()
            assertSame(original, function.runtimeEffect)
            assertEquals(setOf(first), places)
            assertEquals(setOf(first), contents)
        }
        assertEquals(1, calls)
    }

    @Test fun unknownEffectPlacesAreRejectedBeforeSizeOrIteratorCallbacks() = inFunction { function ->
        var callbacks = 0
        val places = object : AbstractSet<Place>() {
            override val size: Int get() {
                callbacks++
                return 1
            }
            override fun iterator(): Iterator<Place> {
                callbacks++
                return emptyList<Place>().iterator()
            }
        }
        function.runtimeEffect = Effect.Writes(places)
        assertFailsWith<IllegalStateException> { FunctionStateJournal.checkpoint(listOf(function)) }
        assertEquals(0, callbacks)
    }

    @Test fun unknownEffectContentsAreRejectedBeforeSizeOrIteratorCallbacks() = inFunction { function ->
        var callbacks = 0
        val contents = object : AbstractSet<Place>() {
            override val size: Int get() {
                callbacks++
                return 1
            }
            override fun iterator(): Iterator<Place> {
                callbacks++
                return emptyList<Place>().iterator()
            }
        }
        function.runtimeEffect = Effect.Writes(emptySet(), contents)
        assertFailsWith<IllegalStateException> { FunctionStateJournal.checkpoint(listOf(function)) }
        assertEquals(0, callbacks)
    }

    @Test fun falselyTypedEffectSetIsRejectedWithoutCallingHostHashCode() = inFunction { function ->
        var hashes = 0
        val host = object {
            override fun hashCode(): Int {
                hashes++
                return 7
            }
        }
        val contents = hashSetOf<Any>(host)
        hashes = 0
        @Suppress("UNCHECKED_CAST")
        val falselyTyped = contents as Set<Place>
        function.runtimeEffect = Effect.Writes(emptySet(), falselyTyped)
        val failure = assertFailsWith<IllegalStateException> { FunctionStateJournal.checkpoint(listOf(function)) }
        assertTrue(failure.message.orEmpty().contains("Place"))
        assertEquals(0, hashes)
    }

    @Test fun erasedPlacePathIsRejectedBeforeCheckpointOrRollbackCanCallHostCode() = inFunction { function ->
        function.runtimeEffect = Effect.Pure
        val before = FunctionStateJournal.checkpoint(listOf(function))
        var callbacks = 0
        val host = object {
            override fun hashCode(): Int {
                callbacks++
                return 7
            }
            override fun equals(other: Any?): Boolean {
                callbacks++
                return this === other
            }
            override fun toString(): String {
                callbacks++
                return "host"
            }
        }
        @Suppress("UNCHECKED_CAST")
        val erasedPath = arrayListOf<Any>(host) as List<PathSegment>
        val invalidPlace = Place(SymbolId.fresh(), erasedPath)
        val effect = Effect.Writes(hashSetOf(invalidPlace))
        // Constructing this fixture hashes its Place; journal operations must never do so.
        callbacks = 0
        function.runtimeEffect = effect
        val failure = assertFailsWith<IllegalStateException> { FunctionStateJournal.checkpoint(listOf(function)) }
        assertTrue(failure.message.orEmpty().contains("path segments"))
        assertEquals(0, callbacks)
        assertFailsWith<IllegalStateException> { before.captureCurrent() }
        assertEquals(0, callbacks)
        before.restore()
        assertSame(Effect.Pure, function.runtimeEffect)
        assertEquals(0, callbacks)
    }

    @Test fun frameExitStateAndNoStackScopeSurviveRepeatedNestedRestoration() = inFunction { function ->
        function.returnType = MCFPPBaseType.Int
        val nested = NoStackFunction("nested", function)
        function.child.add(nested)
        nested.registerFrameExit()
        val original = function.frameExits.single()
        val before = FunctionStateJournal.checkpoint(listOf(function))
        nested.commands.add("say prepared")
        nested.registerFrameExit()
        function.assignReturnVar(MCInt(7))
        function.recordFallthroughExit()
        val after = before.captureCurrent()
        before.restore()
        assertSame(original, function.frameExits.single())
        assertTrue(nested.commands.isEmpty())
        assertTrue(nested.scope is NoStackFunctionScope)
        after.restore()
        assertEquals(2, function.frameExits.size)
        assertEquals("say prepared", nested.commands.single().toString())
        assertEquals(7, (StorageAccess.read(function.returnVar) as MCInt).value)
        before.restore()
        assertSame(original, function.frameExits.single())
    }

    private val ownerSource = SourceSpan("owner-state.mcfpp", 0, 1, 1, 1, 1, 2)

    private fun ownedFunctions(function: Function): Pair<DataTemplate, List<Function>> {
        val owner = DataTemplate("Owner", "test")
        val native = NativeFunction("native_owner", "test", FunctionJournalNative::class.java.getMethod("other"))
        val functions = listOf(function, native)
        functions.forEach {
            // The global constructor records NONE; assigning an owner changes only the getter.
            assertEquals(Function.Companion.OwnerType.NONE, it.declarationOwnerTypeState())
            it.owner = owner
            assertEquals(Function.Companion.OwnerType.TEMPLATE, it.ownerType)
        }
        return owner to functions
    }

    @Test fun noOpRestorePreservesRawOwnerStateWhenThePublicGetterIsDerived() = inFunction { function ->
        val (owner, functions) = ownedFunctions(function)
        val domain = DeclarationDomainStamp.captureRoots(functions, ownerSource)
        val before = FunctionStateJournal.checkpoint(functions)
        repeat(2) {
            before.restore()
            functions.forEach {
                assertSame(owner, it.owner)
                assertEquals(Function.Companion.OwnerType.TEMPLATE, it.ownerType)
                assertEquals(Function.Companion.OwnerType.NONE, it.declarationOwnerTypeState())
            }
            domain.requireCurrent()
        }
        functions.forEach {
            it.ownerType = Function.Companion.OwnerType.BASIC
            assertEquals(Function.Companion.OwnerType.TEMPLATE, it.ownerType)
            assertEquals(Function.Companion.OwnerType.BASIC, it.declarationOwnerTypeState())
        }
        val changed = assertFailsWith<PreparationFactFailure> { domain.requireCurrent() }
        assertEquals(PreparationFactCode.UNPREPARED_TYPE, changed.code)
        assertEquals(ownerSource, changed.source)
        before.restore()
        domain.requireCurrent()
    }

    @Test fun ownerIdentityAndRawStateSurviveThreeGenerationsForOrdinaryAndNativeFunctions() = inFunction { function ->
        val (owner, functions) = ownedFunctions(function)
        val original = DeclarationDomainStamp.captureRoots(functions, ownerSource)
        val before = FunctionStateJournal.checkpoint(functions)
        functions.forEach { it.ownerType = Function.Companion.OwnerType.TEMPLATE }
        val prepared = DeclarationDomainStamp.captureRoots(functions, ownerSource)
        val after = before.captureCurrent()
        functions.forEach {
            it.owner = null
            it.ownerType = Function.Companion.OwnerType.BASIC
        }
        val detached = DeclarationDomainStamp.captureRoots(functions, ownerSource)
        val latest = after.captureCurrent()
        repeat(2) {
            before.restore()
            functions.forEach {
                assertSame(owner, it.owner)
                assertEquals(Function.Companion.OwnerType.NONE, it.declarationOwnerTypeState())
                assertEquals(Function.Companion.OwnerType.TEMPLATE, it.ownerType)
            }
            original.requireCurrent()
            assertFailsWith<PreparationFactFailure> { prepared.requireCurrent() }
            after.restore()
            functions.forEach {
                assertSame(owner, it.owner)
                assertEquals(Function.Companion.OwnerType.TEMPLATE, it.declarationOwnerTypeState())
                assertEquals(Function.Companion.OwnerType.TEMPLATE, it.ownerType)
            }
            prepared.requireCurrent()
            latest.restore()
            functions.forEach {
                assertNull(it.owner)
                assertEquals(Function.Companion.OwnerType.BASIC, it.declarationOwnerTypeState())
                assertEquals(Function.Companion.OwnerType.NONE, it.ownerType)
            }
            detached.requireCurrent()
            assertFailsWith<PreparationFactFailure> { original.requireCurrent() }
        }
        before.restore()
        original.requireCurrent()
    }

    @Test fun fullDomainPreparedRevisionSurvivesApplyWithoutNormalizingNativeOwnerState() = inFunction { function ->
        val (owner, functions) = ownedFunctions(function)
        val original = DeclarationDomainStamp.captureRoots(functions, ownerSource)
        val capability = original.forCompletion()
        val before = CompilerStateJournal.checkpointDomains(listOf(capability))
        var calls = 0
        lateinit var revision: DeclarationDomainStamp
        val commit = before.prepare {
            calls++
            function.ownerType = Function.Companion.OwnerType.BASIC
            // NativeFunction deliberately keeps raw NONE while its getter still returns TEMPLATE.
            revision = capability.refresh()
            revision.requireCurrent()
        }
        original.requireCurrent()
        assertFailsWith<PreparationFactFailure> { revision.requireCurrent() }
        repeat(2) {
            commit.apply()
            assertEquals(1, calls)
            functions.forEach {
                assertSame(owner, it.owner)
                assertEquals(Function.Companion.OwnerType.TEMPLATE, it.ownerType)
            }
            assertEquals(Function.Companion.OwnerType.BASIC, function.declarationOwnerTypeState())
            assertEquals(Function.Companion.OwnerType.NONE, functions[1].declarationOwnerTypeState())
            revision.requireCurrent()
            assertFailsWith<PreparationFactFailure> { original.requireCurrent() }
            commit.rollback()
            original.requireCurrent()
            functions.forEach { assertEquals(Function.Companion.OwnerType.NONE, it.declarationOwnerTypeState()) }
        }
    }

    @Test fun failedFullDomainPreparationRestoresRawOwnerStateAndItsOriginalRevision() = inFunction { function ->
        val (owner, functions) = ownedFunctions(function)
        val original = DeclarationDomainStamp.captureRoots(functions, ownerSource)
        val before = CompilerStateJournal.checkpointDomains(listOf(original.forCompletion()))
        var calls = 0
        val failure = assertFailsWith<IllegalArgumentException> {
            before.prepare {
                calls++
                functions.forEach {
                    it.owner = null
                    it.ownerType = Function.Companion.OwnerType.BASIC
                }
                assertFailsWith<PreparationFactFailure> { original.requireCurrent() }
                throw IllegalArgumentException("abort owner changes")
            }
        }
        assertEquals("abort owner changes", failure.message)
        assertEquals(1, calls)
        functions.forEach {
            assertSame(owner, it.owner)
            assertEquals(Function.Companion.OwnerType.NONE, it.declarationOwnerTypeState())
            assertEquals(Function.Companion.OwnerType.TEMPLATE, it.ownerType)
        }
        original.requireCurrent()
    }
}

@NoExternalWrites
object FunctionJournalNative {
    lateinit var command: Command
    lateinit var value: MCInt
    var calls = 0
    @JvmStatic fun mutate() {
        calls++
        command.replace("slot" to "prepared")
        value.assignedBy(MCInt(17))
        Function.addCommand("# native prepared")
    }
    @JvmStatic fun other() = Unit
}
