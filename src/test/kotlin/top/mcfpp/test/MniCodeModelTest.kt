package top.mcfpp.test

import top.mcfpp.mni.api.*
import java.io.ByteArrayOutputStream
import java.net.URI
import javax.tools.DiagnosticCollector
import javax.tools.FileObject
import javax.tools.ForwardingJavaFileManager
import javax.tools.JavaFileManager
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider
import kotlin.test.*
import kotlin.test.Test

class MniCodeModelTest {
    private val session = SessionId(1)
    private val span = SourceSpan("example.mcfpp", 5, 10, 2, 1, 2, 6)
    private val metadata = CodeMetadata(session, ScopeId(session, 1), Origin(span))
    private val intType = ImmutableTypeRef<IntT>(TypeKey(1, 1), "int", kind = TypeKind.INT)
    private val boolType = ImmutableTypeRef<BoolT>(TypeKey(1, 2), "bool", kind = TypeKind.BOOL)
    private fun literal(value: Int): Expr<IntT> = ImmutableExpr(metadata, intType, ExprNode.Literal(ConstantValue.Integral(value.toLong())))

    @Test fun constantsAreDeeplyImmutableAndDoNotRetainInputCollections() {
        val elements = arrayListOf<ConstantValue>(ConstantValue.Integral(1))
        val sequence = ConstantValue.Sequence(elements)
        val fields = linkedMapOf<String, ConstantValue>("items" to sequence)
        val record = ConstantValue.Record(fields)
        elements.clear()
        fields.clear()
        assertEquals(listOf(ConstantValue.Integral(1)), sequence.elements)
        assertSame(sequence, record.fields["items"])
        assertFailsWith<UnsupportedOperationException> { (sequence.elements as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (record.fields as MutableMap<*, *>).clear() }
        assertEquals(record, ConstantValue.Record(mapOf("items" to ConstantValue.Sequence(listOf(ConstantValue.Integral(1))))))
    }

    @Test fun typeIdentityDoesNotDependOnNamesOrErasedHostGenerics() {
        val sameName = ImmutableTypeRef<IntT>(TypeKey(2, 1), "int", kind = TypeKind.INT)
        val sameIdentity = ImmutableTypeRef<IntT>(TypeKey(1, 1), "alias", kind = TypeKind.INT)
        assertFalse(intType.sameType(sameName))
        assertTrue(intType.sameType(sameIdentity))
        assertEquals(intType, sameIdentity)
        @Suppress("UNCHECKED_CAST")
        val wrongPhantom = boolType as TypeRef<IntT>
        assertFalse(intType.isAssignableFrom(wrongPhantom))
        assertTrue(wrongPhantom.sameType(boolType))
        val supertype = ImmutableTypeRef<IntT>(TypeKey(1, 3), "accepted", assignableFrom = listOf(intType.key))
        assertTrue(supertype.isAssignableFrom(intType))
        assertFalse(intType.isAssignableFrom(supertype))
    }

    @Test fun referenceArgumentsRetainTheOriginalLocationAndAliasing() {
        val local = ImmutableLocal(metadata, intType, SymbolId(session, 1), "health")
        val other = ImmutableLocal(metadata, intType, SymbolId(session, 2), "health")
        val first = CallArgument.ref(local)
        val second = CallArgument.ref(local)
        assertEquals(ParamMode.REF, first.mode())
        assertSame(local, assertIs<ArgumentPayload.Reference>(first.payload).reference)
        assertSame(local, assertIs<ArgumentPayload.Reference>(second.payload).reference)
        assertNotEquals(local.symbol, other.symbol)
        val alias = ImmutableRef(metadata, intType, NodeLocation(local.symbol))
        assertEquals(local.location, alias.location)
        assertNotEquals(local.location, other.location)
        val read = assertIs<ExprNode.Read>((local.load() as ImmutableExpr<*>).node)
        assertSame(local.location, read.location)
        assertSame(local.load(), local.load())
        val value = ImmutableValue(local.load())
        assertSame(value.expr(), value.expr())
        assertEquals(span, first.source())
    }

    @Test fun codeAndSignatureCollectionsAreSnapshotsRatherThanReadOnlyAliases() {
        val local = ImmutableLocal(metadata, intType, SymbolId(session, 1), "n")
        val statements = arrayListOf<Stmt>(ImmutableStmt(metadata, StmtNode.Assign(local, literal(1))))
        val block = ImmutableBlock(metadata, statements)
        val params = arrayListOf(ParameterView.of("n", intType, ParamMode.REF))
        val signature = FunctionSignature.of(params, intType)
        val function = ImmutableFunctionRef(FunctionKey(1, "helper"), "helper", signature, FunctionKind.HELPER, session)
        val arguments = arrayListOf(CallArgument.ref(local))
        val call = ExprNode.Call(function, arguments)
        statements.clear()
        params.clear()
        arguments.clear()
        assertEquals(1, block.statements().size)
        assertEquals(ParamMode.REF, signature.parameters().single().mode())
        assertSame(local, assertIs<ArgumentPayload.Reference>(call.arguments.single().payload).reference)
        assertFailsWith<UnsupportedOperationException> { (block.statements() as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (signature.parameters() as MutableList<*>).clear() }
        assertFailsWith<UnsupportedOperationException> { (call.arguments as MutableList<*>).clear() }
    }

    @Test fun sessionAndContextIdentityCannotBeMixedByAHandleConstructor() {
        val foreign = SessionId(2)
        assertFailsWith<IllegalArgumentException> { CodeMetadata(session, ScopeId(foreign, 1), Origin(span)) }
        assertFailsWith<IllegalArgumentException> {
            CodeMetadata(session, ScopeId(session, 1), Origin(span), ExecutionContextId(foreign, 1))
        }
        assertFailsWith<IllegalArgumentException> { ImmutableLocal(metadata, intType, SymbolId(foreign, 1), "n") }
        assertFailsWith<IllegalArgumentException> { ImmutableRef(metadata, intType, NodeLocation(SymbolId(foreign, 1))) }
        val frames = arrayListOf(ExpansionOriginFrame("macro", span))
        val origin = Origin(span, expansionFrames = frames)
        frames.clear()
        assertEquals("macro", origin.expansionFrames.single().macroName)
        assertFailsWith<UnsupportedOperationException> { (origin.expansionFrames as MutableList<*>).clear() }
    }

    @Test fun constantAccessChecksActualTypeAndReportsTheUserLocation() {
        val integer = ImmutableConst(literal(7), ConstantValue.Integral(7))
        assertEquals(7, integer.intValue())
        val booleanExpr = ImmutableExpr(metadata, boolType, ExprNode.Literal(ConstantValue.Bool(true)))
        assertTrue(ImmutableConst(booleanExpr, ConstantValue.Bool(true)).boolValue())
        val failure = assertFailsWith<CodeModelException> { integer.boolValue() }
        assertEquals(span, failure.source)
        assertTrue(failure.message!!.contains("example.mcfpp:2:1"))
        val oversized = ConstantValue.Integral(Long.MAX_VALUE)
        val oversizedExpr = ImmutableExpr(metadata, intType, ExprNode.Literal(oversized))
        assertFailsWith<CodeModelException> { ImmutableConst(oversizedExpr, oversized).intValue() }
        val local = ImmutableLocal(metadata, intType, SymbolId(session, 1), "dynamic")
        assertFailsWith<CodeModelException> { ImmutableConst(local.load(), ConstantValue.Integral(0)) }
        assertFailsWith<CodeModelException> { ImmutableConst(literal(1), ConstantValue.Integral(2)) }
        @Suppress("UNCHECKED_CAST")
        val erased = booleanExpr as Expr<IntT>
        assertFailsWith<CodeModelException> { ImmutableConst(erased, ConstantValue.Integral(1)).intValue() }
    }

    @Test fun javaCanUseThePublicModelAndModeFactoriesWithoutKotlinFunctionTypes() {
        val compiler = assertNotNull(ToolProvider.getSystemJavaCompiler(), "A JDK is required by the project toolchain")
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val source = object : SimpleJavaFileObject(URI.create("string:///MniModelUse.java"), JavaFileObject.Kind.SOURCE) {
            override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = """
                import top.mcfpp.mni.api.*;
                import java.util.List;
                class MniModelUse {
                    static <T> CallArgument byRef(Ref<T> ref) { return CallArgument.ref(ref); }
                    static <T> CallArgument byValue(Value<T> value) { return CallArgument.value(value.expr()); }
                    static CallArgument byConst(Const<IntT> value) { return CallArgument.constant(value); }
                    static ConstantValue values() { return ConstantValue.sequence(List.of(ConstantValue.integral(1))); }
                    static FunctionSignature<VoidT> signature(TypeRef<VoidT> type, TypeRef<IntT> param) {
                        return FunctionSignature.of(List.of(ParameterView.of("n", param, ParamMode.REF)), type);
                    }
                    static boolean same(TypeRef<?> a, TypeRef<?> b) { return a.sameType(b); }
                }
            """.trimIndent()
        }
        compiler.getStandardFileManager(diagnostics, null, null).use { standard ->
            val manager = object : ForwardingJavaFileManager<JavaFileManager>(standard) {
                override fun getJavaFileForOutput(
                    location: JavaFileManager.Location,
                    className: String,
                    kind: JavaFileObject.Kind,
                    sibling: FileObject?
                ): JavaFileObject = object : SimpleJavaFileObject(URI.create("memory:///" + className.replace('.', '/') + kind.extension), kind) {
                    override fun openOutputStream() = ByteArrayOutputStream()
                }
            }
            val classpath = listOf(Code::class.java, kotlin.Unit::class.java).map {
                java.nio.file.Path.of(it.protectionDomain.codeSource.location.toURI()).toString()
            }.distinct().joinToString(java.io.File.pathSeparator)
            assertTrue(
                compiler.getTask(null, manager, diagnostics, listOf("-proc:none", "-classpath", classpath), null, listOf(source)).call(),
                diagnostics.diagnostics.joinToString("\n")
            )
        }
    }
}
