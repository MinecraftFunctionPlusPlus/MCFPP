package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.annotations.MNIAccessor
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.annotations.MNIMutator
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.OnScoreboard
import top.mcfpp.core.lang.Var
import top.mcfpp.io.LibBinReader
import top.mcfpp.test.library.LegacyAnnotationFixtures
import top.mcfpp.library.api.LibraryImportSession
import top.mcfpp.library.format.LibraryBinaryWriter
import top.mcfpp.library.format.LibraryWire
import top.mcfpp.library.format.LibraryFormatException
import top.mcfpp.library.format.LibraryFormatCode
import top.mcfpp.io.info.AbstractFunctionInfo
import top.mcfpp.io.info.DataTemplateInfo
import top.mcfpp.io.info.GenericDataTemplateInfo
import top.mcfpp.io.info.LibraryMetadata
import top.mcfpp.mni.NativeCallContext
import top.mcfpp.mni.annotation.DataOnly
import top.mcfpp.mni.annotation.NoExternalWrites
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.property.FunctionAccessor
import top.mcfpp.model.property.NativeAccessor
import top.mcfpp.model.property.NativeMutator
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.test.util.compilerDiagnostics
import top.mcfpp.test.util.executePublishedFunctions
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.TypeId
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import top.mcfpp.model.property.Property as FieldProperty

class DataInheritanceLibraryMetadataTest {
    @Test fun freshLayoutsRestoreCompletedEffectsAndKeepLocalAndCanonicalFieldOwners() = withLibrary { output ->
        source(
            """
            namespace fixture.metadata
            data Root {var value:int=1;const key:int=1;}
            @DataOnly
            data Middle:Root {}
            data Grand:Middle {}
            data Sibling:Root {}
            data Local:Root {const key:int=8;}
            func main(){}
        """
        )
        val original = local("Root")
        val middle = local("Middle")
        assertEquals(
            listOf(false, true, true, false),
            listOf("Root", "Middle", "Grand", "Sibling").map {
                (local(it).scope.getVar("value") as MCInt).isDataOnly
            }
        )
        LegacyAnnotationFixtures.write(output)
        assertTrue(Files.size(output.resolve("bin.mclib")) > 8)
        middle.annotations.single().params.add("writer-only")
        fresh(output)
        val restored = imported("Root")
        assertNotSame(original, restored, "A fresh project must not return the writer's canonical model")
        assertEquals(
            listOf(false, true, true, false),
            listOf("Root", "Middle", "Grand", "Sibling").map {
                (imported(it).scope.getVar("value") as MCInt).isDataOnly
            }
        )
        val rootField = restored.scope.getVar("value")!!
        for (name in listOf("Middle", "Grand", "Sibling")) {
            val field = imported(name).scope.getVar("value")!!
            assertNotSame(rootField, field)
            assertSame(restored, field.declaredParentTemplate)
        }
        val local = imported("Local")
        assertSame(local, local.scope.getVar("key")!!.declaredParentTemplate)
        assertTrue(local.scope.getVar("key")!!.isConst)
        assertEquals("8", local.preInit.getValue("key").text)
        assertTrue(imported("Middle").annotations.single().params.isEmpty())
        assertTrue(imported("Middle").pendingDeclarationAnnotations.isEmpty())
    }

