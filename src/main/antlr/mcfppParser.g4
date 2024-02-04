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
    tokenVocab = mcfppLexer;
}

//一个mcfpp文件
compilationUnit
    :   importDeclaration*
        namespaceDeclaration?
        statement*
        typeDeclaration*
        EOF
    ;

//命名空间声明
namespaceDeclaration
    :   doc_comment? NAMESPACE Identifier (DOT Identifier)* ';'
    ;

importDeclaration
    :   IMPORT Identifier ('.' Identifier)* ('.' cls = (ClassIdentifier|'*'))? ';'
    ;

//类或函数声明
typeDeclaration
    :   doc_comment? declarations
    ;

//类或函数声明
declarations
    :   classDeclaration
    |   functionDeclaration
    |   inlineFunctionDeclaration
    |   nativeFuncDeclaration
    |   compileTimeFuncDeclaration
    |   compileTimeClassDeclaration
    |   nativeClassDeclaration
    |   templateDeclaration
    |   extensionFunctionDeclaration
    |   interfaceDeclaration
    |   globalDeclaration
    ;

//全局声明
globalDeclaration
    :   GLOBAL '{' (doc_comment? fieldDeclaration ';')* '}'
    ;

//类声明
classDeclaration
    :   classAnnotation? STATIC? FINAL? ABSTRACT? CLASS classWithoutNamespace (':' className (',' className)*)? classBody
    ;

compileTimeClassDeclaration
    :   CONST CLASS classWithoutNamespace (':' className (',' className)*)? classBody
    ;

nativeClassDeclaration
    :   CLASS classWithoutNamespace '=' javaRefer ';'
    ;

staticClassMemberDeclaration
    :   accessModifier? STATIC classMember
    ;

classMemberDeclaration
    :   accessModifier? classMember
    ;

classBody
    :   '{' (doc_comment? (classMemberDeclaration|staticClassMemberDeclaration))* '}'
    ;

//类成员
classMember
    :   classFunctionDeclaration
    |   classFieldDeclaration ';'
    |   constructorDeclaration
    |   nativeClassFunctionDeclaration
    |   abstractClassFunctionDeclaration
    ;

classFunctionDeclaration
    :   funcAnnoation? OVERRIDE? FUNCTION Identifier '(' parameterList? ')' (':' functionReturnType)? '{' functionBody '}'
    ;

abstractClassFunctionDeclaration
    :   funcAnnoation? OVERRIDE? FUNCTION Identifier '(' parameterList? ')' (':' functionReturnType)? ';'
    ;

nativeClassFunctionDeclaration
    :   funcAnnoation? OVERRIDE? FUNCTION Identifier '(' parameterList? ')' (':' functionReturnType)? '=' javaRefer ';'
    ;

classFieldDeclaration
    :   accessModifier? type fieldDeclarationExpression
    ;

//数据模板
templateDeclaration
    :   FINAL? TEMPLATE genericity classWithoutNamespace (EXTENDS className)? templateBody
    ;

templateBody
    :   '{' (doc_comment? (templateMemberDeclaration|staticTemplateMemberDeclaration))* '}'
    ;

templateMemberDeclaration
    :   accessModifier? templateMember
    ;

staticTemplateMemberDeclaration
    :   accessModifier? STATIC? templateMember
    ;

templateMember
    :   templateFunctionDeclaration
    |   templateFieldDeclaration ';'
    ;

templateFunctionDeclaration
    :  FUNCTION Identifier '(' parameterList? ')' (':' functionReturnType)? '{' functionBody '}'
    ;

templateFieldDeclaration
    :   (templateFieldDeclarationExpression ',')* templateFieldDeclarationExpression ';'
    ;

templateFieldDeclarationExpression
    :   CONST? type? Identifier ':' expression
    ;


//接口声明
interfaceDeclaration
    :   classAnnotation? INTERFACE classWithoutNamespace (':' className (',' className)*)? interfaceBody
    ;

interfaceBody
    :   '{'( doc_comment? interfaceFunctionDeclaration )* '}'
    ;

interfaceFunctionDeclaration
    :   funcAnnoation? FUNCTION Identifier '(' parameterList? ')' (':' functionReturnType)? ';'
    ;

