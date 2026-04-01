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
    :   (separator* namespaceDeclaration (separator | EOF))?
        (separator* importDeclaration (separator | EOF))*
        (separator* typealiasDeclaration (separator | EOF))*
        (separator* topStatement)?
        (separator* typeDeclaration (separator | EOF))*
        EOF
    ;

separator
    :   (SEMICOLON | NL)+
    ;

topStatement
    : (statement (separator | EOF))+
    ;

//命名空间声明
namespaceDeclaration
    :   doc_comment? NAMESPACE Identifier (DOT Identifier)*
    ;

importDeclaration
    :   IMPORT importType (AS Identifier)? (FROM Identifier)?
    ;

importType
    :   Identifier (DOT Identifier)* COLON (Identifier | MULT)
    ;

typealiasDeclaration
    :   TYPEALIAS type AS Identifier
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
    |   interfaceDeclaration
    |   enumDeclaration
    |   annotation
    ;

operationOverrideDeclaration
    :   OPERATOR supportOperator NL* functionParams NL* (ARROW functionReturnType)? NL* curlBlock
    ;

nativeOperationOverrideDeclaration
    :   OPERATOR supportOperator NL* functionParams NL* (ARROW functionReturnType)? NL* ASSIGNMENT NL* javaRefer
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
    :   LCURL NL* getter? separator+ setter? separator* RCURL
    ;

getter
    :   GET NL* curlBlock
    |   GET NL* ASSIGNMENT NL* javaRefer SEMICOLON
    |   GET NL* ASSIGNMENT NL* expression SEMICOLON?
    |   GET SEMICOLON?
    ;

setter
    :   SET NL* curlBlock
    |   SET NL* ASSIGNMENT javaRefer
    |   SET NL* ASSIGNMENT expression
    |   SET SEMICOLON
    ;

compoundDeclaration
    :   declarationName NL* (COLON NL* extendName NL* (COMMA NL* extendName NL*)*)?
    ;

//数据模板
templateDeclaration
    :   FINAL? NL* DATA NL* ABSTRACT? NL*
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
    :   LCURL NL* (doc_comment? templateMemberDeclaration separator*)* RCURL
    ;

templateMemberDeclaration
    :   accessModifier? NL* templateMember
    ;

templateMember
    :   templateFunctionDeclaration
    |   templateFieldDeclaration
    |   templateConstructorDeclaration
    |   operationOverrideDeclaration
    |   nativeOperationOverrideDeclaration
    |   annotation
    ;

functionDeclarationPart
    :   FUNCTION NL* Identifier NL* functionParams (NL* ARROW NL* functionReturnType)?
    ;

templateFunctionDeclaration
    :   OVERRIDE? NL* ABSTRACT? NL* functionDeclarationPart (NL* curlBlock)?
    ;

templateFieldDeclaration
    :    CONST? VAR? NL* Identifier NL* (AS NL* templateType)? (NL* ASSIGNMENT expression)? NL* accessor?
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

//接口声明
interfaceDeclaration
    :   INTERFACE NL* compoundDeclaration NL* templateBody?
    ;

compileTimeFuncDeclaration
    :   CONST NL* functionDeclarationPart NL* curlBlock
    ;

inlineFunctionDeclaration
    :   INLINE NL* functionDeclarationPart NL* curlBlock
    ;

//函数声明
functionDeclaration
    :   functionDeclarationPart NL* curlBlock
    ;

extensionFunctionDeclaration
    :   FUNCTION NL* (type DOT)? Identifier functionParams (NL* ARROW NL* functionReturnType)? NL* curlBlock
    ;

//枚举
enumDeclaration
    :   ENUM NL* Identifier NL* enumBody
    ;

enumBody
    :   LCURL NL* enumMember (NL* COMMA NL* enumMember)* NL* RCURL
    ;

enumMember
    :   Identifier NL* (ASSIGNMENT NL* nbtValue)?
    ;


namespaceID
    : (Identifier (DOT Identifier)* COLON)? Identifier
    ;

nativeFuncDeclaration
    :   functionDeclarationPart NL* ASSIGNMENT NL* javaRefer SEMICOLON?
    ;

javaRefer
    :   Identifier (DOT Identifier)*
    ;

accessModifier
    :   PRIVATE
    |   PROTECTED
    |   PUBLIC
    ;

//构造函数声明
templateConstructorDeclaration
    :   CONSTRUCTOR NL* normalParams? NL* curlBlock
    ;

//变量声明
fieldDeclaration
    :   fieldModifier? NL* VAR NL* Identifier NL* (AS NL* type)? NL* (ASSIGNMENT NL* expression)?
    ;

fieldModifier : CONST|DYNAMIC|IMPORT;

functionParams
    :   readOnlyParams? NL* normalParams
    ;

readOnlyParams
    :   LANGLE NL* parameterList? NL* RANGLE
    ;

normalParams
    :   LPAREN NL* parameterList? NL* RPAREN
    ;

//参数列表
parameterList
    :   parameter (NL* COMMA NL* parameter)*
    ;

//参数
parameter
    :   STATIC? NL* VAR? NL* (Identifier NL* AS NL*)? type (NL* ASSIGNMENT NL* value)?
    ;

//能作为语句的表达式
statementExpression
    :   (varWithSelector NL* ASSIGNMENT NL* )? expression
    ;

//表达式
expression
    :   primary
    |   commonBinaryOperatorExpression
    ;

//其他运算符
commonBinaryOperatorExpression
    :   conditionalOrExpression (op+=(PIPE | Identifier) conditionalOrExpression)*
    ;

//或
conditionalOrExpression
    :   conditionalAndExpression (NL* op+=DISJ NL* conditionalAndExpression )*
    ;

