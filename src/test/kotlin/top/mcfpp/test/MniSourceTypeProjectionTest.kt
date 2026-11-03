package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.*
import top.mcfpp.model.compound.CompoundData
import top.mcfpp.model.function.Function
import top.mcfpp.type.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MniSourceTypeProjectionTest {
    private val span = SourceSpan("catalog.mcfpp", 10, 18, 2, 3, 2, 11)

    private class PreparedType(
        override val typeId: TypeId,
        override val typeName: String,
        parents: ArrayList<MCFPPType> = arrayListOf()
    ) : MCFPPType(parents) {
        override val instanceData: CompoundData get() = error("Catalog must not query instanceData")
        override fun tryResolve() = error("Catalog must not prepare declarations")
    }

    private fun declaration(
        owner: MCFPPType,
        name: String,
        type: MCFPPType,
        writable: Boolean = true,
        key: String = name
    ): SourceFieldDeclaration = SourceFieldDeclaration(SourceFieldId(owner.typeId, key), owner.typeId, name, type.typeId, writable, span)
    private fun view(receiver: MCFPPType, field: SourceFieldDeclaration) = SourceFieldView(receiver.typeId, field.declaration)
    private fun prepared(types: List<MCFPPType>) = SourceTypeCatalog.snapshot(types, emptyList(), emptyList())
    private fun declared(type: MCFPPType) = SourceCallbackType.Declared(type.typeId)

    @Test fun preparedCallbacksShareTheFrozenGraphAndOnlyKnownCanonicalSignaturesCanBeQueried() {
        val inner = SourceCallbackSignature(
            listOf(SourceCallbackParameter(declared(MCFPPBaseType.Int), ParamMode.REF)),
            declared(MCFPPBaseType.Int)
        )
        val equivalent = SourceCallbackSignature(inner.parameters, inner.returnType)
        val outer = SourceCallbackSignature(
            listOf(SourceCallbackParameter(SourceCallbackType.Callback(inner), ParamMode.INLINE)),
            declared(MCFPPPrivateType.Void)
        )
        val catalog = SourceTypeCatalog.snapshot(
            listOf(MCFPPBaseType.Int, MCFPPBaseType.Bool, MCFPPPrivateType.Void),
            callbacks = listOf(inner, equivalent, outer)
        )
        val int = catalog.ref(MCFPPBaseType.Int)
        val signature = FunctionSignature.of(listOf(ParameterView.of("differentName", int, ParamMode.REF)), int)
        val callback = catalog.inlineType(signature)!!
        assertSame(catalog.graph, (callback as ImmutableTypeRef<*>).graph)
        assertSame(int, callback.inlineSignature()!!.returnType())
        assertSame(callback, catalog.inlineType(callback.inlineSignature()!!))
        val nested = catalog.inlineType(
            FunctionSignature.of(
                listOf(ParameterView.of("f", callback, ParamMode.INLINE)),
                catalog.ref(MCFPPPrivateType.Void)
            )
        )!!
        assertSame(callback, nested.inlineSignature()!!.parameters().single().type())
        assertSame(callback.inlineSignature(), nested.inlineSignature()!!.parameters().single().inlineSignature())
        assertSame(catalog.graph, (nested as ImmutableTypeRef<*>).graph)
        val unknown = FunctionSignature.of(listOf(ParameterView.of("p", int, ParamMode.VALUE)), int)
        repeat(3) { assertNull(catalog.inlineType(unknown)) }
        assertSame(callback, catalog.inlineType(signature))
        val fake = ImmutableTypeRef<Any>((int as ImmutableTypeRef<*>).key, int.name(), kind = int.kind, graph = catalog.graph)
        assertNull(catalog.inlineType(FunctionSignature.of(listOf(ParameterView.of("p", fake, ParamMode.REF)), int)))
        val foreign = SourceTypeCatalog.snapshot(listOf(MCFPPBaseType.Int), callbacks = listOf(inner))
        assertNull(
            catalog.inlineType(
                foreign.inlineType(
                    FunctionSignature.of(
                        listOf(ParameterView.of("p", foreign.ref(MCFPPBaseType.Int), ParamMode.REF)),
                        foreign.ref(MCFPPBaseType.Int)
                    )
                )!!.inlineSignature()!!
            )
        )
        // A valid same-domain callback handle cannot authenticate a conflicting explicit descriptor.
        assertNull(
            catalog.inlineType(
                FunctionSignature.of(
                    listOf(ParameterView.of("f", callback, ParamMode.INLINE, unknown)),
                    catalog.ref(MCFPPPrivateType.Void)
                )
            )
        )
    }

    @Test fun callbackFieldsIndicesConstantsAndDefaultReadsCanFormOneCanonicalRecursiveDomain() {
        val owner = PreparedType(TypeId.fresh(), "Owner")
        val handler = PreparedType(TypeId.fresh(), "Handler")
        val handlers = MCFPPListType(handler)
        val parameters = arrayListOf(
            SourceCallbackParameter(declared(owner), ParamMode.REF),
            SourceCallbackParameter(declared(handler), ParamMode.INLINE)
        )
        val signature = SourceCallbackSignature(parameters, declared(owner))
        val structuralAlias = SourceCallbackSignature(
            listOf(
                SourceCallbackParameter(declared(owner), ParamMode.REF),
                SourceCallbackParameter(SourceCallbackType.Callback(signature), ParamMode.INLINE)
            ),
            declared(owner)
        )
        val field = declaration(owner, "handler", handler)
        val fields = arrayListOf(field)
        val domain = listOf(owner, handler, handlers, MCFPPBaseType.Int, MCFPPPrivateType.Void)
        val catalog = SourceTypeCatalog.snapshot(
            domain,
            fields,
            listOf(view(owner, field)),
            constantSchemas = mapOf(owner.typeId to SourceConstantSchema.Record(mapOf("handler" to handler.typeId))),
            callbacks = listOf(signature, structuralAlias),
            callbackTypes = mapOf(handler.typeId to signature)
        )
        parameters.clear()
        fields.clear()
        val callback = catalog.ref(handler)
        val descriptor = callback.inlineSignature()!!
        assertSame(callback, catalog.field(field.declaration).type())
        assertSame(catalog.ref(owner), descriptor.parameters()[0].type())
        assertSame(callback, descriptor.parameters()[1].type())
        assertSame(descriptor, descriptor.parameters()[1].inlineSignature())
        assertSame(callback, catalog.inlineType(descriptor))
        assertEquals((callback as ImmutableTypeRef<*>).key, catalog.indexSchema(catalog.ref(handlers))!!.element)
        assertEquals(callback.key, assertIs<ConstantSchema.Record>(catalog.constantSchema(catalog.ref(owner))).fields.getValue("handler"))
        val first = SourceSymbol("first", callback, SymbolId(SessionId(0), 201))
        val second = SourceSymbol("second", callback, SymbolId(SessionId(0), 202))
        val function = ImmutableFunctionRef(
            FunctionKey(callback.key.universe, "recursiveDefault"),
            "recursiveDefault",
            FunctionSignature.of(
                listOf(
                    ParameterView.of("first", callback, ParamMode.VALUE),
                    ParameterView.of("second", callback, ParamMode.VALUE)
                ),
                catalog.ref(MCFPPPrivateType.Void)
            ),
            FunctionKind.ORDINARY
        )
        val source = SourceFunction(
            "recursiveDefault",
            function,
            normalDefaultExpressions = mapOf(1 to read(first)),
            normalSymbols = listOf(first, second)
        )
        BindingEnvironment(catalog, emptyList(), listOf(source), listOf(catalog.sourceField(catalog.ref(owner), "handler")!!))
        assertSame(callback, source.normalDefaultExpressions.getValue(1).type)
        assertFailsWith<UnsupportedOperationException> { (descriptor.parameters() as MutableList<*>).clear() }
    }

    @Test fun wholeRepreparationReplacesCallbackFactsWithoutExtendingOrMutatingPublishedDomains() {
        val handler = PreparedType(TypeId.fresh(), "Handler")
        val types = listOf(handler, MCFPPBaseType.Int, MCFPPBaseType.Bool)
        val first = SourceCallbackSignature(listOf(SourceCallbackParameter(declared(MCFPPBaseType.Int))), declared(MCFPPBaseType.Int))
        val second = SourceCallbackSignature(listOf(SourceCallbackParameter(declared(MCFPPBaseType.Bool))), declared(MCFPPBaseType.Int))
        val mapping = linkedMapOf(handler.typeId to first)
        val old = SourceTypeCatalog.snapshot(types, emptyList(), emptyList(), callbackTypes = mapping)
        mapping[handler.typeId] = second
        val fresh = SourceTypeCatalog.snapshot(types, emptyList(), emptyList(), callbackTypes = mapping)
        assertSame(old.ref(MCFPPBaseType.Int), old.ref(handler).inlineSignature()!!.parameters().single().type())
        assertSame(fresh.ref(MCFPPBaseType.Bool), fresh.ref(handler).inlineSignature()!!.parameters().single().type())
        val requested = FunctionSignature.of(listOf(ParameterView.of("p", old.ref(MCFPPBaseType.Bool), ParamMode.VALUE)), old.ref(MCFPPBaseType.Int))
        assertNull(old.inlineType(requested))
        assertSame(
            fresh.ref(handler),
            fresh.inlineType(
                FunctionSignature.of(
                    listOf(ParameterView.of("p", fresh.ref(MCFPPBaseType.Bool), ParamMode.VALUE)),
                    fresh.ref(MCFPPBaseType.Int)
                )
            )
        )
        assertNull(fresh.inlineType(old.ref(handler).inlineSignature()!!))
        assertFalse(old.ref(handler).sameType(fresh.ref(handler)))
    }

    @Test fun incompleteCallbackDomainsAreRejectedBeforeAnyGraphIsPublished() {
        val types = listOf(MCFPPBaseType.Int, MCFPPPrivateType.Void)
        val missing = PreparedType(TypeId.fresh(), "Missing")
        for (signature in listOf(
            SourceCallbackSignature(emptyList(), declared(missing)),
            SourceCallbackSignature(listOf(SourceCallbackParameter(declared(MCFPPPrivateType.Void))), declared(MCFPPBaseType.Int)),
            SourceCallbackSignature(listOf(SourceCallbackParameter(declared(MCFPPBaseType.Int), ParamMode.INLINE)), declared(MCFPPBaseType.Int))
        )) {
            assertFailsWith<IllegalArgumentException> { SourceTypeCatalog.snapshot(types, callbacks = listOf(signature)) }
        }
        assertFailsWith<IllegalArgumentException> {
            SourceTypeCatalog.snapshot(
                types,
                emptyList(),
                emptyList(),
                callbackTypes = mapOf(
                    missing.typeId to
                        SourceCallbackSignature(emptyList(), declared(MCFPPBaseType.Int))
                )
            )
        }
    }

    @Test fun cyclesAndGenericOwnersResolveToTheSameCanonicalFieldAndTypeHandles() {
        val node = PreparedType(TypeId.Declaration("data", "fixture", "Node"), "Node")
        val peer = PreparedType(TypeId.Declaration("data", "fixture", "Peer"), "Peer")
        val head = TypeId.Declaration("data", "fixture", "Box")
        val intBox = PreparedType(TypeId.Applied(head, listOf(MCFPPBaseType.Int.typeId)), "Box<int>")
        val boolBox = PreparedType(TypeId.Applied(head, listOf(MCFPPBaseType.Bool.typeId)), "Box<bool>")
        val next = declaration(node, "next", node)
        val partner = declaration(node, "partner", peer)
        val back = declaration(peer, "back", node)
        val intValue = declaration(intBox, "value", MCFPPBaseType.Int)
        val boolValue = declaration(boolBox, "value", MCFPPBaseType.Bool)
        val fields = arrayListOf(next, partner, back, intValue, boolValue)
        val views = arrayListOf(
            view(node, next),
            view(node, partner),
            view(peer, back),
            view(intBox, intValue),
            view(boolBox, boolValue)
        )
        val catalog = SourceTypeCatalog.snapshot(listOf(node, peer, intBox, boolBox, MCFPPBaseType.Int, MCFPPBaseType.Bool), fields, views)
        fields.clear()
        views.clear()
        val nodeRef = catalog.ref(node)
        assertSame(catalog.graph, (nodeRef as ImmutableTypeRef<*>).graph)
        assertSame(nodeRef, catalog.field(next.declaration).type())
        assertSame(nodeRef, catalog.field(nodeRef, "partner")!!.type().fields().single().type())
        repeat(500) { assertSame(catalog.field(next.declaration), nodeRef.fields().first()) }
        assertNotEquals(
            (catalog.field(intValue.declaration) as ImmutableFieldRef<*>).key,
            (catalog.field(boolValue.declaration) as ImmutableFieldRef<*>).key
        )
        assertSame(catalog.ref(MCFPPBaseType.Int), catalog.field(intValue.declaration).type())
        assertSame(catalog.ref(MCFPPBaseType.Bool), catalog.field(boolValue.declaration).type())
        assertFailsWith<UnsupportedOperationException> { (nodeRef.fields() as MutableList<*>).clear() }
    }

    @Test fun inheritedVisibilityUsesPreparedSelectionAndKeepsOriginalDeclaringOwner() {
        val parent = PreparedType(TypeId.fresh(), "Parent")
        val inherited = PreparedType(TypeId.fresh(), "Inherited", arrayListOf(parent))
        val hidden = PreparedType(TypeId.fresh(), "Hidden", arrayListOf(parent))
        val original = declaration(parent, "value", MCFPPBaseType.Int)
        val privateField = declaration(parent, "privateValue", MCFPPBaseType.Int)
        val replacement = declaration(hidden, "value", MCFPPBaseType.Bool, false)
        val catalog = SourceTypeCatalog.snapshot(
            listOf(parent, inherited, hidden, MCFPPBaseType.Int, MCFPPBaseType.Bool),
            listOf(original, privateField, replacement),
            listOf(
                view(parent, original),
                view(parent, privateField),
                view(inherited, original),
                view(hidden, replacement)
            )
        )
        assertSame(catalog.field(original.declaration), catalog.field(catalog.ref(inherited), "value"))
        assertEquals(
            (catalog.ref(parent) as ImmutableTypeRef<*>).key,
            (catalog.field(catalog.ref(inherited), "value") as ImmutableFieldRef<*>).key.owner
        )
        assertSame(catalog.field(replacement.declaration), catalog.field(catalog.ref(hidden), "value"))
        assertNull(catalog.field(catalog.ref(inherited), "privateValue"))
        assertTrue(catalog.ref(parent).isAssignableFrom(catalog.ref(inherited)))
        val field = catalog.sourceField(catalog.ref(inherited), "value")!!
        val env = BindingEnvironment(catalog, emptyList(), emptyList(), listOf(field))
        assertSame(field, env.field(catalog.ref(inherited), "value"))
        assertNull(env.field(catalog.ref(parent), "value")) // Catalog shape grants no caller capability.
        val receiver = catalog.ref(inherited) as ImmutableTypeRef<*>
        val fakeReceiver = ImmutableTypeRef<Any>(receiver.key, receiver.name(), kind = receiver.kind, graph = receiver.graph)
        assertFalse(catalog.owns(fakeReceiver))
        assertFailsWith<IllegalArgumentException> { env.field(fakeReceiver, "value") }
        val originalRef = field.canonical as ImmutableFieldRef<*>
        val impostor = ImmutableFieldRef(originalRef.key, field.name, field.type, field.writable, field.source)
        assertFailsWith<IllegalArgumentException> {
            BindingEnvironment(catalog, emptyList(), emptyList(), listOf(field.copy(canonical = impostor)))
        }
        assertFailsWith<IllegalArgumentException> {
            BindingEnvironment(catalog, emptyList(), emptyList(), listOf(SourceField(catalog.ref(parent), "value", field.type, true)))
        }
    }

    @Test fun laterCompilerMutationCannotChangeOldFieldsParentsIndexOrConstantSchema() {
        val parent = PreparedType(TypeId.fresh(), "Parent")
        val child = PreparedType(TypeId.fresh(), "Child", arrayListOf(parent))
        val list = MCFPPListType(MCFPPBaseType.Int)
        val original = declaration(child, "value", MCFPPBaseType.Int)
        val mutableProof = linkedMapOf("head" to MCFPPBaseType.Int.typeId, "tail" to child.typeId)
        val proof = SourceConstantSchema.Record(mutableProof)
        val domain = listOf(parent, child, list, MCFPPBaseType.Int, MCFPPBaseType.Bool, MCFPPBaseType.Object)
        val old = SourceTypeCatalog.snapshot(domain, listOf(original), listOf(view(child, original)), mapOf(child.typeId to proof))
        val oldList = old.ref(list)
        mutableProof.clear()
        list.generic[0] = MCFPPBaseType.Bool
        child.parentType.clear()
        val replacement = declaration(child, "value", MCFPPBaseType.Bool)
        val changed = SourceTypeCatalog.snapshot(domain, listOf(replacement), listOf(view(child, replacement)))
        assertTrue(old.ref(parent).isAssignableFrom(old.ref(child)))
        assertFalse(changed.ref(parent).isAssignableFrom(changed.ref(child)))
        assertEquals((old.ref(MCFPPBaseType.Int) as ImmutableTypeRef<*>).key, old.indexSchema(oldList)!!.element)
        assertEquals((changed.ref(MCFPPBaseType.Bool) as ImmutableTypeRef<*>).key, changed.indexSchema(changed.ref(list))!!.element)
        assertSame(old.ref(MCFPPBaseType.Int), old.field(original.declaration).type())
        assertSame(changed.ref(MCFPPBaseType.Bool), changed.field(replacement.declaration).type())
        val schema = assertIs<ConstantSchema.Record>(old.constantSchema(old.ref(child)))
        assertEquals(setOf("head", "tail"), schema.fields.keys)
        assertEquals((old.ref(child) as ImmutableTypeRef<*>).key, schema.fields.getValue("tail"))
        assertFailsWith<UnsupportedOperationException> { (schema.fields as MutableMap<*, *>).clear() }
        assertFalse(old.ref(child).sameType(changed.ref(child)))
        assertFalse(old.owns(changed.ref(child)))
        assertFailsWith<IllegalArgumentException> { old.ref(list) } // Reverse lookup cannot repair a changed TypeId.
    }

    @Test fun schemasAndConversionsComeFromCanonicalFactsRatherThanDisplayNames() {
        val mutable = MCFPPListType(MCFPPBaseType.Int)
        val immutable = MCFPPImmutableListType(MCFPPBaseType.Int)
        val lookalike = PreparedType(TypeId.fresh(), "int")
        val catalog = prepared(
            listOf(
                MCFPPBaseType.Int, MCFPPBaseType.Bool, MCFPPBaseType.Float,
                MCFPPBaseType.String, MCFPPBaseType.Object, MCFPPBaseType.Any, MCFPPPrivateType.Void,
                MCFPPPrivateType.Null, MCFPPConcreteType.Type, MCFPPNBTType.NBT, MCFPPNBTType.Double,
                mutable, immutable, lookalike
            )
        )
        assertEquals(ConstantShape.INT32, assertIs<ConstantSchema.Scalar>(catalog.constantSchema(catalog.ref(MCFPPBaseType.Int))).shape)
        assertEquals(ConstantShape.TYPE, assertIs<ConstantSchema.Scalar>(catalog.constantSchema(catalog.ref(MCFPPConcreteType.Type))).shape)
        assertEquals(ConstantShape.NULL, assertIs<ConstantSchema.Scalar>(catalog.constantSchema(catalog.ref(MCFPPPrivateType.Null))).shape)
        assertEquals(ConstantShape.NBT, assertIs<ConstantSchema.Scalar>(catalog.constantSchema(catalog.ref(MCFPPNBTType.NBT))).shape)
        assertNull(catalog.constantSchema(catalog.ref(lookalike)))
        assertTrue(catalog.indexSchema(catalog.ref(mutable))!!.writable)
        assertFalse(catalog.indexSchema(catalog.ref(immutable))!!.writable)
        assertIs<ConstantSchema.Sequence>(catalog.constantSchema(catalog.ref(immutable)))
        assertNull(catalog.indexSchema(catalog.ref(MCFPPBaseType.Bool)))
        val int = catalog.ref(MCFPPBaseType.Int)
        val float = catalog.ref(MCFPPBaseType.Float)
        assertTrue(catalog.permits(int, float, ConversionKind.IMPLICIT))
        assertTrue(catalog.permits(int, float, ConversionKind.EXPLICIT))
        assertEquals(TypeRelations.Conversion.INT_TO_FLOAT.rank, catalog.conversionRank(int, float))
        assertFalse(catalog.permits(catalog.ref(MCFPPBaseType.Bool), int, ConversionKind.IMPLICIT))
        val fake = ImmutableTypeRef<Any>((int as ImmutableTypeRef<*>).key, "int", kind = TypeKind.INT)
        assertFalse(catalog.owns(fake))
        assertFalse(catalog.permits(fake, float, ConversionKind.EXPLICIT))
        assertFailsWith<IllegalArgumentException> { catalog.indexSchema(fake) }
        assertFailsWith<IllegalArgumentException> { BindingEnvironment(catalog, listOf(SourceSymbol("fake", fake, SymbolId(SessionId(0), 1))), emptyList()) }
    }

    @Test fun incompleteOrAmbiguousPreparedDomainsFailBeforePublishingHandles() {
        val owner = PreparedType(TypeId.fresh(), "Owner")
        val field = declaration(owner, "value", MCFPPBaseType.Int)
        assertFailsWith<IllegalArgumentException> {
            SourceTypeCatalog.snapshot(listOf(owner), listOf(field), listOf(view(owner, field)))
        }
        assertFailsWith<IllegalArgumentException> { prepared(listOf(MCFPPListType(MCFPPBaseType.Int))) }
        assertFailsWith<IllegalArgumentException> {
            SourceTypeCatalog.snapshot(listOf(owner, MCFPPBaseType.Int), listOf(field, field), emptyList())
        }
        val duplicateName = declaration(owner, "value", MCFPPBaseType.Int, key = "different-declaration")
        assertFailsWith<IllegalArgumentException> {
            SourceTypeCatalog.snapshot(
                listOf(owner, MCFPPBaseType.Int),
                listOf(field, duplicateName),
                listOf(view(owner, field), view(owner, duplicateName))
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SourceTypeCatalog.snapshot(
                listOf(owner),
                emptyList(),
                emptyList(),
                mapOf(owner.typeId to SourceConstantSchema.Sequence(MCFPPBaseType.Int.typeId))
            )
        }
    }

    private fun read(symbol: SourceSymbol) = BoundExpr(
        BoundExprNode.Read(
            BoundLocation(symbol, emptyList(), symbol.type, symbol.writable, null, span)
        ),
        symbol.type,
        null,
        BoundEffect.READ,
        span
    )

    @Test fun preparedCatalogCollectsTransitiveParentsBeforePublishingCanonicalHandles() {
        val ancestor = PreparedType(TypeId.fresh(), "Ancestor")
        val parent = PreparedType(TypeId.fresh(), "Parent", arrayListOf(ancestor))
        val child = PreparedType(TypeId.fresh(), "Child", arrayListOf(parent))
        ancestor.parentType = arrayListOf(parent) // Prepared parent cycles terminate during collection.
        val legacy = SourceTypeCatalog.snapshot(listOf(child))
        assertFailsWith<IllegalArgumentException> { legacy.ref(parent) }
        val catalog = prepared(listOf(child))
        assertTrue(catalog.ref(parent).isAssignableFrom(catalog.ref(child)))
        assertTrue(catalog.ref(ancestor).isAssignableFrom(catalog.ref(child)))
        assertSame(catalog.ref(parent), catalog.graph.type((catalog.ref(parent) as ImmutableTypeRef<*>).key))
        child.parentType.clear()
        parent.parentType.clear()
        assertTrue(catalog.ref(ancestor).isAssignableFrom(catalog.ref(child)))
        assertFailsWith<IllegalArgumentException> { prepared(listOf(child, child)) }
    }

    @Test fun nestedDefaultTemplatesFreezePathsCallsCallbackNamesAndSourceOrderBeforeValidation() {
        val list = MCFPPListType(MCFPPBaseType.Int)
        val catalog = SourceTypeCatalog.snapshot(
            listOf(list, MCFPPBaseType.Int, MCFPPPrivateType.Void),
            emptyList(),
            emptyList(),
            callbacks = listOf(
                SourceCallbackSignature(
                    listOf(SourceCallbackParameter(declared(MCFPPBaseType.Int))),
                    declared(MCFPPBaseType.Int)
                )
            )
        )
        val int = catalog.ref(MCFPPBaseType.Int)
        val a = SourceSymbol("a", catalog.ref(list), SymbolId(SessionId(0), 101))
        val b = SourceSymbol("b", int, SymbolId(SessionId(0), 102))
        val zero = BoundExpr(
            BoundExprNode.Literal(ConstantValue.Integral(0)),
            int,
            ConstantValue.Integral(0),
            BoundEffect.PURE,
            span
        )
        val path = arrayListOf<BoundLocationStep>(BoundLocationStep.Index(zero, 0))
        val location = BoundLocation(a, path, int, false, null, span)
        val indexed = BoundExpr(BoundExprNode.Read(location), int, null, BoundEffect.READ, span)
        val callbackNames = arrayListOf("item")
        val callbackSignature = FunctionSignature.of(listOf(ParameterView.of("item", int, ParamMode.VALUE)), int)
        val callbackType = catalog.inlineType(callbackSignature)!!
        val callback = BoundCallArgument.Callback(
            SourceCallbackSyntax(
                callbackNames,
                callbackSignature,
                callbackType,
                "item -> item",
                span
            )
        )
        val value = BoundCallArgument.Value(indexed)
        val reference = BoundCallArgument.Reference(location)
        val readonly = arrayListOf(indexed)
        val arguments = arrayListOf<BoundCallArgument>(value, reference, callback)
        val order = arrayListOf<SourceEvaluationArgument>(
            SourceEvaluationArgument.Readonly(indexed),
            SourceEvaluationArgument.Normal(value),
            SourceEvaluationArgument.Normal(reference),
            SourceEvaluationArgument.Normal(callback)
        )
        val effects = arrayListOf(BoundEffect.READ, BoundEffect.CALL)
        val universe = (int as ImmutableTypeRef<*>).key.universe
        val targetRef = ImmutableFunctionRef(
            FunctionKey(universe, "nested"),
            "nested",
            FunctionSignature.of(
                listOf(
                    ParameterView.of("value", int, ParamMode.VALUE),
                    ParameterView.of("reference", int, ParamMode.REF),
                    ParameterView.of("callback", callbackType, ParamMode.INLINE, callbackSignature)
                ),
                int
            ),
            FunctionKind.NATIVE,
            readonlyParameters = listOf(ParameterView.of("readonly", int, ParamMode.VALUE))
        )
        val target = SourceFunction("nested", targetRef, readonlyParameters = listOf(SourceParameter(int)))
        val sourceCall = SourceCall("nested", target, readonly, arguments, span, order, effects, BoundEffect.CALL)
        val declarationSpan = SourceSpan("defaults.mcfpp", 30, 48, 4, 2, 4, 20)
        val template = BoundExpr(
            BoundExprNode.Convert(
                BoundExpr(
                    BoundExprNode.Call(sourceCall),
                    int,
                    null,
                    BoundEffect.CALL,
                    span
                ),
                int
            ),
            int,
            null,
            BoundEffect.CALL,
            declarationSpan
        )
        val function = ImmutableFunctionRef(
            FunctionKey(universe, "frozen"),
            "frozen",
            FunctionSignature.of(
                listOf(
                    ParameterView.of("a", a.type, ParamMode.VALUE),
                    ParameterView.of("b", int, ParamMode.VALUE)
                ),
                catalog.ref(MCFPPPrivateType.Void)
            ),
            FunctionKind.ORDINARY
        )
        val declaration = SourceFunction("frozen", function, normalDefaultExpressions = mapOf(1 to template), normalSymbols = listOf(a, b))
        BindingEnvironment(catalog, emptyList(), listOf(declaration))

        path[0] = BoundLocationStep.Index(read(b), null)
        val failure = assertFailsWith<SourceBindingFailure> {
            BindingEnvironment(
                catalog,
                emptyList(),
                listOf(
                    SourceFunction(
                        "frozen",
                        function,
                        normalDefaultExpressions = mapOf(1 to template),
                        normalSymbols = listOf(a, b)
                    )
                )
            )
        }
        assertEquals(declarationSpan, failure.source)
        path.clear()
        readonly.clear()
        arguments.clear()
        order.clear()
        effects.clear()
        callbackNames.clear()
        BindingEnvironment(catalog, emptyList(), listOf(declaration))
        val frozen = declaration.normalDefaultExpressions.getValue(1)
        val frozenCall = assertIs<BoundExprNode.Call>(assertIs<BoundExprNode.Convert>(frozen.node).value.node).sourceCall
        assertEquals(declarationSpan, frozen.source)
        assertEquals(span, frozenCall.source)
        assertSame(target, frozenCall.target)
        assertEquals(1, frozenCall.readonlyArguments.size)
        assertEquals(3, frozenCall.arguments.size)
        assertEquals(4, frozenCall.evaluationOrder.size)
        assertEquals(listOf(BoundEffect.READ, BoundEffect.CALL), frozenCall.effectOrder)
        val frozenRead = assertIs<BoundExprNode.Read>(frozenCall.readonlyArguments.single().node).location
        assertSame(a, frozenRead.root)
        assertSame(int, frozenRead.type)
        assertEquals(span, frozenRead.source)
        assertEquals(ConstantValue.Integral(0), assertIs<BoundLocationStep.Index>(frozenRead.path.single()).expression.constant)
        assertSame(frozenRead, assertIs<BoundCallArgument.Reference>(frozenCall.arguments[1]).location)
        assertSame(frozenCall.arguments[0], assertIs<SourceEvaluationArgument.Normal>(frozenCall.evaluationOrder[1]).argument)
        assertSame(frozenCall.readonlyArguments[0], assertIs<SourceEvaluationArgument.Readonly>(frozenCall.evaluationOrder[0]).expression)
        val frozenCallback = assertIs<BoundCallArgument.Callback>(frozenCall.arguments[2]).callback
        assertEquals(listOf("item"), frozenCallback.parameterNames)
        assertSame(callbackType, frozenCallback.callbackType)
        assertFailsWith<UnsupportedOperationException> { (frozenRead.path as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (frozenCall.readonlyArguments as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (frozenCall.arguments as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (frozenCall.evaluationOrder as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (frozenCall.effectOrder as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (frozenCallback.parameterNames as MutableList<*>).clear() }
    }

    @Test fun defaultsRejectCyclesAndForwardReadsHiddenInCallEvaluationOrderWithDeclarationSpans() {
        val catalog = prepared(listOf(MCFPPBaseType.Int, MCFPPPrivateType.Void))
        val int = catalog.ref(MCFPPBaseType.Int)
        val a = SourceSymbol("a", int, SymbolId(SessionId(0), 111))
        val b = SourceSymbol("b", int, SymbolId(SessionId(0), 112))
        val function = ImmutableFunctionRef(
            FunctionKey((int as ImmutableTypeRef<*>).key.universe, "cyclic"),
            "cyclic",
            FunctionSignature.of(
                listOf(
                    ParameterView.of("a", int, ParamMode.VALUE),
                    ParameterView.of("b", int, ParamMode.VALUE)
                ),
                catalog.ref(MCFPPPrivateType.Void)
            ),
            FunctionKind.ORDINARY
        )
        val path = arrayListOf<BoundLocationStep>()
        val cyclic = BoundExpr(BoundExprNode.Read(BoundLocation(a, path, int, false, null, span)), int, null, BoundEffect.READ, span)
        path.add(BoundLocationStep.Index(cyclic, null))
        val cycleFailure = assertFailsWith<SourceBindingFailure> {
            SourceFunction("cyclic", function, normalDefaultExpressions = mapOf(1 to cyclic), normalSymbols = listOf(a, b))
        }
        assertEquals(span, cycleFailure.source)
        val target = SourceFunction(
            "nested",
            ImmutableFunctionRef(
                FunctionKey(int.key.universe, "nested"),
                "nested",
                FunctionSignature.of(emptyList(), int),
                FunctionKind.NATIVE
            )
        )
        val hidden = BoundExpr(
            BoundExprNode.Call(
                SourceCall(
                    "nested",
                    target,
                    emptyList(),
                    emptyList(),
                    span,
                    listOf(SourceEvaluationArgument.Normal(BoundCallArgument.Value(read(b)))),
                    listOf(BoundEffect.CALL),
                    BoundEffect.CALL
                )
            ),
            int,
            null,
            BoundEffect.CALL,
            span
        )
        val forwardFailure = assertFailsWith<SourceBindingFailure> {
            BindingEnvironment(
                catalog,
                emptyList(),
                listOf(
                    SourceFunction(
                        "cyclic",
                        function,
                        normalDefaultExpressions = mapOf(1 to hidden),
                        normalSymbols = listOf(a, b)
                    )
                )
            )
        }
        assertEquals(span, forwardFailure.source)
    }

    @Test fun defaultTemplatesUseFormalIdentityAndRejectSelfOrForwardDependencies() {
        val catalog = prepared(listOf(MCFPPBaseType.Int, MCFPPPrivateType.Void))
        val int = catalog.ref(MCFPPBaseType.Int)
        val a = SourceSymbol("a", int, SymbolId(SessionId(0), 10))
        val b = SourceSymbol("b", int, SymbolId(SessionId(0), 11))
        val parameters = listOf(ParameterView.of("a", int, ParamMode.VALUE), ParameterView.of("b", int, ParamMode.VALUE))
        val function = ImmutableFunctionRef(
            FunctionKey((int as ImmutableTypeRef<*>).key.universe, "defaults"),
            "defaults",
            FunctionSignature.of(parameters, catalog.ref(MCFPPPrivateType.Void)),
            FunctionKind.ORDINARY
        )
        val formalInputs = arrayListOf(a, b)
        val declaration = SourceFunction("defaults", function, normalDefaultExpressions = mapOf(1 to read(a)), normalSymbols = formalInputs)
        formalInputs.clear()
        BindingEnvironment(catalog, listOf(SourceSymbol("a", int, SymbolId(SessionId(0), 90))), listOf(declaration))
        assertSame(a, declaration.normalSymbols[0])
        assertFailsWith<UnsupportedOperationException> { (declaration.normalSymbols as MutableList<*>).clear() }
        for ((index, expression) in listOf(0 to read(b), 1 to read(b))) {
            val failure = assertFailsWith<SourceBindingFailure> {
                BindingEnvironment(
                    catalog,
                    emptyList(),
                    listOf(
                        SourceFunction(
                            "defaults",
                            function,
                            normalDefaultExpressions = mapOf(index to expression),
                            normalSymbols = listOf(a, b)
                        )
                    )
                )
            }
            assertEquals(span, failure.source)
        }
        assertFailsWith<IllegalArgumentException> {
            BindingEnvironment(
                catalog,
                emptyList(),
                listOf(
                    SourceFunction(
                        "defaults",
                        function,
                        normalDefaultExpressions = mapOf(1 to read(a))
                    )
                )
            )
        }
        assertFailsWith<IllegalArgumentException> {
            BindingEnvironment(
                catalog,
                emptyList(),
                listOf(
                    SourceFunction(
                        "defaults",
                        function,
                        normalSymbols = listOf(a, b.copy(symbol = a.symbol))
                    )
                )
            )
        }
        val inconsistent = a.copy(writable = true)
        assertFailsWith<SourceBindingFailure> {
            BindingEnvironment(
                catalog,
                emptyList(),
                listOf(
                    SourceFunction(
                        "defaults",
                        function,
                        normalDefaultExpressions = mapOf(1 to read(inconsistent)),
                        normalSymbols = listOf(a, b)
                    )
                )
            )
        }
    }

    @Test fun readonlyDefaultsCannotDependOnNormalFormalsAndFunctionMetadataCannotLeakForeignTypes() {
        val catalog = prepared(listOf(MCFPPBaseType.Int, MCFPPPrivateType.Void))
        val int = catalog.ref(MCFPPBaseType.Int)
        val r = SourceSymbol("r", int, SymbolId(SessionId(0), 20))
        val n = SourceSymbol("n", int, SymbolId(SessionId(0), 21))
        val readonly = listOf(ParameterView.of("r", int, ParamMode.VALUE))
        val function = ImmutableFunctionRef(
            FunctionKey((int as ImmutableTypeRef<*>).key.universe, "readonly"),
            "readonly",
            FunctionSignature.of(listOf(ParameterView.of("n", int, ParamMode.VALUE)), catalog.ref(MCFPPPrivateType.Void)),
            FunctionKind.NATIVE,
            readonlyParameters = readonly
        )
        val value = BoundExpr(BoundExprNode.Literal(ConstantValue.Integral(1)), int, ConstantValue.Integral(1), BoundEffect.PURE, span)
        BindingEnvironment(
            catalog,
            emptyList(),
            listOf(
                SourceFunction(
                    "readonly",
                    function,
                    readonlyParameters = listOf(SourceParameter(int)),
                    readonlyDefaultExpressions = mapOf(0 to value),
                    normalDefaultExpressions = mapOf(0 to read(r)),
                    readonlySymbols = listOf(r),
                    normalSymbols = listOf(n)
                )
            )
        )
        val failure = assertFailsWith<SourceBindingFailure> {
            BindingEnvironment(
                catalog,
                emptyList(),
                listOf(
                    SourceFunction(
                        "readonly",
                        function,
                        readonlyParameters = listOf(SourceParameter(int)),
                        readonlyDefaultExpressions = mapOf(0 to read(n)),
                        readonlySymbols = listOf(r),
                        normalSymbols = listOf(n)
                    )
                )
            )
        }
        assertEquals(span, failure.source)
        val foreign = prepared(listOf(MCFPPBaseType.Int)).ref(MCFPPBaseType.Int)
        val poisoned = ImmutableFunctionRef(
            FunctionKey(int.key.universe, "foreign"),
            "foreign",
            FunctionSignature.of(emptyList(), catalog.ref(MCFPPPrivateType.Void)),
            FunctionKind.NATIVE,
            readonlyParameters = listOf(ParameterView.of("foreign", foreign, ParamMode.VALUE))
        )
        assertFailsWith<IllegalArgumentException> { BindingEnvironment(catalog, emptyList(), listOf(SourceFunction("foreign", poisoned))) }
    }

    @Test fun preparingCatalogAndDefaultsDoesNotExecuteCompilerGettersCallsOrCommands() {
        Project.init()
        val current = Function("catalog_sentinel", "fixture", null)
        current.runInFunction {
            val commands = current.commands.toList()
            val variables = current.scope.allVars.toList()
            val children = current.child.toList()
            val owner = PreparedType(TypeId.fresh(), "NoGetter")
            val field = declaration(owner, "next", owner)
            val catalog = SourceTypeCatalog.snapshot(
                listOf(owner, MCFPPBaseType.Int, MCFPPPrivateType.Void),
                listOf(field),
                listOf(view(owner, field))
            )
            val int = catalog.ref(MCFPPBaseType.Int)
            val callback = ImmutableFunctionRef(
                FunctionKey((int as ImmutableTypeRef<*>).key.universe, "effect"),
                "effect",
                FunctionSignature.of(emptyList(), int),
                FunctionKind.NATIVE
            )
            val target = SourceFunction("effect", callback, BoundEffect.UNKNOWN)
            val call = SourceCall("effect", target, emptyList(), emptyList(), span, emptyList(), listOf(BoundEffect.UNKNOWN), BoundEffect.UNKNOWN)
            val expression = BoundExpr(BoundExprNode.Call(call), int, null, BoundEffect.UNKNOWN, span)
            val parameter = SourceSymbol("p", int, SymbolId(SessionId(0), 30))
            val function = ImmutableFunctionRef(
                FunctionKey(int.key.universe, "consumer"),
                "consumer",
                FunctionSignature.of(listOf(ParameterView.of("p", int, ParamMode.VALUE)), catalog.ref(MCFPPPrivateType.Void)),
                FunctionKind.ORDINARY
            )
            BindingEnvironment(
                catalog,
                emptyList(),
                listOf(
                    SourceFunction(
                        "consumer",
                        function,
                        normalDefaultExpressions = mapOf(0 to expression),
                        normalSymbols = listOf(parameter)
                    )
                )
            )
            assertSame(current, Function.currFunction)
            assertEquals(commands, current.commands.toList())
            assertEquals(variables, current.scope.allVars.toList())
            assertEquals(children, current.child.toList())
        }
    }
}
