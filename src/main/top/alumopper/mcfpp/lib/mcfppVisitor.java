// Generated from java-escape by ANTLR 4.11.1

package top.alumopper.mcfpp.lib;

import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link mcfppParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface mcfppVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link mcfppParser#compilationUnit}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCompilationUnit(mcfppParser.CompilationUnitContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#namespaceDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNamespaceDeclaration(mcfppParser.NamespaceDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#typeDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeDeclaration(mcfppParser.TypeDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#classOrFunctionDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClassOrFunctionDeclaration(mcfppParser.ClassOrFunctionDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#classDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClassDeclaration(mcfppParser.ClassDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#nativeClassDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNativeClassDeclaration(mcfppParser.NativeClassDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#classBody}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClassBody(mcfppParser.ClassBodyContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#staticClassMemberDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStaticClassMemberDeclaration(mcfppParser.StaticClassMemberDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#classMemberDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClassMemberDeclaration(mcfppParser.ClassMemberDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#classMember}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClassMember(mcfppParser.ClassMemberContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#classFunctionDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClassFunctionDeclaration(mcfppParser.ClassFunctionDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#functionDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionDeclaration(mcfppParser.FunctionDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#namespaceID}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNamespaceID(mcfppParser.NamespaceIDContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#nativeFuncDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNativeFuncDeclaration(mcfppParser.NativeFuncDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#javaRefer}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitJavaRefer(mcfppParser.JavaReferContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#stringName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStringName(mcfppParser.StringNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#accessModifier}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAccessModifier(mcfppParser.AccessModifierContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#constructorDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstructorDeclaration(mcfppParser.ConstructorDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#nativeConstructorDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNativeConstructorDeclaration(mcfppParser.NativeConstructorDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#constructorCall}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstructorCall(mcfppParser.ConstructorCallContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#fieldDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFieldDeclaration(mcfppParser.FieldDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#parameterList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParameterList(mcfppParser.ParameterListContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#parameter}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParameter(mcfppParser.ParameterContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpression(mcfppParser.ExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#statementExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementExpression(mcfppParser.StatementExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#conditionalExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionalExpression(mcfppParser.ConditionalExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#conditionalOrExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionalOrExpression(mcfppParser.ConditionalOrExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#conditionalAndExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionalAndExpression(mcfppParser.ConditionalAndExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#equalityExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEqualityExpression(mcfppParser.EqualityExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#relationalExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRelationalExpression(mcfppParser.RelationalExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#relationalOp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRelationalOp(mcfppParser.RelationalOpContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#additiveExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAdditiveExpression(mcfppParser.AdditiveExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#multiplicativeExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMultiplicativeExpression(mcfppParser.MultiplicativeExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#unaryExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnaryExpression(mcfppParser.UnaryExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#basicExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBasicExpression(mcfppParser.BasicExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#castExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCastExpression(mcfppParser.CastExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#primary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPrimary(mcfppParser.PrimaryContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#varWithSelector}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVarWithSelector(mcfppParser.VarWithSelectorContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#var}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVar(mcfppParser.VarContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#identifierSuffix}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdentifierSuffix(mcfppParser.IdentifierSuffixContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#selector}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelector(mcfppParser.SelectorContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#arguments}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArguments(mcfppParser.ArgumentsContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#functionBody}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionBody(mcfppParser.FunctionBodyContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#functionCall}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionCall(mcfppParser.FunctionCallContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement(mcfppParser.StatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#orgCommand}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOrgCommand(mcfppParser.OrgCommandContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#controlStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitControlStatement(mcfppParser.ControlStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#ifStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIfStatement(mcfppParser.IfStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#elseIfStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitElseIfStatement(mcfppParser.ElseIfStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#ifBlock}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIfBlock(mcfppParser.IfBlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#forStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForStatement(mcfppParser.ForStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#forBlock}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForBlock(mcfppParser.ForBlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#forControl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForControl(mcfppParser.ForControlContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#forInit}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForInit(mcfppParser.ForInitContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#forUpdate}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForUpdate(mcfppParser.ForUpdateContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#whileStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhileStatement(mcfppParser.WhileStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#whileBlock}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhileBlock(mcfppParser.WhileBlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#doWhileStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDoWhileStatement(mcfppParser.DoWhileStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#doWhileBlock}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDoWhileBlock(mcfppParser.DoWhileBlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#selfAddOrMinusStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelfAddOrMinusStatement(mcfppParser.SelfAddOrMinusStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#tryStoreStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTryStoreStatement(mcfppParser.TryStoreStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlock(mcfppParser.BlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#selfAddOrMinusExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelfAddOrMinusExpression(mcfppParser.SelfAddOrMinusExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#expressionList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpressionList(mcfppParser.ExpressionListContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitType(mcfppParser.TypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#value}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitValue(mcfppParser.ValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#className}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClassName(mcfppParser.ClassNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link mcfppParser#functionTag}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionTag(mcfppParser.FunctionTagContext ctx);
}