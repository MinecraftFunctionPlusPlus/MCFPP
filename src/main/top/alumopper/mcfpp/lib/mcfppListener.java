// Generated from java-escape by ANTLR 4.11.1

package top.alumopper.mcfpp.lib;

import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link mcfppParser}.
 */
public interface mcfppListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link mcfppParser#compilationUnit}.
	 * @param ctx the parse tree
	 */
	void enterCompilationUnit(mcfppParser.CompilationUnitContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#compilationUnit}.
	 * @param ctx the parse tree
	 */
	void exitCompilationUnit(mcfppParser.CompilationUnitContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#namespaceDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterNamespaceDeclaration(mcfppParser.NamespaceDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#namespaceDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitNamespaceDeclaration(mcfppParser.NamespaceDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#typeDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterTypeDeclaration(mcfppParser.TypeDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#typeDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitTypeDeclaration(mcfppParser.TypeDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#classOrFunctionDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterClassOrFunctionDeclaration(mcfppParser.ClassOrFunctionDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#classOrFunctionDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitClassOrFunctionDeclaration(mcfppParser.ClassOrFunctionDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#classDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterClassDeclaration(mcfppParser.ClassDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#classDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitClassDeclaration(mcfppParser.ClassDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#nativeClassDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterNativeClassDeclaration(mcfppParser.NativeClassDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#nativeClassDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitNativeClassDeclaration(mcfppParser.NativeClassDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#classBody}.
	 * @param ctx the parse tree
	 */
	void enterClassBody(mcfppParser.ClassBodyContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#classBody}.
	 * @param ctx the parse tree
	 */
	void exitClassBody(mcfppParser.ClassBodyContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#staticClassMemberDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterStaticClassMemberDeclaration(mcfppParser.StaticClassMemberDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#staticClassMemberDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitStaticClassMemberDeclaration(mcfppParser.StaticClassMemberDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#classMemberDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterClassMemberDeclaration(mcfppParser.ClassMemberDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#classMemberDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitClassMemberDeclaration(mcfppParser.ClassMemberDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#classMember}.
	 * @param ctx the parse tree
	 */
	void enterClassMember(mcfppParser.ClassMemberContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#classMember}.
	 * @param ctx the parse tree
	 */
	void exitClassMember(mcfppParser.ClassMemberContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#classFunctionDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterClassFunctionDeclaration(mcfppParser.ClassFunctionDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#classFunctionDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitClassFunctionDeclaration(mcfppParser.ClassFunctionDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#functionDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterFunctionDeclaration(mcfppParser.FunctionDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#functionDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitFunctionDeclaration(mcfppParser.FunctionDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#namespaceID}.
	 * @param ctx the parse tree
	 */
	void enterNamespaceID(mcfppParser.NamespaceIDContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#namespaceID}.
	 * @param ctx the parse tree
	 */
	void exitNamespaceID(mcfppParser.NamespaceIDContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#nativeFuncDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterNativeFuncDeclaration(mcfppParser.NativeFuncDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#nativeFuncDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitNativeFuncDeclaration(mcfppParser.NativeFuncDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#javaRefer}.
	 * @param ctx the parse tree
	 */
	void enterJavaRefer(mcfppParser.JavaReferContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#javaRefer}.
	 * @param ctx the parse tree
	 */
	void exitJavaRefer(mcfppParser.JavaReferContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#stringName}.
	 * @param ctx the parse tree
	 */
	void enterStringName(mcfppParser.StringNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#stringName}.
	 * @param ctx the parse tree
	 */
	void exitStringName(mcfppParser.StringNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#accessModifier}.
	 * @param ctx the parse tree
	 */
	void enterAccessModifier(mcfppParser.AccessModifierContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#accessModifier}.
	 * @param ctx the parse tree
	 */
	void exitAccessModifier(mcfppParser.AccessModifierContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#constructorDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterConstructorDeclaration(mcfppParser.ConstructorDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#constructorDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitConstructorDeclaration(mcfppParser.ConstructorDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#nativeConstructorDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterNativeConstructorDeclaration(mcfppParser.NativeConstructorDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#nativeConstructorDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitNativeConstructorDeclaration(mcfppParser.NativeConstructorDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#constructorCall}.
	 * @param ctx the parse tree
	 */
	void enterConstructorCall(mcfppParser.ConstructorCallContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#constructorCall}.
	 * @param ctx the parse tree
	 */
	void exitConstructorCall(mcfppParser.ConstructorCallContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#fieldDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterFieldDeclaration(mcfppParser.FieldDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#fieldDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitFieldDeclaration(mcfppParser.FieldDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#parameterList}.
	 * @param ctx the parse tree
	 */
	void enterParameterList(mcfppParser.ParameterListContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#parameterList}.
	 * @param ctx the parse tree
	 */
	void exitParameterList(mcfppParser.ParameterListContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#parameter}.
	 * @param ctx the parse tree
	 */
	void enterParameter(mcfppParser.ParameterContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#parameter}.
	 * @param ctx the parse tree
	 */
	void exitParameter(mcfppParser.ParameterContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterExpression(mcfppParser.ExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitExpression(mcfppParser.ExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#statementExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementExpression(mcfppParser.StatementExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#statementExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementExpression(mcfppParser.StatementExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#conditionalExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionalExpression(mcfppParser.ConditionalExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#conditionalExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionalExpression(mcfppParser.ConditionalExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#conditionalOrExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionalOrExpression(mcfppParser.ConditionalOrExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#conditionalOrExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionalOrExpression(mcfppParser.ConditionalOrExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#conditionalAndExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionalAndExpression(mcfppParser.ConditionalAndExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#conditionalAndExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionalAndExpression(mcfppParser.ConditionalAndExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#equalityExpression}.
	 * @param ctx the parse tree
	 */
	void enterEqualityExpression(mcfppParser.EqualityExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#equalityExpression}.
	 * @param ctx the parse tree
	 */
	void exitEqualityExpression(mcfppParser.EqualityExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#relationalExpression}.
	 * @param ctx the parse tree
	 */
	void enterRelationalExpression(mcfppParser.RelationalExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#relationalExpression}.
	 * @param ctx the parse tree
	 */
	void exitRelationalExpression(mcfppParser.RelationalExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#relationalOp}.
	 * @param ctx the parse tree
	 */
	void enterRelationalOp(mcfppParser.RelationalOpContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#relationalOp}.
	 * @param ctx the parse tree
	 */
	void exitRelationalOp(mcfppParser.RelationalOpContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#additiveExpression}.
	 * @param ctx the parse tree
	 */
	void enterAdditiveExpression(mcfppParser.AdditiveExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#additiveExpression}.
	 * @param ctx the parse tree
	 */
	void exitAdditiveExpression(mcfppParser.AdditiveExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#multiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void enterMultiplicativeExpression(mcfppParser.MultiplicativeExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#multiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void exitMultiplicativeExpression(mcfppParser.MultiplicativeExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#unaryExpression}.
	 * @param ctx the parse tree
	 */
	void enterUnaryExpression(mcfppParser.UnaryExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#unaryExpression}.
	 * @param ctx the parse tree
	 */
	void exitUnaryExpression(mcfppParser.UnaryExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#basicExpression}.
	 * @param ctx the parse tree
	 */
	void enterBasicExpression(mcfppParser.BasicExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#basicExpression}.
	 * @param ctx the parse tree
	 */
	void exitBasicExpression(mcfppParser.BasicExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#castExpression}.
	 * @param ctx the parse tree
	 */
	void enterCastExpression(mcfppParser.CastExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#castExpression}.
	 * @param ctx the parse tree
	 */
	void exitCastExpression(mcfppParser.CastExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#primary}.
	 * @param ctx the parse tree
	 */
	void enterPrimary(mcfppParser.PrimaryContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#primary}.
	 * @param ctx the parse tree
	 */
	void exitPrimary(mcfppParser.PrimaryContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#varWithSelector}.
	 * @param ctx the parse tree
	 */
	void enterVarWithSelector(mcfppParser.VarWithSelectorContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#varWithSelector}.
	 * @param ctx the parse tree
	 */
	void exitVarWithSelector(mcfppParser.VarWithSelectorContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#var}.
	 * @param ctx the parse tree
	 */
	void enterVar(mcfppParser.VarContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#var}.
	 * @param ctx the parse tree
	 */
	void exitVar(mcfppParser.VarContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#identifierSuffix}.
	 * @param ctx the parse tree
	 */
	void enterIdentifierSuffix(mcfppParser.IdentifierSuffixContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#identifierSuffix}.
	 * @param ctx the parse tree
	 */
	void exitIdentifierSuffix(mcfppParser.IdentifierSuffixContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#selector}.
	 * @param ctx the parse tree
	 */
	void enterSelector(mcfppParser.SelectorContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#selector}.
	 * @param ctx the parse tree
	 */
	void exitSelector(mcfppParser.SelectorContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#arguments}.
	 * @param ctx the parse tree
	 */
	void enterArguments(mcfppParser.ArgumentsContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#arguments}.
	 * @param ctx the parse tree
	 */
	void exitArguments(mcfppParser.ArgumentsContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#functionBody}.
	 * @param ctx the parse tree
	 */
	void enterFunctionBody(mcfppParser.FunctionBodyContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#functionBody}.
	 * @param ctx the parse tree
	 */
	void exitFunctionBody(mcfppParser.FunctionBodyContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#functionCall}.
	 * @param ctx the parse tree
	 */
	void enterFunctionCall(mcfppParser.FunctionCallContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#functionCall}.
	 * @param ctx the parse tree
	 */
	void exitFunctionCall(mcfppParser.FunctionCallContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterStatement(mcfppParser.StatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitStatement(mcfppParser.StatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#orgCommand}.
	 * @param ctx the parse tree
	 */
	void enterOrgCommand(mcfppParser.OrgCommandContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#orgCommand}.
	 * @param ctx the parse tree
	 */
	void exitOrgCommand(mcfppParser.OrgCommandContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#controlStatement}.
	 * @param ctx the parse tree
	 */
	void enterControlStatement(mcfppParser.ControlStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#controlStatement}.
	 * @param ctx the parse tree
	 */
	void exitControlStatement(mcfppParser.ControlStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#ifStatement}.
	 * @param ctx the parse tree
	 */
	void enterIfStatement(mcfppParser.IfStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#ifStatement}.
	 * @param ctx the parse tree
	 */
	void exitIfStatement(mcfppParser.IfStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#elseIfStatement}.
	 * @param ctx the parse tree
	 */
	void enterElseIfStatement(mcfppParser.ElseIfStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#elseIfStatement}.
	 * @param ctx the parse tree
	 */
	void exitElseIfStatement(mcfppParser.ElseIfStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#ifBlock}.
	 * @param ctx the parse tree
	 */
	void enterIfBlock(mcfppParser.IfBlockContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#ifBlock}.
	 * @param ctx the parse tree
	 */
	void exitIfBlock(mcfppParser.IfBlockContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#forStatement}.
	 * @param ctx the parse tree
	 */
	void enterForStatement(mcfppParser.ForStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#forStatement}.
	 * @param ctx the parse tree
	 */
	void exitForStatement(mcfppParser.ForStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#forBlock}.
	 * @param ctx the parse tree
	 */
	void enterForBlock(mcfppParser.ForBlockContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#forBlock}.
	 * @param ctx the parse tree
	 */
	void exitForBlock(mcfppParser.ForBlockContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#forControl}.
	 * @param ctx the parse tree
	 */
	void enterForControl(mcfppParser.ForControlContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#forControl}.
	 * @param ctx the parse tree
	 */
	void exitForControl(mcfppParser.ForControlContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#forInit}.
	 * @param ctx the parse tree
	 */
	void enterForInit(mcfppParser.ForInitContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#forInit}.
	 * @param ctx the parse tree
	 */
	void exitForInit(mcfppParser.ForInitContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#forUpdate}.
	 * @param ctx the parse tree
	 */
	void enterForUpdate(mcfppParser.ForUpdateContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#forUpdate}.
	 * @param ctx the parse tree
	 */
	void exitForUpdate(mcfppParser.ForUpdateContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#whileStatement}.
	 * @param ctx the parse tree
	 */
	void enterWhileStatement(mcfppParser.WhileStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#whileStatement}.
	 * @param ctx the parse tree
	 */
	void exitWhileStatement(mcfppParser.WhileStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#whileBlock}.
	 * @param ctx the parse tree
	 */
	void enterWhileBlock(mcfppParser.WhileBlockContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#whileBlock}.
	 * @param ctx the parse tree
	 */
	void exitWhileBlock(mcfppParser.WhileBlockContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#doWhileStatement}.
	 * @param ctx the parse tree
	 */
	void enterDoWhileStatement(mcfppParser.DoWhileStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#doWhileStatement}.
	 * @param ctx the parse tree
	 */
	void exitDoWhileStatement(mcfppParser.DoWhileStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#doWhileBlock}.
	 * @param ctx the parse tree
	 */
	void enterDoWhileBlock(mcfppParser.DoWhileBlockContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#doWhileBlock}.
	 * @param ctx the parse tree
	 */
	void exitDoWhileBlock(mcfppParser.DoWhileBlockContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#selfAddOrMinusStatement}.
	 * @param ctx the parse tree
	 */
	void enterSelfAddOrMinusStatement(mcfppParser.SelfAddOrMinusStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#selfAddOrMinusStatement}.
	 * @param ctx the parse tree
	 */
	void exitSelfAddOrMinusStatement(mcfppParser.SelfAddOrMinusStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#tryStoreStatement}.
	 * @param ctx the parse tree
	 */
	void enterTryStoreStatement(mcfppParser.TryStoreStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#tryStoreStatement}.
	 * @param ctx the parse tree
	 */
	void exitTryStoreStatement(mcfppParser.TryStoreStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#block}.
	 * @param ctx the parse tree
	 */
	void enterBlock(mcfppParser.BlockContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#block}.
	 * @param ctx the parse tree
	 */
	void exitBlock(mcfppParser.BlockContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#selfAddOrMinusExpression}.
	 * @param ctx the parse tree
	 */
	void enterSelfAddOrMinusExpression(mcfppParser.SelfAddOrMinusExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#selfAddOrMinusExpression}.
	 * @param ctx the parse tree
	 */
	void exitSelfAddOrMinusExpression(mcfppParser.SelfAddOrMinusExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#expressionList}.
	 * @param ctx the parse tree
	 */
	void enterExpressionList(mcfppParser.ExpressionListContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#expressionList}.
	 * @param ctx the parse tree
	 */
	void exitExpressionList(mcfppParser.ExpressionListContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#type}.
	 * @param ctx the parse tree
	 */
	void enterType(mcfppParser.TypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#type}.
	 * @param ctx the parse tree
	 */
	void exitType(mcfppParser.TypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#value}.
	 * @param ctx the parse tree
	 */
	void enterValue(mcfppParser.ValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#value}.
	 * @param ctx the parse tree
	 */
	void exitValue(mcfppParser.ValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#className}.
	 * @param ctx the parse tree
	 */
	void enterClassName(mcfppParser.ClassNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#className}.
	 * @param ctx the parse tree
	 */
	void exitClassName(mcfppParser.ClassNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link mcfppParser#functionTag}.
	 * @param ctx the parse tree
	 */
	void enterFunctionTag(mcfppParser.FunctionTagContext ctx);
	/**
	 * Exit a parse tree produced by {@link mcfppParser#functionTag}.
	 * @param ctx the parse tree
	 */
	void exitFunctionTag(mcfppParser.FunctionTagContext ctx);
}