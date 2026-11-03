package top.mcfpp.library.syntax

/** Language operations in source evaluation order. This model has no parser/session objects. */
sealed interface Expression : SemanticNode {
    data class Literal(override val scope: ScopeId, val constant: ConstantRef) : Expression {
        override val tag: Int get() = 0x0101
    }
    data class Name(override val scope: ScopeId, val use: NameUse) : Expression {
        override val tag: Int get() = 0x0102
    }
    data class This(override val scope: ScopeId, val owner: DeclarationReference) : Expression {
        override val tag: Int get() = 0x0103
    }
    data class Super(override val scope: ScopeId, val owner: DeclarationReference) : Expression {
        override val tag: Int get() = 0x0104
    }
    data class TypeValue(override val scope: ScopeId, val type: TypeSyntax) : Expression {
        override val tag: Int get() = 0x0105
    }
    data class Group(override val scope: ScopeId, val value: Expression?) : Expression {
        override val tag: Int get() = 0x0106
    }
    data class Unary(override val scope: ScopeId, val operator: String, val operand: Expression) : Expression {
        init { require(operator == "!" || operator == "-") }
        override val tag: Int get() = 0x0107
    }
    data class Binary(
        override val scope: ScopeId, val operator: String, val left: Expression, val right: Expression
    ) : Expression {
        init { require(operator in binaryOperators || languageIdentifier.matches(operator)) }
        override val tag: Int get() = 0x0108
    }
    data class Cast(override val scope: ScopeId, val value: Expression, val target: TypeSyntax) : Expression {
        override val tag: Int get() = 0x0109
    }
    data class Member(
        override val scope: ScopeId, val receiver: Expression, val name: String,
        val keyword: MemberKeyword = MemberKeyword.IDENTIFIER,
        val candidates: List<DeclarationReference>? = null
    ) : Expression {
        init { require(name.isNotEmpty()) }
        override val tag: Int get() = 0x010a
    }
    data class Index(override val scope: ScopeId, val receiver: Expression, val index: Expression?) : Expression {
        override val tag: Int get() = 0x010b
    }
    data class PropertyFilter(
        override val scope: ScopeId, val receiver: Expression, val entries: List<PropertyEntry>
    ) : Expression { override val tag: Int get() = 0x010c }
    data class Call(
        override val scope: ScopeId, val callee: Expression, val readonly: List<Expression>,
        val normal: List<Argument>, val trailingLambda: Expression? = null
    ) : Expression {
        init { require(trailingLambda == null || trailingLambda is Lambda) }
        override val tag: Int get() = 0x010d
    }
    /** Body.root owns statements; tail is evaluated separately and must not occur in Body.root. */
    data class Lambda(
        override val scope: ScopeId, val declaration: DeclarationId, val parameters: List<DeclarationId>,
        val explicitParameters: Boolean, val body: BodyId, val tail: Expression?
    ) : Expression {
        init { require(!explicitParameters || parameters.isNotEmpty()) }
        override val tag: Int get() = 0x010e
    }
    data class Range(override val scope: ScopeId, val lower: Expression?, val upper: Expression?) : Expression {
        init { require(lower != null || upper != null) }
        override val tag: Int get() = 0x010f
    }
    data class Coordinate(override val scope: ScopeId, val dimensions: List<Dimension>) : Expression {
        init { require(dimensions.size in 2..3) }
        override val tag: Int get() = 0x0110
    }
    data class Selector(override val scope: ScopeId, val kind: SelectorKind) : Expression {
        override val tag: Int get() = 0x0111
    }
    data class ListValue(override val scope: ScopeId, val elements: List<Expression>) : Expression {
        override val tag: Int get() = 0x0112
    }
    data class Compound(override val scope: ScopeId, val entries: List<CompoundEntry>) : Expression {
        override val tag: Int get() = 0x0113
    }
    data class NbtArray(override val scope: ScopeId, val element: NbtArrayElement, val values: List<ConstantRef>) : Expression {
        override val tag: Int get() = 0x0114
    }
    data class InterpolatedString(override val scope: ScopeId, val parts: List<TextPart>) : Expression {
        override val tag: Int get() = 0x0115
    }
}

private val binaryOperators = setOf("*", "/", "%", "+", "-", "<", ">", "<=", ">=", "==", "!=", "~=", "&&", "||", "|")
private val languageIdentifier = Regex("[\\p{L}_][\\p{L}\\p{Nd}_]*")

data class Argument(val expression: Expression) { val kind: Int get() = 1 }
data class PropertyEntry(val name: String, val value: Expression)
data class CompoundEntry(val key: String, val value: Expression)
data class Dimension(val mode: DimensionMode, val value: ConstantRef?) {
    init { require(mode != DimensionMode.ABSOLUTE || value != null) }
}
sealed interface TextPart {
    data class Text(val value: String) : TextPart
    data class Interpolation(val expression: Expression) : TextPart
}
enum class MemberKeyword(val wireValue: Int) { IDENTIFIER(0), MAP(1), SET(2) }
enum class SelectorKind(val wireValue: Int) { ALL_PLAYERS(1), RANDOM_PLAYER(2), NEAREST_PLAYER(3), SELF(4), ALL_ENTITIES(5) }
enum class DimensionMode(val wireValue: Int) { ABSOLUTE(1), RELATIVE(2), LOCAL(3) }
enum class NbtArrayElement(val wireValue: Int) { BYTE(1), INT(2), LONG(3) }
