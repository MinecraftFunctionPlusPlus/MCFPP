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

grammar mcfpp;

//一个mcfpp文件
compilationUnit
    :   importDeclaration*
        namespaceDeclaration?
        typeDeclaration *
        EOF
    ;

//命名空间声明
namespaceDeclaration
    :   'namespace' Identifier ('.' Identifier)* ';'
    ;

importDeclaration
    :   IMPORT Identifier ('.' Identifier)* ('.' cls = (ClassIdentifier|'*'))? ';'
    ;

//类或函数声明
typeDeclaration
    :   annoation
    |   declarations
    ;

//类或函数声明
declarations
    :   classDeclaration
    |   functionDeclaration
    |   nativeFuncDeclaration
    |   nativeClassDeclaration
    |   structDeclaration
    |   extensionFunctionDeclaration
    |   interfaceDeclaration
    |   globalDeclaration
    ;

//全局声明
globalDeclaration
    :   GLOBAL '{' (fieldDeclaration ';')* '}'
    ;

//类声明
classDeclaration
    :   STATIC? FINAL? ABSTRACT? CLASS classWithoutNamespace (':' className (',' className)*)? classBody
    ;

nativeClassDeclaration
    :   NATIVE CLASS classWithoutNamespace '->' javaRefer ';'
    |   NATIVE CLASS classWithoutNamespace '->' javaRefer '{' nativeClassBody '}'
    ;

nativeClassBody
    :   '{' nativeClassFunctionDeclaration* '}'
    ;

staticClassMemberDeclaration
    :   accessModifier? STATIC classMember
    ;

classMemberDeclaration
    :   accessModifier? classMember
    ;

classBody
    :   '{' (classMemberDeclaration|staticClassMemberDeclaration)* '}'
    ;

//类成员
classMember
    :   classFunctionDeclaration
    |   fieldDeclaration ';'
    |   constructorDeclaration
    |   nativeFuncDeclaration
    |   abstractClassFunctionDeclaration
    ;

classFunctionDeclaration
    :   OVERRIDE? functionReturnType Identifier '(' parameterList? ')' '{' functionBody '}'
    ;

abstractClassFunctionDeclaration
    :   OVERRIDE? ABSTRACT functionReturnType Identifier '(' parameterList? ')' ';'
    ;

nativeClassFunctionDeclaration
    :   accessModifier? NATIVE Identifier '(' parameterList? ')' ';'
    ;

//结构体
structDeclaration
    :   FINAL? STRUCT classWithoutNamespace (EXTENDS className)? structBody
    ;

structBody
    :   '{' (structMemberDeclaration|staticStructMemberDeclaration)* '}'
    ;

structMemberDeclaration
    :   accessModifier? structMember
    ;

staticStructMemberDeclaration
    :   accessModifier? STATIC? structMember
    ;

structMember
    :   structFunctionDeclaration
    |   structFieldDeclaration ';'
    |   constructorDeclaration
    ;

structFunctionDeclaration
    :   functionReturnType (className '.')? Identifier '(' parameterList? ')' '{' functionBody '}'
    ;

structFieldDeclaration
    :   CONST? 'int'? Identifier
    ;

//接口声明
interfaceDeclaration
    :   INTERFACE classWithoutNamespace (':' className (',' className)*)? interfaceBody
    ;

interfaceBody
    :   '{' interfaceFunctionDeclaration* '}'
    ;

interfaceFunctionDeclaration
    :   functionReturnType Identifier '(' parameterList? ')' ';'
    ;

//函数声明
functionDeclaration
    :    INLINE? functionReturnType Identifier '(' parameterList? ')' '{' functionBody '}'
    ;

extensionFunctionDeclaration
    :   STATIC? functionReturnType (type '.')? Identifier '(' parameterList? ')' '{' functionBody '}'
    ;

namespaceID
    : (Identifier ( '.' Identifier)* ':')? Identifier
    ;

nativeFuncDeclaration
    :   NATIVE Identifier '(' parameterList? ')' '->' javaRefer ';'
    ;

