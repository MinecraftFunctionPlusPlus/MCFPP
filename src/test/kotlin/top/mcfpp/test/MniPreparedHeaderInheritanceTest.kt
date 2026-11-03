package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.antlr.ReadonlyParamsDeclarationSyntax
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.io.MCFPPFile
import top.mcfpp.mni.api.SourceSpan
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.Namespace
import top.mcfpp.model.annotation.Annotation
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.DataTemplateParam
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.GenericObjectDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

/** Real declaration syntax and the real reader queue, without the bundled library or eager compile. */
class MniPreparedHeaderInheritanceTest {
    private val source = SourceSpan("header-inheritance.mcfpp", 0, 8, 1, 1, 1, 9)
    private fun parser(text: String) = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, source.file))))
    private fun file(): MCFPPFile {
        Project.init()
        val namespace = Namespace("header.inheritance")
        GlobalScope.localNamespaces[namespace.identifier] = namespace
        return MCFPPFile(namespace).also { namespace.scope.journalFileFields.add(it.field) }
    }

    private fun prototype(file: MCFPPFile, text: String): GenericDataTemplate {
        val parser = parser(text)
        val objectDeclaration = text.startsWith("object")
        val body: P.TemplateBodyContext
        val header: P.CompoundDeclarationContext
        if (objectDeclaration) {
            val declaration = parser.objectTemplateDeclaration()
            body = declaration.templateBody()
            header = declaration.compoundDeclaration()
        } else {
            val declaration = parser.templateDeclaration()
            body = declaration.templateBody()
            header = declaration.compoundDeclaration()
        }
        assertEquals(0, parser.numberOfSyntaxErrors, text)
        assertEquals(Token.EOF, parser.currentToken.type, text)
        val name = header.declarationName().classWithoutNamespace().text
        val prototype = if (objectDeclaration) GenericObjectDataTemplate(body, name, file.namespace.identifier)
        else GenericDataTemplate(body, name, file.namespace.identifier)
        prototype.declarationFile = file
        header.declarationName().readOnlyParams().readOnlyParameterList().readOnlyParameter().forEachIndexed { index, parameter ->
            prototype.readOnlyParams.add(
                DataTemplateParam(
                    ReadonlyParamsDeclarationSyntax.readonlyTypeName(parameter),
                    ReadonlyParamsDeclarationSyntax.readonlyParameterName(parameter),
                    if (ReadonlyParamsDeclarationSyntax.isBareTypeParameter(parameter)) MCFPPConcreteType.Type else null
                )
            )
        }
        prototype.parentID.addAll(header.extendName().map { it.text })
        if (objectDeclaration) file.namespace.scope.objects.add(prototype)
        else file.namespace.scope.template[name] = prototype
        return prototype
    }

    private class Work(
        val session: SignaturePreparationSession,
        val batch: PreparedHeaderBatch,
        val lexical: DeclarationLexicalSnapshot
    ) {
        val owner = batch.headers.single()
        fun read() = session.readHeaders(batch, lexical)
    }

    private fun work(file: MCFPPFile, type: String = "Child<int>"): Work {
        val target = Function("target", file.namespace.identifier, null).apply {
            declarationFile = file
            normalParams.add(FunctionParam(UnresolvedType(type), "value", this))
        }
        val session = SignaturePreparationSession()
        val result = assertIs<SignaturePreparationResult.NeedsCompletion>(
            session.prepare(SignaturePreparationRequest(listOf(target), emptyList(), source))
        )
        return Work(session, result.staged, result.requests.single().lexical)
    }

    private fun header(result: HeaderReadResult, prototype: GenericDataTemplate) = assertIs<HeaderReadResult.LocalHeaders>(result).headers.single { it.owner.prototype === prototype }

    private fun rejected(result: HeaderReadResult, vararg fragments: String) {
        val diagnostic = assertIs<HeaderReadResult.Rejected>(result).diagnostics.single()
        assertEquals(source.file, diagnostic.source.file)
        fragments.forEach { assertTrue(it in diagnostic.message, diagnostic.message) }
        assertEquals(0, Project.errorCount, "Pure reader diagnostics must not leak to the compiler logger")
    }

    private fun unchanged(result: HeaderReadResult.LocalHeaders, vararg prototypes: GenericDataTemplate) {
        result.headers.forEach {
            assertTrue(it.hasCompleteInheritedView)
            assertFalse(it.owner.model.hasCompletedDeclaration)
            assertTrue(it.owner.model.scope.allVars.isEmpty())
            assertTrue(it.owner.model.parent.isEmpty())
            assertTrue(it.owner.model.constructors.isEmpty())
        }
        prototypes.forEach {
            assertEquals(0, it.index)
            assertTrue(it.compiledTemplates.isEmpty())
            assertTrue(it.scope.allVars.isEmpty())
        }
        assertEquals(0, Project.errorCount)
    }

    @Test fun aSameBindingDiamondKeepsOneCanonicalSourceInEitherParentOrder() {
        for (parents in listOf("Left<T>,Right<T>", "Right<T>,Left<T>")) {
            val file = file()
            val root = prototype(file, "data Root<T>{var x:T;}")
            val left = prototype(file, "data Left<T>:Root<T>{}")
            val right = prototype(file, "data Right<T>:Root<T>{}")
            val child = prototype(file, "data Child<T>:$parents{}")
            val result = assertIs<HeaderReadResult.LocalHeaders>(work(file).read())
            val base = header(result, root)
            val view = header(result, child)
            assertTrue(view.fields.isEmpty(), "Inherited declarations must never become local adapters")
            val field = view.visibleFields.single()
            assertEquals(base.owner.identity, field.origin)
            assertSame(base.fields.single().declaration, field.declaration)
            assertSame(MCFPPBaseType.Int, field.bound.type)
            assertEquals(header(result, left).parents.single().type.typeId, header(result, right).parents.single().type.typeId)
            unchanged(result, root, left, right, child)
        }
    }

    @Test fun theMoreSpecificFieldOwnerWinsInBothTheReaderAndRealLayout() {
        for (parents in listOf("Left<T>,Right<T>", "Right<T>,Left<T>")) {
            val file = file()
            val root = prototype(file, "data Root<T>{var x:T;}")
            val left = prototype(file, "data Left<T>:Root<T>{}")
            val right = prototype(file, "data Right<T>:Root<T>{var x:T;}")
            val child = prototype(file, "data Child<T>:$parents{}")
            val result = assertIs<HeaderReadResult.LocalHeaders>(work(file).read())
            assertEquals(header(result, right).owner.identity, header(result, child).visibleFields.single().origin)
            unchanged(result, root, left, right, child)

            fun actual(name: String) = DataTemplate(name, "legacy.inheritance").apply { declarationFile = null }
            fun field(owner: DataTemplate) {
                owner.addMember(MCInt("x").apply { declaredParentTemplate = owner })
            }
            val actualRoot = actual("Root").also(::field)
            val actualLeft = actual("Left").apply { extends(actualRoot) }
            val actualRight = actual("Right").apply { extends(actualRoot) }.also(::field)
            val actualChild = actual("Child").apply {
                if (parents.startsWith("Left")) {
                    extends(actualLeft)
                    extends(actualRight)
                } else {
                    extends(actualRight)
                    extends(actualLeft)
                }
            }
            listOf(actualRoot, actualLeft, actualRight, actualChild).forEach { it.flatExtends() }
            assertSame(actualRight, actualChild.scope.fieldOrigins.getValue("x").single().owner)
            assertSame(actualRight, actualChild.scope.getVar("x")!!.declaredParentTemplate)
            assertEquals(0, Project.errorCount)
        }
    }

    @Test fun unrelatedEqualSlotsRequireAnExplicitCompatibleLocalMerge() {
        for (parents in listOf("A<T>,B<T>", "B<T>,A<T>")) for (local in listOf("", "var x:int=9;")) {
            val file = file()
            prototype(file, "data A<T>{var x:int;}")
            prototype(file, "data B<T>{var x:int;}")
            val child = prototype(file, "data Child<T>:$parents{$local}")
            val result = work(file).read()
            if (local.isEmpty()) rejected(result, "Ambiguous inherited field 'x'", "name=A", "name=B")
            else {
                val view = header(result, child)
                assertEquals(view.owner.identity, view.visibleFields.single().origin)
                assertSame(view.fields.single().declaration, view.visibleFields.single().declaration)
                assertEquals("9", view.fields.single().initializer!!.text)
                unchanged(assertIs<HeaderReadResult.LocalHeaders>(result), child)
            }
        }
    }

    @Test fun incompatibleParentsCannotBeRepairedByALocalField() {
        for (parents in listOf("A<T>,B<T>", "B<T>,A<T>")) for (local in listOf("", "var x:int;")) {
            val file = file()
            prototype(file, "data A<T>{var x:int;}")
            prototype(file, "data B<T>{var x:bool;}")
            prototype(file, "data Child<T>:$parents{$local}")
            rejected(work(file).read(), "Incompatible inherited field 'x'", "typeId", "name=A", "name=B")
        }
    }

    @Test fun nullableConstAndStaticConflictsUseTheSharedSlotContract() {
        for ((first, second, reason) in listOf(
            Triple("data A<T>{var x:int;}", "data B<T>{var x:int?;}", "nullable"),
            Triple("data A<T>{var x:int;}", "data B<T>{const x:int=1;}", "const"),
            Triple("data A<T>{var x:int;}", "object data B<T>{var x:int;}", "static")
        )) for (parents in listOf("A<T>,B<T>", "B<T>,A<T>")) {
            val file = file()
            prototype(file, first)
            prototype(file, second)
            prototype(file, "data Child<T>:$parents{}")
            rejected(work(file).read(), "Incompatible inherited field 'x'", reason)
        }
    }

    @Test fun localAccessCannotNarrowAnInheritedSlot() {
        val file = file()
        prototype(file, "data Root<T>{protected var x:T;}")
        prototype(file, "data Child<T>:Root<T>{private var x:T;}")
        rejected(work(file).read(), "Incompatible inherited field 'x'", "access")
    }

    @Test fun localAccessMayWidenAndKeepsItsOwnConstInitializerAndProperty() {
        val file = file()
        prototype(file, "data Root<T>{protected const x:int=1;}")
        val child = prototype(file, "data Child<T>:Root<T>{const x:int=8;}")
        val view = header(work(file).read(), child)
        val field = view.visibleFields.single()
        assertEquals(view.owner.identity, field.origin)
        assertEquals(AccessModifier.PUBLIC, field.contract.access)
        assertTrue(field.contract.isConst)
        assertEquals(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(8)), field.bound.constant)
        assertEquals(PreparedHeaderProperty.DEFAULT, field.property)
    }

    @Test fun differentBindingsOfTheSameGenericRemainDistinctOrigins() {
        for (parents in listOf("Root<int>,Root<bool>", "Root<bool>,Root<int>")) {
            val file = file()
            val root = prototype(file, "data Root<T>{var x:int;}")
            prototype(file, "data Child<T>:$parents{}")
            fun identity(type: MCFPPType) = TypeId.Specialized(
                TypeId.Declaration("template", root.namespace, root.identifier),
                listOf(CompilerValue.Typed(MCFPPConcreteType.Type.typeId, CompilerValue.TypeValue(type.typeId)))
            )
            val integer = identity(MCFPPBaseType.Int)
            val boolean = identity(MCFPPBaseType.Bool)
            assertNotEquals(integer, boolean)
            val origins = if (parents.startsWith("Root<int>")) listOf(integer, boolean) else listOf(boolean, integer)
            rejected(work(file).read(), "Ambiguous inherited field 'x'", "from ${origins.joinToString()}")
        }
    }

    @Test fun inheritanceCyclesAreRejectedWhileFieldReferenceCyclesRemainLegal() {
        val file = file()
        prototype(file, "data A<T>:B<T>{}")
        prototype(file, "data B<T>:A<T>{}")
        rejected(work(file, "A<int>").read(), "Cyclic inheritance", "name=A", "name=B")
        val other = file()
        val node = prototype(other, "data Node<T>{var next:Node<T>;}")
        val view = header(work(other, "Node<int>").read(), node)
        assertSame(view.owner.type, view.visibleFields.single().bound.type)
        assertTrue(view.hasCompleteInheritedView)
    }

    @Test fun completedOrdinaryParentsSupplyFactsWithoutChangingTheirLayoutOrEffects() {
        val file = file()
        val parent = DataTemplate("Parent", file.namespace.identifier).apply { declarationFile = file }
        val field = MCInt("x").apply {
            declaredParentTemplate = parent
            isFinal = true
        }
        parent.addMember(field)
        file.namespace.scope.template[parent.identifier] = parent
        Project.compileStage = Project.CompileStage.COMPILE
        Project.registerTemplateContract(parent)
        Project.completeTemplateDeclarations()
        assertTrue(parent.hasCompletedDeclaration)
        val child = prototype(file, "data Child<T>:Parent{var x:int;}")
        rejected(work(file).read(), "Incompatible inherited field 'x'", "final")
        assertSame(field, parent.scope.getVar("x"))
        assertTrue(parent.hasCompletedDeclaration)
        assertTrue(child.compiledTemplates.isEmpty())
    }

    @Test fun publishedCompleteViewsAreReusedAndInheritedFactDriftIsRejected() {
        val file = file()
        val parent = DataTemplate("Parent", file.namespace.identifier).apply { declarationFile = file }
        val field = MCInt("x").apply { declaredParentTemplate = parent }
        parent.addMember(field)
        file.namespace.scope.template[parent.identifier] = parent
        Project.compileStage = Project.CompileStage.COMPILE
        Project.registerTemplateContract(parent)
        Project.completeTemplateDeclarations()
        val child = prototype(file, "data Child<T>:Parent{}")
        val first = assertIs<HeaderReadResult.LocalHeaders>(work(file).read())
        val record = header(first, child)
        first.staged.publishHeaders()
        val second = assertIs<HeaderReadResult.LocalHeaders>(work(file).read())
        assertSame(record, header(second, child))
        field.isConst = true
        rejected(work(file).read(), "Published header facts changed")
        assertSame(record, record.owner.localHeader)
        assertFalse(record.visibleFields.single().contract.isConst)
    }

    @Test fun anEmptyPublishedShellKeepsTheCompleteViewInTheBatchUntilExplicitPublish() {
        val file = file()
        prototype(file, "data Root<T>{var x:T;}")
        val child = prototype(file, "data Child<T>:Root<T>{}")
        val work = work(file)
        work.batch.publishHeaders()
        assertNull(work.owner.localHeader)
        val result = assertIs<HeaderReadResult.LocalHeaders>(work.read())
        val view = header(result, child)
        assertTrue(view.hasCompleteInheritedView)
        assertEquals("x", view.visibleFields.single().name)
        assertNull(work.owner.localHeader)
        result.staged.publishHeaders()
        assertSame(view, work.owner.localHeader)
        assertTrue(work.owner.model.scope.allVars.isEmpty())
        assertTrue(child.compiledTemplates.isEmpty())
    }

    @Test fun annotationAndAmbientCompilerStateAreUntouchedByInheritedPreparation() {
        val file = file()
        val root = prototype(file, "data Root<T>{var x:T;}")
        val child = prototype(file, "data Child<T>:Root<T>{var y:int=3;}")
        val annotation = Probe()
        root.annotations.add(annotation)
        child.annotations.add(annotation)
        val current = Function.currFunction
        val forced = Function.forcedField
        val currentFile = MCFPPFile.currFile
        val currentTemplate = DataTemplate.currTemplate
        val result = assertIs<HeaderReadResult.LocalHeaders>(work(file).read())
        assertEquals(setOf("x", "y"), header(result, child).visibleFields.map { it.name }.toSet())
        assertEquals(0, annotation.calls)
        assertSame(current, Function.currFunction)
        assertSame(forced, Function.forcedField)
        assertSame(currentFile, MCFPPFile.currFile)
        assertSame(currentTemplate, DataTemplate.currTemplate)
        unchanged(result, root, child)
    }

    @Test fun unsupportedCompletedParentAdaptersAreRejectedBeforeTheirTypeGetterRuns() {
        val file = file()
        val parent = DataTemplate("Parent", file.namespace.identifier).apply { declarationFile = file }
        parent.addMember(MCInt("x").apply { declaredParentTemplate = parent })
        file.namespace.scope.template[parent.identifier] = parent
        Project.compileStage = Project.CompileStage.COMPILE
        Project.registerTemplateContract(parent)
        Project.completeTemplateDeclarations()
        assertTrue(parent.hasCompletedDeclaration)
        val trap = GetterTrap()
        parent.scope.putVar("x", trap, true)
        GetterTrap.reads = 0
        prototype(file, "data Child<T>:Parent{}")
        val target = Function("target", file.namespace.identifier, null).apply {
            declarationFile = file
            normalParams.add(FunctionParam(UnresolvedType("Child<int>"), "value", this))
        }
        val preparation = SignaturePreparationSession().prepare(
            SignaturePreparationRequest(listOf(target), emptyList(), source)
        )
        val diagnostic = assertIs<SignaturePreparationResult.Rejected>(preparation).diagnostics.single()
        assertEquals(source, diagnostic.source)
        assertTrue("Unsupported declaration field" in diagnostic.message, diagnostic.message)
        assertTrue(GetterTrap::class.java.name in diagnostic.message, diagnostic.message)
        assertEquals(0, GetterTrap.reads)
        assertEquals(0, Project.errorCount)
        assertSame(trap, parent.scope.getVar("x"))
    }

    @Test fun nonTemplateParentsReceiveAnExplicitSourceDiagnostic() {
        val file = file()
        val child = prototype(file, "data Child<T>:T{}")
        assertEquals(listOf("T"), child.parentID)
        val work = work(file, "Child<int>")
        assertEquals(
            CompilerValue.Typed(MCFPPConcreteType.Type.typeId, CompilerValue.TypeValue(MCFPPBaseType.Int.typeId)),
            work.owner.readonlyFacts.single().value
        )
        rejected(work.read(), "parent must be a template")
    }

    @Test fun aFinalParentCannotAcquireAValidInheritedHeaderView() {
        val file = file()
        prototype(file, "data Root<T>{var x:T;}").isFinal = true
        prototype(file, "data Child<T>:Root<T>{}")
        rejected(work(file).read(), "Cannot extend final template", "name=Root")
    }

    private fun completedParent(file: MCFPPFile, field: Var<*>, extraParent: CompoundData? = null): DataTemplate {
        val parent = DataTemplate("Parent", file.namespace.identifier).apply {
            declarationFile = file
            // Match Project.resolveField's production default, including DataObject -> object.
            extends(DataTemplate.baseDataTemplate)
            extraParent?.let { extends(it) }
        }
        field.declaredParentTemplate = parent
        assertTrue(parent.addMember(field))
        file.namespace.scope.template[parent.identifier] = parent
        Project.compileStage = Project.CompileStage.COMPILE
        Project.registerTemplateContract(parent)
        Project.completeTemplateDeclarations()
        assertTrue(parent.hasCompletedDeclaration)
        assertEquals(0, Project.errorCount)
        return parent
    }

    @Test fun ordinaryParentConstantProofAndDescriptorsSurviveAndValueDriftIsRejected() {
        val file = file()
        fun literal(value: Long) = StorageAccess.literal(
            MCFPPBaseType.Int,
            CompilerValue.Integral(value),
            "x",
            mapOf(MCFPPBaseType.Bool.typeId to MCFPPBaseType.Bool)
        ).apply { isConst = true }
        val field = literal(7)
        val parent = completedParent(file, field)
        val child = prototype(file, "data Child<T>:Parent{}")
        val first = assertIs<HeaderReadResult.LocalHeaders>(work(file).read())
        val record = header(first, child)
        val inherited = record.visibleFields.single()
        val seven = CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(7))
        assertEquals(seven, inherited.bound.constant)
        assertSame(MCFPPBaseType.Bool, inherited.bound.descriptors[MCFPPBaseType.Bool.typeId])
        assertSame(parent, field.declaredParentTemplate)
        assertEquals(seven, DeclarationLexicalSnapshot.rawValue(field)!!.constant)
        first.staged.publishHeaders()
        val replacement = literal(9).apply { declaredParentTemplate = parent }
        assertTrue(parent.scope.putVar("x", replacement, true))
        assertEquals(
            CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(9)),
            DeclarationLexicalSnapshot.rawValue(replacement)!!.constant
        )
        rejected(work(file).read(), "Published header facts changed")
        assertSame(record, record.owner.localHeader)
        assertEquals(seven, inherited.bound.constant)
        assertTrue(parent.hasCompletedDeclaration)
    }

    @Test fun theWholeOrdinaryParentDescriptorSetIsFrozenAndParticipatesInDriftChecks() {
        val file = file()
        val nested = MCFPPListType(MCFPPListType(MCFPPBaseType.Int))
        val nestedId = nested.typeId
        val field = StorageAccess.literal(
            MCFPPBaseType.Int,
            CompilerValue.Integral(7),
            "x",
            mapOf(nestedId to nested)
        ).apply { isConst = true }
        completedParent(file, field)
        val child = prototype(file, "data Child<T>:Parent{}")
        val first = assertIs<HeaderReadResult.LocalHeaders>(work(file).read())
        val record = header(first, child)
        val descriptors = record.visibleFields.single().bound.descriptors
        val snapshot = assertIs<MCFPPListType>(descriptors.getValue(nestedId))
        assertNotSame(nested, snapshot)
        assertNotSame(nested.generic.single(), snapshot.generic.single())
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (descriptors as MutableMap<TypeId, MCFPPType>)[MCFPPBaseType.Bool.typeId] = MCFPPBaseType.Bool
        }
        first.staged.publishHeaders()
        field.storageBinding!!.data.types[MCFPPBaseType.Bool.typeId] = MCFPPBaseType.Bool
        rejected(work(file).read(), "Published header facts changed")
        assertFalse(MCFPPBaseType.Bool.typeId in descriptors)
        assertSame(record, record.owner.localHeader)
        nested.generic[0] = MCFPPBaseType.Bool
        assertEquals(nestedId, snapshot.typeId)
    }

    @Test fun nestedOrdinaryConstantTypesAndProofsArePreparedWithoutAliasingStorage() {
        val file = file()
        val nested = MCFPPListType(MCFPPListType(MCFPPBaseType.Int))
        val nestedId = nested.typeId
        val proof = CompilerValue.Sequence(listOf(CompilerValue.Sequence(listOf(CompilerValue.Integral(7)))))
        val field = StorageAccess.literal(nested, proof, "x").apply { isConst = true }
        completedParent(file, field)
        val child = prototype(file, "data Child<T>:Parent{}")
        val record = header(work(file).read(), child)
        val bound = record.visibleFields.single().bound
        assertEquals(CompilerValue.Typed(nestedId, proof), bound.constant)
        assertNotSame(nested, bound.type)
        assertEquals(nestedId, bound.type.typeId)
        assertTrue(nestedId in bound.descriptors)
        (nested.generic.single() as MCFPPListType).generic[0] = MCFPPBaseType.Bool
        assertEquals(nestedId, bound.type.typeId)
        assertEquals(CompilerValue.Typed(nestedId, proof), bound.constant)
    }

    @Test fun evenUnusedOrdinaryStorageDescriptorsMustHaveMatchingPreparedIdentities() {
        val file = file()
        val field = StorageAccess.literal(MCFPPBaseType.Int, CompilerValue.Integral(7), "x").apply { isConst = true }
        completedParent(file, field)
        field.storageBinding!!.data.types[MCFPPBaseType.Bool.typeId] = MCFPPListType(MCFPPBaseType.Int)
        prototype(file, "data Child<T>:Parent{}")
        rejected(work(file).read(), "descriptor identity does not match its key")
    }

    @Test fun theProductionDefaultBaseChainKeepsOrdinaryFieldSourceOriginAndClosedFacts() {
        val file = file()
        val field = StorageAccess.literal(
            MCFPPBaseType.Int,
            CompilerValue.Integral(7),
            "x",
            mapOf(MCFPPBaseType.Bool.typeId to MCFPPBaseType.Bool)
        ).apply { isConst = true }
        val parent = completedParent(file, field)
        val dataObject = DataTemplate.baseDataTemplate
        val builtinObject = MCFPPBaseType.Object.instanceData
        assertSame(dataObject, parent.parent.single())
        assertSame(builtinObject, dataObject.parent.single())
        assertEquals(CompoundData::class.java, builtinObject.javaClass)
        val parentFields = parent.scope.allVars.toList()
        val baseFields = dataObject.scope.allVars.toList()
        val builtinFields = builtinObject.scope.allVars.toList()
        val child = prototype(file, "data Child<T>:Parent{var local:T;}")
        val result = assertIs<HeaderReadResult.LocalHeaders>(work(file).read())
        val view = header(result, child)
        assertEquals(listOf("local"), view.fields.map { it.name })
        assertEquals(setOf("x", "local"), view.visibleFields.map { it.name }.toSet())
        val inherited = view.visibleFields.single { it.name == "x" }
        assertEquals(parent.inheritanceIdentity, inherited.origin)
        assertSame(MCFPPBaseType.Int, inherited.bound.type)
        assertEquals(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(7)), inherited.bound.constant)
        assertSame(MCFPPBaseType.Bool, inherited.bound.descriptors[MCFPPBaseType.Bool.typeId])
        assertEquals(view.parents.single().source, inherited.source)
        assertEquals(source.file, inherited.source.file)
        assertNull(inherited.declaration)
        assertEquals(view.owner.identity, view.visibleFields.single { it.name == "local" }.origin)
        assertSame(view.fields.single().declaration, view.visibleFields.single { it.name == "local" }.declaration)
        assertSame(field, parent.scope.getVar("x"))
        assertEquals(parentFields, parent.scope.allVars.toList())
        assertEquals(baseFields, dataObject.scope.allVars.toList())
        assertEquals(builtinFields, builtinObject.scope.allVars.toList())
        assertSame(dataObject, parent.parent.single())
        assertSame(builtinObject, dataObject.parent.single())
        unchanged(result, child)
    }

    @Test fun aSameNamedPlainCompoundParentCannotImpersonateTheTrustedBuiltinObject() {
        val file = file()
        val impostor = CompoundData("object", "mcfpp.lang")
        assertNotSame(MCFPPBaseType.Object.instanceData, impostor)
        completedParent(file, MCInt("x"), impostor)
        prototype(file, "data Child<T>:Parent{}")
        rejected(work(file).read(), "Unsupported inherited declaration shape", "CompoundData")
    }

    class GetterTrap : MCInt("x") {
        override var type: MCFPPType
            get() {
                reads++
                return MCFPPBaseType.Int
            }
            set(@Suppress("UNUSED_PARAMETER") value) {}
        companion object {
            var reads = 0
        }
    }

    class Probe : Annotation("HeaderInheritanceProbe", "header.inheritance") {
        var calls = 0
        override fun forFunction(function: Function) {
            calls++
        }
        override fun forField(field: Var<*>) {
            calls++
        }
        override fun forDataTemplate(dataTemplate: DataTemplate) {
            calls++
        }
    }
}
