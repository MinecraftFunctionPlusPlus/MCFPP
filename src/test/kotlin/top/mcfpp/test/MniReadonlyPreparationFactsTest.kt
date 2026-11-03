package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import top.mcfpp.Project
import top.mcfpp.analysis.*
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.command.Command
import top.mcfpp.core.lang.MCInt
import top.mcfpp.io.MCFPPFile
import top.mcfpp.io.info.DeclarationEnvironmentInfo
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.Namespace
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.FunctionParam
import top.mcfpp.model.function.GenericFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.*
import kotlin.test.*
import kotlin.test.Test
import top.mcfpp.antlr.mcfppParser as P

class MniReadonlyPreparationFactsTest {
    private fun fixture(): Pair<MCFPPFile, Function> {
        Project.init()
        val namespace = Namespace("fixture.preparation")
        GlobalScope.localNamespaces[namespace.identifier] = namespace
        val file = MCFPPFile(namespace)
        // Keep both real relationships. Alias setup below writes prepared facts directly:
        // registration's containType query recurses through these two scopes.
        namespace.scope.journalFileFields.add(file.field)
        return file to Function("declaration", namespace.identifier, null).apply { declarationFile = file }
    }

    private fun parser(text: String): P = P(CommonTokenStream(mcfppLexer(CharStreams.fromString(text, "facts.mcfpp"))))
    private fun expression(text: String): P.ExpressionContext {
        val parser = parser(text)
        return parser.expression().also {
            assertEquals(0, parser.numberOfSyntaxErrors, text)
            assertEquals(Token.EOF, parser.currentToken.type, text)
        }
    }
    private fun value(text: String): P.ValueContext {
        val parser = parser(text)
        return parser.value().also {
            assertEquals(0, parser.numberOfSyntaxErrors, text)
            assertEquals(Token.EOF, parser.currentToken.type, text)
        }
    }
    private fun type(text: String): P.TypeContext {
        val parser = parser(text)
        return parser.type().also { assertEquals(0, parser.numberOfSyntaxErrors, text) }
    }
    private fun binding(
        file: MCFPPFile,
        formals: List<DeclarationLexicalSnapshot.Formal> = emptyList(),
        index: Int = formals.count { it.readonly },
        previous: Map<String, DeclarationBinding.Bound> = emptyMap(),
        types: Map<String, MCFPPType> = emptyMap()
    ) = DeclarationBinding(
        DeclarationPreparationContext(
            DeclarationLexicalSnapshot.capture(file, file.namespace.identifier, formals = formals),
            index,
            previous,
            types
        )
    )
    private fun int(number: Long) = DeclarationBinding.Bound(
        MCFPPBaseType.Int,
        CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(number))
    )
    private fun payload(bound: DeclarationBinding.Bound) = assertIs<CompilerValue.Typed>(bound.constant).payload
    private fun failure(code: PreparationFactCode, action: () -> Unit): PreparationFactFailure = assertFailsWith<PreparationFactFailure> { action() }.also {
        assertEquals(code, it.code)
        assertEquals("facts.mcfpp", it.source.file)
        assertTrue(it.source.endOffset > it.source.startOffset)
    }

    @Test fun declarationImportsAndTypesRemainIndependentOfCallerAndLaterImportChanges() {
        val (file, declaration) = fixture()
        val imported = Namespace("fixture.original")
        GlobalScope.localNamespaces[imported.identifier] = imported
        imported.scope.putResolvedAlias("Imported", MCFPPBaseType.Int, forced = true)
        file.unsolvedImports[imported.identifier] = "*"
        file.field.importedNamespaceField[imported.identifier] = imported.scope
        val snapshot = DeclarationLexicalSnapshot.capture(declaration)
        val other = Namespace("fixture.caller")
        other.scope.putResolvedAlias("Imported", MCFPPBaseType.Bool, forced = true)
        GlobalScope.localNamespaces[other.identifier] = other
        file.unsolvedImports.clear()
        file.field.importedNamespaceField.clear()
        imported.scope.putResolvedAlias("Imported", MCFPPBaseType.Float, true)
        Project.currNamespace = other.identifier
        MCFPPFile.currFile = MCFPPFile(other)
        val pure = DeclarationBinding(DeclarationPreparationContext(snapshot))
        assertSame(MCFPPBaseType.Int, pure.type(type("Imported")))
        assertSame(MCFPPBaseType.Int, pure.type(type("fixture.original:Imported")))
        failure(PreparationFactCode.UNKNOWN_NAME) { pure.type(type("fixture.caller:Imported")) }
        assertEquals(mapOf("fixture.original" to "*"), snapshot.imports)
    }

    @Test fun persistedDeclarationEnvironmentUsesRegisteredNamespacesWithoutRestoringAFile() {
        val (_, declaration) = fixture()
        val imported = Namespace("fixture.library")
        GlobalScope.libNamespaces[imported.identifier] = imported
        imported.scope.putResolvedAlias("Element", MCFPPBaseType.Bool, forced = true)
        declaration.declarationFile = null
        declaration.declarationEnvironment = DeclarationEnvironmentInfo(
            declaration.namespace,
            mapOf(imported.identifier to "*")
        )
        val currentFile = MCFPPFile.currFile
        val snapshot = DeclarationLexicalSnapshot.capture(declaration)
        assertSame(MCFPPBaseType.Bool, DeclarationBinding(DeclarationPreparationContext(snapshot)).type(type("Element")))
        assertNull(declaration.declarationFile)
        assertSame(currentFile, MCFPPFile.currFile)
    }

    @Test fun namespacePriorityAndAllVisibleNamespaceCandidateGroupsAreRetained() {
        val (file, declaration) = fixture()
        val library = Namespace(file.namespace.identifier)
        GlobalScope.libNamespaces[library.identifier] = library
        file.namespace.scope.putResolvedAlias("Choice", MCFPPBaseType.Int, forced = true)
        library.scope.putResolvedAlias("Choice", MCFPPBaseType.Float, forced = true)
        val localA = Function("target", declaration.namespace, null)
        val localB = Function("target", declaration.namespace, null)
        val hidden = Function("target", declaration.namespace, null)
        file.namespace.scope.functions["target"] = arrayListOf(localA, localB)
        library.scope.functions["target"] = arrayListOf(hidden)
        val imported = Namespace("fixture.visible")
        val visible = Function("target", imported.identifier, null)
        imported.scope.functions["target"] = arrayListOf(visible)
        GlobalScope.localNamespaces[imported.identifier] = imported
        file.field.importedNamespaceField[imported.identifier] = imported.scope
        val snapshot = DeclarationLexicalSnapshot.capture(declaration)
        assertSame(MCFPPBaseType.Int, snapshot.type(null, "Choice", type("Choice")))
        assertEquals(listOf(localA, localB, visible), snapshot.functions(null, "target"))
        assertEquals(listOf(visible), snapshot.functions(imported.identifier, "target"))
    }

    @Test fun actualFormalIdentitiesAndSourceValueDefaultsAreCopiedWithoutEvaluatingDefaultVar() {
        val (file, _) = fixture()
        val declaration = GenericFunction("generic", file.namespace.identifier, parser("{}").curlBlock())
            .apply { declarationFile = file }
        val syntax = parser("a:int=7").readOnlyParameter()
        val readonly = FunctionParam(MCFPPBaseType.Int, "a", declaration, hasDefault = true, isReadOnly = true)
            .apply {
                defaultContext = syntax.value()
                defaultValue = int(7).constant
            }
        val normal = FunctionParam(MCFPPBaseType.Int, "x", declaration)
        declaration.readOnlyParams.add(readonly)
        declaration.normalParams.add(normal)
        val snapshot = DeclarationLexicalSnapshot.capture(declaration)
        readonly.identifier = "changed"
        declaration.readOnlyParams.clear()
        declaration.normalParams.clear()
        assertEquals(listOf("a", "x"), snapshot.formals.map { it.name })
        assertSame(readonly, snapshot.formals[0].identity)
        assertSame(normal, snapshot.formals[1].identity)
        assertSame(syntax.value(), snapshot.formals[0].defaultSyntax)
        assertEquals(
            CompilerValue.Integral(7),
            payload(
                DeclarationBinding(DeclarationPreparationContext(snapshot))
                    .readonlyValue(snapshot.formals[0].defaultSyntax!!)
            )
        )
    }

    @Test fun precedingReadonlyFactsShadowDeclarationAndCallerNames() {
        val (file, _) = fixture()
        file.field.vars["a"] = MCInt(99, "a")
        val a = DeclarationLexicalSnapshot.Formal("a", Any(), MCFPPBaseType.Int, true)
        val b = DeclarationLexicalSnapshot.Formal("b", Any(), MCFPPBaseType.Int, true)
        // Expression templates are a model/preparation input; parameter source defaults still use ValueContext.
        val pure = binding(file, listOf(a, b), 1, mapOf("a" to int(3)))
        val result = pure.readonly(expression("a + 1"))
        assertEquals(CompilerValue.Integral(4), payload(result))
        assertEquals(setOf("a"), result.dependencies)
    }

    @Test fun selfForwardRuntimeAndCyclicFormalDependenciesCannotFallBackToFreeNames() {
        val (file, _) = fixture()
        for (name in listOf("a", "b", "runtime")) file.field.vars[name] = MCInt(90, name)
        val formals = listOf("a", "b", "runtime").mapIndexed { index, name ->
            DeclarationLexicalSnapshot.Formal(name, Any(), MCFPPBaseType.Int, index < 2)
        }
        for (name in listOf("a", "b", "runtime"))
            failure(PreparationFactCode.FORMAL_DEPENDENCY) { binding(file, formals, 0).readonly(expression(name)) }
        // a -> b -> a is rejected on the first forward edge, before any partial facts are available.
        failure(PreparationFactCode.FORMAL_DEPENDENCY) { binding(file, formals, 0).readonly(expression("b + 1")) }
        assertFailsWith<IllegalArgumentException> { binding(file, formals, 0, mapOf("a" to int(1))) }
    }

    @Test fun rawFreeValueCaptureNeverReadsVariableGettersOrResetsStackIndices() {
        val (file, declaration) = fixture()
        val constant = object : MCInt(7, "freeValue") {
            var armed = false
            override var type: MCFPPType
                get() = if (armed) error("Preparation read Var.type") else MCFPPBaseType.Int
                set(_) {}
        }
        file.field.vars["freeValue"] = constant
        constant.stackIndex = 13
        constant.armed = true
        val snapshot = DeclarationLexicalSnapshot.capture(declaration)
        assertEquals(
            CompilerValue.Integral(7),
            payload(
                DeclarationBinding(DeclarationPreparationContext(snapshot))
                    .readonly(expression("freeValue"))
            )
        )
        assertEquals(13, constant.stackIndex)
        constant.armed = false
        StorageAccess.write(constant, MCInt(9))
        assertEquals(
            CompilerValue.Integral(7),
            payload(
                DeclarationBinding(DeclarationPreparationContext(snapshot))
                    .readonly(expression("freeValue"))
            )
        )
    }

    @Test fun anUnpreparedVariableStillShadowsASameNamedType() {
        val (file, _) = fixture()
        file.field.vars["Collision"] = MCInt("Collision")
        file.field.putResolvedAlias("Collision", MCFPPBaseType.Int, forced = true)
        failure(PreparationFactCode.NON_CLOSED_VALUE) { binding(file).readonly(expression("Collision")) }
    }

    @Test fun floatNegationKeepsExactBitsIncludingNegativeZero() {
        val (file, _) = fixture()
        Project.config.version = "26.3"
        val formals = listOf("a", "b").map { DeclarationLexicalSnapshot.Formal(it, Any(), MCFPPBaseType.Float, true) }
        for (number in listOf(1.25f, 0.0f)) {
            val fact = DeclarationBinding.Bound(
                MCFPPBaseType.Float,
                CompilerValue.Typed(
                    MCFPPBaseType.Float.typeId,
                    CompilerValue.FloatBits(number.toRawBits())
                )
            )
            assertEquals(
                CompilerValue.FloatBits((-number).toRawBits()),
                payload(
                    binding(
                        file,
                        formals,
                        1,
                        mapOf("a" to fact)
                    ).readonly(expression("-a"))
                )
            )
        }
    }

    @Test fun mixedNumericComparisonAndReadonlyPromotionShareSourceConstantFacts() {
        val (file, _) = fixture()
        Project.config.version = "26.3"
        val pure = binding(file)
        val compared = pure.readonly(expression("1 < 1.5f"))
        assertSame(MCFPPBaseType.Bool, compared.type)
        assertEquals(CompilerValue.Bool(true), payload(compared))
        val promoted = pure.constrain(pure.readonly(expression("7")), MCFPPBaseType.Float)
        assertEquals(CompilerValue.FloatBits(7f.toRawBits()), payload(promoted))
        val catalog = SourceTypeCatalog.snapshot(listOf(MCFPPBaseType.Int, MCFPPBaseType.Float, MCFPPBaseType.Bool))
        assertEquals(
            ConstantValue.FloatBits(7f.toRawBits()),
            SourceDefaultBinder().typedLiteral(
                promoted,
                catalog,
                preparationSpan(expression("7"))
            ).constant
        )
        assertFailsWith<SourceBindingFailure> {
            SourceDefaultBinder().typedLiteral(
                DeclarationBinding.Bound(
                    MCFPPBaseType.Float,
                    CompilerValue.Typed(
                        MCFPPBaseType.Float.typeId,
                        CompilerValue.FloatBits(Float.NaN.toRawBits())
                    )
                ),
                catalog,
                SourceSpan.unknown()
            )
        }
    }

    @Test fun int32WrapFloorDivisionBoundsAndShortCircuitMatchTheSharedEvaluator() {
        val (file, _) = fixture()
        val pure = binding(file)
        assertEquals(CompilerValue.Integral(Int.MIN_VALUE.toLong()), payload(pure.readonly(expression("2147483647 + 1"))))
        assertEquals(CompilerValue.Integral(Int.MIN_VALUE.toLong()), payload(pure.readonly(expression("-2147483648"))))
        assertEquals(CompilerValue.Integral(-2), payload(pure.readonly(expression("-3 / 2"))))
        assertEquals(CompilerValue.Bool(false), payload(pure.readonly(expression("false && (1 / 0 == 0)"))))
        failure(PreparationFactCode.UNSUPPORTED_EXPRESSION) { pure.readonly(expression("2147483648")) }
    }

    @Test fun closedNestedSourceListsBecomeSchemaCheckedTypedLiterals() {
        val (file, _) = fixture()
        val fact = binding(file).readonlyValue(value("[[1, 2], [3, 4]]"))
        val inner = MCFPPListType(MCFPPBaseType.Int)
        val outer = MCFPPListType(inner)
        val catalog = SourceTypeCatalog.snapshot(listOf(MCFPPBaseType.Int, inner, outer))
        val literal = SourceDefaultBinder().typedLiteral(fact, catalog, preparationSpan(value("[[1,2],[3,4]]")))
        val rows = assertIs<ConstantValue.Sequence>(literal.constant).elements
        assertEquals(
            listOf(listOf(1L, 2L), listOf(3L, 4L)),
            rows.map { row ->
                assertIs<ConstantValue.Sequence>(row).elements.map { assertIs<ConstantValue.Integral>(it).value }
            }
        )
        assertSame(catalog.ref(outer), literal.type)
        assertIs<BoundExprNode.Literal>(literal.node)
        assertEquals(BoundEffect.PURE, literal.effect)
    }

    @Test fun closedSourceRecordsUseCanonicalFieldSchemasAndRejectMismatchedSchemas() {
        val (file, _) = fixture()
        val syntax = value("{left: 1, right: 2}")
        val fact = binding(file).readonlyValue(syntax)
        val dict = MCFPPDictType(MCFPPBaseType.Int)
        fun catalog(right: MCFPPType) = SourceTypeCatalog.snapshot(
            listOf(MCFPPBaseType.Int, MCFPPBaseType.Bool, dict),
            emptyList(),
            emptyList(),
            mapOf(
                dict.typeId to SourceConstantSchema.Record(
                    mapOf(
                        "left" to MCFPPBaseType.Int.typeId,
                        "right" to right.typeId
                    )
                )
            )
        )
        val literal = SourceDefaultBinder().typedLiteral(fact, catalog(MCFPPBaseType.Int), preparationSpan(syntax))
        assertEquals(
            mapOf("left" to ConstantValue.Integral(1), "right" to ConstantValue.Integral(2)),
            assertIs<ConstantValue.Record>(literal.constant).fields
        )
        assertFailsWith<SourceBindingFailure> { SourceDefaultBinder().typedLiteral(fact, catalog(MCFPPBaseType.Bool), preparationSpan(syntax)) }
    }

    @Test fun recursiveTypeValueProofsAreReboundToExactlyTheProvidedDomain() {
        fixture()
        val list = MCFPPListType(MCFPPConcreteType.Type)
        val fact = DeclarationBinding.Bound(
            list,
            CompilerValue.Typed(
                list.typeId,
                CompilerValue.Sequence(
                    listOf(
                        CompilerValue.Typed(MCFPPConcreteType.Type.typeId, CompilerValue.TypeValue(MCFPPBaseType.Int.typeId))
                    )
                )
            )
        )
        fun domain() = SourceTypeCatalog.snapshot(listOf(list, MCFPPConcreteType.Type, MCFPPBaseType.Int))
        val old = domain()
        val fresh = domain()
        val first = SourceDefaultBinder().typedLiteral(fact, old, SourceSpan.unknown())
        val second = SourceDefaultBinder().typedLiteral(fact, fresh, SourceSpan.unknown())
        val a = assertIs<ConstantValue.TypeValue>(assertIs<ConstantValue.Sequence>(first.constant).elements.single()).type
        val b = assertIs<ConstantValue.TypeValue>(assertIs<ConstantValue.Sequence>(second.constant).elements.single()).type
        assertTrue(old.owns(a))
        assertFalse(fresh.owns(a))
        assertTrue(fresh.owns(b))
        assertFalse(old.owns(b))
        assertFailsWith<SourceBindingFailure> {
            SourceDefaultBinder().typedLiteral(
                fact,
                SourceTypeCatalog.snapshot(listOf(list, MCFPPConcreteType.Type)),
                SourceSpan.unknown()
            )
        }
    }

    @Test fun dynamicCompositesCallsAndPropertyOperatorsFailBeforeAnyCompilerExecution() {
        val (file, declaration) = fixture()
        val runtime = MCInt("runtime")
        file.field.vars["runtime"] = runtime
        val current = Function("caller", "fixture.caller", null)
        current.scope.vars["sentinel"] = MCInt(29, "sentinel")
        current.commands.add(Command("say unchanged"))
        current.runInFunction {
            val commands = current.commands.toList()
            val vars = current.scope.vars.toMap()
            val children = current.child.toList()
            val parents = current.parent.toList()
            val resources = Project.macroFunction.toMap()
            val constants = Project.constants.toMap()
            val flow = StorageAccess.flowSnapshot(current.scope.vars.values.toList())
            val pure = DeclarationBinding(DeclarationPreparationContext(DeclarationLexicalSnapshot.capture(declaration)))
            for (source in listOf("[runtime]", "{entry: runtime}"))
                failure(PreparationFactCode.NON_CLOSED_VALUE) { pure.readonlyValue(value(source)) }
            for (source in listOf("effect()", "[effect()]", "1[changed=effect()]", "(1).value"))
                failure(PreparationFactCode.UNSUPPORTED_EXPRESSION) { pure.readonly(expression(source)) }
            assertSame(current, Function.currFunction)
            assertEquals(commands, current.commands.toList())
            assertEquals(vars, current.scope.vars)
            assertEquals(children, current.child)
            assertEquals(parents, current.parent)
            assertEquals(resources, Project.macroFunction)
            assertEquals(constants, Project.constants)
            assertEquals(flow, StorageAccess.flowSnapshot(current.scope.vars.values.toList()))
            assertEquals(0, Project.errorCount)
        }
    }

    @Test fun unresolvedAliasesAndTemplateWrappersNeverEnterLegacyResolution() {
        val (file, _) = fixture()
        val alias = MCFPPTypeAliasType(type("Missing"))
        file.field.journalTypeAliases["Pending"] = alias
        val pure = binding(file)
        failure(PreparationFactCode.UNPREPARED_TYPE) { pure.type(type("Pending")) }
        assertNull(alias.cachedTarget)
        val owner = top.mcfpp.model.compound.GenericDataTemplate(parser("{}").templateBody(), "Box", file.namespace.identifier)
        file.namespace.scope.genericTemplate["Box" to emptyList()] = owner
        val wrapper = MCFPPGenericDataTemplateType(
            owner,
            arrayListOf(),
            TypeId.Specialized(TypeId.Declaration("template", file.namespace.identifier, "Box"), emptyList())
        )
        val guarded = binding(file, types = mapOf("T" to wrapper))
        failure(PreparationFactCode.UNPREPARED_TYPE) { guarded.type(type("T")) }
        failure(PreparationFactCode.UNPREPARED_TYPE) { guarded.readonly(expression("T")) }
        failure(PreparationFactCode.UNPREPARED_TYPE) { guarded.readonlyValue(value("[T]")) }
        file.field.putResolvedAlias("Wrapped", wrapper, forced = true)
        failure(PreparationFactCode.UNPREPARED_TYPE) { binding(file).readonly(expression("Wrapped")) }
        for ((name, nested) in mapOf(
            "WrappedList" to MCFPPListType(wrapper),
            "WrappedDict" to MCFPPDictType(wrapper),
            "WrappedUnion" to MCFPPUnionType(MCFPPBaseType.Int, wrapper)
        )) {
            file.field.putResolvedAlias(name, nested, forced = true)
            val cached = binding(file, types = mapOf("BoundNested" to nested))
            failure(PreparationFactCode.UNPREPARED_TYPE) { cached.type(type(name)) }
            failure(PreparationFactCode.UNPREPARED_TYPE) { cached.readonly(expression(name)) }
            failure(PreparationFactCode.UNPREPARED_TYPE) { cached.readonlyValue(value("[$name]")) }
            failure(PreparationFactCode.UNPREPARED_TYPE) { cached.readonly(expression("BoundNested")) }
        }
        assertSame(owner, wrapper.checkpointPrivateState().elements.single())
        assertTrue(owner.compiledTemplates.isEmpty())
        assertEquals(0, Project.errorCount)
    }

    @Test fun preparedScalarAndContainerTypeConstantsKeepTheirCanonicalLiteralProofs() {
        val (file, _) = fixture()
        val list = MCFPPListType(MCFPPBaseType.Bool)
        val pure = binding(file, types = mapOf("T" to MCFPPBaseType.Int, "PreparedList" to list))
        assertSame(MCFPPBaseType.Int, pure.type(type("T")))
        assertEquals(CompilerValue.TypeValue(MCFPPBaseType.Int.typeId), payload(pure.readonly(expression("T"))))
        assertEquals(CompilerValue.TypeValue(list.typeId), payload(pure.readonly(expression("PreparedList"))))
        file.field.putResolvedAlias("PreparedAlias", MCFPPBaseType.Float, forced = true)
        assertEquals(
            CompilerValue.TypeValue(MCFPPBaseType.Float.typeId),
            payload(binding(file).readonly(expression("PreparedAlias")))
        )
        val listOfTypes = MCFPPListType(MCFPPConcreteType.Type)
        val catalog = SourceTypeCatalog.snapshot(listOf(MCFPPBaseType.Int, MCFPPConcreteType.Type, listOfTypes))
        val syntax = value("[T]")
        val literal = SourceDefaultBinder().typedLiteral(pure.readonlyValue(syntax), catalog, preparationSpan(syntax))
        val item = assertIs<ConstantValue.TypeValue>(assertIs<ConstantValue.Sequence>(literal.constant).elements.single())
        assertSame(catalog.ref(MCFPPBaseType.Int), item.type)
        val formals = listOf("TypeArg", "NextArg").map {
            DeclarationLexicalSnapshot.Formal(it, Any(), MCFPPConcreteType.Type, true)
        }
        val previous = DeclarationBinding.Bound(
            MCFPPConcreteType.Type,
            CompilerValue.Typed(MCFPPConcreteType.Type.typeId, CompilerValue.TypeValue(MCFPPBaseType.Int.typeId)),
            descriptors = mapOf(MCFPPBaseType.Int.typeId to MCFPPBaseType.Int)
        )
        assertEquals(
            CompilerValue.TypeValue(MCFPPBaseType.Int.typeId),
            payload(binding(file, formals, 1, mapOf("TypeArg" to previous)).readonly(expression("TypeArg")))
        )
        assertEquals(CompilerValue.Integral(7), payload(pure.readonly(expression("7"))))
    }

    @Test fun unknownAliasAndTypeBindingShapesAreRejectedBeforeOpenGetters() {
        val (file, _) = fixture()
        var getterCalls = 0
        val opaque = object : MCFPPType() {
            override val typeId: TypeId get() {
                getterCalls++
                error("Unexpected type identity read")
            }
            override val typeName: String get() {
                getterCalls++
                error("Unexpected type name read")
            }
        }
        val pure = binding(file, types = mapOf("Opaque" to opaque, "Nested" to MCFPPListType(opaque)))
        for (name in listOf("Opaque", "Nested")) {
            failure(PreparationFactCode.UNPREPARED_TYPE) { pure.type(type(name)) }
            failure(PreparationFactCode.UNPREPARED_TYPE) { pure.readonly(expression(name)) }
            failure(PreparationFactCode.UNPREPARED_TYPE) { pure.readonlyValue(value("[$name]")) }
        }
        file.field.putResolvedAlias("OpaqueAlias", opaque, forced = true)
        val union = MCFPPUnionType(MCFPPBaseType.Int, MCFPPBaseType.Bool).apply { types = arrayOf(MCFPPBaseType.Int, opaque) }
        file.field.putResolvedAlias("OpaqueUnion", union, forced = true)
        val aliases = binding(file)
        failure(PreparationFactCode.UNPREPARED_TYPE) { aliases.type(type("OpaqueAlias")) }
        failure(PreparationFactCode.UNPREPARED_TYPE) { aliases.readonly(expression("OpaqueAlias")) }
        failure(PreparationFactCode.UNPREPARED_TYPE) { aliases.readonly(expression("OpaqueUnion")) }
        val cyclic = MCFPPListType(MCFPPBaseType.Int).apply { generic[0] = this }
        failure(PreparationFactCode.UNPREPARED_TYPE) {
            binding(file, types = mapOf("Cyclic" to cyclic)).readonly(expression("Cyclic"))
        }
        assertEquals(0, getterCalls)
        assertEquals(0, Project.errorCount)
    }
}
