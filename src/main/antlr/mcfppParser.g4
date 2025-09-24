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
    :   (namespaceDeclaration (separator | EOF))?
        (importDeclaration (separator | EOF))*
        (typealiasDeclaration (separator | EOF))*
        topStatement?
        typeDeclaration*
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
    :   Identifier (DOT Identifier)* ':' (Identifier|'*')
    ;

typealiasDeclaration
    :   TYPEALIAS type AS Identifier
    ;

//类或函数声明
typeDeclaration
    :   doc_comment? declarations
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
    :   OPERATOR supportOperator NL* functionParams NL* (ARROW functionReturnType)? NL* '=' NL* javaRefer
    ;

supportOperator
    :   '+'
    |   '-'
    |   '*'
    |   SLASH
    |   '%'
    |   '>'
    |   '<'
    |   '>='
    |   '<='
    |   '=='
    |   '!='
    |   WVEQ
    |   '||'
    |   '&&'
    |   '|'
    |   Identifier
    ;

accessor
    :   '{' NL* getter? NL* setter? NL* '}'
    ;

getter
    :   GET NL* curlBlock
    |   GET NL* '=' NL* javaRefer SEMICOLON
    |   GET NL* '=' NL* expression SEMICOLON?
    |   GET SEMICOLON?
    ;

setter
    :   SET NL* curlBlock
    |   SET NL* '=' javaRefer
    |   SET NL* '=' expression
    |   SET SEMICOLON
    ;

compoundDeclaration
    :   declarationName NL* (COLON NL* extendName NL* (',' NL* extendName NL*)*)?
    ;

//数据模板
templateDeclaration
    :   FINAL? NL* DATA NL*
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
    :   '{' NL* (doc_comment? templateMemberDeclaration NL*)* '}'
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
    :   OVERRIDE? NL* functionDeclarationPart NL* curlBlock
    ;

templateFieldDeclaration
    :    CONST? NL* VAR? NL* Identifier NL* (AS NL* templateType)? (NL* '=' expression)? NL* accessor?
    ;

templateType
    :   singleTemplateFieldType | unionTemplateFieldType
    ;

singleTemplateFieldType
    :   type QUEST?
    ;

unionTemplateFieldType
    :   '(' NL* type NL* (PIPE type NL*)* ')' QUEST?
    ;

declarationName
    :   classWithoutNamespace NL* readOnlyParams?
    ;

extendName
    :   className readOnlyArgs?
    ;

//接口声明
interfaceDeclaration
    :   INTERFACE NL* compoundDeclaration NL* interfaceBody?
    ;

interfaceBody
    :   '{' NL* ( doc_comment? NL* annotation? NL* interfaceFunctionDeclaration )* NL* '}'
    ;

interfaceFunctionDeclaration
    :   functionDeclarationPart NL* curlBlock?
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
    :   FUNCTION NL* (type '.')? Identifier functionParams (NL* ARROW NL* functionReturnType)? NL* curlBlock
    ;

//枚举
enumDeclaration
    :   ENUM NL* Identifier NL* enumBody
    ;

enumBody
    :   '{' NL* enumMember (NL* ',' NL* enumMember)* NL* '}'
    ;

enumMember
    :   Identifier NL* ('=' NL* nbtValue)?
    ;


namespaceID
    : (Identifier ( '.' Identifier)* ':')? Identifier
    ;

nativeFuncDeclaration
    :   functionDeclarationPart NL* '=' NL* javaRefer SEMICOLON?
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
templateConstructorDeclaration
    :   CONSTRUCTOR NL* normalParams NL* curlBlock
    ;

//变量声明
fieldDeclaration
    :   fieldModifier? NL* VAR NL* Identifier NL* (AS NL* type)? NL* ('=' NL* expression)?
    ;

fieldModifier : CONST|DYNAMIC|IMPORT;

functionParams
    :   readOnlyParams? NL* normalParams
    ;

readOnlyParams
    :   '<' NL* parameterList? NL* '>'
    ;

normalParams
    :   '(' NL* parameterList? NL* ')'
    ;

//参数列表
parameterList
    :   parameter (NL* ',' NL* parameter)*
    ;

//参数
parameter
    :   STATIC? NL* VAR? NL* (Identifier NL* AS NL*)? type (NL* '=' NL* value)?
    ;

//能作为语句的表达式
statementExpression
    :   (varWithSelector NL* '=' NL* )? expression
    ;

//表达式
expression
    :   primary
    |   commonBinaryOperatorExpression
    ;

//其他运算符
commonBinaryOperatorExpression
    :   conditionalOrExpression (NL* op+=('|' | Identifier) NL* conditionalOrExpression)*
    ;

//或
conditionalOrExpression
    :   conditionalAndExpression (NL* op+='||' NL* conditionalAndExpression )*
    ;

//与
conditionalAndExpression
    :   equalityExpression (NL* op+='&&' NL* equalityExpression )*
    ;

//等同
equalityExpression
    :   relationalExpression (NL* op+=('==' | '!=' | WVEQ) NL* relationalExpression )*
    ;

//比较关系
relationalExpression
    :   additiveExpression (NL* op+=('<' | '>' | '<=' | '>=') NL* additiveExpression )*
    ;

//加减
additiveExpression
    :   multiplicativeExpression (NL* op+=('+' | '-') NL* multiplicativeExpression )*
    ;

