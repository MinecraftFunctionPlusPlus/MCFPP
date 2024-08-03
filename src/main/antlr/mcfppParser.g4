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

//TODO 类型别名和导入库文件的语句顺序不用这么严格
//一个mcfpp文件
compilationUnit
    :   namespaceDeclaration?
        importDeclaration*
        topStatement
        typeDeclaration*
        EOF
    ;

topStatement
    : statement*
    ;

//命名空间声明
namespaceDeclaration
    :   doc_comment? NAMESPACE Identifier (DOT Identifier)* ';'
    ;

importDeclaration
    :   IMPORT Identifier ('.' Identifier)* ('.' cls = (Identifier|'*'))? (AS Identifier)? (FROM Identifier)? ';'
    ;

//类或函数声明
typeDeclaration
    :   doc_comment? declarations
    ;

//类或函数声明
declarations
    :   classDeclaration
    |   genericClassImplement
    |   objectClassDeclaration
    |   functionDeclaration
    |   inlineFunctionDeclaration
    |   nativeFuncDeclaration
    |   compileTimeFuncDeclaration
    |   compileTimeClassDeclaration
    |   nativeClassDeclaration
    |   templateDeclaration
    |   objectTemplateDeclaration
    |   extensionFunctionDeclaration
    |   interfaceDeclaration
    |   globalDeclaration
    |   enumDeclaration
    ;

//全局声明
globalDeclaration
    :   GLOBAL '{' (doc_comment? fieldDeclaration ';')* '}'
    ;

//类声明
classDeclaration
    :   classAnnotation? STATIC? FINAL? ABSTRACT? CLASS classWithoutNamespace readOnlyParams? (COLON className (',' className)*)? classBody
    ;

objectClassDeclaration
    :   classAnnotation? OBJECT CLASS classWithoutNamespace readOnlyParams? (COLON className (',' className)*)? classBody
    ;

compileTimeClassDeclaration
    :   CONST CLASS classWithoutNamespace (COLON className (',' className)*)? classBody
    ;

nativeClassDeclaration
    :   CLASS classWithoutNamespace '=' javaRefer ';'
    ;

classMemberDeclaration
    :   accessModifier? classMember
    ;

classBody
    :   '{' (doc_comment? classMemberDeclaration)* '}'
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
    :   funcAnnoation? OVERRIDE? FUNCTION Identifier functionParams (ARROW functionReturnType)? '{' functionBody '}'
    ;

abstractClassFunctionDeclaration
    :   funcAnnoation? OVERRIDE? FUNCTION Identifier functionParams (ARROW functionReturnType)? ';'
    ;

nativeClassFunctionDeclaration
    :   funcAnnoation? OVERRIDE? FUNCTION Identifier functionParams (ARROW functionReturnType)? '=' javaRefer ';'
    ;

classFieldDeclaration
    :   accessModifier? type fieldDeclarationExpression
    ;

genericClassImplement
    :   IMPL classAnnotation? STATIC? FINAL? ABSTRACT? CLASS classWithoutNamespace readOnlyArgs (COLON className (',' className)*)? classBody
    ;

//数据模板
templateDeclaration
    :   FINAL? OBJECT? DATA classWithoutNamespace (COLON className)? templateBody
    ;

//数据模板
objectTemplateDeclaration
    :   FINAL? OBJECT DATA classWithoutNamespace (COLON className)? templateBody
    ;

templateBody
    :   '{' (doc_comment? templateMemberDeclaration)* '}'
    ;

templateMemberDeclaration
    :   accessModifier? templateMember
    ;

templateMember
    :   templateFunctionDeclaration
    |   templateFieldDeclaration
    ;

templateFunctionDeclaration
    :  FUNCTION Identifier functionParams (ARROW functionReturnType)? '{' functionBody '}'
    ;

templateFieldDeclaration
    :   CONST? type Identifier ('=' expression)? ';'
    ;

//接口声明
interfaceDeclaration
    :   classAnnotation? INTERFACE classWithoutNamespace (ARROW className (',' className)*)? interfaceBody
    ;

interfaceBody
    :   '{'( doc_comment? interfaceFunctionDeclaration )* '}'
    ;

interfaceFunctionDeclaration
    :   funcAnnoation? FUNCTION Identifier functionParams (ARROW functionReturnType)? ';'
    ;

