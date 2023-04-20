import { CompilationUnitContext, NamespaceDeclarationContext, TypeDeclarationContext, ClassOrFunctionDeclarationContext, ClassDeclarationContext, ClassBodyContext, StaticClassMemberDeclarationContext, ClassMemberDeclarationContext, ClassMemberContext, ClassFunctionDeclarationContext, FunctionDeclarationContext, NamespaceIDContext, NativeDeclarationContext, JavaReferContext, StringNameContext, AccessModifierContext, ConstructorDeclarationContext, NativeConstructorDeclarationContext, ConstructorCallContext, FieldDeclarationContext, ParameterListContext, ParameterContext, ExpressionContext, StatementExpressionContext, ConditionalExpressionContext, ConditionalOrExpressionContext, ConditionalAndExpressionContext, EqualityExpressionContext, RelationalExpressionContext, RelationalOpContext, AdditiveExpressionContext, MultiplicativeExpressionContext, UnaryExpressionContext, BasicExpressionContext, CastExpressionContext, PrimaryContext, VarWithSelectorContext, VarContext, IdentifierSuffixContext, SelectorContext, ArgumentsContext, FunctionBodyContext, FunctionCallContext, StatementContext, OrgCommandContext, ControlStatementContext, IfStatementContext, ElseIfStatementContext, IfBlockContext, ForStatementContext, ForBlockContext, ForControlContext, ForInitContext, ForUpdateContext, WhileStatementContext, WhileBlockContext, DoWhileStatementContext, DoWhileBlockContext, SelfAddOrMinusStatementContext, TryStoreStatementContext, BlockContext, SelfAddOrMinusExpressionContext, ExpressionListContext, TypeContext, ValueContext, ClassNameContext, FunctionTagContext } from "../antlr/MCFPPParser.js";
import { MCFPPVisitor } from "../antlr/MCFPPVisitor.js";
import { AbstractParseTreeVisitor } from "antlr4ts/tree/AbstractParseTreeVisitor.js";