//乘除
multiplicativeExpression
    :   castExpression (NL* op+=( '*' | SLASH | '%' ) NL* castExpression )*
    ;

//强制类型转换表达式
castExpression
    :  unaryExpression (NL* AS NL* type)?
    ;

//一元表达式
unaryExpression
    :   '!' NL* unaryExpression
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
    :   primary (NL* '[' NL* propertyOperatorExpression (NL* ',' NL* propertyOperatorExpression)* NL* ']')?
    ;

propertyOperatorExpression
    :   Identifier NL* '=' NL* expression
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
    :   '(' NL* (expression NL*)? ')'
    ;

varWithSuffix
    :   Identifier NL* identifierSuffix*
    ;

functionCall
    :   namespaceID NL* arguments
    ;

identifierSuffix
    :   '[' NL* (expression NL*)? ']'
    ;

selector
    :   '.' NL* var
    ;

arguments
    :   readOnlyArgs? NL* normalArgs
    ;

readOnlyArgs
    :   '<' NL* (expressionList NL*)? '>'
    ;

normalArgs
    :   '(' NL* (expressionList NL*)? ')'
    ;

statement
    :   fieldDeclaration
    |   statementExpression
    |   ifStatement
    |   whileStatement
    |   doWhileStatement
    |   tryStoreStatement
    |   controlStatement
    |   orgCommand
    |   SEMICOLON
    |   returnStatement
    |   executeStatement
    ;

executeStatement
    :   EXECUTE NL* '(' NL* executeContext (NL* ',' NL* executeContext)* NL* ')' NL* block
    ;

executeContext
    :   executeExpression NL* '=' NL* expression
    ;

executeExpression
    :   var (NL* '.' NL* var)*
    ;

orgCommand
    :   SLASH (NL* orgCommandContent)* OrgCommandEnd
    ;

orgCommandContent
    :   orgCommandExpression
    |   OrgCommandText
    ;

orgCommandExpression
    :   OrgCommandExprStart expression '}'
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
    :   TRY NL* block NL* STORE NL* '(' NL* Identifier NL* ')'
    ;

returnStatement
    : RETURN (NL* expression)?
    ;

curlBlock
    :   '{' NL* (statement (separator statement)* separator?)? NL* '}'
    ;

block
    :   curlBlock
    |   statement
    ;

expressionList
    :   expression (NL* ',' NL* expression)*
    ;

type
    :   typeWithoutExcl EXCL?
    ;

typeWithoutExcl
    :   normalType
    |   VecType
    |   (LIST | MAP | DICT) NL* '<' NL* type NL* '>'
    |   ENTITY NL* '<' NL* nbtInt NL* '>'
    |   ENTITY NL* '<' NL* LineString (NL* ',' NL* LineString)* NL* '>'
    |   ENTITY NL* '<' NL* nbtInt NL* ',' NL* LineString (NL* ',' NL* LineString)* NL* '>'
    |   className NL* readOnlyArgs?
    |   Identifier
    |   unionTemplateType
    |   anonymousTemplateType
    ;

anonymousTemplateType
    :   DATA (NL* COLON NL* extendName (NL* ',' NL* extendName)*)? NL* templateBody
    ;

unionTemplateType
    :   '(' NL* type (NL* UNION NL* type)* NL* ')'
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
    :   (Identifier ('.' Identifier)* ':' NL*)? classWithoutNamespace
    ;

classWithoutNamespace
    :   Identifier
    ;

annotation
    :   '@' id=Identifier NL* annotationArgs?
    ;

annotationArgs
    :   '<' (NL* value (NL* ',' NL* value)*)? NL* '>'
    ;

range
    :   num1=var NL* '..' NL* num2=var
    |   num1=var NL* '..'
    |   '..' NL* num2=var
    ;

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

nbtByte: NBTByte;
nbtShort:  NBTShort;
nbtInt:  NBTInt;
nbtLong: NBTLong;
nbtFloat: NBTFloat;
nbtDouble: NBTDouble;
nbtBool: TRUE | FALSE;

nbtByteArray: NBT_BYTE_ARRAY_BEGIN NL* nbtByte (NL* ',' NL* nbtByte)* NL* ']';
nbtIntArray: NBT_INT_ARRAY_BEGIN NL* nbtInt (NL* ',' NL* nbtInt)* NL* ']';
nbtLongArray: NBT_LONG_ARRAY_BEGIN NL* nbtLong (NL* ',' NL* nbtLong)* NL* ']';

nbtList: '[' (NL* expression (NL* ','NL* expression)* )* NL* ']';
nbtKeyValuePair: key=Identifier NL* ':' NL* expression;
nbtCompound: '{'(NL* nbtKeyValuePair (NL* ',' NL* nbtKeyValuePair)* )* NL* '}';

multiLineStringLiteral
    : TRIPLE_QUOTE_OPEN multiLineStringContent* TRIPLE_QUOTE_CLOSE
    ;

multiLineStringContent
    : multiLineStringExpression
    | MultiLineStrText
    | MultiLineStringQuote
    ;

multiLineStringExpression
    : MultiLineStrExprStart NL* expression NL* '}'
    ;
//
// Whitespace and comments
//

doc_comment
    :   DOC_COMMENT
    ;