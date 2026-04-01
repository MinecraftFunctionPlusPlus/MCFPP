package top.mcfpp.antlr

import top.mcfpp.Project.withCompilationContext
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.antlr.mcfppParser.Range1Context
import top.mcfpp.core.lang.*
import top.mcfpp.core.lang.bool.ScoreBoolConcrete
import top.mcfpp.core.lang.entity.SelectorVar
import top.mcfpp.core.lang.nbt.*
import top.mcfpp.core.lang.obj.ObjectVar
import top.mcfpp.lib.EntitySelector
import top.mcfpp.model.function.Function
import top.mcfpp.nbt.tags.Tag
import top.mcfpp.nbt.tags.primitive.DoubleTag
import top.mcfpp.nbt.tags.primitive.LongTag
import top.mcfpp.nbt.tags.primitive.StringTag
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPEnumType
import top.mcfpp.type.MCFPPType
import top.mcfpp.util.LogProcessor
import top.mcfpp.util.NBTUtil.toNBTByte
import top.mcfpp.util.NBTUtil.toNBTDouble
import top.mcfpp.util.NBTUtil.toNBTFloat
import top.mcfpp.util.NBTUtil.toNBTLong
import top.mcfpp.util.NBTUtil.toNBTShort
import top.mcfpp.util.TextTranslator
import top.mcfpp.util.TextTranslator.translate
import java.util.*

