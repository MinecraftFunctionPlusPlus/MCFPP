package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.analysis.SymbolId
import top.mcfpp.analysis.TypeKnowledge
import top.mcfpp.analysis.ValueFacts
import top.mcfpp.analysis.ValueKnowledge
import top.mcfpp.analysis.lambda.LambdaCaptureRules
import top.mcfpp.analysis.lambda.LambdaReferenceKind
import top.mcfpp.core.lang.LambdaCapture
import top.mcfpp.core.lang.LambdaCaptureDiagnosticCode
import top.mcfpp.core.lang.LambdaCaptureKind
import top.mcfpp.core.lang.LambdaCaptureResult
import top.mcfpp.core.lang.LambdaCopyContract
import top.mcfpp.core.lang.LambdaEnvironment
import top.mcfpp.core.lang.MCAny
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.Var
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPFunctionType
import top.mcfpp.type.MCFPPListType
import top.mcfpp.type.MCFPPPrivateType
import top.mcfpp.type.MCFPPType
import top.mcfpp.type.MCFPPUnionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LambdaCaptureRulesTest {

    private fun intValue(value: Long) =
        CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(value))

    /** A declared, mutable local value with a stable lexical symbol. */
    private fun declaredInt(value: Long, name: String): MCInt {
        val local = assertIs<MCInt>(StorageAccess.literal(MCFPPBaseType.Int, CompilerValue.Integral(value), name))
        local.bindDeclaration(name)
        return local
    }

    /** Test-only fact update; no commands and no second value model. */
    private fun rewriteInt(value: Var<*>, next: Long) {
        val binding = StorageAccess.ensure(value)
        binding.data.write(binding.place, ValueFacts(
            TypeKnowledge.Exact(MCFPPBaseType.Int.typeId),
            ValueKnowledge.Constant(intValue(next))
        ))
    }

    private fun captured(result: LambdaCaptureResult): LambdaCapture {
        val captured = assertIs<LambdaCaptureResult.Captured>(result)
        return captured.capture
    }

    @Test
    fun scalarCaptureKeepsTheCreationTimeValueAndIsNotASharedBox() {
        val source = declaredInt(1, "counter")
        val first = captured(LambdaCaptureRules.capture(source))

        assertEquals(LambdaCaptureKind.CREATION_TIME_VALUE, first.kind)
        assertEquals(LambdaCopyContract.SCALAR_SNAPSHOT, first.contract)
        assertTrue(first.isCreationTimeValue)
        assertFalse(first.isGlobalRead)
        assertNull(first.owned)
        assertNull(first.global)
        assertEquals(intValue(1), first.currentSnapshot())

        // Later writes to the outer local must not change the captured value.
        rewriteInt(source, 9)
        assertEquals(intValue(9), StorageAccess.snapshot(source))
        assertEquals(intValue(1), first.currentSnapshot())

        // A second lambda evaluated later owns its own snapshot, not a shared mutable box.
        val second = captured(LambdaCaptureRules.capture(source))
        assertEquals(intValue(9), second.currentSnapshot())
        assertNotEquals(first.currentSnapshot(), second.currentSnapshot())
    }

    @Test
    fun globalCaptureReadsTheGlobalDeclarationAndNeverFreezesIt() {
        val source = declaredInt(1, "globalCounter")
        source.isStatic = true
        val capture = captured(LambdaCaptureRules.capture(source))

        assertEquals(LambdaCaptureKind.GLOBAL_READ, capture.kind)
        assertTrue(capture.isGlobalRead)
        assertNull(capture.snapshot)
        assertNull(capture.owned)
        assertSame(source, capture.global)
        assertEquals(intValue(1), capture.currentSnapshot())

        rewriteInt(source, 12)
        assertEquals(intValue(12), capture.currentSnapshot())
        assertNull(LambdaCaptureRules.writeDiagnostic(capture))
        assertNull(LambdaEnvironment.of(listOf(capture)).diagnosticForWrite(capture.symbol))
    }

    @Test
    fun captureIdentityIsTheLexicalSymbolNotTheSpelling() {
        val first = declaredInt(1, "sameName")
        val second = declaredInt(2, "sameName")
        val firstCapture = captured(LambdaCaptureRules.capture(first))
        val secondCapture = captured(LambdaCaptureRules.capture(second))

        assertNotEquals(firstCapture.symbol, secondCapture.symbol)
        assertEquals(first.symbol!!.id, firstCapture.symbol)
        assertEquals(second.symbol!!.id, secondCapture.symbol)

        val environment = LambdaEnvironment.of(listOf(firstCapture, secondCapture))
        assertSame(firstCapture, environment[first.symbol!!.id])
        assertSame(secondCapture, environment[second.symbol!!.id])

        // Resolving a same-named binding from another scope is an identity error, not a
        // reason to silently use that value.
        val mismatch = assertIs<LambdaCaptureResult.Rejected>(
            LambdaCaptureRules.capture(first, second.symbol))
        assertEquals(LambdaCaptureDiagnosticCode.IDENTITY_MISMATCH, mismatch.diagnostic.code)
    }

    @Test
    fun captureWritesAreDiagnosedAsReadOnly() {
        val capture = captured(LambdaCaptureRules.capture(declaredInt(3, "fixed")))
        val diagnostic = assertNotNull(LambdaCaptureRules.writeDiagnostic(capture))

        assertEquals(LambdaCaptureDiagnosticCode.CAPTURE_WRITE_READONLY, diagnostic.code)
        assertTrue(diagnostic.message.contains("read-only"))
        assertTrue(diagnostic.message.contains("fold"))

        val environment = LambdaEnvironment.of(listOf(capture))
        assertEquals(diagnostic.code, assertNotNull(environment.diagnosticForWrite(capture.symbol)).code)
        assertNull(environment.diagnosticForWrite(SymbolId.fresh()))

        val before = Project.errorCount
        diagnostic.report()
        assertEquals(before + 1, Project.errorCount)
    }

    @Test
    fun uninitializedAndErasedValuesGetConcreteCopyDiagnostics() {
        val unset = MCInt("unset")
        StorageAccess.ensure(unset)
        val uninitialized = assertIs<LambdaCaptureResult.Rejected>(LambdaCaptureRules.capture(unset))
        assertEquals(LambdaCaptureDiagnosticCode.UNINITIALIZED_SOURCE, uninitialized.diagnostic.code)

        val erased = MCAny("erased")
        StorageAccess.bindIncomingParameter(erased)
        val noContract = assertIs<LambdaCaptureResult.Rejected>(LambdaCaptureRules.capture(erased))
        assertEquals(LambdaCaptureDiagnosticCode.NO_COPY_CONTRACT, noContract.diagnostic.code)
        assertTrue(noContract.diagnostic.message.contains("erased"))
        assertTrue(noContract.diagnostic.message.contains("actual-type"))
    }

    @Test
    fun valueFamiliesExposeTheirExistingCopyContract() {
        assertEquals(LambdaCopyContract.SCALAR_SNAPSHOT, LambdaCaptureRules.contractFor(MCFPPBaseType.Int))
        assertEquals(LambdaCopyContract.SCALAR_SNAPSHOT, LambdaCaptureRules.contractFor(MCFPPBaseType.String))
        assertEquals(LambdaCopyContract.SCALAR_SNAPSHOT,
            LambdaCaptureRules.contractFor(MCFPPUnionType(MCFPPBaseType.Int, MCFPPBaseType.String)))
        assertEquals(LambdaCopyContract.COMPILER_VALUE_SNAPSHOT, LambdaCaptureRules.contractFor(MCFPPBaseType.Pos3))
        assertEquals(LambdaCopyContract.RUNTIME_REPRESENTATION_COPY,
            LambdaCaptureRules.contractFor(MCFPPListType(MCFPPBaseType.Int)))
        assertEquals(LambdaCopyContract.COMPILER_VALUE_SNAPSHOT,
            LambdaCaptureRules.contractFor(MCFPPListType(MCFPPBaseType.Pos3)))
        assertEquals(LambdaCopyContract.NOT_COPYABLE, LambdaCaptureRules.contractFor(MCFPPBaseType.Any))
        assertEquals(LambdaCopyContract.RUNTIME_REPRESENTATION_COPY,
            LambdaCaptureRules.contractFor(MCFPPFunctionType(listOf(MCFPPBaseType.Int), MCFPPPrivateType.Void)))
        assertEquals(LambdaCopyContract.NOT_COPYABLE,
            LambdaCaptureRules.contractFor(MCFPPPrivateType.StaticMemberViewType))

        // Ordinary copies never claim an arbitrary recursive deep copy.
        assertFalse(LambdaCopyContract.RUNTIME_REPRESENTATION_COPY.arbitraryDeepCopy)
        assertFalse(LambdaCopyContract.SCALAR_SNAPSHOT.arbitraryDeepCopy)
        assertTrue(LambdaCopyContract.RUNTIME_REPRESENTATION_COPY.independentValue)
    }

    @Test
    fun typesWithoutAnExplicitCopyContractAreRejectedByDefault() {
        // Runtime representation alone is not a copy contract: an unrecognized type must not
        // silently inherit RUNTIME_REPRESENTATION_COPY.
        val opaque = object : MCFPPType() {
            override val typeName get() = "opaque"
        }
        assertTrue(opaque.hasRuntimeRepresentation)
        assertEquals(LambdaCopyContract.NOT_COPYABLE, LambdaCaptureRules.contractFor(opaque))
        assertEquals(LambdaCopyContract.NOT_COPYABLE,
            LambdaCaptureRules.contractFor(MCFPPUnionType(MCFPPBaseType.Int, opaque)))
    }

    @Test
    fun scoreAndNbtReferenceCapturesAreExplicitlyDeferred() {
        for (kind in LambdaReferenceKind.values()) {
            val rejected = LambdaCaptureRules.captureReference(kind, "target", MCFPPBaseType.Int)
            assertEquals(LambdaCaptureDiagnosticCode.REFERENCE_CAPTURE_DEFERRED, rejected.diagnostic.code)
            assertTrue(rejected.diagnostic.message.contains(kind.displayName))
            assertTrue(rejected.diagnostic.message.contains("deferred"))
        }
    }
}