export class MCFPPWorkVisitor extends AbstractParseTreeVisitor<string[]>
    implements MCFPPVisitor<string[]> {
    protected defaultResult(): string[] {
        throw [];
    }
    visitCompilationUnit(ctx: CompilationUnitContext) {
        console.log("this is first")
        console.log(ctx.text);
        return []
    }
    visitNamespaceDeclaration?: ((ctx: NamespaceDeclarationContext) => string[]) | undefined;
    visitTypeDeclaration?: ((ctx: TypeDeclarationContext) => string[]) | undefined;
    visitClassOrFunctionDeclaration?: ((ctx: ClassOrFunctionDeclarationContext) => string[]) | undefined;
    visitClassDeclaration?: ((ctx: ClassDeclarationContext) => string[]) | undefined;
    visitClassBody?: ((ctx: ClassBodyContext) => string[]) | undefined;
    visitStaticClassMemberDeclaration?: ((ctx: StaticClassMemberDeclarationContext) => string[]) | undefined;
    visitClassMemberDeclaration?: ((ctx: ClassMemberDeclarationContext) => string[]) | undefined;
    visitClassMember?: ((ctx: ClassMemberContext) => string[]) | undefined;
    visitClassFunctionDeclaration?: ((ctx: ClassFunctionDeclarationContext) => string[]) | undefined;
    visitFunctionDeclaration?: ((ctx: FunctionDeclarationContext) => string[]) | undefined;
    visitNamespaceID?: ((ctx: NamespaceIDContext) => string[]) | undefined;
    visitNativeDeclaration?: ((ctx: NativeDeclarationContext) => string[]) | undefined;
    visitJavaRefer?: ((ctx: JavaReferContext) => string[]) | undefined;
    visitStringName?: ((ctx: StringNameContext) => string[]) | undefined;
    visitAccessModifier?: ((ctx: AccessModifierContext) => string[]) | undefined;
    visitConstructorDeclaration?: ((ctx: ConstructorDeclarationContext) => string[]) | undefined;
    visitNativeConstructorDeclaration?: ((ctx: NativeConstructorDeclarationContext) => string[]) | undefined;
    visitConstructorCall?: ((ctx: ConstructorCallContext) => string[]) | undefined;
    visitFieldDeclaration?: ((ctx: FieldDeclarationContext) => string[]) | undefined;
    visitParameterList?: ((ctx: ParameterListContext) => string[]) | undefined;
    visitParameter?: ((ctx: ParameterContext) => string[]) | undefined;
    visitExpression?: ((ctx: ExpressionContext) => string[]) | undefined;
    visitStatementExpression?: ((ctx: StatementExpressionContext) => string[]) | undefined;
    visitConditionalExpression?: ((ctx: ConditionalExpressionContext) => string[]) | undefined;
    visitConditionalOrExpression?: ((ctx: ConditionalOrExpressionContext) => string[]) | undefined;
    visitConditionalAndExpression?: ((ctx: ConditionalAndExpressionContext) => string[]) | undefined;
    visitEqualityExpression?: ((ctx: EqualityExpressionContext) => string[]) | undefined;
    visitRelationalExpression?: ((ctx: RelationalExpressionContext) => string[]) | undefined;
    visitRelationalOp?: ((ctx: RelationalOpContext) => string[]) | undefined;
    visitAdditiveExpression?: ((ctx: AdditiveExpressionContext) => string[]) | undefined;
    visitMultiplicativeExpression?: ((ctx: MultiplicativeExpressionContext) => string[]) | undefined;
    visitUnaryExpression?: ((ctx: UnaryExpressionContext) => string[]) | undefined;
    visitBasicExpression?: ((ctx: BasicExpressionContext) => string[]) | undefined;
    visitCastExpression?: ((ctx: CastExpressionContext) => string[]) | undefined;
    visitPrimary?: ((ctx: PrimaryContext) => string[]) | undefined;
    visitVarWithSelector?: ((ctx: VarWithSelectorContext) => string[]) | undefined;
    visitVar?: ((ctx: VarContext) => string[]) | undefined;
    visitIdentifierSuffix?: ((ctx: IdentifierSuffixContext) => string[]) | undefined;
    visitSelector?: ((ctx: SelectorContext) => string[]) | undefined;
    visitArguments?: ((ctx: ArgumentsContext) => string[]) | undefined;
    visitFunctionBody?: ((ctx: FunctionBodyContext) => string[]) | undefined;
    visitFunctionCall?: ((ctx: FunctionCallContext) => string[]) | undefined;
    visitStatement?: ((ctx: StatementContext) => string[]) | undefined;
    visitOrgCommand?: ((ctx: OrgCommandContext) => string[]) | undefined;
    visitControlStatement?: ((ctx: ControlStatementContext) => string[]) | undefined;
    visitIfStatement?: ((ctx: IfStatementContext) => string[]) | undefined;
    visitElseIfStatement?: ((ctx: ElseIfStatementContext) => string[]) | undefined;
    visitIfBlock?: ((ctx: IfBlockContext) => string[]) | undefined;
    visitForStatement?: ((ctx: ForStatementContext) => string[]) | undefined;
    visitForBlock?: ((ctx: ForBlockContext) => string[]) | undefined;
    visitForControl?: ((ctx: ForControlContext) => string[]) | undefined;
    visitForInit?: ((ctx: ForInitContext) => string[]) | undefined;
    visitForUpdate?: ((ctx: ForUpdateContext) => string[]) | undefined;
    visitWhileStatement?: ((ctx: WhileStatementContext) => string[]) | undefined;
    visitWhileBlock?: ((ctx: WhileBlockContext) => string[]) | undefined;
    visitDoWhileStatement?: ((ctx: DoWhileStatementContext) => string[]) | undefined;
    visitDoWhileBlock?: ((ctx: DoWhileBlockContext) => string[]) | undefined;
    visitSelfAddOrMinusStatement?: ((ctx: SelfAddOrMinusStatementContext) => string[]) | undefined;
    visitTryStoreStatement?: ((ctx: TryStoreStatementContext) => string[]) | undefined;
    visitBlock?: ((ctx: BlockContext) => string[]) | undefined;
    visitSelfAddOrMinusExpression?: ((ctx: SelfAddOrMinusExpressionContext) => string[]) | undefined;
    visitExpressionList?: ((ctx: ExpressionListContext) => string[]) | undefined;
    visitType?: ((ctx: TypeContext) => string[]) | undefined;
    visitValue?: ((ctx: ValueContext) => string[]) | undefined;
    visitClassName?: ((ctx: ClassNameContext) => string[]) | undefined;
    visitFunctionTag?: ((ctx: FunctionTagContext) => string[]) | undefined;
}