    @Test fun freshMembersPreserveFinalAccessReturnAbstractAndDerivedStaticMetadata() = withLibrary { output ->
        source(
            """
            namespace fixture.metadata
            @From("top.mcfpp.test.DataInheritanceLibraryMetadataTest")
            data Root {
                var value:int=1;
                @DataOnly var enabled:bool=true;
                func ordinary():int{return 7;}
                func generic<T:type>(value:T):int{return 3;}
                constructor(){}
            }
            object data StaticBox {func peek():int{return 7;}}
            abstract data AbstractRoot {abstract func needed():int;}
            func main(){}
        """
        )
        val root = local("Root")
        root.constructors.single().accessModifier = AccessModifier.PROTECTED
        root.scope.getVar("value")!!.apply {
            isFinal = true
            accessModifier = AccessModifier.PROTECTED
        }
        root.scope.getProperty("value")!!.apply {
            isFinal = true
            accessModifier = AccessModifier.PROTECTED
        }
        for (name in listOf("ordinary", "generic", "host")) {
            root.scope.functions.getValue(name).single().apply {
                isFinal = true
                accessModifier = AccessModifier.PROTECTED
                annotations.add(MetadataMarker().apply { values.add(name) })
            }
        }
        LegacyAnnotationFixtures.write(output)
        fresh(output)
        val restored = imported("Root")
        assertNotSame(root, restored)
        assertEquals(AccessModifier.PROTECTED, restored.constructors.single().accessModifier)
        assertTrue(restored.scope.getVar("value")!!.isFinal)
        assertEquals(AccessModifier.PROTECTED, restored.scope.getVar("value")!!.accessModifier)
        assertTrue(restored.scope.getProperty("value")!!.isFinal)
        assertEquals(AccessModifier.PROTECTED, restored.scope.getProperty("value")!!.accessModifier)
        assertFalse(restored.scope.getProperty("value")!!.isStatic)
        assertTrue(assertIs<OnScoreboard>(restored.scope.getVar("enabled")).isDataOnly)
        for (name in listOf("ordinary", "generic", "host")) {
            val function = restored.scope.functions.getValue(name).single()
            assertSame(restored, function.owner)
            assertTrue(function.isFinal, name)
            assertFalse(function.isStatic, name)
            assertEquals(AccessModifier.PROTECTED, function.accessModifier)
            assertEquals(MCFPPBaseType.Int, function.returnType)
            assertEquals(listOf(name), assertIs<MetadataMarker>(function.annotations.single()).values)
        }
        assertIs<GenericFunction>(restored.scope.functions.getValue("generic").single())
        assertIs<NativeFunction>(restored.scope.functions.getValue("host").single())
        val staticOwner = assertIs<DataTemplate>(GlobalScope.libNamespaces.getValue("fixture.metadata").scope.getObject("StaticBox"))
        val staticMethod = staticOwner.scope.functions.getValue("peek").single()
        assertSame(staticOwner, staticMethod.owner)
        assertTrue(staticMethod.isStatic)
        val abstractOwner = imported("AbstractRoot")
        assertTrue(abstractOwner.isAbstract)
        assertTrue(abstractOwner.scope.functions.getValue("needed").single().isAbstract)
        assertNull(AbstractFunctionInfo.currFunction, "Constructor metadata restoration must restore its construction context")
    }

    @Test fun freshGenericHeadersKeepPendingAnnotationAliasesWithoutSharingWriterState() = withLibrary { output ->
        source(
            """
            namespace fixture.metadata
            @DataOnly
            data Generic<T> {var value:T;}
            func main(){}
        """
        )
        val original = genericLocal()
        val annotation = assertIs<DataOnly>(original.annotations.single())
        original.pendingDeclarationAnnotations.add(annotation)
        annotation.params.add("saved")
        LegacyAnnotationFixtures.write(output)
        annotation.params.add("writer-only")
        fresh(output)
        val restored = assertIs<GenericDataTemplate>(GlobalScope.libNamespaces.getValue("fixture.metadata").scope.getTemplate("Generic"))
        assertNotSame(original, restored)
        assertEquals(1, restored.readOnlyParams.size)
        assertEquals("T", restored.readOnlyParams.single().identifier)
        assertTrue(restored.compiledTemplates.isEmpty(), "A fresh raw header must not reuse writer specializations")
        val copied = assertIs<DataOnly>(restored.annotations.single())
        assertSame(copied, restored.pendingDeclarationAnnotations.single())
        assertNotSame(annotation, copied)
        assertEquals(listOf("saved"), copied.params)
        copied.params.add("reader-only")
        assertEquals(listOf("saved", "writer-only"), annotation.params)
    }

