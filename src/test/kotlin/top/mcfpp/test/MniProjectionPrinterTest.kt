package top.mcfpp.test

import top.mcfpp.mni.api.*
import top.mcfpp.mni.expand.CodeArena
import top.mcfpp.mni.expand.CodePrinter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MniProjectionPrinterTest {
    private val source = SourceSpan("printer.mcfpp", 1, 8, 1, 1, 1, 9)

    @Test fun printsIndexReadonlyAndCapturedAddressWithTraversalStableNames() {
        val first = render(sessionValue = 91, physicalValue = 700)
        val second = render(sessionValue = 912, physicalValue = 9000)

        assertEquals(first.output, second.output)
        assertTrue(first.output.contains("index read s0"), first.output)
        assertTrue(first.output.contains("capture a0 = s0[0]@c0"), first.output)
        assertTrue(first.output.contains("read address a0[2]@c0 readonly"), first.output)
        assertTrue(first.output.contains("readonly<\"T\":"), first.output)
        assertTrue(first.output.contains("readonly(\"int\"#t1=7, \"float\"#t2=f32(0x3fc00000))"), first.output)
        assertTrue(first.output.contains("f32(0x3fc00000)"), first.output)
        assertEquals(first.output, CodePrinter().print(first.code))
    }

    @Test fun printsSelfAndMutualRecursiveCallbackSignaturesWithStableBackReferences() {
        val self = recursiveCallback(sessionValue = 93, physicalValue = 720, mutual = false)
        val selfText = CodePrinter().print(self)
        val selfDefinition = selfText.substringAfter("sig0(").substringBefore(" ->")
        assertTrue(selfText.contains("sig0("), selfText)
        assertTrue(selfDefinition.startsWith("inline \"CallbackA\""), selfText)
        assertTrue(selfDefinition.endsWith("@sig0)"), selfText)
        assertEquals(selfText, CodePrinter().print(self))
        assertEquals(
            selfText,
            CodePrinter().print(
                recursiveCallback(sessionValue = 930, physicalValue = 7200, mutual = false)
            )
        )

        val mutual = recursiveCallback(sessionValue = 94, physicalValue = 730, mutual = true)
        val mutualText = CodePrinter().print(mutual)
        assertTrue(mutualText.contains("sig0(inline \"CallbackB\""), mutualText)
        assertTrue(mutualText.contains("sig1(inline \"CallbackA\""), mutualText)
        val nestedDefinition = mutualText.substringAfter("sig1(").substringBefore(" ->")
        assertTrue(nestedDefinition.contains("inline \"CallbackA\""), mutualText)
        assertTrue(nestedDefinition.endsWith("@sig0)"), mutualText)
        assertTrue(mutualText.contains("@sig0"), mutualText)
        assertEquals(mutualText, CodePrinter().print(mutual))
        assertEquals(
            mutualText,
            CodePrinter().print(
                recursiveCallback(sessionValue = 940, physicalValue = 7300, mutual = true)
            )
        )
    }

    private data class Rendered(val output: String, val code: Code)

    private fun recursiveCallback(sessionValue: Long, physicalValue: Long, mutual: Boolean): InlineFn<*> {
        val session = SessionId(sessionValue)
        val scope = ScopeId(session, 0)
        val metadata = CodeMetadata(session, scope, Origin(source))
        val intKey = TypeKey(sessionValue, physicalValue + 1)
        val firstKey = TypeKey(sessionValue, physicalValue + 2)
        val secondKey = TypeKey(sessionValue, physicalValue + 3)
        val firstSignature = FrozenInlineSignatureRecord(
            listOf(
                FrozenParameterRecord(
                    if (mutual) "next" else "again",
                    if (mutual) secondKey else firstKey,
                    ParamMode.INLINE
                )
            ),
            intKey
        )
        val records = arrayListOf(
            FrozenTypeRecord(intKey, "int", "builtin:int", TypeKind.INT),
            FrozenTypeRecord(
                firstKey,
                "CallbackA",
                "fixture:CallbackA",
                TypeKind.OTHER,
                inlineSignature = firstSignature
            )
        )
        if (mutual) records.add(
            FrozenTypeRecord(
                secondKey,
                "CallbackB",
                "fixture:CallbackB",
                TypeKind.OTHER,
                inlineSignature = FrozenInlineSignatureRecord(
                    listOf(
                        FrozenParameterRecord("previous", firstKey, ParamMode.INLINE)
                    ),
                    intKey
                )
            )
        )
        val graph = FrozenTypeGraph.freeze(records)
        return ImmutableInlineFn<Any?>(
            metadata,
            graph.type(firstKey).inlineSignature()!!,
            CallbackId(sessionValue, if (mutual) "mutual" else "self"),
            InlineTarget.Declared(FunctionKey(sessionValue, "${if (mutual) "mutual" else "self"}_target"))
        )
    }

    private fun render(sessionValue: Long, physicalValue: Long): Rendered {
        val session = SessionId(sessionValue)
        val scope = ScopeId(session, 0)
        val context = ExecutionContextId(session, physicalValue + 1)
        val metadata = CodeMetadata(session, scope, Origin(source), context)
        val intKey = TypeKey(sessionValue, physicalValue + 1)
        val listKey = TypeKey(sessionValue, physicalValue + 2)
        val floatKey = TypeKey(sessionValue, physicalValue + 3)
        val graph = FrozenTypeGraph.freeze(
            listOf(
                FrozenTypeRecord(
                    intKey,
                    "int",
                    "builtin:int",
                    TypeKind.INT,
                    constant = ConstantSchema.Scalar(ConstantShape.INT32)
                ),
                FrozenTypeRecord(
                    listKey,
                    "List<int>",
                    "builtin:list<builtin:int>",
                    TypeKind.OTHER,
                    index = IndexSchema(intKey, false)
                ),
                FrozenTypeRecord(
                    floatKey,
                    "float",
                    "builtin:float",
                    TypeKind.FLOAT,
                    constant = ConstantSchema.Scalar(ConstantShape.FLOAT32)
                )
            )
        )
        val intType = graph.type(intKey)
        val listType = graph.type(listKey)
        val floatType = graph.type(floatKey)
        val arena = CodeArena(session, scope) { _, _ -> }
        val root = arena.ref(metadata, listType, NodeLocation(arena.freshSymbol(), executionContextId = context, writable = false))
        repeat((physicalValue / 10).toInt()) { arena.freshAddress() }
        val address = arena.freshAddress()
        val capture = arena.stmt(
            metadata,
            StmtNode.CaptureAddress(
                address,
                NodeLocation(root.location.root, listOf(LocationStep.Index(0)), context, writable = false),
                listType
            )
        )
        val zero = arena.expr(metadata, intType, ExprNode.Literal(ConstantValue.Integral(0)))
        val indexed = arena.expr(metadata, intType, ExprNode.Index(root.load(), zero))
        val capturedRead = arena.expr(
            metadata,
            intType,
            ExprNode.Read(
                NodeLocation(
                    root.location.root,
                    listOf(LocationStep.Index(2)),
                    context,
                    writable = false,
                    capturedBase = address
                )
            )
        )
        val function = ImmutableFunctionRef(
            FunctionKey(sessionValue, "consume"),
            "consume",
            FunctionSignature.of(emptyList(), intType),
            FunctionKind.ORDINARY,
            readonlyParameters = listOf(
                ParameterView.of("T", intType, ParamMode.CONST),
                ParameterView.of("scale", floatType, ParamMode.CONST)
            )
        )
        val call = arena.expr(
            metadata,
            intType,
            ExprNode.Call(
                function,
                emptyList(),
                listOf(
                    TypedConstant(intType, ConstantValue.Integral(7), source),
                    TypedConstant(floatType, ConstantValue.FloatBits(1.5f.toRawBits()), source)
                )
            )
        )
        val code = arena.block(
            metadata,
            listOf(
                capture,
                arena.stmt(metadata, StmtNode.Eval(indexed)),
                arena.stmt(metadata, StmtNode.Eval(capturedRead)),
                arena.stmt(metadata, StmtNode.Eval(call))
            )
        )
        val output = CodePrinter().print(code)
        arena.close()
        return Rendered(output, code)
    }
}