compileTimeFuncDeclaration
    :   CONST FUNCTION Identifier functionParams (ARROW functionReturnType)? '{' functionBody '}'
    ;

inlineFunctionDeclaration
    :   funcAnnoation? INLINE FUNCTION Identifier functionParams (ARROW functionReturnType)? '{' functionBody '}'
    ;

//函数声明
functionDeclaration
    :   funcAnnoation? FUNCTION Identifier functionParams? (ARROW functionReturnType)? '{' functionBody '}'
    ;

extensionFunctionDeclaration
    :   funcAnnoation? STATIC? FUNCTION (type '.')? Identifier functionParams (ARROW functionReturnType)? '{' functionBody '}'
    ;

//枚举
enumDeclaration
    :   ENUM Identifier '{' enumBody '}'
    ;

enumBody
    :   enumMember (',' enumMember)*
    ;

enumMember
    :   Identifier ('=' intValue)?
    ;


namespaceID
    : (Identifier ( '.' Identifier)* ':')? Identifier
    ;

nativeFuncDeclaration
    :   funcAnnoation? FUNCTION Identifier functionParams (ARROW functionReturnType)? '=' javaRefer ';'
    ;

javaRefer
    :   Identifier ('.' Identifier)*
    ;

accessModifier
    :   PRIVATE
    |   PROTECTED
    |   PUBLIC
    ;

//构造函数声明
constructorDeclaration
    :   funcAnnoation? accessModifier? CONSTRUCTOR normalParams '{' functionBody '}'
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

functionParams
    :   readOnlyParams? normalParams
    ;

readOnlyParams
    :   '<' parameterList? '>'
    ;

normalParams
    :   '(' parameterList? ')'
    ;

//参数列表
parameterList
    :   parameter (',' parameter)*
    ;

//参数
parameter
    :   STATIC? type Identifier ('=' expression)?
    ;

//表达式
expression
    :   primary
    |   conditionalOrExpression
    ;

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
    |   range
    |   value
    |   THIS
    |   SUPER
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
    :   readOnlyArgs? normalArgs
    ;

readOnlyArgs
    :   '<' expressionList? '>'
    ;

normalArgs
    :   '(' expressionList? ')'
    ;

functionBody
    :   statement*
    ;

statement
    :   fieldDeclaration ';'
    |   statementExpression ';'
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
    :   normalType
    |   LIST '<' type '>'
    |   className readOnlyArgs?
    |   Identifier
    ;

normalType
    :   INT
    |   ENTITY
    |   BOOL
    |   FLOAT
    |   SELECTOR
    |   STRING
    |   JTEXT
    |   NBT
    |   TYPE
    |   ANY
    |   VecType         //向量
    |   MAP
    |   DICT
    ;

functionReturnType
    :   type
    |   VOID
    ;

value
    :   intValue
    |   floatValue
    |   LineString
    |   boolValue
    |   multiLineStringLiteral
    |   nbtValue
    |   type
    |   TargetSelector
    ;

intValue
    :   IntegerConstant
    ;

floatValue
    :   FloatConstant
    ;

boolValue
    :   BooleanConstant
    ;

className
    :   (Identifier ('.' Identifier)* ':')? classWithoutNamespace
    ;

typeList
    :   '<' typeList? '>'
    ;

classWithoutNamespace
    :   Identifier
    ;

funcAnnoation
    :   '@' id=Identifier arguments?
    ;

classAnnotation
    :   '@' id=Identifier arguments?
    ;

range
    :   num1=var '..' num2=var
    |   num1=var '..'
    |   '..' num2=var
    ;

namespacePath
    :   Identifier ':' Identifier ('/' Identifier)*
    ;


nbtValue
    :   LineString
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

nbtByte: NBTByte;
nbtShort:  NBTShort;
nbtInt:  IntegerConstant;
nbtLong: NBTLong;
nbtFloat: FloatConstant;
nbtDouble: NBTDouble;

nbtByteArray: NBT_BYTE_ARRAY_BEGIN nbtByte (',' nbtByte)* ']';
nbtIntArray: NBT_INT_ARRAY_BEGIN nbtInt (',' nbtInt)* ']';
nbtLongArray: NBT_LONG_ARRAY_BEGIN nbtLong (',' nbtLong)* ']';

nbtList: '[' (nbtValue (',' nbtValue)* )* ']';
nbtKeyValuePair: key=Identifier ':' nbtValue;
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