    @Test fun metadataSnapshotsDetachPendingAnnotationGraphsAndReuseCanonicalReadsOnlyWithinOneProject() = withLibrary { output ->
        source("namespace fixture.metadata\ndata Root {var value:int=1;}\nfunc main(){}")
        val root = local("Root")
        val marker = MetadataMarker().apply { values.add("saved") }
        root.annotations.add(marker)
        root.pendingDeclarationAnnotations.add(marker)
        LegacyAnnotationFixtures.write(output)
        marker.values.add("writer-only")
        fun freshRead(inspect: (DataTemplate) -> Unit): DataTemplate {
            LibraryMetadata.reset()
            Project.config.includes = arrayListOf()
            lateinit var value: DataTemplate
            MCFPPStringTest.readFromString("import fixture.metadata:*;\nfunc main(){}", version = "26.3",
                afterFileCreated = {
                    Project.collectingTemplateSignatures {
                        LegacyAnnotationFixtures.install(listOf(output))
                        value = imported("Root")
                        inspect(value)
                    }
                })
            assertEquals(0, Project.errorCount)
            return value
        }
        val restored = freshRead { value ->
            assertNotSame(root, value)
            assertSame(value, imported("Root"))
            val annotation = assertIs<MetadataMarker>(value.annotations.single())
            assertNotSame(marker, annotation)
            assertSame(annotation, value.pendingDeclarationAnnotations.single())
            assertEquals(listOf("saved"), annotation.values)
            annotation.values.add("first-reader")
        }
        assertTrue(restored.pendingDeclarationAnnotations.isEmpty(), "The real declaration pipeline completes pending annotations")
        assertSame(restored, imported("Root"))
        val second = freshRead { value ->
            assertNotSame(restored, value)
            assertSame(value, imported("Root"))
            val annotation = assertIs<MetadataMarker>(value.annotations.single())
            assertNotSame(restored.annotations.single(), annotation)
            assertSame(annotation, value.pendingDeclarationAnnotations.single())
            assertEquals(listOf("saved"), annotation.values)
        }
        assertTrue(second.pendingDeclarationAnnotations.isEmpty(), "The second project also completes its own pending annotations")
        assertEquals(listOf("saved", "first-reader"), assertIs<MetadataMarker>(restored.annotations.single()).values)
        assertEquals(listOf("saved", "writer-only"), marker.values)
    }

    @Test fun thePreviousLibraryVersionIsRejectedBeforeAnyDeclarationIsRead() = withLibrary { _ ->
        Project.init()
        val failure = rejectedVersion(LibraryWire.CURRENT_VERSION - 1)
        assertEquals(LibraryFormatCode.UNSUPPORTED, failure.code)
        assertEquals(0, Project.errorCount, "The explicit import API rejects the header through its exception")
        assertTrue(GlobalScope.libNamespaces.isEmpty())
    }

    @Test fun aFutureLibraryVersionIsRejectedBeforeAnyDeclarationIsRead() = withLibrary { _ ->
        Project.init()
        val failure = rejectedVersion(LibraryWire.CURRENT_VERSION + 1)
        assertEquals(LibraryFormatCode.NEWER, failure.code)
        assertEquals(0, Project.errorCount, "The explicit import API rejects the header through its exception")
        assertTrue(GlobalScope.libNamespaces.isEmpty())
    }

    private fun rejectedVersion(version: Long): LibraryFormatException {
        val bytes = LibraryBinaryWriter().apply {
            rawBytes(byteArrayOf(0x4d, 0x43, 0x46, 0x50, 0x4c, 0x49, 0x42, 0))
            u32(version)
        }.toByteArray()
        // The version must be rejected before attempting to read the absent declaration payload.
        return LibraryImportSession().use { session ->
            kotlin.test.assertFailsWith<LibraryFormatException> { session.stage(bytes) }
        }
    }

