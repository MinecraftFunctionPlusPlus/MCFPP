package top.mcfpp.doc

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONWriter
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Syntax-only catalog preparation: uses the compiler's declaration/comment collector. */
fun main(args: Array<String>) {
    require(args.size == 3) { "Expected source directory, output file, and package identity" }
    val root = Path.of(args[0])
    val destination = Path.of(args[1])
    val packageId = args[2]
    val errors = arrayListOf<String>()
    val symbols = arrayListOf<DocumentationSymbol>()
    val listener = object : BaseErrorListener() {
        override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int,
                                 charPositionInLine: Int, msg: String?, e: RecognitionException?) {
            errors += "${recognizer?.inputStream?.sourceName}:$line:${charPositionInLine + 1}: $msg"
        }
    }
    Files.list(root).use { files ->
        files.filter { it.toString().endsWith(".mcfpp") }.sorted().forEach { path ->
            val lexer = mcfppLexer(CharStreams.fromPath(path)).apply {
                removeErrorListeners(); addErrorListener(listener)
            }
            val parser = mcfppParser(CommonTokenStream(lexer)).apply {
                removeErrorListeners(); addErrorListener(listener)
            }
            val unit = parser.compilationUnit()
            if (errors.isNotEmpty()) return@forEach
            val namespace = unit.namespaceDeclaration()?.namespacePath()?.text ?: "mcfpp.lang"
            val source = SourceDocumentation(unit, namespace, path.toString())
            for (item in source.declarations.filter { it.access in setOf("public", "protected") }) {
                val id = DocumentationExport.symbolId(packageId, namespace, item.owner, item.name, item.kind, item.parameters, item.readonly, item.receiver)
                symbols += DocumentationSymbol(id, packageId, namespace, item.name, item.kind, item.owner,
                    item.signature, item.access, item.document.markdown, item.document.exportTags(), item.parameters,
                    item.readonly, item.result, item.receiver,
                    DocumentationSource("src/main/mcfpp/lang/${path.fileName}", item.syntax.start.line, item.syntax.start.charPositionInLine + 1))
            }
        }
    }
    check(errors.isEmpty()) { errors.joinToString("\n") }
    val model = JSON.parseObject(JSON.toJSONString(DocumentationModel("MCFPP core API", symbols)))
    model["language"] = "mcfpp"
    model["default"] = true
    Files.createDirectories(destination.parent)
    val temporary = destination.resolveSibling("${destination.fileName}.tmp")
    Files.writeString(temporary, JSON.toJSONString(model, JSONWriter.Feature.PrettyFormat) + "\n")
    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
    println("Exported ${symbols.size} explicit MCFPP declarations to $destination")
}
