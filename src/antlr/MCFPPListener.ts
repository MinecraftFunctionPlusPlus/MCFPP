// Generated from ./antlr/MCFPP.g4 by ANTLR 4.9.0-SNAPSHOT


import { ParseTreeListener } from "antlr4ts/tree/ParseTreeListener.js";

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
 * This interface defines a complete listener for a parse tree produced by
 * `MCFPPParser`.
 */
export interface MCFPPListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by `MCFPPParser.compilationUnit`.
	 * @param ctx the parse tree
	 */
	enterCompilationUnit?: (ctx: CompilationUnitContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.compilationUnit`.
	 * @param ctx the parse tree
	 */
	exitCompilationUnit?: (ctx: CompilationUnitContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.namespaceDeclaration`.
	 * @param ctx the parse tree
	 */
	enterNamespaceDeclaration?: (ctx: NamespaceDeclarationContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.namespaceDeclaration`.
	 * @param ctx the parse tree
	 */
	exitNamespaceDeclaration?: (ctx: NamespaceDeclarationContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.typeDeclaration`.
	 * @param ctx the parse tree
	 */
	enterTypeDeclaration?: (ctx: TypeDeclarationContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.typeDeclaration`.
	 * @param ctx the parse tree
	 */
	exitTypeDeclaration?: (ctx: TypeDeclarationContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.classOrFunctionDeclaration`.
	 * @param ctx the parse tree
	 */
	enterClassOrFunctionDeclaration?: (ctx: ClassOrFunctionDeclarationContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.classOrFunctionDeclaration`.
	 * @param ctx the parse tree
	 */
	exitClassOrFunctionDeclaration?: (ctx: ClassOrFunctionDeclarationContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.classDeclaration`.
	 * @param ctx the parse tree
	 */
	enterClassDeclaration?: (ctx: ClassDeclarationContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.classDeclaration`.
	 * @param ctx the parse tree
	 */
	exitClassDeclaration?: (ctx: ClassDeclarationContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.classBody`.
	 * @param ctx the parse tree
	 */
	enterClassBody?: (ctx: ClassBodyContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.classBody`.
	 * @param ctx the parse tree
	 */
	exitClassBody?: (ctx: ClassBodyContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.staticClassMemberDeclaration`.
	 * @param ctx the parse tree
	 */
	enterStaticClassMemberDeclaration?: (ctx: StaticClassMemberDeclarationContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.staticClassMemberDeclaration`.
	 * @param ctx the parse tree
	 */
	exitStaticClassMemberDeclaration?: (ctx: StaticClassMemberDeclarationContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.classMemberDeclaration`.
	 * @param ctx the parse tree
	 */
	enterClassMemberDeclaration?: (ctx: ClassMemberDeclarationContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.classMemberDeclaration`.
	 * @param ctx the parse tree
	 */
	exitClassMemberDeclaration?: (ctx: ClassMemberDeclarationContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.classMember`.
	 * @param ctx the parse tree
	 */
	enterClassMember?: (ctx: ClassMemberContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.classMember`.
	 * @param ctx the parse tree
	 */
	exitClassMember?: (ctx: ClassMemberContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.classFunctionDeclaration`.
	 * @param ctx the parse tree
	 */
	enterClassFunctionDeclaration?: (ctx: ClassFunctionDeclarationContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.classFunctionDeclaration`.
	 * @param ctx the parse tree
	 */
	exitClassFunctionDeclaration?: (ctx: ClassFunctionDeclarationContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.functionDeclaration`.
	 * @param ctx the parse tree
	 */
	enterFunctionDeclaration?: (ctx: FunctionDeclarationContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.functionDeclaration`.
	 * @param ctx the parse tree
	 */
	exitFunctionDeclaration?: (ctx: FunctionDeclarationContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.namespaceID`.
	 * @param ctx the parse tree
	 */
	enterNamespaceID?: (ctx: NamespaceIDContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.namespaceID`.
	 * @param ctx the parse tree
	 */
	exitNamespaceID?: (ctx: NamespaceIDContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.nativeDeclaration`.
	 * @param ctx the parse tree
	 */
	enterNativeDeclaration?: (ctx: NativeDeclarationContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.nativeDeclaration`.
	 * @param ctx the parse tree
	 */
	exitNativeDeclaration?: (ctx: NativeDeclarationContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.javaRefer`.
	 * @param ctx the parse tree
	 */
	enterJavaRefer?: (ctx: JavaReferContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.javaRefer`.
	 * @param ctx the parse tree
	 */
	exitJavaRefer?: (ctx: JavaReferContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.stringName`.
	 * @param ctx the parse tree
	 */
	enterStringName?: (ctx: StringNameContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.stringName`.
	 * @param ctx the parse tree
	 */
	exitStringName?: (ctx: StringNameContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.accessModifier`.
	 * @param ctx the parse tree
	 */
	enterAccessModifier?: (ctx: AccessModifierContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.accessModifier`.
	 * @param ctx the parse tree
	 */
	exitAccessModifier?: (ctx: AccessModifierContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.constructorDeclaration`.
	 * @param ctx the parse tree
	 */
	enterConstructorDeclaration?: (ctx: ConstructorDeclarationContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.constructorDeclaration`.
	 * @param ctx the parse tree
	 */
	exitConstructorDeclaration?: (ctx: ConstructorDeclarationContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.nativeConstructorDeclaration`.
	 * @param ctx the parse tree
	 */
	enterNativeConstructorDeclaration?: (ctx: NativeConstructorDeclarationContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.nativeConstructorDeclaration`.
	 * @param ctx the parse tree
	 */
	exitNativeConstructorDeclaration?: (ctx: NativeConstructorDeclarationContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.constructorCall`.
	 * @param ctx the parse tree
	 */
	enterConstructorCall?: (ctx: ConstructorCallContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.constructorCall`.
	 * @param ctx the parse tree
	 */
	exitConstructorCall?: (ctx: ConstructorCallContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.fieldDeclaration`.
	 * @param ctx the parse tree
	 */
	enterFieldDeclaration?: (ctx: FieldDeclarationContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.fieldDeclaration`.
	 * @param ctx the parse tree
	 */
	exitFieldDeclaration?: (ctx: FieldDeclarationContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.parameterList`.
	 * @param ctx the parse tree
	 */
	enterParameterList?: (ctx: ParameterListContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.parameterList`.
	 * @param ctx the parse tree
	 */
	exitParameterList?: (ctx: ParameterListContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.parameter`.
	 * @param ctx the parse tree
	 */
	enterParameter?: (ctx: ParameterContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.parameter`.
	 * @param ctx the parse tree
	 */
	exitParameter?: (ctx: ParameterContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.expression`.
	 * @param ctx the parse tree
	 */
	enterExpression?: (ctx: ExpressionContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.expression`.
	 * @param ctx the parse tree
	 */
	exitExpression?: (ctx: ExpressionContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.statementExpression`.
	 * @param ctx the parse tree
	 */
	enterStatementExpression?: (ctx: StatementExpressionContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.statementExpression`.
	 * @param ctx the parse tree
	 */
	exitStatementExpression?: (ctx: StatementExpressionContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.conditionalExpression`.
	 * @param ctx the parse tree
	 */
	enterConditionalExpression?: (ctx: ConditionalExpressionContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.conditionalExpression`.
	 * @param ctx the parse tree
	 */
	exitConditionalExpression?: (ctx: ConditionalExpressionContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.conditionalOrExpression`.
	 * @param ctx the parse tree
	 */
	enterConditionalOrExpression?: (ctx: ConditionalOrExpressionContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.conditionalOrExpression`.
	 * @param ctx the parse tree
	 */
	exitConditionalOrExpression?: (ctx: ConditionalOrExpressionContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.conditionalAndExpression`.
	 * @param ctx the parse tree
	 */
	enterConditionalAndExpression?: (ctx: ConditionalAndExpressionContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.conditionalAndExpression`.
	 * @param ctx the parse tree
	 */
	exitConditionalAndExpression?: (ctx: ConditionalAndExpressionContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.equalityExpression`.
	 * @param ctx the parse tree
	 */
	enterEqualityExpression?: (ctx: EqualityExpressionContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.equalityExpression`.
	 * @param ctx the parse tree
	 */
	exitEqualityExpression?: (ctx: EqualityExpressionContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.relationalExpression`.
	 * @param ctx the parse tree
	 */
	enterRelationalExpression?: (ctx: RelationalExpressionContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.relationalExpression`.
	 * @param ctx the parse tree
	 */
	exitRelationalExpression?: (ctx: RelationalExpressionContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.relationalOp`.
	 * @param ctx the parse tree
	 */
	enterRelationalOp?: (ctx: RelationalOpContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.relationalOp`.
	 * @param ctx the parse tree
	 */
	exitRelationalOp?: (ctx: RelationalOpContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.additiveExpression`.
	 * @param ctx the parse tree
	 */
	enterAdditiveExpression?: (ctx: AdditiveExpressionContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.additiveExpression`.
	 * @param ctx the parse tree
	 */
	exitAdditiveExpression?: (ctx: AdditiveExpressionContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.multiplicativeExpression`.
	 * @param ctx the parse tree
	 */
	enterMultiplicativeExpression?: (ctx: MultiplicativeExpressionContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.multiplicativeExpression`.
	 * @param ctx the parse tree
	 */
	exitMultiplicativeExpression?: (ctx: MultiplicativeExpressionContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.unaryExpression`.
	 * @param ctx the parse tree
	 */
	enterUnaryExpression?: (ctx: UnaryExpressionContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.unaryExpression`.
	 * @param ctx the parse tree
	 */
	exitUnaryExpression?: (ctx: UnaryExpressionContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.basicExpression`.
	 * @param ctx the parse tree
	 */
	enterBasicExpression?: (ctx: BasicExpressionContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.basicExpression`.
	 * @param ctx the parse tree
	 */
	exitBasicExpression?: (ctx: BasicExpressionContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.castExpression`.
	 * @param ctx the parse tree
	 */
	enterCastExpression?: (ctx: CastExpressionContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.castExpression`.
	 * @param ctx the parse tree
	 */
	exitCastExpression?: (ctx: CastExpressionContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.primary`.
	 * @param ctx the parse tree
	 */
	enterPrimary?: (ctx: PrimaryContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.primary`.
	 * @param ctx the parse tree
	 */
	exitPrimary?: (ctx: PrimaryContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.varWithSelector`.
	 * @param ctx the parse tree
	 */
	enterVarWithSelector?: (ctx: VarWithSelectorContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.varWithSelector`.
	 * @param ctx the parse tree
	 */
	exitVarWithSelector?: (ctx: VarWithSelectorContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.var`.
	 * @param ctx the parse tree
	 */
	enterVar?: (ctx: VarContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.var`.
	 * @param ctx the parse tree
	 */
	exitVar?: (ctx: VarContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.identifierSuffix`.
	 * @param ctx the parse tree
	 */
	enterIdentifierSuffix?: (ctx: IdentifierSuffixContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.identifierSuffix`.
	 * @param ctx the parse tree
	 */
	exitIdentifierSuffix?: (ctx: IdentifierSuffixContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.selector`.
	 * @param ctx the parse tree
	 */
	enterSelector?: (ctx: SelectorContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.selector`.
	 * @param ctx the parse tree
	 */
	exitSelector?: (ctx: SelectorContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.arguments`.
	 * @param ctx the parse tree
	 */
	enterArguments?: (ctx: ArgumentsContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.arguments`.
	 * @param ctx the parse tree
	 */
	exitArguments?: (ctx: ArgumentsContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.functionBody`.
	 * @param ctx the parse tree
	 */
	enterFunctionBody?: (ctx: FunctionBodyContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.functionBody`.
	 * @param ctx the parse tree
	 */
	exitFunctionBody?: (ctx: FunctionBodyContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.functionCall`.
	 * @param ctx the parse tree
	 */
	enterFunctionCall?: (ctx: FunctionCallContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.functionCall`.
	 * @param ctx the parse tree
	 */
	exitFunctionCall?: (ctx: FunctionCallContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.statement`.
	 * @param ctx the parse tree
	 */
	enterStatement?: (ctx: StatementContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.statement`.
	 * @param ctx the parse tree
	 */
	exitStatement?: (ctx: StatementContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.orgCommand`.
	 * @param ctx the parse tree
	 */
	enterOrgCommand?: (ctx: OrgCommandContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.orgCommand`.
	 * @param ctx the parse tree
	 */
	exitOrgCommand?: (ctx: OrgCommandContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.controlStatement`.
	 * @param ctx the parse tree
	 */
	enterControlStatement?: (ctx: ControlStatementContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.controlStatement`.
	 * @param ctx the parse tree
	 */
	exitControlStatement?: (ctx: ControlStatementContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.ifStatement`.
	 * @param ctx the parse tree
	 */
	enterIfStatement?: (ctx: IfStatementContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.ifStatement`.
	 * @param ctx the parse tree
	 */
	exitIfStatement?: (ctx: IfStatementContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.elseIfStatement`.
	 * @param ctx the parse tree
	 */
	enterElseIfStatement?: (ctx: ElseIfStatementContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.elseIfStatement`.
	 * @param ctx the parse tree
	 */
	exitElseIfStatement?: (ctx: ElseIfStatementContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.ifBlock`.
	 * @param ctx the parse tree
	 */
	enterIfBlock?: (ctx: IfBlockContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.ifBlock`.
	 * @param ctx the parse tree
	 */
	exitIfBlock?: (ctx: IfBlockContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.forStatement`.
	 * @param ctx the parse tree
	 */
	enterForStatement?: (ctx: ForStatementContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.forStatement`.
	 * @param ctx the parse tree
	 */
	exitForStatement?: (ctx: ForStatementContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.forBlock`.
	 * @param ctx the parse tree
	 */
	enterForBlock?: (ctx: ForBlockContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.forBlock`.
	 * @param ctx the parse tree
	 */
	exitForBlock?: (ctx: ForBlockContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.forControl`.
	 * @param ctx the parse tree
	 */
	enterForControl?: (ctx: ForControlContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.forControl`.
	 * @param ctx the parse tree
	 */
	exitForControl?: (ctx: ForControlContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.forInit`.
	 * @param ctx the parse tree
	 */
	enterForInit?: (ctx: ForInitContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.forInit`.
	 * @param ctx the parse tree
	 */
	exitForInit?: (ctx: ForInitContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.forUpdate`.
	 * @param ctx the parse tree
	 */
	enterForUpdate?: (ctx: ForUpdateContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.forUpdate`.
	 * @param ctx the parse tree
	 */
	exitForUpdate?: (ctx: ForUpdateContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.whileStatement`.
	 * @param ctx the parse tree
	 */
	enterWhileStatement?: (ctx: WhileStatementContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.whileStatement`.
	 * @param ctx the parse tree
	 */
	exitWhileStatement?: (ctx: WhileStatementContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.whileBlock`.
	 * @param ctx the parse tree
	 */
	enterWhileBlock?: (ctx: WhileBlockContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.whileBlock`.
	 * @param ctx the parse tree
	 */
	exitWhileBlock?: (ctx: WhileBlockContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.doWhileStatement`.
	 * @param ctx the parse tree
	 */
	enterDoWhileStatement?: (ctx: DoWhileStatementContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.doWhileStatement`.
	 * @param ctx the parse tree
	 */
	exitDoWhileStatement?: (ctx: DoWhileStatementContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.doWhileBlock`.
	 * @param ctx the parse tree
	 */
	enterDoWhileBlock?: (ctx: DoWhileBlockContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.doWhileBlock`.
	 * @param ctx the parse tree
	 */
	exitDoWhileBlock?: (ctx: DoWhileBlockContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.selfAddOrMinusStatement`.
	 * @param ctx the parse tree
	 */
	enterSelfAddOrMinusStatement?: (ctx: SelfAddOrMinusStatementContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.selfAddOrMinusStatement`.
	 * @param ctx the parse tree
	 */
	exitSelfAddOrMinusStatement?: (ctx: SelfAddOrMinusStatementContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.tryStoreStatement`.
	 * @param ctx the parse tree
	 */
	enterTryStoreStatement?: (ctx: TryStoreStatementContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.tryStoreStatement`.
	 * @param ctx the parse tree
	 */
	exitTryStoreStatement?: (ctx: TryStoreStatementContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.block`.
	 * @param ctx the parse tree
	 */
	enterBlock?: (ctx: BlockContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.block`.
	 * @param ctx the parse tree
	 */
	exitBlock?: (ctx: BlockContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.selfAddOrMinusExpression`.
	 * @param ctx the parse tree
	 */
	enterSelfAddOrMinusExpression?: (ctx: SelfAddOrMinusExpressionContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.selfAddOrMinusExpression`.
	 * @param ctx the parse tree
	 */
	exitSelfAddOrMinusExpression?: (ctx: SelfAddOrMinusExpressionContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.expressionList`.
	 * @param ctx the parse tree
	 */
	enterExpressionList?: (ctx: ExpressionListContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.expressionList`.
	 * @param ctx the parse tree
	 */
	exitExpressionList?: (ctx: ExpressionListContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.type`.
	 * @param ctx the parse tree
	 */
	enterType?: (ctx: TypeContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.type`.
	 * @param ctx the parse tree
	 */
	exitType?: (ctx: TypeContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.value`.
	 * @param ctx the parse tree
	 */
	enterValue?: (ctx: ValueContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.value`.
	 * @param ctx the parse tree
	 */
	exitValue?: (ctx: ValueContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.className`.
	 * @param ctx the parse tree
	 */
	enterClassName?: (ctx: ClassNameContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.className`.
	 * @param ctx the parse tree
	 */
	exitClassName?: (ctx: ClassNameContext) => void;

	/**
	 * Enter a parse tree produced by `MCFPPParser.functionTag`.
	 * @param ctx the parse tree
	 */
	enterFunctionTag?: (ctx: FunctionTagContext) => void;
	/**
	 * Exit a parse tree produced by `MCFPPParser.functionTag`.
	 * @param ctx the parse tree
	 */
	exitFunctionTag?: (ctx: FunctionTagContext) => void;
}