    @Test fun freshCompletedNonIdempotentAnnotationsDoNotRunAgainButActualPendingAnnotationsRunOnce() = withLibrary { output ->
        val register: () -> Unit = {
            GlobalScope.localNamespaces.getValue("fixture.metadata").scope.addAnnotation("Generate", GeneratingAnnotation::class.java)
        }
        val processors = Project.stageProcessor[Project.CompileStage.INDEX_TYPE.ordinal]
        processors.add(register)
        try {
            source(
                """
                namespace fixture.metadata
                data Root {var value:int=1;}
                @Generate
                data Middle:Root {}
                data Grand:Middle {}
                data Sibling:Root {}
                data Pending:Root {}
                func main(){}
            """
            )
        } finally {
            processors.remove(register)
        }
        val middle = local("Middle")
        val completed = assertIs<GeneratingAnnotation>(middle.annotations.single())
        assertEquals(1, completed.applications)
        assertTrue(middle.pendingDeclarationAnnotations.isEmpty())
        assertNotNull(middle.scope.getVar("generated_1"))
        assertNull(middle.scope.getVar("generated_2"))
        val pending = GeneratingAnnotation()
        local("Pending").annotations.add(pending)
        local("Pending").pendingDeclarationAnnotations.add(pending)
        LegacyAnnotationFixtures.write(output)
        completed.applications = 99
        pending.applications = 99
        fresh(output)
        val freshMiddle = imported("Middle")
        val freshCompleted = assertIs<GeneratingAnnotation>(freshMiddle.annotations.single())
        val freshPending = assertIs<GeneratingAnnotation>(imported("Pending").annotations.single())
        assertNotSame(completed, freshCompleted)
        assertNotSame(pending, freshPending)
        assertEquals(1, freshCompleted.applications)
        assertEquals(1, freshPending.applications)
        assertEquals(
            listOf(false, true, true, false, true),
            listOf("Root", "Middle", "Grand", "Sibling", "Pending").map {
                assertIs<MCInt>(imported(it).scope.getVar("value")).isDataOnly
            }
        )
        val root = imported("Root")
        for (name in listOf("Middle", "Grand", "Pending")) {
            val owner = imported(name)
            assertNotNull(owner.scope.getVar("generated_1"))
            assertNull(owner.scope.getVar("generated_2"), name)
            assertSame(root, owner.scope.fieldOrigins.getValue("value").single().owner)
            assertSame(root, owner.scope.getVar("value")!!.declaredParentTemplate)
            assertTrue(owner.pendingDeclarationAnnotations.isEmpty())
        }
        assertSame(freshMiddle, imported("Grand").scope.getVar("generated_1")!!.declaredParentTemplate)
        assertSame(freshMiddle, freshMiddle.scope.getVar("generated_1")!!.declaredParentTemplate)
        assertNull(root.scope.getVar("generated_1"))
        assertNull(imported("Sibling").scope.getVar("generated_1"))
        Project.completeTemplateDeclarations()
        assertEquals(1, freshCompleted.applications)
        assertEquals(1, freshPending.applications)
        assertNull(freshMiddle.scope.getVar("generated_2"))
        assertNull(imported("Pending").scope.getVar("generated_2"))
        assertEquals(0, Project.errorCount)
    }

    @Test fun freshExplicitAndNativeAccessorsUseRealReceivingDeclarationsAndIndependentWrappers() = withLibrary { output ->
        source(
            """
            namespace fixture.metadata
            data Root {
                var value:int=1 {get=field+2;set;}
                var nativeValue:int=1 {
                    get=top.mcfpp.test.DataInheritanceLibraryMetadataTest;
                    set=top.mcfpp.test.DataInheritanceLibraryMetadataTest;
                }
            }
            data Middle:Root {}
            data Grand:Middle {}
            data Sibling:Middle {}
            func main(){}
        """
        )
        val oldGetter = assertIs<FunctionAccessor>(local("Root").scope.getProperty("value")!!.accessor).function
        LegacyAnnotationFixtures.write(output)
        nativeCalls.clear()
        fresh(
            output,
            """
            func main(){
                var item=Grand();
                item.value=4;
                external observed=item.value;
                external nativeObserved=item.nativeValue;
                item.nativeValue=8;
            }
        """
        )
        val root = imported("Root")
        val getter = assertIs<FunctionAccessor>(root.scope.getProperty("value")!!.accessor).function
        assertNotSame(oldGetter, getter)
        assertSame(root, getter.owner)
        for (name in listOf("value", "nativeValue")) {
            val declarations = listOf("Root", "Middle", "Grand", "Sibling").map { imported(it).scope.getProperty(name)!! }
            for ((index, property) in declarations.withIndex()) {
                val function = when (val accessor = property.accessor) {
                    is FunctionAccessor -> accessor.function
                    is NativeAccessor -> accessor.function
                    else -> error("Expected an explicit getter")
                }
                assertSame(root, function.owner)
                assertSame(root, property.declaredParentTemplate)
                assertSame(
                    when (val accessor = declarations[0].accessor) {
                        is FunctionAccessor -> accessor.function
                        is NativeAccessor -> accessor.function
                        else -> error("Expected a canonical getter")
                    },
                    function
                )
                declarations.drop(index + 1).forEach { other ->
                    assertNotSame(property.accessor, other.accessor)
                    assertNotSame(property.mutator, other.mutator)
                }
            }
        }
        assertIs<NativeMutator>(imported("Grand").scope.getProperty("nativeValue")!!.mutator).let {
            assertSame(root, it.function.owner)
        }
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val execution = execute(main)
        assertEquals(6, execution.read(assertIs<MCInt>(main.scope.getVar("observed"))))
        assertEquals(7, execution.read(assertIs<MCInt>(main.scope.getVar("nativeObserved"))))
        assertEquals(listOf("get", "set:8"), nativeCalls)
        assertEquals(0, execution.stackDepth)
    }

