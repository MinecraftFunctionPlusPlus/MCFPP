@file:Suppress("UNUSED_PARAMETER")

package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.Project
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.annotations.MNIMacro
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.io.LibBinReader
import top.mcfpp.io.LibBinWriter
import top.mcfpp.io.info.*
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniMacroLoadingTest {
    private val bases get() = listOf(
        MCFPPBaseType.Int,
        MCFPPBaseType.Bool,
        MCFPPBaseType.Float,
        MCFPPBaseType.String,
        MCFPPBaseType.Object,
        MCFPPBaseType.Any,
        MCFPPPrivateType.Void
    )

    private fun namespace(provider: Class<*> = LoadingMacroEntries::class.java) = Namespace("fixture.loading").apply { declareMniFixtures(provider) }

    private fun prepare(namespace: Namespace, extra: List<MCFPPType> = emptyList(), names: Map<String, MCFPPType> = emptyMap()) = MacroLoadingSnapshot.prepare(listOf(namespace), bases + extra, names, report = {})

    @Test fun namespaceDiscoveryDefersTypesAndPublishesImmutableCanonicalDescriptorsWithoutInvokingMacros() {
        Project.init()
        val namespace = namespace()
        assertEquals(setOf("all", "empty", "expression"), namespace.macroDeclarations().map { it.methodName }.toSet())
        assertTrue(namespace.scope.functions.isEmpty())
        val before = Function.currFunction.commands.toList()
        val snapshot = prepare(namespace)
        assertTrue(snapshot.diagnostics.isEmpty())
        val entries = snapshot.namespaces.getValue(namespace.identifier)
        assertSame(snapshot.types, entries.types)
        assertEquals(ParamMode.entries, entries.find("all").single().parameters.map { it.mode() })
        assertTrue(
            entries.functions.all { function ->
                snapshot.types.owns(function.returnType) && function.parameters.all { snapshot.types.owns(it.type()) }
            }
        )
        val callback = entries.find("all").single().parameters.last().type() as ImmutableTypeRef<*>
        assertSame(snapshot.types.graph, callback.graph)
        assertSame(snapshot.types.ref(MCFPPBaseType.Int), callback.inlineSignature()!!.parameters().single().type())
        assertSame(snapshot.types.ref(MCFPPPrivateType.Void), callback.inlineSignature()!!.returnType())
        assertEquals(before, Function.currFunction.commands.toList())
        assertEquals(0, Project.errorCount)
        assertFailsWith<UnsupportedOperationException> { (entries.functions as MutableList).clear() }
        assertFailsWith<UnsupportedOperationException> { (namespace.macroDeclarations() as MutableList).clear() }
    }

    @Test fun javaAndKotlinProvidersUseTheSameClassDiscoveryAndReflectionProtocol() {
        Project.init()
        val directory = Files.createTempDirectory("mcfpp-java-macro")
        try {
            val source = directory.resolve("JavaLoadingMacro.java")
            Files.writeString(
                source,
                """
                import top.mcfpp.annotations.*;
                import top.mcfpp.mni.api.*;
                public class JavaLoadingMacro {
                    @MNIMacro(normalParams ={"int"}, returnType="int")
                    public static Expr<IntT> expression(MacroContext context, Value<IntT> value) {
                        throw new AssertionError("registration invoked Java macro");
                    }
                }
                """.trimIndent()
            )
            val classpath = listOf(MNIMacro::class.java, MacroContext::class.java, kotlin.Unit::class.java)
                .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }.distinct()
                .joinToString(java.io.File.pathSeparator)
            assertEquals(
                0,
                ToolProvider.getSystemJavaCompiler().run(
                    null, null, null,
                    "-proc:none", "-classpath", classpath, "-d", directory.toString(), source.toString()
                )
            )
            URLClassLoader(arrayOf(directory.toUri().toURL()), Project.classLoader).use { loader ->
                val java = Namespace("fixture.java").apply { declareMniFixtures("JavaLoadingMacro", loader) }
                val kotlin = namespace()
                val snapshot = MacroLoadingSnapshot.prepare(listOf(java, kotlin), bases, loader = loader, report = {})
                assertTrue(snapshot.diagnostics.isEmpty())
                val javaEntry = snapshot.namespaces.getValue(java.identifier).find("expression").single()
                val kotlinEntry = snapshot.namespaces.getValue(kotlin.identifier).find("expression").single()
                assertEquals(javaEntry.parameters.map { it.mode() }, kotlinEntry.parameters.map { it.mode() })
                assertTrue(javaEntry.parameters.single().type().sameType(kotlinEntry.parameters.single().type()))
                assertTrue(javaEntry.returnType.sameType(kotlinEntry.returnType))
                assertEquals(loader, javaEntry.javaMethod.declaringClass.classLoader)
            }
        } finally {
            remove(directory)
        }
    }

    @Test fun ordinaryAndMacroSignaturesCannotBeDistinguishedByModesOrReturnTypes() {
        Project.init()
        val namespace = namespace(LoadingCollisionMacro::class.java)
        val ordinary = Function("collide", namespace.identifier, null).apply {
            appendNormalParam(MCFPPBaseType.Int, "value")
            returnType = MCFPPBaseType.Bool
        }
        namespace.scope.addFunction(ordinary, false)
        val snapshot = prepare(namespace)
        assertEquals(MacroLoadingCode.ORDINARY_CONFLICT, snapshot.diagnostics.single().code)
        assertTrue(snapshot.diagnostics.single().message.contains("LoadingCollisionMacro"))
        assertTrue(snapshot.diagnostics.single().reason.contains(ordinary.namespaceID.toString()))
        assertTrue(snapshot.namespaces.getValue(namespace.identifier).functions.isEmpty())
        assertSame(ordinary, namespace.scope.functions.getValue("collide").single())
        assertEquals(0, Project.errorCount)
    }

    @Test fun preparationRechecksOrdinaryDeclarationsAddedAfterEarlyLibraryDiscovery() {
        Project.init()
        val namespace = namespace(LoadingCollisionMacro::class.java)
        val early = prepare(namespace)
        assertEquals(1, early.namespaces.getValue(namespace.identifier).functions.size)
        namespace.scope.addFunction(
            Function("collide", namespace.identifier, null).apply {
                appendNormalParam(MCFPPBaseType.Int, "value")
            },
            false
        )
        val complete = prepare(namespace)
        assertEquals(MacroLoadingCode.ORDINARY_CONFLICT, complete.diagnostics.single().code)
        assertTrue(complete.namespaces.getValue(namespace.identifier).functions.isEmpty())
        assertEquals(1, early.namespaces.getValue(namespace.identifier).functions.size)
    }

    @Test fun requiredGenericReadonlyParameterDistinguishesAnOrdinaryDeclarationFromAMacro() {
        Project.init()
        val namespace = namespace(LoadingCollisionMacro::class.java)
        val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString("{}"))))
        val body = parser.curlBlock()
        assertEquals(0, parser.numberOfSyntaxErrors)
        val ordinary = GenericFunction("collide", namespace.identifier, body).apply {
            readOnlyParams.add(FunctionParam(MCFPPConcreteType.Type, "T", this, isReadOnly = true))
            appendNormalParam(MCFPPBaseType.Int, "value")
        }
        assertFalse(ordinary.readOnlyParams.single().hasDefault)
        assertTrue(namespace.scope.addFunction(ordinary, false))
        val commands = Function.currFunction.commands.toList()
        val snapshot = prepare(namespace, listOf(MCFPPConcreteType.Type))
        assertTrue(snapshot.diagnostics.isEmpty())
        val macro = snapshot.namespaces.getValue(namespace.identifier).find("collide").single()
        assertEquals(ParamMode.REF, macro.parameters.single().mode())
        assertSame(snapshot.types.ref(MCFPPBaseType.Int), macro.parameters.single().type())
        assertSame(ordinary, namespace.scope.functions.getValue("collide").single())
        assertEquals(commands, Function.currFunction.commands.toList())
        assertEquals(0, Project.errorCount)
    }

    @Test fun requiredNativeReadonlyParameterDistinguishesAnOrdinaryDeclarationFromAMacro() {
        Project.init()
        val namespace = namespace(LoadingCollisionMacro::class.java).apply {
            declareMniFixtures(LoadingReadonlyNative::class.java)
        }
        val ordinary = namespace.scope.functions.getValue("collide").single() as NativeFunction
        assertEquals(1, ordinary.readOnlyParams.size)
        assertFalse(ordinary.readOnlyParams.single().hasDefault)
        val commands = Function.currFunction.commands.toList()
        val snapshot = prepare(namespace)
        assertTrue(snapshot.diagnostics.isEmpty())
        val macro = snapshot.namespaces.getValue(namespace.identifier).find("collide").single()
        assertEquals(ParamMode.REF, macro.parameters.single().mode())
        assertSame(snapshot.types.ref(MCFPPBaseType.Int), macro.parameters.single().type())
        assertSame(ordinary, namespace.scope.functions.getValue("collide").single())
        assertEquals(commands, Function.currFunction.commands.toList())
        assertEquals(0, Project.errorCount)
    }

    @Test fun mergedNamespaceDescriptorsRejectModeOnlyOverloadsAndKeepFirstValidEntry() {
        Project.init()
        val first = namespace(LoadingCollisionMacro::class.java)
        val second = namespace(LoadingDuplicateMacro::class.java)
        first.merge(second)
        first.declareMniFixtures(LoadingCollisionMacro::class.java) // Repeated discovery is idempotent.
        assertEquals(2, first.macroDeclarations().size)
        val snapshot = prepare(first)
        assertEquals(MacroLoadingCode.DUPLICATE, snapshot.diagnostics.single().code)
        assertEquals(
            LoadingCollisionMacro::class.java,
            snapshot.namespaces.getValue(first.identifier).functions.single().javaMethod.declaringClass
        )
    }

    @Test fun compoundInjectionDoesNotRegisterNamespaceMacrosAndInstanceEntriesAreRejected() {
        Project.init()
        val compound = CompoundData("Member", "fixture.loading")
        compound.injectedBy(LoadingMacroEntries::class.java)
        assertTrue(compound.scope.functions.isEmpty())
        val namespace = namespace(LoadingMemberMacro::class.java)
        val snapshot = prepare(namespace)
        assertTrue(snapshot.namespaces.getValue(namespace.identifier).functions.isEmpty())
        assertEquals(MacroLoadingCode.INVALID_MACRO, snapshot.diagnostics.single().code)
        assertTrue(snapshot.diagnostics.single().reason.contains("public static"))
    }

    @Test fun realLibraryWriteAndReadRestoreRawDescriptorsAndReprojectIntoTheNewCatalog() {
        Project.init()
        val directory = Files.createTempDirectory("mcfpp-macro-library")
        try {
            val namespace = namespace()
            GlobalScope.localNamespaces[namespace.identifier] = namespace
            val old = prepare(namespace)
            LibBinWriter.write(directory.toString())
            Project.init()
            Files.newInputStream(directory.resolve("bin.mclib")).use(LibBinReader::readFromStream)
            val restored = GlobalScope.libNamespaces.getValue(namespace.identifier)
            val directory = assertNotNull(PreparedNativeDirectory.current())
            val snapshot = MacroLoadingSnapshot.prepare(listOf(restored), (bases + directory.sourceTypes).distinctBy { it.typeId },
                report = {}, callbacks = directory.callbacks)
            val fresh = PreparedMacroCatalog(snapshot, directory)
            assertTrue(fresh.diagnostics.isEmpty())
            fun descriptors(entries: List<MacroFunction>) = entries.map {
                Triple(it.identifier, it.javaMethod, it.parameters.map { parameter -> parameter.mode() })
            }.toSet()
            assertEquals(descriptors(old.namespaces.getValue(namespace.identifier).functions),
                descriptors(fresh.namespaces.getValue(namespace.identifier).functions))
            val oldEntry = old.namespaces.getValue(namespace.identifier).find("expression").single()
            val freshEntry = fresh.namespaces.getValue(namespace.identifier).find("expression").single()
            assertFalse(oldEntry.returnType.sameType(freshEntry.returnType))
            assertSame(fresh.types.ref(MCFPPBaseType.Int), freshEntry.returnType)
            assertEquals(oldEntry.javaMethod, freshEntry.javaMethod)
            val oldCallback = old.namespaces.getValue(namespace.identifier).find("all").single().parameters.last().type()
            val freshCallback = fresh.namespaces.getValue(namespace.identifier).find("all").single().parameters.last().type()
            assertFalse(oldCallback.sameType(freshCallback))
            assertSame(fresh.types.graph, (freshCallback as ImmutableTypeRef<*>).graph)
            assertSame(freshCallback, fresh.types.inlineType(freshCallback.inlineSignature()!!))
            assertNull(old.types.inlineType(freshCallback.inlineSignature()!!))
            assertEquals(0, Project.errorCount)
        } finally {
            remove(directory)
        }
    }

    @Test fun unavailableOrChangedLibraryProvidersProduceLocatedDiagnosticsWithoutPublishingEntries() {
        Project.init()
        val original = namespace().macroDeclarations().first { it.methodName == "expression" }
        val changed = MacroFunctionInfo(
            original.providerClass,
            original.methodName,
            original.parameterClasses,
            original.returnClass,
            original.identifier,
            original.parameters,
            "bool"
        )
        val missing = MacroFunctionInfo(
            "missing.provider.DoesNotExist",
            original.methodName,
            original.parameterClasses,
            original.returnClass,
            original.identifier,
            original.parameters,
            original.sourceReturnType
        )
        val namespace = Namespace("fixture.invalid").apply { restoreMacroDeclarations(listOf(changed, missing)) }
        val snapshot = prepare(namespace)
        assertTrue(snapshot.namespaces.getValue(namespace.identifier).functions.isEmpty())
        assertEquals(2, snapshot.diagnostics.size)
        assertTrue(snapshot.diagnostics.all { it.code == MacroLoadingCode.INVALID_PROVIDER })
        assertTrue(snapshot.diagnostics.any { it.message.contains("recompile the library") })
        assertTrue(snapshot.diagnostics.all { it.message.contains(it.descriptor.methodDescriptor) })
    }

    @Test fun newlyDeclaredDerivedTypesAreAcceptedOnlyByExplicitlyReprojectedMacroSnapshots() {
        Project.init()
        val namespace = namespace(LoadingBaseMacro::class.java)
        val parent = DataTemplate("Base", "fixture.loading").getType()
        val early = prepare(namespace, listOf(parent))
        assertTrue(early.diagnostics.isEmpty())
        val child = DataTemplate("Child", "fixture.loading").getType().apply {
            parentType = ArrayList<MCFPPType>(parentType).apply { add(parent) }
        }
        assertFailsWith<IllegalArgumentException> { early.types.ref(child) }
        val fresh = prepare(namespace, listOf(parent, child))
        assertTrue(fresh.diagnostics.isEmpty())
        assertTrue(fresh.types.isSubtype(fresh.types.ref(child), fresh.types.ref(parent)))
        assertSame(
            fresh.namespaces.getValue(namespace.identifier).find("accept").single().reference,
            bind(fresh, namespace, "accept", child).target.function
        )
        assertFalse(early.types.ref(parent).sameType(fresh.types.ref(parent)))
        assertFailsWith<IllegalArgumentException> { early.types.ref(child) }
    }

    @Test fun changingParentFactsOnTheSameObjectsAndSameDomainSizeRequiresANewSnapshot() {
        Project.init()
        val namespace = namespace(LoadingBaseMacro::class.java)
        val parent = DataTemplate("Base", "fixture.loading").getType()
        val child = DataTemplate("Child", "fixture.loading").getType()
        val early = prepare(namespace, listOf(parent, child))
        assertFailsWith<SourceBindingFailure> { bind(early, namespace, "accept", child) }
        child.parentType = ArrayList<MCFPPType>(child.parentType).apply { add(parent) }
        val fresh = prepare(namespace, listOf(parent, child))
        assertSame(
            fresh.namespaces.getValue(namespace.identifier).find("accept").single().reference,
            bind(fresh, namespace, "accept", child).target.function
        )
        assertFalse(early.types.isSubtype(early.types.ref(child), early.types.ref(parent)))
        assertFailsWith<SourceBindingFailure> { bind(early, namespace, "accept", child) }
    }

    @Test fun completedGenericFactsAndExplicitAliasBindingsAreFrozenPerPreparation() {
        Project.init()
        val namespace = namespace(LoadingGenericMacro::class.java)
        val list = MCFPPListType(MCFPPBaseType.Int)
        val early = prepare(namespace, listOf(list), mapOf("PreparedList" to list))
        val oldType = early.namespaces.getValue(namespace.identifier).functions.single().parameters.single().type()
        assertEquals(MCFPPBaseType.Int.typeId, early.types.facts(oldType).elementType)
        list.generic[0] = MCFPPBaseType.Bool
        val fresh = prepare(namespace, listOf(list), mapOf("PreparedList" to list))
        val newType = fresh.namespaces.getValue(namespace.identifier).functions.single().parameters.single().type()
        assertEquals(MCFPPBaseType.Bool.typeId, fresh.types.facts(newType).elementType)
        assertEquals(MCFPPBaseType.Int.typeId, early.types.facts(oldType).elementType)
        assertFalse(oldType.sameType(newType))
        assertSame(newType, bind(fresh, namespace, "acceptList", list).target.function.parameters().single().type())
        fun index(snapshot: MacroLoadingSnapshot, type: TypeRef<*>): BoundExpr {
            val env = BindingEnvironment(
                snapshot.types,
                listOf(SourceSymbol("values", type, SymbolId(SessionId(0), 2))),
                emptyList()
            )
            val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString("values[0]"))))
            val syntax = parser.expression()
            assertEquals(0, parser.numberOfSyntaxErrors)
            return SnapshotSourceCodeBinder().expression(syntax, env)
        }
        assertSame(early.types.ref(MCFPPBaseType.Int), index(early, oldType).type)
        assertSame(fresh.types.ref(MCFPPBaseType.Bool), index(fresh, newType).type)
        assertEquals(0, Project.errorCount)
    }

    @Test fun namespaceSpecificPreparedNamesShareOneCatalogAndDescriptorsDefensivelyCopyCollections() {
        Project.init()
        val first = namespace(LoadingGenericMacro::class.java)
        val second = Namespace("fixture.second").apply { declareMniFixtures(LoadingGenericMacro::class.java) }
        val firstList = MCFPPListType(MCFPPBaseType.Int)
        val secondList = MCFPPListType(MCFPPBaseType.Bool)
        val snapshot = MacroLoadingSnapshot.prepare(
            listOf(first, second),
            bases + listOf(firstList, secondList),
            preparedNamespaceTypeNames = mapOf(
                first.identifier to mapOf("PreparedList" to firstList),
                second.identifier to mapOf("PreparedList" to secondList)
            ),
            report = {}
        )
        assertTrue(snapshot.diagnostics.isEmpty())
        val firstType = snapshot.namespaces.getValue(first.identifier).functions.single().parameters.single().type()
        val secondType = snapshot.namespaces.getValue(second.identifier).functions.single().parameters.single().type()
        assertFalse(firstType.sameType(secondType))
        assertSame(snapshot.types.ref(firstList), firstType)
        assertSame(snapshot.types.ref(secondList), secondType)
        assertFailsWith<IllegalArgumentException> {
            MacroLoadingSnapshot.prepare(listOf(first), bases, mapOf("PreparedList" to firstList), report = {})
        }
        val classes = arrayListOf(MacroContext::class.java.name)
        val params = arrayListOf<MacroParameterInfo>()
        val descriptor = MacroFunctionInfo("Provider", "method", classes, Block::class.java.name, "", params, "void")
        classes.clear()
        params.add(MacroParameterInfo("unexpected", "int", ParamMode.VALUE))
        assertEquals(listOf(MacroContext::class.java.name), descriptor.parameterClasses)
        assertTrue(descriptor.parameters.isEmpty())
        assertFailsWith<UnsupportedOperationException> { (descriptor.parameterClasses as MutableList).clear() }
    }

    @Test fun nestedCallbackAliasesFromDifferentNamespacesResolveBeforeOneGraphIsFrozen() {
        Project.init()
        val first = namespace(LoadingNestedCallbackMacro::class.java)
        val second = Namespace("fixture.nested").apply { declareMniFixtures(LoadingNestedCallbackMacro::class.java) }
        val commands = Function.currFunction.commands.toList()
        val snapshot = MacroLoadingSnapshot.prepare(
            listOf(first, second),
            bases,
            preparedTypeNames = mapOf("Item" to MCFPPBaseType.Bool),
            preparedNamespaceTypeNames = mapOf(
                first.identifier to mapOf("Item" to MCFPPBaseType.Int),
                second.identifier to mapOf("Item" to MCFPPBaseType.Int)
            ),
            report = {}
        )
        assertTrue(snapshot.diagnostics.isEmpty())
        val left = snapshot.namespaces.getValue(first.identifier).find("nested").single()
        val right = snapshot.namespaces.getValue(second.identifier).find("nested").single()
        val outer = left.parameters.single().type()
        assertSame(outer, right.parameters.single().type())
        assertSame(snapshot.types.graph, (outer as ImmutableTypeRef<*>).graph)
        val inner = outer.inlineSignature()!!.parameters().single()
        assertEquals(ParamMode.INLINE, inner.mode())
        assertSame(snapshot.types.graph, (inner.type() as ImmutableTypeRef<*>).graph)
        assertEquals(ParamMode.REF, inner.inlineSignature()!!.parameters().single().mode())
        assertSame(snapshot.types.ref(MCFPPBaseType.Int), inner.inlineSignature()!!.parameters().single().type())
        assertSame(snapshot.types.ref(MCFPPBaseType.Int), inner.inlineSignature()!!.returnType())
        assertSame(snapshot.types.ref(MCFPPPrivateType.Void), outer.inlineSignature()!!.returnType())
        assertSame(outer, snapshot.types.inlineType(outer.inlineSignature()!!))
        val returned = snapshot.namespaces.getValue(first.identifier).find("returned").single().returnType
        assertSame(snapshot.types.graph, (returned as ImmutableTypeRef<*>).graph)
        assertTrue(returned.inlineSignature()!!.parameters().isEmpty())
        assertSame(inner.type(), returned.inlineSignature()!!.returnType())
        assertSame(left.javaMethod, left.prepared.javaMethod)
        assertEquals(commands, Function.currFunction.commands.toList())
        assertEquals(0, Project.errorCount)
    }

    @Test fun discoveringAnotherCallbackRequiresWholePreparationAndLeavesTheOldGraphClosed() {
        Project.init()
        val namespace = namespace()
        val old = prepare(namespace)
        val unknown = FunctionSignature.of(emptyList(), old.types.ref(MCFPPBaseType.Int))
        assertNull(old.types.inlineType(unknown))
        namespace.declareMniFixtures(LoadingReturningCallbackMacro::class.java)
        val fresh = prepare(namespace)
        assertTrue(fresh.diagnostics.isEmpty())
        val callback = fresh.namespaces.getValue(namespace.identifier).find("returning").single().parameters.single().type()
        assertSame(fresh.types.graph, (callback as ImmutableTypeRef<*>).graph)
        assertSame(callback, fresh.types.inlineType(FunctionSignature.of(emptyList(), fresh.types.ref(MCFPPBaseType.Int))))
        assertNull(old.types.inlineType(unknown))
        assertNull(old.types.inlineType(callback.inlineSignature()!!))
        assertTrue(old.namespaces.getValue(namespace.identifier).find("returning").isEmpty())
        val oldCallback = old.namespaces.getValue(namespace.identifier).find("all").single().parameters.last().type()
        assertSame(oldCallback, old.types.inlineType(oldCallback.inlineSignature()!!))
    }

    @Test fun preparedFieldsAndRecursiveCallbackAliasesAreSharedWithTheProductionMacroCatalog() {
        Project.init()
        val namespace = namespace(LoadingDeclaredCallbackMacro::class.java)
        val owner = DataTemplate("Owner", "fixture.loading").getType()
        val handler = DataTemplate("Handler", "fixture.loading").getType()
        val span = SourceSpan("prepared.mcfpp", 0, 7, 1, 1, 1, 8)
        val field = SourceFieldDeclaration(SourceFieldId(owner.typeId, "handler"), owner.typeId, "handler", handler.typeId, true, span)
        val signature = SourceCallbackSignature(
            listOf(SourceCallbackParameter(SourceCallbackType.Declared(handler.typeId), ParamMode.INLINE)),
            SourceCallbackType.Declared(owner.typeId)
        )
        val snapshot = MacroLoadingSnapshot.prepare(
            listOf(namespace),
            bases + listOf(owner, handler),
            preparedTypeNames = mapOf("PreparedHandler" to handler),
            report = {},
            fields = listOf(field),
            fieldViews = listOf(SourceFieldView(owner.typeId, field.declaration)),
            constantSchemas = mapOf(owner.typeId to SourceConstantSchema.Record(mapOf("handler" to handler.typeId))),
            callbackTypes = mapOf(handler.typeId to signature)
        )
        assertTrue(snapshot.diagnostics.isEmpty())
        val macro = snapshot.namespaces.getValue(namespace.identifier).find("declared").single()
        val callback = macro.parameters.single().type()
        assertSame(snapshot.types.ref(handler), callback)
        assertSame(callback, snapshot.types.field(field.declaration).type())
        assertSame(callback, callback.inlineSignature()!!.parameters().single().type())
        assertSame(snapshot.types.ref(owner), callback.inlineSignature()!!.returnType())
        assertSame(snapshot.types.graph, (callback as ImmutableTypeRef<*>).graph)
        assertSame(callback, snapshot.types.inlineType(callback.inlineSignature()!!))
        val symbol = SourceSymbol("handler", callback, SymbolId(SessionId(0), 901))
        val environment = BindingEnvironment(
            snapshot.types,
            listOf(symbol),
            listOf(SourceFunction("declared", macro.reference)),
            listOf(snapshot.types.sourceField(snapshot.types.ref(owner), "handler")!!)
        )
        assertSame(callback, environment.types.ref(handler))
        assertEquals(0, Project.errorCount)
    }

    @Test fun invalidCallbackTreesDoNotPoisonOtherPreparedMacroEntries() {
        Project.init()
        val namespace = namespace(LoadingInvalidCallbackMacros::class.java)
        val snapshot = prepare(namespace)
        assertEquals(setOf("valid"), snapshot.namespaces.getValue(namespace.identifier).functions.map { it.identifier }.toSet())
        assertEquals(3, snapshot.diagnostics.size)
        assertTrue(snapshot.diagnostics.all { it.code == MacroLoadingCode.INVALID_MACRO })
        assertTrue(snapshot.diagnostics.any { it.reason.contains("void") })
        assertTrue(snapshot.diagnostics.any { it.reason.contains("function signature") })
        assertTrue(snapshot.diagnostics.any { it.reason.contains("MissingType") })
    }

    private fun bind(snapshot: MacroLoadingSnapshot, namespace: Namespace, name: String, argumentType: MCFPPType): SourceCall {
        val functions = snapshot.namespaces.getValue(namespace.identifier).functions.map { SourceFunction(it.identifier, it.reference) }
        val env = BindingEnvironment(
            snapshot.types,
            listOf(SourceSymbol("value", snapshot.types.ref(argumentType), SymbolId(SessionId(0), 1))),
            functions
        )
        val parser = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString("$name(value)"))))
        val syntax = parser.functionCall()
        assertEquals(0, parser.numberOfSyntaxErrors)
        return SnapshotSourceCodeBinder().call(syntax, env)
    }

    private fun remove(directory: Path) {
        Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
}

