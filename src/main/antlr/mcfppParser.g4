/*
 [The "BSD licence"]
 Copyright (c) 2016 Pascal Gruen
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:
 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
 3. The name of the author may not be used to endorse or promote products
    derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

parser grammar mcfppParser;

options {
    // Regenerate the standard library whenever this grammar or its vocabulary changes.
    tokenVocab = mcfppLexer;
}

// Compatibility-only token. The lexer never emits it; the shim alternatives
// below exist solely to keep the generated accessor surface usable by the
// existing read-only consumers while they migrate to the new rules.
tokens { LEGACY_ACCESSOR_SHIM }

@members {
private boolean startsExplicitObjectLiteral() {
    if (_input.LA(1) != LCURL) return false;
    int offset = 2;
    while (_input.LA(offset) == NL) offset++;
    int key = _input.LA(offset++);
    if (key != Identifier && key != DATA && key != ENTITY && key != TYPE && key != NBT && key != LineString)
        return false;
    while (_input.LA(offset) == NL) offset++;
    return _input.LA(offset) == COLON;
}

private boolean startsSignedNumericRange() {
    // Keep the sign on the left endpoint, including the minimum integer literal.
    if (_input.LA(1) != SUB || _input.LA(3) != RANGE) return false;
    int number = _input.LA(2);
    return number == NBTByte || number == NBTShort || number == NBTInt ||
        number == NBTLong || number == NBTFloat || number == NBTDouble;
}

private boolean endsReadonlyCallArguments() {
    if (_input.LA(1) != RANGLE) return false;
    int offset = 2;
    while (_input.LA(offset) == NL) offset++;
    if (_input.LA(offset) != LPAREN) return false;
    // A top-level readonly argument ends before the ordinary argument list.
    // A grouped comparison or a nested call owns a nearer expression context.
    ParserRuleContext context = _ctx;
    while (context != null && !(context instanceof ExpressionContext)) context = context.getParent();
    if (context == null || !(context.getParent() instanceof ExpressionListContext) ||
        !(context.getParent().getParent() instanceof ReadOnlyArgsContext)) return false;
    // In f<a > (b)>(), the first group is the comparison's right operand.
    // Closing there instead would leave an empty () as a comparison operand.
    int depth = 0;
    do {
        int token = _input.LA(offset++);
        if (token == Token.EOF) return true;
        if (token == LPAREN) depth++;
        else if (token == RPAREN) depth--;
    } while (depth > 0);
    while (_input.LA(offset) == NL) offset++;
    if (_input.LA(offset++) != RANGLE) return true;
    while (_input.LA(offset) == NL) offset++;
    if (_input.LA(offset++) != LPAREN) return true;
    while (_input.LA(offset) == NL) offset++;
    return _input.LA(offset) != RPAREN;
}
}

//一个mcfpp文件
compilationUnit
    :   (separator* namespaceDeclaration (separator | EOF))?
        (separator* importDeclaration (separator | EOF))*
        (separator* typealiasDeclaration (separator | EOF))*
        (separator* topStatement)?
        (separator* typeDeclaration (separator | EOF))*
        separator* EOF
    ;

separator
    :   (SEMICOLON | NL)+
    ;

topStatement
    : (statement (separator | EOF))+
    ;

//命名空间声明
namespaceDeclaration
    :   doc_comment? NL* NAMESPACE namespacePath
    ;

importDeclaration
    :   IMPORT importType (AS Identifier)? (FROM Identifier)?
    ;

importType
    :   namespacePath COLON (Identifier nestedClassName* | MULT)
    ;

typealiasDeclaration
    :   doc_comment? NL* TYPEALIAS type AS Identifier
    ;

//类或函数声明
typeDeclaration
    :   doc_comment? NL* declarations
    ;

//类或函数声明
declarations
    :   functionDeclaration
    |   inlineFunctionDeclaration
    |   nativeFuncDeclaration
    |   compileTimeFuncDeclaration
    |   templateDeclaration
    |   objectTemplateDeclaration
    |   extensionFunctionDeclaration
    |   enumDeclaration
    |   annotation
    ;

operationOverrideDeclaration
    :   OPERATOR supportOperator NL* functionParams NL* (COLON functionReturnType)? NL* curlBlock
    ;

nativeOperationOverrideDeclaration
    :   OPERATOR supportOperator NL* functionParams NL* (COLON functionReturnType)? NL* ASSIGNMENT NL* javaRefer
    ;

supportOperator
    :   ADD
    |   SUB
    |   MULT
    |   SLASH
    |   MOD
    |   RANGLE
    |   LANGLE
    |   GE
    |   LE
    |   EQEQ
    |   EXCL_EQ
    |   WVEQ
    |   DISJ
    |   CONJ
    |   PIPE
    |   Identifier
    ;

accessor
    :   LCURL NL* (getter (separator+ setter)? | setter)? separator* RCURL
    ;

getter
    :   GET NL* curlBlock
    |   GET NL* ASSIGNMENT NL* javaRefer
    |   GET NL* ASSIGNMENT NL* expression
    |   GET
    ;

setter
    :   SET NL* curlBlock
    |   SET NL* ASSIGNMENT javaRefer
    |   SET NL* ASSIGNMENT expression
    |   SET
    ;

compoundDeclaration
    :   declarationName NL* (COLON NL* extendName NL* (COMMA NL* extendName NL*)*)?
    ;

//数据模板
templateDeclaration
    :   (FINAL | ABSTRACT)? NL* (DATA | ENTITY) NL*
        (compoundDeclaration
        | (declarationName NL* AS NL* type)
        )
        NL* templateBody?
    ;

//数据模板
objectTemplateDeclaration
    :   FINAL? NL* OBJECT NL* DATA NL* compoundDeclaration NL* templateBody?
    ;

templateBody
    :   LCURL NL* (doc_comment? NL* templateMemberDeclaration separator*)* RCURL
    ;

templateMemberDeclaration
    :   accessModifier? NL* templateMember
    ;

templateMember
    :   templateFunctionDeclaration
    |   templateDeclaration
    |   templateFieldDeclaration
    |   templateConstructorDeclaration
    |   templateFactoryDeclaration
    |   operationOverrideDeclaration
    |   nativeOperationOverrideDeclaration
    |   annotation
    ;

functionDeclarationPart
    :   contextParameters? NL* FUNCTION NL* Identifier NL* functionParams (NL* COLON NL* functionReturnType)?
    ;

contextParameters
    :   CONTEXT NL* LPAREN NL* contextParameter (NL* COMMA NL* contextParameter)* NL* RPAREN
    ;

contextParameter
    :   Identifier NL* COLON NL* type
    ;

templateFunctionDeclaration
    :   ENTITY? NL* OVERRIDE? NL* ABSTRACT? NL* functionDeclarationPart (NL* curlBlock)?
    ;

templateFieldDeclaration
    :   ENTITY? NL* (VAR | CONST) NL* Identifier NL* (COLON NL* templateType)? (NL* ASSIGNMENT expression)? NL* accessor?
    ;

templateType
    :   singleTemplateFieldType | unionTemplateFieldType
    ;

singleTemplateFieldType
    :   type QUEST?
    ;

unionTemplateFieldType
    :   LPAREN NL* type NL* (PIPE type NL*)* RPAREN QUEST?
    ;

declarationName
    :   classWithoutNamespace NL* readOnlyParams?
    ;

extendName
    :   className readOnlyArgs?
    ;

compileTimeFuncDeclaration
    :   CONST NL* functionDeclarationPart NL* curlBlock
    ;

inlineFunctionDeclaration
    :   contextParameters? NL* INLINE NL* functionDeclarationPart NL* curlBlock
    ;

//函数声明
functionDeclaration
    :   functionDeclarationPart NL* curlBlock
    ;

extensionFunctionDeclaration
    :   contextParameters? NL* FUNCTION NL* (type DOT)? Identifier functionParams (NL* COLON NL* functionReturnType)? NL* curlBlock
    ;

//枚举
enumDeclaration
    :   ENUM NL* Identifier NL* enumBody
    ;

enumBody
    :   LCURL NL* enumMember (NL* COMMA NL* enumMember)* NL* RCURL
    ;

enumMember
    :   doc_comment? NL* Identifier NL* (ASSIGNMENT NL* nbtValue)?
    ;


namespaceID
    : (namespacePath COLON)? Identifier
    ;

namespacePath
    : namespaceSegment (DOT namespaceSegment)*
    ;

namespaceSegment
    : Identifier | ENTITY
    ;

nativeFuncDeclaration
    :   functionDeclarationPart NL* ASSIGNMENT NL* javaRefer SEMICOLON?
    ;

javaRefer
    :   (Identifier | CONTEXT) (DOT (Identifier | CONTEXT))*
    ;

accessModifier
    :   PRIVATE
    |   PROTECTED
    |   PUBLIC
    ;

//构造函数声明
templateConstructorDeclaration
    :   CONSTRUCTOR NL* normalParams? NL* (COLON NL* functionCall (NL* curlBlock)? | curlBlock)
    ;

// Factories are ordinary functions associated with the enclosing template.
// Their return type is the complete instantiated Self, and there is no implicit this.
templateFactoryDeclaration
    :   NEW NL* normalParams NL* curlBlock
    ;

//变量声明
fieldDeclaration
    :   doc_comment? NL* (VAR | fieldModifier) NL* Identifier NL* (COLON NL* type)? NL* (ASSIGNMENT NL* expression)?
    ;

fieldModifier : CONST | EXTERNAL | IMPORT NL* VAR;

functionParams
    :   readOnlyParams? NL* normalParams
    ;

readOnlyParams
    :   LANGLE NL* readOnlyParameterList? NL* RANGLE
    ;

normalParams
    :   LPAREN NL* parameterList? NL* RPAREN
    ;

// 只读参数允许省略类型标注，<T> 等价于 <T: type>。
readOnlyParameterList
    :   readOnlyParameter (NL* COMMA NL* readOnlyParameter)*
    ;

readOnlyParameter
    :   (OUT | IN)? NL* Identifier (NL* COLON NL* type)? (NL* ASSIGNMENT NL* value)?
    ;

// 普通参数必须声明名称和类型。
parameterList
    :   parameter (NL* COMMA NL* parameter)*
    ;

parameter
    :   Identifier NL* COLON NL* type (NL* ASSIGNMENT NL* value)?
    ;

//能作为语句的表达式
statementExpression
    :   (varWithSelector assignmentOperator NL* )? expression
    ;

assignmentOperator
    :   ASSIGNMENT | ADD_ASSIGNMENT | SUB_ASSIGNMENT | MULT_ASSIGNMENT | DIV_ASSIGNMENT | MOD_ASSIGNMENT
    ;

// Top-level expression. A trailing lambda is accepted only here, not in the
// condition-specific expression below.
expression
    :   lambdaCallExpression
    |   commonBinaryOperatorExpression
    |   primary
    ;

// A trailing lambda belongs to the call it follows. Conditions use
// conditionExpression so that the block of an if/while cannot be mistaken
// for a trailing lambda; parenthesize such a call in a condition instead.
lambdaCallExpression
    :   jvmAccessExpression (NL* selector)+ NL* inlineLambda (NL* selector)*
    |   functionCall NL* inlineLambda (NL* selector)*
    |   varWithSuffix NL* inlineLambda (NL* selector)*
    ;

conditionExpression
    :   commonBinaryOperatorExpression
    |   primary
    ;

//其他运算符
commonBinaryOperatorExpression
    :   conditionalOrExpression (op+=(PIPE | Identifier) NL* conditionalOrExpression)*
    ;

//或
conditionalOrExpression
    :   conditionalAndExpression (op+=DISJ NL* conditionalAndExpression )*
    ;

//与
conditionalAndExpression
    :   equalityExpression (op+=CONJ NL* equalityExpression )*
    ;

//等同
equalityExpression
    :   relationalExpression (op+=(EQEQ | EXCL_EQ | WVEQ) NL* relationalExpression )*
    ;

//比较关系
relationalExpression
    :   elvisExpression ({!endsReadonlyCallArguments()}? op+=(LANGLE | RANGLE | LE | GE) NL* elvisExpression )*
    ;

elvisExpression
    :   additiveExpression (ELVIS NL* elvisOperand)*
    ;

elvisOperand
    :   additiveExpression
    |   returnExpression
    ;

returnExpression
    :   RETURN expression?
    ;

//加减
additiveExpression
    :   multiplicativeExpression (op+=(ADD | SUB) NL* multiplicativeExpression )*
    ;

//乘除
multiplicativeExpression
    :   castExpression (op+=(MULT | SLASH | MOD) NL* castExpression )*
    ;

//强制类型转换表达式
castExpression
    :  unaryExpression (AS NL* type)?
    ;

//一元表达式
unaryExpression
    :   {!startsSignedNumericRange()}? (EXCL | SUB) NL* unaryExpression
    |   rightVarExpression
    ;

//右侧计算式取出的变量
rightVarExpression
    :   varWithSelector
    ;

varWithSelector
    : jvmAccessExpression (NL* selector)*
    ;

jvmAccessExpression
    :   propertyOperator
    ;

//字段操作器
propertyOperator
    :   primary (NL* LSQUARE NL* propertyOperatorExpression (NL* COMMA NL* propertyOperatorExpression)* NL* RSQUARE)?
    ;

propertyOperatorExpression
    :   Identifier NL* ASSIGNMENT NL* expression
    ;

//初级表达式
primary
    :   range
    |   {startsExplicitObjectLiteral()}? value
    |   inlineLambda
    |   value
    |   var
    |   THIS
    |   SUPER
    |   type
    ;

var
    :   bucketExpression
    |   functionCall
    |   varWithSuffix
    ;

bucketExpression
    :   LPAREN NL* (expression NL*)? RPAREN
    ;

varWithSuffix
    :   Identifier (NL* identifierSuffix)*
    ;

functionCall
    :   namespaceID NL* arguments
    ;

identifierSuffix
    :   LSQUARE NL* (expression NL*)? RSQUARE
    ;

selector
    :   (DOT | SAFE_DOT) NL* var (NL* inlineLambda)?
    |   (DOT | SAFE_DOT) NL* memberKeyword NL* arguments? (NL* inlineLambda)?
    |   EXCL EXCL (NL* identifierSuffix)*
    ;

// Type/accessor keywords can also name ordinary member calls.
memberKeyword
    :   MAP
    |   NEW
    |   ANY
    |   GET
    |   SET
    |   JTEXT
    |   SELECTOR
    ;

arguments
    :   readOnlyArgs? NL* normalArgs
    ;

readOnlyArgs
    :   LANGLE NL* (expressionList NL*)? RANGLE
    ;

normalArgs
    :   LPAREN NL* ((expressionList | callArgumentList) NL*)? RPAREN
    ;

// Keep expressionList first so existing visitors retain their ordinary-call tree.
// The mixed form preserves the syntax category and order for the pure call binder.
callArgumentList
    :   callArgument (NL* COMMA NL* callArgument)*
    ;

callArgument
    // Retain the old node only for located migration errors, including old cached ASTs.
    :   legacy=REF NL* expression {notifyErrorListeners($legacy, "Legacy 'ref expression' is no longer supported; create ScoreRef or NBTRef<T> from an explicit Minecraft address and pass it as an ordinary value.", null);}
    |   inlineLambda
    |   Identifier NL* ASSIGNMENT NL* expression
    |   expression
    ;

// Kotlin-style lambda. The parameter list requires the arrow; a no-parameter
// lambda has no arrow. The body keeps the last expression distinct from a
// semicolon-terminated statement.
inlineLambda
    :   LCURL NL* (lambdaParameterList NL* ARROW NL*)? lambdaBody NL* RCURL
    |   LEGACY_ACCESSOR_SHIM Identifier+ curlBlock
    ;

// Diagnostic-only entry point. It is intentionally not reachable from
// compilationUnit, so (args) => { ... } is not valid source. Migration code can
// probe this rule after a source parse failure at DOUBLE_ARROW to report the
// precise old callback shape.
legacyLambdaSyntax
    :   LPAREN NL* (Identifier (NL* COMMA NL* Identifier)*)? NL* RPAREN
        NL* DOUBLE_ARROW NL* curlBlock
    ;

lambdaParameterList
    :   lambdaParameter (NL* COMMA NL* lambdaParameter)*
    ;

lambdaParameter
    :   Identifier (NL* COLON NL* type)?
    ;

lambdaBody
    :   lambdaTailExpression
    |   (statement separator)+ (lambdaTailExpression | statement)
    |   (statement separator)+
    |   statement
    |   // An empty synchronous callback is valid, including an explicit parameter list.
    ;

lambdaTailExpression
    :   expression
    ;

statement
    :   fieldDeclaration
    |   statementExpression
    |   ifStatement
    |   whileStatement
    |   doWhileStatement
    |   tryStoreStatement
    |   contextScopeStatement
    |   controlStatement
    |   orgCommand
    |   SEMICOLON
    |   returnStatement
    |   foreachStatement
    |   commandBlock
    ;

contextScopeStatement
    :   CONTEXT NL* normalArgs NL* block
    ;

foreachStatement
    :   FOR NL* LPAREN NL* Identifier NL* COLON NL* expression NL* RPAREN NL* block
    ;

orgCommand
    :   SLASH (NL* orgCommandContent)*
    ;

orgCommandContent
    :   orgCommandExpression
    |   OrgCommandText
    ;

orgCommandExpression
    :   OrgCommandExprStart expression RCURL
    ;

controlStatement
    :   BREAK
    |   CONTINUE
    ;

ifStatement
    :   IF NL* conditionExpression NL* block (NL* elseIfStatement)* (NL* elseStatement)?
    |   LEGACY_ACCESSOR_SHIM expression
    ;

elseIfStatement
    :   ELSE NL* IF NL* conditionExpression NL* block
    |   LEGACY_ACCESSOR_SHIM expression
    ;

elseStatement
    :   ELSE NL* block
    ;

whileStatement
    :   WHILE NL* conditionExpression NL* block
    |   LEGACY_ACCESSOR_SHIM expression
    ;

doWhileStatement
    :   DO NL* block NL* WHILE NL* expression
    ;

tryStoreStatement
    :   TRY NL* block NL* STORE NL* LPAREN NL* Identifier NL* RPAREN
    ;

returnStatement
    : RETURN expression?
    ;

curlBlock
    :   LCURL NL* (statement (separator statement)* separator?)? NL* RCURL
    ;

block
    :   curlBlock
    |   statement
    ;

expressionList
    :   expression (NL* COMMA NL* expression)*
    ;

type
    :   typeBody
    ;

typeBody
    :   normalType
    |   VecType
    |   (LIST | IMMUTABLE_LIST | MAP | DICT) NL* LANGLE NL* type NL* RANGLE
    |   ENTITY NL* LANGLE NL* nbtInt NL* RANGLE
    |   ENTITY NL* LANGLE NL* LineString (NL* COMMA NL* LineString)* NL* RANGLE
    |   ENTITY NL* LANGLE NL* nbtInt NL* COMMA NL* LineString (NL* COMMA NL* LineString)* NL* RANGLE
    |   className (NL* readOnlyArgs)?
    |   Identifier
    |   unionType
    |   unionTemplateType
    |   anonymousTemplateType
    |   literalObjectType
    |   functionType
    ;

// (int, int) -> int / () -> void. Parameter and result types stay
// ordinary type / functionReturnType rules so callers can inspect them.
functionType
    :   contextTypeParameters? NL* LPAREN NL* (type (NL* COMMA NL* type)*)? NL* RPAREN
        NL* ARROW NL* functionReturnType
    ;

contextTypeParameters
    :   CONTEXT NL* LPAREN NL* type (NL* COMMA NL* type)* NL* RPAREN
    ;

anonymousTemplateType
    :   DATA (NL* COLON NL* extendName (NL* COMMA NL* extendName)*)? NL* templateBody
    ;

literalObjectType
    :   LCURL NL* (literalObjectTypeField (NL* COMMA NL* literalObjectTypeField)*)? NL* RCURL
    ;

literalObjectTypeField
    :   Identifier NL* COLON NL* type
    ;

unionType
    :   LPAREN NL* type (NL* PIPE NL* type)+ NL* RPAREN
    ;

unionTemplateType
    :   LPAREN NL* type (NL* UNION NL* type)* NL* RPAREN
    ;

normalType
    :   OBJECT
    |   INT
    |   ENTITY
    |   BOOL
    |   BYTE
    |   SHORT
    |   LONG
    |   FLOAT
    |   DOUBLE
    |   SELECTOR
    |   STRING
    |   JTEXT
    |   NBT
    |   TYPE
    |   ANY
    |   BYTEARRAY
    |   INTARRAY
    |   LONGARRAY
    ;

functionReturnType
    :   type
    |   VOID
    ;

value
    :   coordinate
    |   LineString
    |   multiLineStringLiteral
    |   nbtValue
    |   TargetSelector
    |   NULL
    ;

coordinate
    :   coordinateDimension NL* coordinateDimension (NL* coordinateDimension)?
    ;

coordinateDimension
    :   (RelativeValue | nbtInt | nbtFloat | nbtDouble)
    ;

className
    :   (namespacePath COLON NL*)? classWithoutNamespace nestedClassName*
    ;

nestedClassName
    :   DOT Identifier
    ;

classWithoutNamespace
    :   Identifier
    ;

annotation
    :   AT id=Identifier NL* annotationArgs?
    ;

annotationArgs
    :   LPAREN (NL* value (NL* COMMA NL* value)*)? NL* RPAREN
    ;

range
    :   num1=range1 RANGE NL* num2=range1
    |   num1=range1 RANGE
    |   RANGE NL* num2=range1
    ;

range1: var | value;

nbtValue
    :   LineString
    |   nbtBool
    |   nbtByte
    |   nbtShort
    |   nbtInt
    |   nbtLong
    |   nbtFloat
    |   nbtDouble
    |   nbtCompound
    |   nbtList
    |   nbtByteArray
    |   nbtIntArray
    |   nbtLongArray
    ;

nbtByte: SUB? NBTByte;
nbtShort: SUB?  NBTShort;
nbtInt: SUB?  NBTInt;
nbtLong: SUB? NBTLong;
nbtFloat: SUB? NBTFloat;
nbtDouble: SUB? NBTDouble;
nbtBool: TRUE | FALSE;

nbtByteArray: NBT_BYTE_ARRAY_BEGIN NL* (nbtByte (NL* COMMA NL* nbtByte)*)? NL* RSQUARE;
nbtIntArray: NBT_INT_ARRAY_BEGIN NL* (nbtInt (NL* COMMA NL* nbtInt)*)? NL* RSQUARE;
nbtLongArray: NBT_LONG_ARRAY_BEGIN NL* (nbtLong (NL* COMMA NL* nbtLong)*)? NL* RSQUARE;

nbtList: LSQUARE NL* (expression (NL* COMMA NL* expression)*)? NL* RSQUARE;
nbtKeyValuePair
    :   key=(Identifier | DATA | ENTITY | TYPE | NBT | LineString) NL* COLON NL* expression
    |   key=Identifier
    ;
nbtCompound: LCURL (NL* nbtKeyValuePair (NL* COMMA NL* nbtKeyValuePair)* )* NL* RCURL;

multiLineStringLiteral
    : TRIPLE_QUOTE_OPEN multiLineStringContent* TRIPLE_QUOTE_CLOSE
    ;

multiLineStringContent
    : multiLineStringExpression
    | MultiLineStrText
    | MultiLineStringQuote
    ;

multiLineStringExpression
    : MultiLineStrExprStart NL* expression NL* RCURL
    ;
//
// Whitespace and comments
//

doc_comment
    :   DOC_COMMENT
    ;

// A lexical command block supplies implicit command slashes and removes the
// slash that introduces an ordinary MCFPP statement.
commandBlock
    :   COMMAND_BLOCK_OPEN NL* (statement (separator statement)* separator?)? NL* RCURL
    ;
