package top.mcfpp.test.library

import top.mcfpp.library.format.CompoundCodec
import top.mcfpp.library.format.LibraryFormatCode
import top.mcfpp.library.format.LibraryFormatException
import top.mcfpp.library.format.LibraryModelCodec
import top.mcfpp.library.format.LibraryWire
import top.mcfpp.library.format.NativeCodec
import top.mcfpp.library.model.CompoundDeclaration
import top.mcfpp.library.model.DeclarationAccess
import top.mcfpp.library.model.FunctionDeclaration
import top.mcfpp.library.model.LibraryCompatibility
import top.mcfpp.library.model.LibraryType
import top.mcfpp.library.syntax.ConstantValue
import top.mcfpp.library.syntax.Expression
import top.mcfpp.library.syntax.PackageKey
import top.mcfpp.library.syntax.Statement
import top.mcfpp.library.syntax.TextPart
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Frozen production output: these tests never compile source or regenerate expected bytes. */
class LibraryPublishedFixtureTest {
    private val codec = LibraryModelCodec(listOf(CompoundCodec, NativeCodec))

    @Test
    fun productionV1BytesRetainPublicationTargetPrivateDeclarationsAndSemanticBodies() {
        val bytes = fixture("bin.mclib", "f3b28effbe2722d7c0c4dade5280b90fc35fbcc2cf800f7d9e386984ae5d27c9")
        assertEquals(1L, LibraryWire.decode(bytes).formatVersion)
        val model = codec.decode(bytes, LibraryCompatibility(minecraftVersion = "26.3"))
        assertEquals(PackageKey("fixture:semantic-lib", "1.0.0"), model.manifest.library)
        assertEquals("mcfpp", model.manifest.classifier)
        assertEquals("26.3", model.manifest.target.minecraftVersion)
        assertEquals("mcfpp.source/1", model.manifest.semantics)
        assertEquals("mcfpp.runtime/1", model.manifest.runtimeAbi)
        assertTrue(model.localReferencesValidated)

        val box = model.declarations.declarations.filterIsInstance<CompoundDeclaration>()
            .single { it.common.name == "MathBox" }
        val members = box.members.map(model.declarations::declaration)
        val hidden = members.filterIsInstance<FunctionDeclaration>().single { it.common.name == "hidden" }
        assertEquals(DeclarationAccess.PRIVATE, hidden.common.access)
        assertTrue(members.filterIsInstance<FunctionDeclaration>().any { it.common.name == "add" })
        val answer = model.declarations.declarations.filterIsInstance<FunctionDeclaration>()
            .single { it.common.name == "answer" }
        assertEquals(DeclarationAccess.PUBLIC, answer.common.access)
        assertEquals(LibraryType.Builtin("int"), model.tables.type(assertNotNull(answer.result)))
        assertEquals("value", model.declarations.declaration(answer.normal.single()).common.name)

        val hiddenBody = model.syntax.body(assertNotNull(hidden.body))
        val block = assertIs<Statement.Block>(model.syntax.node(hiddenBody.root))
        val command = block.statements.filterIsInstance<Statement.Command>().single()
        assertTrue(command.parts.filterIsInstance<TextPart.Text>().joinToString("") { it.value }
            .contains("say installed-private-body"))
        val returned = block.statements.filterIsInstance<Statement.Return>().single()
        val addition = assertIs<Expression.Binary>(returned.value)
        assertEquals("+", addition.operator)
        val one = assertIs<Expression.Literal>(addition.right)
        val typedOne = assertIs<ConstantValue.Typed>(model.tables.constant(one.constant))
        assertEquals(LibraryType.Builtin("int"), model.tables.type(typedOne.type))
        assertEquals(ConstantValue.Integral(1), model.tables.constant(typedOne.value))

        val answerBody = model.syntax.body(assertNotNull(answer.body))
        val answerBlock = assertIs<Statement.Block>(model.syntax.node(answerBody.root))
        assertTrue(answerBlock.statements.any { it is Statement.Local })
        assertIs<Expression.Call>(answerBlock.statements.filterIsInstance<Statement.Return>().single().value)
    }

    @Test
    fun frozenFutureFormatIsRejectedByTheActualModelReaderWithUpgradeGuidance() {
        val future = fixture("future-v2.mclib", "610ab256647b055bcc8fb24ecdab97aa0c63077df510dbffc8d3280cee39d9a9")
        val failure = assertFailsWith<LibraryFormatException> { codec.decode(future) }
        assertEquals(LibraryFormatCode.NEWER, failure.code)
        assertEquals(8L, failure.byteOffset)
        assertTrue(failure.detail.contains("2"), failure.message)
        assertTrue(failure.detail.contains("upgrade"), failure.message)
    }

    private fun fixture(name: String, expectedSha256: String): ByteArray {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/library/published/semantic-lib-v1/$name"))
            .use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(expectedSha256, digest, "The committed fixture bytes changed: $name")
        return bytes
    }
}