class LoadingMacroEntries {
    companion object {
        @JvmStatic
        @MNIMacro(
            normalParams = [
                "int",
                "int",
                "int",
                "(int)->void"
            ]
        )
        fun all(context: MacroContext, value: Value<IntT>, destination: Ref<IntT>, constant: Const<IntT>, callback: InlineFn<VoidT>): Block = error("registration invoked macro")

        @JvmStatic @MNIMacro
        fun empty(context: MacroContext): Block = error("registration invoked macro")

        @JvmStatic
        @MNIMacro(normalParams = ["int"], returnType = "int")
        fun expression(context: MacroContext, value: Value<IntT>): Expr<IntT> = error("registration invoked macro")
    }
}

class LoadingCollisionMacro {
    companion object {
        @JvmStatic
        @MNIMacro(normalParams = ["int"])
        fun collide(context: MacroContext, value: Ref<IntT>): Block = error("registration invoked macro")
    }
}

class LoadingDuplicateMacro {
    companion object {
        @JvmStatic
        @MNIMacro(normalParams = ["int"])
        fun collide(context: MacroContext, value: Const<IntT>): Block = error("registration invoked macro")
    }
}

class LoadingReadonlyNative {
    companion object {
        @JvmStatic
        @MNIFunction(identifier = "collide", readOnlyParams = ["int"], normalParams = ["int"])
        fun collide(context: NativeCallContext) {
            error("registration invoked readonly native")
        }
    }
}

