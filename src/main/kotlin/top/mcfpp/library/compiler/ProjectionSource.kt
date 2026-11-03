package top.mcfpp.library.compiler

import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.misc.Pair

/** A UTF-16 display buffer. It supplies token input streams without invoking a lexer or parser. */
class ProjectionSource(private val label: String = "<library projection>") : CharStream, TokenSource {
    private val buffer = StringBuilder()
    private var cursor = 0
    private var currentLine = 1
    private var currentColumn = 0
    private var factory: TokenFactory<*> = CommonTokenFactory.DEFAULT
    private val emitted = mutableListOf<Token>()
    val text: String get() = buffer.toString()
    val tokens: List<Token> get() = emitted.toList()

    fun append(text: String) {
        buffer.append(text)
        text.forEach { if (it == '\n') { currentLine++; currentColumn = 0 } else currentColumn++ }
    }

    fun token(type: Int, text: String): CommonToken {
        val start = buffer.length
        val line = currentLine
        val column = currentColumn
        append(text)
        return CommonToken(Pair<TokenSource, CharStream>(this, this), type, Token.DEFAULT_CHANNEL,
            start, buffer.length - 1).also {
            it.text = text; it.line = line; it.charPositionInLine = column; it.tokenIndex = emitted.size
            emitted += it
        }
    }

    fun slice(context: ParserRuleContext): String = getText(Interval(context.start.startIndex, context.stop.stopIndex))
    override fun getText(interval: Interval): String {
        if (interval.a < 0 || interval.b < interval.a || interval.a >= buffer.length) return ""
        return buffer.substring(interval.a, minOf(interval.b + 1, buffer.length))
    }
    override fun consume() { check(cursor < buffer.length); cursor++ }
    override fun LA(i: Int): Int {
        if (i == 0) return 0
        val index = if (i < 0) cursor + i else cursor + i - 1
        return if (index in 0 until buffer.length) buffer[index].code else IntStream.EOF
    }
    override fun mark() = -1
    override fun release(marker: Int) = Unit
    override fun index() = cursor
    override fun seek(index: Int) { require(index in 0..buffer.length); cursor = index }
    override fun size() = buffer.length
    override fun getSourceName() = label
    override fun getInputStream(): CharStream = this
    override fun getLine() = currentLine
    override fun getCharPositionInLine() = currentColumn
    override fun setTokenFactory(factory: TokenFactory<*>) { this.factory = factory }
    override fun getTokenFactory(): TokenFactory<*> = factory
    override fun nextToken(): Token = CommonToken(Pair<TokenSource, CharStream>(this, this), Token.EOF,
        Token.DEFAULT_CHANNEL, buffer.length, buffer.length - 1).also { it.text = "<EOF>" }
}
