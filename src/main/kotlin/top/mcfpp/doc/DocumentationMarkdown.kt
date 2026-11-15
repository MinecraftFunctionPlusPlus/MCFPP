package top.mcfpp.doc

import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

internal class DocumentationLinks(private val symbols: List<DocumentationSymbol>) {
    fun resolve(context: DocumentationSymbol, reference: String): DocumentationSymbol? {
        val raw = reference.trim().removeSurrounding("[", "]").replace(Regex("\\s+"), "")
        val path = raw.substringBefore('(').substringBefore('<')
        val normal = if ('(' in raw && raw.endsWith(')')) raw.substringAfter('(').dropLast(1) else null
        val readonly = if ('<' in raw.substringBefore('(')) raw.substringAfter('<').substringBeforeLast('>') else null
        val candidates = symbols.filter { symbol ->
            val local = (symbol.owner?.let { "$it." }.orEmpty()) + symbol.name
            val full = "${symbol.namespace}:$local"
            val nameMatch = path == symbol.name || path == local || path == full || path == full.replace(':', '.') || path == "${symbol.receiver}.${symbol.name}"
            nameMatch && (normal == null || normal == symbol.parameters.joinToString(",") { it.type.replace(Regex("\\s+"), "") }) &&
                (readonly == null || readonly == symbol.readonlyParameters.joinToString(",") { it.type.replace(Regex("\\s+"), "") })
        }
        if (':' in path || candidates.any { path == "${it.namespace}.${it.owner?.let { owner -> "$owner." }.orEmpty()}${it.name}" }) return candidates.singleOrNull()
        val member = candidates.filter { it.packageId == context.packageId && it.namespace == context.namespace && it.owner == context.owner }
        if (member.isNotEmpty()) return member.singleOrNull()
        val local = candidates.filter { it.packageId == context.packageId && it.namespace == context.namespace }
        if (local.isNotEmpty()) return local.singleOrNull()
        val imported = symbols.filter { symbol -> context.imports.any { declaration ->
            val target = declaration.substringBefore(" as ")
            val alias = declaration.substringAfter(" as ", "")
            val namespace = target.substringBeforeLast(':')
            val name = target.substringAfterLast(':')
            symbol.namespace == namespace && (name == "*" || name == symbol.name || name == symbol.owner) &&
                (symbol in candidates || alias.isNotEmpty() && (path == alias && symbol.name == name || path.startsWith("$alias.") && symbol.owner == name && path.substringAfter('.') == symbol.name))
        } }
        return imported.singleOrNull()
    }
}

/** Symbol links are recognized in Markdown text nodes, never inside code, HTML or explicit links. */
internal object DocumentationMarkdown {
    private val parser = Parser.builder().build()
    private val renderer = HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build()
    private val symbolReference = Regex("\\[([\\p{L}_][\\p{L}\\p{N}_:.<>?,| &()-]*)]")

    private fun textNodes(root: Node): List<Text> {
        val nodes = arrayListOf<Text>()
        root.accept(object : AbstractVisitor() {
            override fun visit(paragraph: Paragraph) { mergeLiteralRuns(paragraph); visitChildren(paragraph) }
            override fun visit(heading: Heading) { mergeLiteralRuns(heading); visitChildren(heading) }
            override fun visit(link: Link) = Unit
            override fun visit(image: Image) = Unit
            override fun visit(text: Text) { nodes += text }
        })
        return nodes
    }

    // CommonMark parses the <T> inside [function<T>()] as inline HTML. Rejoin these
    // literal runs before recognizing documentation links. Code and explicit links stay separate.
    private fun mergeLiteralRuns(parent: Node) {
        var node = parent.firstChild
        while (node != null) {
            if (node !is Text && node !is HtmlInline) { node = node.next; continue }
            val run = arrayListOf<Node>()
            while (node is Text || node is HtmlInline) { run += node; node = node.next }
            if (run.none { it is HtmlInline }) continue
            val literal = run.joinToString("") { if (it is Text) it.literal else (it as HtmlInline).literal }
            if (symbolReference.containsMatchIn(literal)) {
                run.first().insertBefore(Text(literal))
                run.forEach { it.unlink() }
            }
        }
    }
    private fun references(markdown: String): List<String> = textNodes(parser.parse(markdown)).flatMap { node ->
        symbolReference.findAll(node.literal).map { it.groupValues[1] }.filter { ' ' !in it.substringBefore('(') }.toList()
    }

    fun seeTarget(content: String): String? {
        if (content.trimStart().startsWith("[")) return null
        val value = Regex("^([^\\s(]+(?:\\([^)]*\\))?)").find(content.trim())?.value ?: return null
        return value.takeUnless { it.startsWith("https://") || it.startsWith("http://") }
    }

    fun references(symbol: DocumentationSymbol): List<String> = (references(symbol.markdown) + symbol.tags.flatMap { tag ->
        references(tag.content) + if (tag.name == "see") listOfNotNull(seeTarget(tag.content)) else emptyList()
    }).distinct()

    fun html(markdown: String, symbol: DocumentationSymbol): String {
        val root = parser.parse(markdown)
        val links = symbol.references.associateBy { it.text }
        for (node in textNodes(root)) {
            val matches = symbolReference.findAll(node.literal).filter { links[it.groupValues[1]]?.included == true }.toList()
            if (matches.isEmpty()) continue
            var position = 0
            for (match in matches) {
                if (match.range.first > position) node.insertBefore(Text(node.literal.substring(position, match.range.first)))
                val target = links.getValue(match.groupValues[1])
                node.insertBefore(Link("#${target.target}", null).apply { appendChild(Text(target.text)) })
                position = match.range.last + 1
            }
            if (position < node.literal.length) node.insertBefore(Text(node.literal.substring(position)))
            node.unlink()
        }
        return renderer.render(root)
    }
}