compileTimeFuncDeclaration
    :   CONST FUNCTION Identifier '(' parameterList? ')' (':' functionReturnType)? '{' functionBody '}'
    ;

inlineFunctionDeclaration
    :   funcAnnoation? INLINE FUNCTION Identifier '(' parameterList? ')' (':' functionReturnType)? '{' functionBody '}'
    ;

//函数声明
functionDeclaration
    :   funcAnnoation? FUNCTION Identifier '(' parameterList? ')' (':' functionReturnType)? '{' functionBody '}'
    ;

extensionFunctionDeclaration
    :   funcAnnoation? STATIC? FUNCTION (type '.')? Identifier '(' parameterList? ')' (':' functionReturnType)? '{' functionBody '}'
    ;

namespaceID
    : (Identifier ( '.' Identifier)* ':')? Identifier
    ;

nativeFuncDeclaration
    :   funcAnnoation? FUNCTION Identifier '(' parameterList? ')' (':' functionReturnType)? '=' javaRefer ';'
    ;

javaRefer
    :   stringName ('.' stringName)*
    ;

stringName
    :   Identifier
    |   ClassIdentifier
    |   NormalString
    ;

accessModifier
    :   PRIVATE
    |   PROTECTED
    |   PUBLIC
    ;

//构造函数声明
constructorDeclaration
    :   funcAnnoation? className '(' parameterList? ')' '{' functionBody '}'
    ;

//构造函数的调用
constructorCall
    :   className arguments
    ;

//变量声明
fieldDeclaration
    :   fieldModifier? type fieldDeclarationExpression (',' fieldDeclarationExpression)*
    |   fieldModifier? VAR Identifier '=' expression
    ;

fieldDeclarationExpression
    :   Identifier ( '=' expression)?
    ;

fieldModifier : CONST|DYNAMIC|IMPORT;

//参数列表
parameterList
    :   parameter (',' parameter)*
    ;

//参数
parameter
    :   STATIC? CONCRETE? type Identifier
    ;

//表达式
expression
    :   primary
    |   conditionalOrExpression
    ;

//expression
//    :   conditionalOrExpression
//    ;

//能作为语句的表达式
statementExpression
    :   (basicExpression '=' )? expression
    ;

//条件表达式
conditionalExpression
    :   conditionalOrExpression ( '?' expression ':' expression )?
    ;

//或
conditionalOrExpression
    :   conditionalAndExpression ( '||' conditionalAndExpression )*
    ;

//与
conditionalAndExpression
    :   equalityExpression ( '&&' equalityExpression )*
    ;

//等同
equalityExpression
    :   relationalExpression ( op=('==' | '!=') relationalExpression )?
    ;

//比较关系
relationalExpression
    :   additiveExpression ( relationalOp additiveExpression )?
    ;

//比较关系运算符
relationalOp
    :   '<='
    |   '>='
    |   '<'
    |   '>'
    ;

//加减
additiveExpression
    :   multiplicativeExpression ( op=('+' | '-') multiplicativeExpression )*
    ;

//乘除
multiplicativeExpression
    :   unaryExpression ( op=( '*' | '/' | '%' ) unaryExpression )*
    ;

//一元表达式
unaryExpression
    :   '!' unaryExpression
    |   castExpression
    |   rightVarExpression
    ;

//右侧计算式取出的变量
rightVarExpression
    :   basicExpression
    ;

//强制类型转换表达式
castExpression
    :  '(' type ')' unaryExpression
    ;

basicExpression
    :   primary
    |   varWithSelector
    ;
//初级表达式
primary
    :   var
    |   value
    |   constructorCall
    |   THIS
    |   SUPER
    |   TargetSelector
    ;

varWithSelector
    : primary selector+
    | type selector+
    ;

var
    :   '(' expression ')'
    |   Identifier identifierSuffix*
    |   namespaceID arguments
    ;

identifierSuffix
    :   '[' conditionalExpression ']'
    ;

selector
    :   '.' var
    ;

arguments
    :   '(' expressionList? ')'
    ;

functionBody
    :   statement*
    ;

//functionCall
//    :   namespaceID arguments
//    |   varWithSelector arguments
//    ;

statement
    :   fieldDeclaration ';'
    |   statementExpression ';'