    @Test fun freshBoundGenericAccessorOwnersResolveByFullCanonicalIdentity() = withLibrary { output ->
        source(
            """
            namespace fixture.metadata
            data A<T> {var value:T {get=field;set;}}
            data IntChild:A<int> {}
            data BoolChild:A<bool> {}
            func main(){}
        """
        )
        val oldInt = local("IntChild").scope.fieldOrigins.getValue("value").single().owner
        LegacyAnnotationFixtures.write(output)
        fresh(output)
        val owners = listOf("IntChild", "BoolChild").map { name ->
            val child = imported(name)
            val owner = child.parent.filterIsInstance<CompiledGenericDataTemplate>().single()
            assertIs<TypeId.Specialized>(owner.inheritanceIdentity)
            assertSame(owner, child.scope.fieldOrigins.getValue("value").single().owner)
            assertSame(owner, child.scope.getVar("value")!!.declaredParentTemplate)
            val getter = assertIs<FunctionAccessor>(child.scope.getProperty("value")!!.accessor).function
            assertSame(owner, getter.owner)
            assertSame(assertIs<FunctionAccessor>(owner.scope.getProperty("value")!!.accessor).function, getter)
            assertEquals(child.scope.getVar("value")!!.type.typeId, getter.returnType.typeId)
            owner
        }
        assertNotSame(oldInt, owners[0])
        assertFalse(owners[0].inheritanceIdentity == owners[1].inheritanceIdentity)
        assertEquals(MCFPPBaseType.Int, imported("IntChild").scope.getVar("value")!!.type)
        assertEquals(MCFPPBaseType.Bool, imported("BoolChild").scope.getVar("value")!!.type)
    }

    @Test fun freshNativeGetterAndSetterDoNotBindSameSignatureOrdinaryMethods() = withLibrary { output ->
        nativeCollisionRoundTrip(
            output,
            """
            func get_nativeValue():int{return 101;}
            func set_nativeValue(value:int):int{return value+100;}
        """,
            nativeMethods = false
        )
    }

    @Test fun freshNativeGetterAndSetterKeepTheirHostTargetWhenNativeMethodsShareTheSignature() = withLibrary { output ->
        nativeCollisionRoundTrip(output, "", nativeMethods = true)
    }

