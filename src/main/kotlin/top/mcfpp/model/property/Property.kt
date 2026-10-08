package top.mcfpp.model.property

import top.mcfpp.core.lang.UnknownVar
import top.mcfpp.core.lang.Var
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.model.CanSelectMember
import top.mcfpp.model.Member
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.util.LogProcessor

/**
 * 一个属性。包含了一个getter和一个setter，同时包装了属性所对应的字段。
 *
 * @param accessor 属性的getter。若为空，则此属性不可读。
 * @param mutator 属性的setter。若为空，则此属性不可写。
 *
 * @see AbstractAccessor
 * @see AbstractMutator
 */
data class Property(val identifier: String, val accessor: AbstractAccessor?, val mutator: AbstractMutator?): Member {

    override var isFinal: Boolean = false

    var parent: Var<*>? = null

    var declaredParentTemplate: DataTemplate? = null

    override var accessModifier: Member.AccessModifier = Member.AccessModifier.PUBLIC

    override var isStatic: Boolean = true

    var isInherited: Boolean = false

    override fun parentTemplate(): DataTemplate? {
        if(parent?.type is MCFPPDataTemplateType){
            return (parent?.type as MCFPPDataTemplateType).template
        }
        return null
    }

    /**
     * 执行这个属性的getter
     *
     * @param caller 这个属性所在的对象
     *
     * @return 这个属性的值
     *
     * @see top.mcfpp.antlr.MCFPPExprVisitor.visitRightVarExpression
     */
    fun getter(caller: CanSelectMember, field: Var<*>): Var<*> {
        if(accessor != null){
            return accessor.getter(caller, field)
        }
        LogProcessor.error("Property ${field.identifier} does not have a getter")
        return UnknownVar(field.identifier)
    }

    /**
     * 执行这个属性的setter
     *
     * @param caller 这个属性所在的对象
     * @param b 要设置的值
     *
     * @return 执行完毕setter操作后的值
     */
    fun setter(caller: CanSelectMember, field: Var<*>, b: Var<*>): Var<*> {
        if(mutator != null){
            return mutator.setter(caller, field, b)
        }else{
            LogProcessor.error("Property ${field.identifier} does not have a setter")
            return UnknownVar(field.identifier)
        }
    }

    fun clone(): Property {
        return Property(identifier , accessor, mutator)
    }

    companion object {
        /** Bind declared accessors without evaluating their bodies or initializer expressions. */
        fun fromContext(ctx: mcfppParser.AccessorContext?, field: Var<*>, template: DataTemplate): Property {
            if (ctx == null) return buildSimpleProperty(field)
            val getter = ctx.getter()?.let { declaration ->
                when {
                    declaration.javaRefer() != null && !languageReference(declaration.javaRefer().text, field, template) && nativeReference(declaration.javaRefer().text, field.identifier, true) -> NativeAccessor(declaration.javaRefer().text, template, field)
                    declaration.javaRefer() != null || declaration.expression() != null -> FunctionAccessor(field.clone(), template,
                        expressionBody(declaration, "return ${source(declaration.expression() ?: declaration.javaRefer())};")).apply {
                        template.scope.addFunction(function, false)
                    }
                    declaration.curlBlock() != null -> FunctionAccessor(field.clone(), template, declaration.curlBlock()).apply {
                        template.scope.addFunction(function, false)
                    }
                    else -> SimpleAccessor()
                }
            }
            val setter = ctx.setter()?.let { declaration ->
                when {
                    declaration.javaRefer() != null && !languageReference(declaration.javaRefer().text, field, template) && nativeReference(declaration.javaRefer().text, field.identifier, false) -> NativeMutator(declaration.javaRefer().text, template, field)
                    declaration.javaRefer() != null || declaration.expression() != null -> FunctionMutator(field.clone(), template,
                        expressionBody(declaration, "field = ${source(declaration.expression() ?: declaration.javaRefer())};")).apply {
                        template.scope.addFunction(function, false)
                    }
                    declaration.curlBlock() != null -> FunctionMutator(field.clone(), template, declaration.curlBlock()).apply {
                        template.scope.addFunction(function, false)
                    }
                    else -> SimpleMutator()
                }
            }
            return Property(field.identifier, getter, setter).apply { isStatic = field.isStatic }
        }

        private fun languageReference(name: String, field: Var<*>, template: DataTemplate): Boolean {
            val root = name.substringBefore('.')
            return root in setOf("this", "field", "value", field.identifier) || template.scope.getVar(root) != null ||
                template.scope.getProperty(root) != null || template.declarationFile?.field?.getType(root) != null
        }

        private fun source(context: org.antlr.v4.runtime.ParserRuleContext): String {
            context.start.inputStream?.let { return it.getText(org.antlr.v4.runtime.misc.Interval(context.start.startIndex, context.stop.stopIndex)) }
            val tokens = arrayListOf<org.antlr.v4.runtime.Token>()
            fun collect(node: org.antlr.v4.runtime.tree.ParseTree) {
                if (node is org.antlr.v4.runtime.tree.TerminalNode) tokens.add(node.symbol)
                else for (index in 0 until node.childCount) collect(node.getChild(index))
            }
            collect(context)
            return buildString {
                var previous: org.antlr.v4.runtime.Token? = null
                for (token in tokens) {
                    previous?.let { last ->
                        if (token.line > last.line) append("\n".repeat(token.line - last.line))
                        else append(" ".repeat((token.startIndex - last.stopIndex - 1).coerceAtLeast(0)))
                    }
                    append(token.text)
                    previous = token
                }
            }
        }

        private fun nativeReference(name: String, field: String, getter: Boolean): Boolean = try {
            top.mcfpp.Project.classLoader.loadClass(name).methods.any { method ->
                java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.contentEquals(arrayOf(top.mcfpp.mni.NativeCallContext::class.java)) &&
                    if (getter) method.getAnnotation(top.mcfpp.annotations.MNIAccessor::class.java)?.value == field
                    else method.getAnnotation(top.mcfpp.annotations.MNIMutator::class.java)?.value == field
            }
        } catch (_: ClassNotFoundException) { false }

        private fun expressionBody(context: org.antlr.v4.runtime.ParserRuleContext, statement: String): mcfppParser.CurlBlockContext {
            val source = "\n".repeat((context.start.line - 1).coerceAtLeast(0)) + "{$statement}"
            return mcfppParser(org.antlr.v4.runtime.CommonTokenStream(top.mcfpp.antlr.mcfppLexer(
                org.antlr.v4.runtime.CharStreams.fromString(source)))).curlBlock()
        }

        fun buildSimpleProperty(field: Var<*>): Property {
            return Property(field.identifier, SimpleAccessor(), SimpleMutator()).apply { isStatic = field.isStatic }
        }

        fun buildSimpleSetter(identifier: String): Property {
            return Property(identifier, null, SimpleMutator())
        }

        fun buildSimpleGetter(identifier: String): Property {
            return Property(identifier, SimpleAccessor(), null)
        }
    }
}