class LoadingMemberMacro {
    @MNIMacro fun member(context: MacroContext): Block = error("registration invoked member macro")
}

class LoadingBaseMacro {
    companion object {
        @JvmStatic
        @MNIMacro(normalParams = ["Base"])
        fun accept(context: MacroContext, value: Value<Any>): Block = error("registration invoked macro")
    }
}

class LoadingGenericMacro {
    companion object {
        @JvmStatic
        @MNIMacro(normalParams = ["PreparedList"])
        fun acceptList(context: MacroContext, value: Value<Any>): Block = error("registration invoked macro")
    }
}

class LoadingNestedCallbackMacro {
    companion object {
        @JvmStatic
        @MNIMacro(normalParams = ["(inline (ref Item)->Item)->void"])
        fun nested(context: MacroContext, callback: InlineFn<VoidT>): Block = error("registration invoked macro")
        @JvmStatic
        @MNIMacro(returnType = "()->(ref Item)->Item")
        fun returned(context: MacroContext): Expr<Any> = error("registration invoked macro")
    }
}

class LoadingReturningCallbackMacro {
    companion object {
        @JvmStatic
        @MNIMacro(normalParams = ["()->int"])
        fun returning(context: MacroContext, callback: InlineFn<IntT>): Block = error("registration invoked macro")
    }
}

class LoadingDeclaredCallbackMacro {
    companion object {
        @JvmStatic
        @MNIMacro(normalParams = ["PreparedHandler"])
        fun declared(context: MacroContext, callback: Value<Any>): Block = error("registration invoked macro")
    }
}

class LoadingInvalidCallbackMacros {
    companion object {
        @JvmStatic
        @MNIMacro(normalParams = ["(void)->int"])
        fun invalidVoid(context: MacroContext, callback: InlineFn<IntT>): Block = error("registration invoked macro")
        @JvmStatic
        @MNIMacro(normalParams = ["int"])
        fun invalidInline(context: MacroContext, callback: InlineFn<IntT>): Block = error("registration invoked macro")
        @JvmStatic
        @MNIMacro(normalParams = ["(MissingType)->int"])
        fun missing(context: MacroContext, callback: InlineFn<IntT>): Block = error("registration invoked macro")
        @JvmStatic @MNIMacro
        fun valid(context: MacroContext): Block = error("registration invoked macro")
    }
}
