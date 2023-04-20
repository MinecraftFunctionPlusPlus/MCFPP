// Generated from ./antlr/MCFPP.g4 by ANTLR 4.9.0-SNAPSHOT


import { ParseTreeVisitor } from "antlr4ts/tree/ParseTreeVisitor.js";

import { CompilationUnitContext } from "./MCFPPParser.js";
import { NamespaceDeclarationContext } from "./MCFPPParser.js";
import { TypeDeclarationContext } from "./MCFPPParser.js";
import { ClassOrFunctionDeclarationContext } from "./MCFPPParser.js";
import { ClassDeclarationContext } from "./MCFPPParser.js";
import { ClassBodyContext } from "./MCFPPParser.js";
import { StaticClassMemberDeclarationContext } from "./MCFPPParser.js";
import { ClassMemberDeclarationContext } from "./MCFPPParser.js";
import { ClassMemberContext } from "./MCFPPParser.js";
import { ClassFunctionDeclarationContext } from "./MCFPPParser.js";
import { FunctionDeclarationContext } from "./MCFPPParser.js";
import { NamespaceIDContext } from "./MCFPPParser.js";
import { NativeDeclarationContext } from "./MCFPPParser.js";
import { JavaReferContext } from "./MCFPPParser.js";
import { StringNameContext } from "./MCFPPParser.js";
import { AccessModifierContext } from "./MCFPPParser.js";
import { ConstructorDeclarationContext } from "./MCFPPParser.js";
import { NativeConstructorDeclarationContext } from "./MCFPPParser.js";
import { ConstructorCallContext } from "./MCFPPParser.js";
import { FieldDeclarationContext } from "./MCFPPParser.js";
import { ParameterListContext } from "./MCFPPParser.js";
import { ParameterContext } from "./MCFPPParser.js";
import { ExpressionContext } from "./MCFPPParser.js";
import { StatementExpressionContext } from "./MCFPPParser.js";
import { ConditionalExpressionContext } from "./MCFPPParser.js";
import { ConditionalOrExpressionContext } from "./MCFPPParser.js";
import { ConditionalAndExpressionContext } from "./MCFPPParser.js";
import { EqualityExpressionContext } from "./MCFPPParser.js";
import { RelationalExpressionContext } from "./MCFPPParser.js";
import { RelationalOpContext } from "./MCFPPParser.js";
import { AdditiveExpressionContext } from "./MCFPPParser.js";
import { MultiplicativeExpressionContext } from "./MCFPPParser.js";
import { UnaryExpressionContext } from "./MCFPPParser.js";
import { BasicExpressionContext } from "./MCFPPParser.js";
import { CastExpressionContext } from "./MCFPPParser.js";
import { PrimaryContext } from "./MCFPPParser.js";
import { VarWithSelectorContext } from "./MCFPPParser.js";
import { VarContext } from "./MCFPPParser.js";
import { IdentifierSuffixContext } from "./MCFPPParser.js";
import { SelectorContext } from "./MCFPPParser.js";
import { ArgumentsContext } from "./MCFPPParser.js";
import { FunctionBodyContext } from "./MCFPPParser.js";
import { FunctionCallContext } from "./MCFPPParser.js";
import { StatementContext } from "./MCFPPParser.js";
import { OrgCommandContext } from "./MCFPPParser.js";
import { ControlStatementContext } from "./MCFPPParser.js";
import { IfStatementContext } from "./MCFPPParser.js";
import { ElseIfStatementContext } from "./MCFPPParser.js";
import { IfBlockContext } from "./MCFPPParser.js";
import { ForStatementContext } from "./MCFPPParser.js";
import { ForBlockContext } from "./MCFPPParser.js";
import { ForControlContext } from "./MCFPPParser.js";
import { ForInitContext } from "./MCFPPParser.js";
import { ForUpdateContext } from "./MCFPPParser.js";
import { WhileStatementContext } from "./MCFPPParser.js";
import { WhileBlockContext } from "./MCFPPParser.js";
import { DoWhileStatementContext } from "./MCFPPParser.js";
import { DoWhileBlockContext } from "./MCFPPParser.js";
import { SelfAddOrMinusStatementContext } from "./MCFPPParser.js";
import { TryStoreStatementContext } from "./MCFPPParser.js";
import { BlockContext } from "./MCFPPParser.js";
import { SelfAddOrMinusExpressionContext } from "./MCFPPParser.js";
import { ExpressionListContext } from "./MCFPPParser.js";
import { TypeContext } from "./MCFPPParser.js";
import { ValueContext } from "./MCFPPParser.js";
import { ClassNameContext } from "./MCFPPParser.js";
import { FunctionTagContext } from "./MCFPPParser.js";


/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by `MCFPPParser`.
 *
 * @param <Result> The return type of the visit operation. Use `void` for
 * operations with no return type.
 */