//    |   functionCall ';'
    |   ifStatement
    |   forStatement
    |   whileStatement
    |   doWhileStatement ';'
    |   ';'
    |   selfAddOrMinusStatement ';'
    |   tryStoreStatement
    |   controlStatement ';'
    |   orgCommand
    |   returnStatement ';'
    ;

orgCommand
    :   OrgCommand
    ;



controlStatement
    :   BREAK
    |   CONTINUE
    ;

ifStatement
    :   IF'('expression')' ifBlock elseIfStatement* elseStatement?
    ;

elseIfStatement
    :   ELSE IF '('expression')' ifBlock
    ;

elseStatement
    :   ELSE ifBlock
    ;

ifBlock
    :   block
    ;

forStatement
    :   FOR '(' forControl ')' forBlock
    ;

forBlock
    :   block
    ;

forControl
    :   forInit ';' expression? ';' forUpdate
    ;

forInit
    :   (fieldDeclaration|statementExpression)?
    |   (fieldDeclaration|statementExpression) (',' (fieldDeclaration|statementExpression))+
    ;

forUpdate
    :   statementExpression*
    ;

whileStatement
    :   WHILE '(' expression ')' whileBlock
    ;

whileBlock
    :   block
    ;

doWhileStatement
    :   DO doWhileBlock WHILE '(' expression ')'
    ;

doWhileBlock
    :   block
    ;

selfAddOrMinusStatement
    :   selfAddOrMinusExpression
    ;

tryStoreStatement
    :   TRY block  STORE '(' Identifier ')' ';'
    ;

returnStatement
    : RETURN expression?
    ;

block
    :   '{' statement* '}'
    ;

selfAddOrMinusExpression
    :   rightVarExpression op = ('++'|'--')
    ;

expressionList
    :   expression (',' expression)*
    ;

type
    :   INT
    |   ENTITY
    |   BOOL
    |   FLOAT
    |   SELECTOR
    |   STRING
    |   JTEXT
    |   NBT
    |   ANY
    |   VEC IntegerLiteral         //向量
    |   className
    ;

functionReturnType
    :   type
    |   VOID
    ;

value
    :   IntegerLiteral
    |   FloatLiteral
    |   LineString
    |   BooleanLiteral
    |   multiLineStringLiteral
    |   nbtValue
    |   range
    ;

className
    :   (Identifier ('.' Identifier)* ':')? classWithoutNamespace
    ;

classWithoutNamespace
    :   ClassIdentifier
    ;

funcAnnoation
    :   '@' id=(Identifier|ClassIdentifier) arguments?
    ;
classAnnotation
    :   '@' id=(Identifier|ClassIdentifier) arguments?
    ;

genericity
    : '<' type '>'
    ;



range
    :   num1=(IntegerLiteral|FloatLiteral) '..' num2=(IntegerLiteral|FloatLiteral)
    |   num1=(IntegerLiteral|FloatLiteral) '..'
    |   '..' num2=(IntegerLiteral|FloatLiteral)
    ;




nbtValue
    :   LineString
    |   NBTByteLiteral
    |   NBTShortLiteral
    |   NBTIntLiteral
    |   NBTLongLiteral
    |   NBTFloatLiteral
    |   NBTDoubleLiteral
    |   nbtCompound
    |   nbtList
    |   nbtByteArray
    |   nbtIntArray
    |   nbtLongArray
    ;



nbtByteArray: '[B;' NBTByteLiteral (',' NBTByteLiteral)* ']';
nbtIntArray: '[I;' NBTIntLiteral (',' NBTIntLiteral)* ']';
nbtLongArray: '[L;' NBTLongLiteral (',' NBTLongLiteral)* ']';

nbtList: '[' (nbtValue (',' nbtValue)* )* ']';
nbtKeyValuePair: key=(Identifier|ClassIdentifier) ':' nbtValue;
nbtCompound: '{'( nbtKeyValuePair (',' nbtKeyValuePair)* )*'}';

multiLineStringLiteral
    : TRIPLE_QUOTE_OPEN multiLineStringContent * TRIPLE_QUOTE_CLOSE
    ;

multiLineStringContent
    : multiLineStringExpression
    | MultiLineStrText
    | MultiLineStringQuote
    ;

multiLineStringExpression
    : MultiLineStrExprStart  expression '}'
    ;
//
// Whitespace and comments
//


doc_comment
    :   DOC_COMMENT
    ;