class MCFPPConcreteExprVisitor(
    private var enumType: MCFPPEnumType? = null
): mcfppParserBaseVisitor<Var<*>?>() {

    private var currSelector : Var<*>? = null

    /**
     * Calculate a concrete expression, which must return a concrete var or null
     * @param ctx the parse tree
     * @return result
     */
    @Override
    override fun visitExpression(ctx: mcfppParser.ExpressionContext): Var<*>? = withCompilationContext(ctx) {
        Function.nullFunction.run {
            if(ctx.primary() != null){
                currSelector = null
                return@withCompilationContext visitPrimary(ctx.primary())
            }else{
                currSelector = null
                return@withCompilationContext visitCommonBinaryOperatorExpression(ctx.commonBinaryOperatorExpression())
            }
        }
    }

    private var visitCommonBinaryOperatorExpressionRe : Var<*>? = null

    /**
     * Calculate a common binary operator expression, such as a | b
     * @param ctx the parse tree
     * @return result
     */
    override fun visitCommonBinaryOperatorExpression(ctx: mcfppParser.CommonBinaryOperatorExpressionContext): Var<*>? = withCompilationContext(ctx) {
        visitCommonBinaryOperatorExpressionRe = visitConditionalOrExpression(ctx.conditionalOrExpression(0)) ?: return null
        for (i in 1..<ctx.conditionalOrExpression().size) {
            val b = visitConditionalOrExpression(ctx.conditionalOrExpression(i)) ?: return null
            visitCommonBinaryOperatorExpressionRe = visitCommonBinaryOperatorExpressionRe!!.constBinaryComputation(b, ctx.op[i].text) ?: return null
        }
        return visitCommonBinaryOperatorExpressionRe
    }

    private var visitConditionalOrExpressionRe : Var<*>? = null
    /**
     * Calculate a conditional or expression, such as a || b
     * @param ctx the parse tree
     * @return result
     */
    override fun visitConditionalOrExpression(ctx: mcfppParser.ConditionalOrExpressionContext): Var<*>? = withCompilationContext(ctx) {
        visitConditionalOrExpressionRe = visitConditionalAndExpression(ctx.conditionalAndExpression(0)) ?: return null
        for (i in 1..<ctx.conditionalAndExpression().size) {
            val b = visitConditionalAndExpression(ctx.conditionalAndExpression(i)) ?: return null
            visitConditionalOrExpressionRe = visitConditionalOrExpressionRe!!.constBinaryComputation(b, ctx.op[i].text) ?: return null
        }
        return visitConditionalOrExpressionRe
    }

    private var visitConditionalAndExpressionRe : Var<*>? = null
    /**
     * Calculate a conditional and expression, such as a && b
     * @param ctx the parse tree
     * @return result
     */
    //和
    @Override
    override fun visitConditionalAndExpression(ctx: mcfppParser.ConditionalAndExpressionContext): Var<*>? = withCompilationContext(ctx) {
        visitConditionalAndExpressionRe = visitEqualityExpression(ctx.equalityExpression(0)) ?: return null
        for (i in 1..<ctx.equalityExpression().size) {
            val b = visitEqualityExpression(ctx.equalityExpression(i)) ?: return null
            visitConditionalAndExpressionRe = visitConditionalAndExpressionRe!!.constBinaryComputation(b, ctx.op[i].text) ?: return null
        }
        return visitConditionalAndExpressionRe!!
    }

    private var visitEqualityExpressionRe : Var<*>? = null
    /**
     * Calculate an equality expression, such as a == b and a != b
     * @param ctx the parse tree
     * @return result
     */
    @Override
    override fun visitEqualityExpression(ctx: mcfppParser.EqualityExpressionContext): Var<*>? = withCompilationContext(ctx)  {
        visitEqualityExpressionRe = visitRelationalExpression(ctx.relationalExpression(0)) ?: return null
        for (i in 1..<ctx.relationalExpression().size) {
            val b: Var<*> = visitRelationalExpression(ctx.relationalExpression(i)) ?: return null
            visitEqualityExpressionRe = visitEqualityExpressionRe!!.constBinaryComputation(b, ctx.op[i].text) ?: return null
        }
        return visitEqualityExpressionRe
    }

    private var visitRelationalExpressionRe : Var<*>? = null
    /**
     * Calculate a relational expression, such as a > b
     * @param ctx the parse tree
     * @return result
     */
    @Override
    override fun visitRelationalExpression(ctx: mcfppParser.RelationalExpressionContext): Var<*>? = withCompilationContext(ctx) {
        visitRelationalExpressionRe = visitAdditiveExpression(ctx.additiveExpression(0)) ?: return null
        for (i in 1..<ctx.additiveExpression().size) {
            val b: Var<*> = visitAdditiveExpression(ctx.additiveExpression(i)) ?: return null
            visitRelationalExpressionRe = visitRelationalExpressionRe!!.constBinaryComputation(b, ctx.op[i].text) ?: return null
        }
        return visitRelationalExpressionRe
    }

    private var visitAdditiveExpressionRe : Var<*>? = null
    /**
     * Calculate an additive expression, such as a + b
     * @param ctx the parse tree
     * @return result
     */
    @Override
    override fun visitAdditiveExpression(ctx: mcfppParser.AdditiveExpressionContext): Var<*>? = withCompilationContext(ctx) {
        visitAdditiveExpressionRe = visitMultiplicativeExpression(ctx.multiplicativeExpression(0)) ?: return null
        for (i in 1..<ctx.multiplicativeExpression().size) {
            val b: Var<*> = visitMultiplicativeExpression(ctx.multiplicativeExpression(i)) ?: return null
            visitAdditiveExpressionRe = visitAdditiveExpressionRe!!.constBinaryComputation(b, ctx.op[i].text) ?: return null
        }
        return visitAdditiveExpressionRe
    }

    private var visitMultiplicativeExpressionRe : Var<*>? = null
    /**
     * Calculate a multiplicative expression, such as a * b
     * @param ctx the parse tree
     * @return result
     */
    //乘法
    @Override
    override fun visitMultiplicativeExpression(ctx: mcfppParser.MultiplicativeExpressionContext): Var<*>? = withCompilationContext(ctx) {
        visitMultiplicativeExpressionRe = visitCastExpression(ctx.castExpression(0)) ?: return null
        for (i in 1..<ctx.castExpression().size) {
            val b: Var<*> = visitCastExpression(ctx.castExpression(i)) ?: return null
            visitMultiplicativeExpressionRe = visitMultiplicativeExpressionRe!!.constBinaryComputation(b, ctx.op[i].text) ?: return null
        }
        return visitMultiplicativeExpressionRe
    }

    /**
     * Calculate a cast expression, such as (a as int)
     * @param ctx the parse tree
     * @return result
     */
    @Override
    override fun visitCastExpression(ctx: mcfppParser.CastExpressionContext): Var<*>? = withCompilationContext(ctx) {
        val a: Var<*> = visitUnaryExpression(ctx.unaryExpression()) ?: return null
        return a.explicitCast(MCFPPType.parseFromContextNotNull(ctx.type(), Function.currFunction.scope))
    }

    /**
     * Calculate a unary expression, such as !a
     * @param ctx the parse tree
     * @return result
     */
    @Override
    override fun visitUnaryExpression(ctx: mcfppParser.UnaryExpressionContext): Var<*>? = withCompilationContext(ctx) {
        return if (ctx.rightVarExpression() != null) {
            visitRightVarExpression(ctx.rightVarExpression())
        } else {
            val a = visitUnaryExpression(ctx.unaryExpression()) ?: return null
            a.constUnaryComputation("!")
        }
    }

    @Override
    override fun visitRightVarExpression(ctx: mcfppParser.RightVarExpressionContext): Var<*>? = withCompilationContext(ctx) {
        return visitVarWithSelector(ctx.varWithSelector())
    }

    /**
     * select a member from a type
     *
     * @param ctx
     * @return result
     */
    @Override
    override fun visitVarWithSelector(ctx: mcfppParser.VarWithSelectorContext): Var<*>? = withCompilationContext(ctx) {
        currSelector = null
        currSelector = visitJvmAccessExpression(ctx.jvmAccessExpression()) ?: return null
        if(currSelector is UnknownVar){
            val typeStr = ctx.jvmAccessExpression().text
            val type = MCFPPType.parseFromString(typeStr, Function.currFunction.scope)
            if(type == null){
                LogProcessor.error(TextTranslator.SYMBOL_NOT_DEFINED.translate(currSelector!!.identifier))
            }else{
                currSelector = ObjectVar(type)
            }
        }
        for (selector in ctx.selector()){
            if(currSelector == null) return null
            visitSelector(selector)
        }
        return currSelector
    }

    /**
     * JVM Access expression, not available in this context
     */
    override fun visitJvmAccessExpression(ctx: mcfppParser.JvmAccessExpressionContext): Var<*>? = withCompilationContext(ctx) {
        return if(ctx.Identifier() != null){
            LogProcessor.error("JVM operator is not allowed in this context")
            null
        }else{
            visitPropertyOperator(ctx.propertyOperator())
        }

    }

    override fun visitPropertyOperator(ctx: mcfppParser.PropertyOperatorContext): Var<*>? = withCompilationContext(ctx) {
        val re = visitPrimary(ctx.primary()) ?: return null
        for (operator in ctx.propertyOperatorExpression()){
            val identifier = operator.Identifier().text //要操作的字段名
            val value = visitExpression(operator.expression()) ?: return null
            val member = re.getMemberVar(identifier, re.getAccess(Function.currFunction))   //获取字段
            val field = Var.checkMember(member, identifier)
            field.replacedBy(field.assignedBy(value))
        }
        return re
    }

    override fun visitSelector(ctx: mcfppParser.SelectorContext): Var<*>? = withCompilationContext(ctx) {
        currSelector = visitVar(ctx.`var`()) ?: return null
        return currSelector
    }

    @Override
    override fun visitPrimary(ctx: mcfppParser.PrimaryContext): Var<*>? = withCompilationContext(ctx) {
        if (ctx.`var`() != null) {
            //Variable, must be a const
            val qwq = visitVar(ctx.`var`()) ?: return null
            if(qwq is UnknownVar && ctx.parent.parent !is mcfppParser.VarWithSelectorContext){
                LogProcessor.error(TextTranslator.SYMBOL_NOT_DEFINED.translate(qwq.identifier))
            }
            return qwq
        } else if (ctx.value() != null) {
            return visitValue(ctx.value())
        } else if (ctx.range() != null){
            //range value
            fun qwq(ctx: Range1Context): Var<*>? {
                return if(ctx.`var`() != null){
                    visitVar(ctx.`var`())
                }else{
                    visitValue(ctx.value())
                }
            }
            val left = ctx.range().num1?.let { qwq(it) }
            val right = ctx.range().num2?.let { qwq(it) }
            if(left is MCNumber<*>? && right is MCNumber<*>?){
                if(left is MCFPPValue<*>? && right is MCFPPValue<*>?){
                    val leftValue = left?.value.toString().toFloatOrNull()
                    val rightValue = right?.value.toString().toFloatOrNull()
                    return RangeVarConcrete(leftValue to rightValue)
                }else{
                    LogProcessor.error("Only concrete var is permitted in this context: ${ctx.range().text}")
                    return null
                }
            }else{
                LogProcessor.error("Range sides should be a number: ${left?.type} and ${right?.type}")
                return UnknownVar("range_" + UUID.randomUUID())
            }
        } else if (ctx.type() != null){
            return MCFPPTypeVar(MCFPPType.parseFromString(ctx.type().text, Function.currFunction.scope)?: run {
                LogProcessor.error(TextTranslator.INVALID_TYPE_ERROR.translate(ctx.type().text))
                MCFPPBaseType.Any
            })
        } else {
            //this or super
            LogProcessor.error("${ctx.text} is not permitted in this context")
            return null
        }
    }

    @Override
    @InsertCommand
    override fun visitVar(ctx: mcfppParser.VarContext): Var<*>? = withCompilationContext(ctx) {
        return if (ctx.varWithSuffix() != null) {
            visitVarWithSuffix(ctx.varWithSuffix())
        } else if (ctx.bucketExpression() != null) {
            // '(' expression ')'
            visitBucketExpression(ctx.bucketExpression())
        } else {
            //Function call
            LogProcessor.error("Function call is not allowed in this context")
            return null
        }
    }

    override fun visitBucketExpression(ctx: mcfppParser.BucketExpressionContext): Var<*>? = withCompilationContext(ctx) {
        return MCFPPConcreteExprVisitor().visit(ctx.expression())
    }

    override fun visitVarWithSuffix(ctx: mcfppParser.VarWithSuffixContext): Var<*>? = withCompilationContext(ctx) {
        val qwq: String = ctx.Identifier().text
        var re = if(currSelector == null) {
            val pwp = Function.currFunction.scope.getVar(qwq)
            if(pwp != null) {
                if(pwp.isConst) {
                    pwp
                } else {
                    LogProcessor.error("Variable $qwq must be const in this context")
                    null
                }
            }else{
                UnknownVar(qwq)
            }
        }else{
            val re  = currSelector!!.getMemberVar(qwq, currSelector!!.getAccess(Function.currFunction))
            if (re.first == null) {
                LogProcessor.error("Cannot get member $qwq")
                UnknownVar(qwq)
            }else if (!re.second){
                LogProcessor.error("Cannot access member $qwq")
                UnknownVar(qwq)
            }else if(re.first !is MCFPPValue<*>){
                LogProcessor.error("Only concrete member is allowed in this context: $qwq")
                return null
            }else{
                re.first!!
            }
        }
        if(re is UnknownVar && currSelector == null){
            val typeStr = ctx.Identifier().text
            val type = MCFPPType.parseFromString(typeStr, Function.currFunction.scope)
            if(type == null){
                LogProcessor.error(TextTranslator.SYMBOL_NOT_DEFINED.translate(ctx.text))
            }else{
                re = ObjectVar(type)
            }
        }
        if(re is UnknownVar && enumType != null && currSelector == null){
            currSelector = ObjectVar(enumType!!)
            val re2  = currSelector!!.getMemberVar(qwq, currSelector!!.getAccess(Function.currFunction))
            if (re2.first == null) {
                LogProcessor.error("Cannot get member ${enumType!!.simpleName}.$qwq")
            }else if (!re2.second){
                LogProcessor.error("Cannot access member ${enumType!!.simpleName}.$qwq")
            }else{
                re = re2.first!!
            }
        }
        if (ctx.identifierSuffix() == null || ctx.identifierSuffix().size == 0) {
            return re
        } else {
            LogProcessor.error("indexer is not supported in this context")
            return null
        }
    }

    @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE")
    override fun visitValue(ctx: mcfppParser.ValueContext): Var<*>? = withCompilationContext(ctx) {
        //常量
        if (ctx.LineString() != null) {
            val r: String = ctx.LineString().text
            return MCStringConcrete(StringTag(r.substring(1, r.length - 1)))
        } else if (ctx.multiLineStringLiteral()!=null){
            val stringArray = mutableListOf<String>()
            var isConcrete = true
            for(stringContext in ctx.multiLineStringLiteral().multiLineStringContent()){
                var r:String
                if(stringContext.MultiLineStrText()!=null) r= stringContext.MultiLineStrText().text
                else if(stringContext.MultiLineStringQuote()!=null) r= stringContext.MultiLineStringQuote().text
                else {
                    val expressionContext = stringContext.multiLineStringExpression().expression()
                    //TODO: 这边只是简单写了一下有解析值的情况
                    val res = visit(expressionContext) //没有解析值的话，应该变成text
                    if(res!=null && res !is MCFPPValue<*>){ isConcrete = false } //这个条件就是说，整个模版中出现没有解析值的情况了
                    r = if(res is MCIntConcrete){
                        res.value.toString()
                    } else{
                        res.toString()
                    }
                }
                stringArray.add(r)
            }
            val tailQuote = ctx.multiLineStringLiteral().TRIPLE_QUOTE_CLOSE().text
            if(tailQuote.length>3) {
                stringArray.add(tailQuote.substring(3,tailQuote.length))
            }
            return MCStringConcrete(StringTag(stringArray.joinToString("")) ) //没有解析值就变不了MCString了
        } else if (ctx.nbtValue() != null){
            return visit(ctx.nbtValue())
        } else if (ctx.TargetSelector() != null){
            return SelectorVar(EntitySelector(ctx.TargetSelector()!!.text[1]))
        } else if(ctx.coordinate() != null){
            val dimensions = ctx.coordinate().coordinateDimension().map { visitCoordinateDimension(it) }
            if(dimensions.size == 3){
                return Pos3Var().apply {
                    x.assignedBy(dimensions[0])
                    y.assignedBy(dimensions[1])
                    z.assignedBy(dimensions[2])
                }
            }
            return Pos2Var().apply {
                x.assignedBy(dimensions[0])
                z.assignedBy(dimensions[1])
            }
        } else if(ctx.NULL() != null){
            return Null
        }
        throw IllegalArgumentException("value_" + ctx.text)
    }

    override fun visitCoordinateDimension(ctx: mcfppParser.CoordinateDimensionContext): Var<*> = withCompilationContext(ctx) {
        if(ctx.nbtInt() != null){
            return PosDimension("", ctx.nbtInt().text.toInt())
        }else if(ctx.nbtFloat() != null) {
            return PosDimension("", ctx.nbtFloat().text.toFloat())
        }else if(ctx.nbtDouble() != null){
            return PosDimension("", ctx.nbtDouble().text.toDouble())
        }else{
            //RelativeValue
            val str = ctx.RelativeValue().text
            if(str.length == 1){
                return PosDimension(str, 0)
            }
            val expr = str.substring(1)
            //Try to convert to number
            var num: Number? = expr.toIntOrNull()
            if(num != null){
                return PosDimension(str[0].toString(), num)
            }
            num = expr.toFloatOrNull()
            if(num != null){
                return PosDimension(str[0].toString(), num)
            }
            LogProcessor.error("Invalid relative value: $expr")
            return PosDimension(str[0].toString(), 0)
        }
    }

    override fun visitNbtValue(ctx: mcfppParser.NbtValueContext): Var<*>? = withCompilationContext(ctx) {
        if(ctx.LineString() != null) {
            return MCStringConcrete(StringTag(ctx.LineString().text))
        }else if(ctx.nbtBool() != null){
            return ScoreBoolConcrete(ctx.nbtBool().text == "true")
        }else if(ctx.nbtByte() != null){
            return MCByteConcrete(ctx.nbtByte().text.toNBTByte())
        }else if(ctx.nbtShort() != null){
            return MCShortConcrete(ctx.nbtShort().text.toNBTShort())
        }else if(ctx.nbtInt() != null) {
            return MCIntConcrete(ctx.nbtInt().text.toInt())
        }else if(ctx.nbtLong() != null){
            return MCLongConcrete(LongTag(ctx.nbtLong().text.toNBTLong()))
        }else if(ctx.nbtFloat() != null){
            return MCFloatConcrete(ctx.nbtFloat().text.toNBTFloat())
        }else if(ctx.nbtDouble() != null) {
            return MCDoubleConcrete(DoubleTag(ctx.nbtDouble().text.toNBTDouble()))
        }else if(ctx.nbtCompound() != null){
            //values in compounds must be a const as well
            val compound = NBTDictionaryConcrete(HashMap())
            for (kv in ctx.nbtCompound().nbtKeyValuePair()){
                val key = kv.Identifier().text
                val value = MCFPPConcreteExprVisitor().visit(kv.expression()) ?: return null
                compound.value[key] = value.type.build(key, (value as MCFPPValue<*>).value)
            }
            return compound
        }else if(ctx.nbtList() != null){
            //as well as list
            val valueList = ArrayList<Var<*>>()
            for (expr in ctx.nbtList().expression()){
                val qwq = MCFPPConcreteExprVisitor().visit(expr) ?: return null
                valueList.add(qwq)
            }
            val re = if(valueList.isEmpty()){
                NBTListConcrete.getEmpty()
            }else{
                NBTListConcrete(valueList, "", valueList.first().type)
            }
            return re
        }else if(ctx.nbtByteArray() != null){
            return NBTBasedDataConcrete(Tag.toNBT(ctx.nbtByteArray().text))
        }else if(ctx.nbtIntArray() != null) {
            return NBTBasedDataConcrete(Tag.toNBT(ctx.nbtIntArray().text))
        }else if(ctx.nbtLongArray() != null) {
            return NBTBasedDataConcrete(Tag.toNBT(ctx.nbtLongArray().text))
        }else {
            LogProcessor.error("Invalid NBT value")
            throw IllegalArgumentException("nbt:" + ctx.text)
        }
    }
}