    private fun nativeCollisionRoundTrip(output: Path, methods: String, nativeMethods: Boolean) {
        source(
            """
            namespace fixture.metadata
            ${if (nativeMethods) "@From(\"top.mcfpp.test.DataInheritanceLibraryMetadataTest\")" else ""}
            data Root {
                var nativeValue:int=1 {
                    get=top.mcfpp.test.DataInheritanceLibraryMetadataTest;
                    set=top.mcfpp.test.DataInheritanceLibraryMetadataTest;
                }
                ${if (nativeMethods) "" else methods.trimIndent()}
            }
            data Child:Root {}
            func main(){}
        """
        )
        val oldRoot = local("Root")
        val oldProperty = oldRoot.scope.getProperty("nativeValue")!!
        val oldGetter = assertIs<NativeAccessor>(oldProperty.accessor).function
        val oldSetter = assertIs<NativeMutator>(oldProperty.mutator).function
        for ((name, helper) in listOf("get_nativeValue" to oldGetter, "set_nativeValue" to oldSetter)) {
            val declared = oldRoot.scope.functions.getValue(name).single()
            assertNotSame(helper, declared)
            assertEquals(helper.returnType.typeId, declared.returnType.typeId)
            assertTrue(top.mcfpp.model.function.ParameterMatcher.sameSignature(helper, declared))
            if (nativeMethods) {
                val native = assertIs<NativeFunction>(declared)
                // Match the receiver protocol as well, so the host target distinguishes
                // these two independently valid native declarations.
                native.caller = oldRoot.getType()
                assertFalse(native.javaMethod == helper.javaMethod)
            } else assertFalse(declared is NativeFunction)
        }
        LegacyAnnotationFixtures.write(output)
        nativeCalls.clear()
        fresh(
            output,
            """
            func main(){
                var item=Child();
                external nativeResult=item.nativeValue;
                item.nativeValue=9;
                external ordinaryGet=item.get_nativeValue();
                external ordinarySet=item.set_nativeValue(5);
            }
        """
        )
        val root = imported("Root")
        val property = root.scope.getProperty("nativeValue")!!
        val getter = assertIs<NativeAccessor>(property.accessor).function
        val setter = assertIs<NativeMutator>(property.mutator).function
        assertNotSame(oldRoot, root)
        assertNotSame(oldGetter, getter)
        assertNotSame(oldSetter, setter)
        assertSame(root, getter.owner)
        assertSame(root, setter.owner)
        assertEquals(oldGetter.javaMethod, getter.javaMethod)
        assertEquals(oldSetter.javaMethod, setter.javaMethod)
        assertNotSame(root.scope.functions.getValue("get_nativeValue").single(), getter)
        assertNotSame(root.scope.functions.getValue("set_nativeValue").single(), setter)
        val inherited = imported("Child").scope.getProperty("nativeValue")!!
        assertSame(getter, assertIs<NativeAccessor>(inherited.accessor).function)
        assertSame(setter, assertIs<NativeMutator>(inherited.mutator).function)
        assertNotSame(property.accessor, inherited.accessor)
        assertNotSame(property.mutator, inherited.mutator)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val execution = execute(main)
        assertEquals(7, execution.read(assertIs<MCInt>(main.scope.getVar("nativeResult"))))
        assertEquals(if (nativeMethods) 201 else 101, execution.read(assertIs<MCInt>(main.scope.getVar("ordinaryGet"))))
        assertEquals(if (nativeMethods) 205 else 105, execution.read(assertIs<MCInt>(main.scope.getVar("ordinarySet"))))
        assertEquals(
            if (nativeMethods) listOf("get", "set:9", "ordinary-get", "ordinary-set:5")
            else listOf("get", "set:9"),
            nativeCalls
        )
        assertEquals(0, execution.stackDepth)
        assertEquals(0, Project.errorCount)
    }

    private fun source(text: String) {
        Project.config.includes = arrayListOf()
        val messages = compilerDiagnostics { MCFPPStringTest.readFromString(text.trimIndent(), version = "26.3") }
        assertEquals(0, Project.errorCount, messages.joinToString("\n"))
    }

    private fun fresh(output: Path, consumer: String = "func main(){}") {
        // MCFPPStringTest calls Project.init before readProject: GlobalScope,
        // generic models, declaration completion, and LibraryMetadata caches reset.
        LibraryMetadata.reset()
        Project.config.includes = arrayListOf()
        val messages = compilerDiagnostics {
            MCFPPStringTest.readFromString("import fixture.metadata:*;\n${consumer.trimIndent()}", version = "26.3",
                afterFileCreated = { LegacyAnnotationFixtures.install(listOf(output)) })
        }
        assertEquals(0, Project.errorCount, messages.joinToString("\n"))
    }