//与
conditionalAndExpression
    :   equalityExpression (NL* op+=CONJ NL* equalityExpression )*
    ;

//等同
equalityExpression
    :   relationalExpression (NL* op+=(EQEQ | EXCL_EQ | WVEQ) NL* relationalExpression )*
    ;

//比较关系
relationalExpression
    :   additiveExpression (NL* op+=(LANGLE | RANGLE | LE | GE) NL* additiveExpression )*
    ;

//加减
additiveExpression
    :   multiplicativeExpression (NL* op+=(ADD | SUB) NL* multiplicativeExpression )*
    ;

//乘除
multiplicativeExpression
    :   castExpression (NL* op+=(MULT | SLASH | MOD) NL* castExpression )*
    ;

//强制类型转换表达式
castExpression
    :  unaryExpression (NL* AS NL* type)?
    ;

//一元表达式
unaryExpression
    :   EXCL NL* unaryExpression
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
    :   propertyOperator (NL* COLONCOLON NL* Identifier)?
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
    |   value
    |   var
    |   THIS
    |   SUPER
    |   type
    ;

var
    :   bucketExpression
    |   varWithSuffix
    |   functionCall
    ;

bucketExpression
    :   LPAREN NL* (expression NL*)? RPAREN
    ;

varWithSuffix
    :   Identifier NL* identifierSuffix*
    ;

functionCall
    :   namespaceID NL* arguments
    ;

identifierSuffix
    :   LSQUARE NL* (expression NL*)? RSQUARE
    ;

selector
    :   DOT NL* var
    ;

arguments
    :   readOnlyArgs? NL* normalArgs
    ;

readOnlyArgs
    :   LANGLE NL* ((expressionList | MULT) NL*)? RANGLE
    ;

normalArgs
    :   LPAREN NL* (expressionList NL*)? RPAREN
    ;

statement
    :   fieldDeclaration
    |   statementExpression
    |   ifStatement
    |   whileStatement
    |   doWhileStatement
    |   tryStoreStatement
    |   executeStatement
    |   controlStatement
    |   orgCommand
    |   SEMICOLON
    |   returnStatement
    |   foreachStatement
    ;

foreachStatement
    :   FOR NL* LPAREN NL* Identifier NL* COLON NL* expression NL* RPAREN NL* block
    ;

executeStatement
    :   EXECUTE NL* LPAREN NL* executeContext (NL* COMMA NL* executeContext)* NL* RPAREN NL* block
    ;

executeContext
    :   executeExpression NL* ASSIGNMENT NL* expression
    ;

executeExpression
    :   AS | (var (NL* DOT NL* var)*)
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
    :   IF NL* bucketExpression NL* block (NL* elseIfStatement)* (NL* elseStatement)?
    ;

elseIfStatement
    :   ELSE NL* IF NL* bucketExpression NL* block
    ;

elseStatement
    :   ELSE NL* block
    ;

whileStatement
    :   WHILE NL* bucketExpression NL* block
    ;

doWhileStatement
    :   DO NL* block NL* WHILE NL* bucketExpression
    ;

tryStoreStatement
    :   TRY NL* block NL* STORE NL* LPAREN NL* Identifier NL* RPAREN
    ;

returnStatement
    : RETURN (NL* expression)?
    ;

curlBlock
    :   LCURL NL* (statement (separator statement)* separator)? NL* RCURL
    ;

block
    :   curlBlock
    |   statement
    ;

expressionList
    :   expression (NL* COMMA NL* expression)*
    ;

type
    :   typeWithoutExcl EXCL?
    ;

typeWithoutExcl
    :   normalType
    |   VecType
    |   (LIST | MAP | DICT) NL* LANGLE NL* (type | MULT) NL* RANGLE
    |   ENTITY NL* LANGLE NL* nbtInt NL* RANGLE
    |   ENTITY NL* LANGLE NL* LineString (NL* COMMA NL* LineString)* NL* RANGLE
    |   ENTITY NL* LANGLE NL* nbtInt NL* COMMA NL* LineString (NL* COMMA NL* LineString)* NL* RANGLE
    |   className NL* readOnlyArgs?
    |   Identifier
    |   unionTemplateType
    |   anonymousTemplateType
    ;

anonymousTemplateType
    :   DATA (NL* COLON NL* extendName (NL* COMMA NL* extendName)*)? NL* templateBody
    ;

unionTemplateType
    :   LPAREN NL* type (NL* UNION NL* type)* NL* RPAREN
    ;

normalType
    :   INT
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
    :   (Identifier (DOT Identifier)* COLON NL*)? classWithoutNamespace
    ;

classWithoutNamespace
    :   Identifier
    ;

annotation
    :   AT id=Identifier NL* annotationArgs?
    ;

annotationArgs
    :   LANGLE (NL* value (NL* COMMA NL* value)*)? NL* RANGLE
    ;

range
    :   num1=range1 NL* RANGE NL* num2=range1
    |   num1=range1 NL* RANGE
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

nbtByteArray: NBT_BYTE_ARRAY_BEGIN NL* nbtByte (NL* COMMA NL* nbtByte)* NL* RSQUARE;
nbtIntArray: NBT_INT_ARRAY_BEGIN NL* nbtInt (NL* COMMA NL* nbtInt)* NL* RSQUARE;
nbtLongArray: NBT_LONG_ARRAY_BEGIN NL* nbtLong (NL* COMMA NL* nbtLong)* NL* RSQUARE;

nbtList: LSQUARE (NL* expression (NL* COMMA NL* expression)* )* NL* RSQUARE;
nbtKeyValuePair: key=Identifier NL* COLON NL* expression;
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
    |   SIMPLE_DOC_COMMENT
    ;