javaRefer
    :   stringName ('.' stringName)*
    ;

stringName
    :   Identifier
    |   ClassIdentifier
    |   NORMALSTRING
    ;

accessModifier
    :   PRIVATE
    |   PROTECTED
    |   PUBLIC
    ;

//构造函数声明
constructorDeclaration
    :   className '(' parameterList? ')' '{' functionBody '}'
    ;

//构造函数的调用
constructorCall
    :   className arguments
    ;

//变量声明
fieldDeclaration
    :   fieldModifier? type fieldDeclarationExpression (',' fieldDeclarationExpression)*
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

OrgCommand
    :   '/' [a-z]* ([ ][a-z:._{}\\[0-9A-Z\]]*)+
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
    :   'int'
    |   'entity'
    |   'bool'
    |   'float'
    |   'selector'
    |   'string'
    |   'jstring'
    |   'nbt'
    |   VEC INT         //向量
    |   className
    ;

functionReturnType
    :   type
    |   'void'
    ;

value
    :   INT
    |   FLOAT
    |   STRING
    |   BOOL
    |   nbtValue
    ;

className
    :   (Identifier ('.' Identifier)* ':')? classWithoutNamespace
    ;

classWithoutNamespace
    :   ClassIdentifier
    ;

annoation
    :   '@' Identifier arguments?
    ;

nbtValue
    :   NBTSTRING
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

nbtByte: INT 'b' | 'B';
nbtShort: INT 's' | 'S';
nbtInt: INT;
nbtLong: INT 'l' | 'L';
nbtFloat: FLOAT 'f' | 'F';
nbtDouble: FLOAT ('d' | 'D')?;

nbtByteArray: '[B;' nbtByte (',' nbtByte)* ']';
nbtIntArray: '[I;' nbtInt (',' nbtInt)* ']';
nbtLongArray: '[L;' nbtLong (',' nbtLong)* ']';

nbtList: '[' (nbtValue (',' nbtValue)* )* ']';
nbtKeyValuePair: key=(Identifier|ClassIdentifier) ':' nbtValue;
nbtCompound: '{'( nbtKeyValuePair (',' nbtKeyValuePair)* )*'}';

NBTSTRING: ('"' .*? '"' )|( '\'' .*? '\'' );

TargetSelector
    :   '@' ('a'|'r'|'p'|'s'|'e')
    ;

THIS:'this';
SUPER:'super';
IF:'if';
ELSE:'else';
WHILE:'while';
FOR:'for';
DO:'do';
TRY:'try';
STORE:'store';

BREAK:'break';
CONTINUE:'continue';
RETURN:'return';

STATIC:'static';
EXTENDS:'extends';
NATIVE:'native';
CONCRETE:'concrete';
FINAL:'final ';

PUBLIC:'public';
PROTECTED:'protected';
PRIVATE:'private';

OVERRIDE: 'override';
ABSTRACT: 'abstract';

CONST:'const';
DYNAMIC:'dynamic';
IMPORT: 'import';

INLINE:'inline';

CLASS:'class';
INTERFACE:'interface';
STRUCT:'struct';

GLOBAL:'global';

INT
    :   [1-9][0-9]*|[0]
    ;

FLOAT
    :   INT '.' [0-9]+
    |   [0-9] '.' [0-9]+ 'e' '-'? [0-9]
    ;

BOOL
    :   'true'
    |   'false'
    ;

VEC:'vec';

WAVE:'~';

Identifier
    :   [a-z_][a-zA-Z0-9_]*
    ;

ClassIdentifier
    :   [A-Z][a-zA-Z0-9_]*
    ;

NORMALSTRING
    :   [A-Za-z0-9_]+
    ;

STRING
    :   '"' .*? '"'
    ;

AT:'@';

//
// Whitespace and comments
//

WS  :  [ \t\r\n\u000C]+ -> skip
    ;

//COMMENT
//    :   '/*' .*? '*/' -> skip
//    ;

LINE_COMMENT
    :   '//' ~[\r\n]* -> skip
    ;