    private fun local(name: String) = GlobalScope.localNamespaces.getValue("fixture.metadata").scope.template.getValue(name)
    private fun imported(name: String) = GlobalScope.libNamespaces.getValue("fixture.metadata").scope.template.getValue(name)
    private fun genericLocal(): GenericDataTemplate = assertIs(
        GlobalScope.localNamespaces.getValue("fixture.metadata").scope.getTemplate("Generic")
    )

    private fun execute(main: Function): ScoreCommandExecutor = executePublishedFunctions(main)

    private fun withLibrary(action: (Path) -> Unit) {
        val output = Files.createTempDirectory("mcfpp-inheritance-metadata-")
        val config = Project.config
        val settings = listOf(
            CompileSettings.isDebug,
            CompileSettings.ignoreStdLib,
            CompileSettings.isLib,
            CompileSettings.printAll,
            CompileSettings.foldIRConstants
        )
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = true
            action(output)
        } finally {
            LegacyAnnotationFixtures.close()
            Project.config = config
            CompileSettings.isDebug = settings[0]
            CompileSettings.ignoreStdLib = settings[1]
            CompileSettings.isLib = settings[2]
            CompileSettings.printAll = settings[3]
            CompileSettings.foldIRConstants = settings[4]
            output.toFile().deleteRecursively()
        }
    }

    class MetadataMarker : Annotation("MetadataMarker", "fixture.metadata") {
        var values = arrayListOf<String>()
            private set
        internal fun restorePortableValues(values: ArrayList<String>) { this.values = values }
        override fun forFunction(function: Function) = Unit
        override fun forDataTemplate(data: DataTemplate) = Unit
        override fun forField(field: top.mcfpp.core.lang.Var<*>) = Unit
    }

    class GeneratingAnnotation : Annotation("Generate", "fixture.metadata") {
        var applications = 0
        override fun forFunction(function: Function) = Unit
        override fun forField(field: Var<*>) = Unit
        override fun forDataTemplate(data: DataTemplate) {
            applications++
            val name = "generated_$applications"
            val generated = MCInt(name).apply {
                declaredParentTemplate = data
                bindDeclaration()
            }
            check(data.addMember(generated))
            check(data.addMember(FieldProperty.buildSimpleProperty(generated).apply { declaredParentTemplate = data }))
            data.preInit[name] = mcfppParser(CommonTokenStream(mcfppLexer(CharStreams.fromString("17")))).expression()
            assertIs<MCInt>(data.scope.getVar("value")).isDataOnly = true
        }
    }

    companion object {
        val nativeCalls = arrayListOf<String>()
        @JvmStatic @NoExternalWrites
        @MNIFunction(returnType = "int", identifier = "host")
        fun returnSeven(context: NativeCallContext) {
            context.publishResult(MCInt(7))
        }
        @JvmStatic @NoExternalWrites
        @MNIAccessor("nativeValue")
        fun propertyGetter(context: NativeCallContext) {
            nativeCalls.add("get")
            context.publishResult(MCInt(7))
        }
        @JvmStatic @NoExternalWrites
        @MNIMutator("nativeValue")
        fun propertySetter(context: NativeCallContext) {
            context.withArguments {
                nativeCalls.add("set:${assertIs<MCInt>(it.single()).value}")
                context.publishResult(it.single())
            }
        }
        @JvmStatic @NoExternalWrites
        @MNIFunction(returnType = "int", identifier = "get_nativeValue", caller = "fixture.metadata:Root")
        fun ordinaryNativeGetter(context: NativeCallContext) {
            nativeCalls.add("ordinary-get")
            context.publishResult(MCInt(201))
        }
        @JvmStatic @NoExternalWrites
        @MNIFunction(normalParams = ["int"], returnType = "int", identifier = "set_nativeValue", caller = "fixture.metadata:Root")
        fun ordinaryNativeSetter(context: NativeCallContext) {
            context.withArguments {
                val value = assertIs<MCInt>(it.single()).value
                nativeCalls.add("ordinary-set:$value")
                context.publishResult(MCInt(value + 200))
            }
        }
    }
}