export interface MCFPPVisitor<Result> extends ParseTreeVisitor<Result> {
	/**
	 * Visit a parse tree produced by `MCFPPParser.compilationUnit`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitCompilationUnit?: (ctx: CompilationUnitContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.namespaceDeclaration`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitNamespaceDeclaration?: (ctx: NamespaceDeclarationContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.typeDeclaration`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitTypeDeclaration?: (ctx: TypeDeclarationContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.classOrFunctionDeclaration`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitClassOrFunctionDeclaration?: (ctx: ClassOrFunctionDeclarationContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.classDeclaration`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitClassDeclaration?: (ctx: ClassDeclarationContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.classBody`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitClassBody?: (ctx: ClassBodyContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.staticClassMemberDeclaration`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitStaticClassMemberDeclaration?: (ctx: StaticClassMemberDeclarationContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.classMemberDeclaration`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitClassMemberDeclaration?: (ctx: ClassMemberDeclarationContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.classMember`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitClassMember?: (ctx: ClassMemberContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.classFunctionDeclaration`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitClassFunctionDeclaration?: (ctx: ClassFunctionDeclarationContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.functionDeclaration`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitFunctionDeclaration?: (ctx: FunctionDeclarationContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.namespaceID`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitNamespaceID?: (ctx: NamespaceIDContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.nativeDeclaration`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitNativeDeclaration?: (ctx: NativeDeclarationContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.javaRefer`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitJavaRefer?: (ctx: JavaReferContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.stringName`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitStringName?: (ctx: StringNameContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.accessModifier`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitAccessModifier?: (ctx: AccessModifierContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.constructorDeclaration`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitConstructorDeclaration?: (ctx: ConstructorDeclarationContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.nativeConstructorDeclaration`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitNativeConstructorDeclaration?: (ctx: NativeConstructorDeclarationContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.constructorCall`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitConstructorCall?: (ctx: ConstructorCallContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.fieldDeclaration`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitFieldDeclaration?: (ctx: FieldDeclarationContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.parameterList`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitParameterList?: (ctx: ParameterListContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.parameter`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitParameter?: (ctx: ParameterContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.expression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitExpression?: (ctx: ExpressionContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.statementExpression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitStatementExpression?: (ctx: StatementExpressionContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.conditionalExpression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitConditionalExpression?: (ctx: ConditionalExpressionContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.conditionalOrExpression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitConditionalOrExpression?: (ctx: ConditionalOrExpressionContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.conditionalAndExpression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitConditionalAndExpression?: (ctx: ConditionalAndExpressionContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.equalityExpression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitEqualityExpression?: (ctx: EqualityExpressionContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.relationalExpression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitRelationalExpression?: (ctx: RelationalExpressionContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.relationalOp`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitRelationalOp?: (ctx: RelationalOpContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.additiveExpression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitAdditiveExpression?: (ctx: AdditiveExpressionContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.multiplicativeExpression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitMultiplicativeExpression?: (ctx: MultiplicativeExpressionContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.unaryExpression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitUnaryExpression?: (ctx: UnaryExpressionContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.basicExpression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitBasicExpression?: (ctx: BasicExpressionContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.castExpression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitCastExpression?: (ctx: CastExpressionContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.primary`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitPrimary?: (ctx: PrimaryContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.varWithSelector`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitVarWithSelector?: (ctx: VarWithSelectorContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.var`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitVar?: (ctx: VarContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.identifierSuffix`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitIdentifierSuffix?: (ctx: IdentifierSuffixContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.selector`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitSelector?: (ctx: SelectorContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.arguments`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitArguments?: (ctx: ArgumentsContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.functionBody`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitFunctionBody?: (ctx: FunctionBodyContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.functionCall`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitFunctionCall?: (ctx: FunctionCallContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.statement`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitStatement?: (ctx: StatementContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.orgCommand`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitOrgCommand?: (ctx: OrgCommandContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.controlStatement`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitControlStatement?: (ctx: ControlStatementContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.ifStatement`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitIfStatement?: (ctx: IfStatementContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.elseIfStatement`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitElseIfStatement?: (ctx: ElseIfStatementContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.ifBlock`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitIfBlock?: (ctx: IfBlockContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.forStatement`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitForStatement?: (ctx: ForStatementContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.forBlock`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitForBlock?: (ctx: ForBlockContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.forControl`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitForControl?: (ctx: ForControlContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.forInit`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitForInit?: (ctx: ForInitContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.forUpdate`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitForUpdate?: (ctx: ForUpdateContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.whileStatement`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitWhileStatement?: (ctx: WhileStatementContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.whileBlock`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitWhileBlock?: (ctx: WhileBlockContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.doWhileStatement`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitDoWhileStatement?: (ctx: DoWhileStatementContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.doWhileBlock`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitDoWhileBlock?: (ctx: DoWhileBlockContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.selfAddOrMinusStatement`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitSelfAddOrMinusStatement?: (ctx: SelfAddOrMinusStatementContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.tryStoreStatement`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitTryStoreStatement?: (ctx: TryStoreStatementContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.block`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitBlock?: (ctx: BlockContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.selfAddOrMinusExpression`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitSelfAddOrMinusExpression?: (ctx: SelfAddOrMinusExpressionContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.expressionList`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitExpressionList?: (ctx: ExpressionListContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.type`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitType?: (ctx: TypeContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.value`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitValue?: (ctx: ValueContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.className`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitClassName?: (ctx: ClassNameContext) => Result;

	/**
	 * Visit a parse tree produced by `MCFPPParser.functionTag`.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	visitFunctionTag?: (ctx: FunctionTagContext) => Result;
}

