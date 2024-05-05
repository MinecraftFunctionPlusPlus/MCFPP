lexer grammar mcfppLexer;

import unicodeClass;

//Base Character Set
WAVE:'~';
RESERVED: '...' ;
DOT: '.' ;
COMMA: ',' ;
LPAREN: '(';
RPAREN: ')';
LSQUARE: '[';
RSQUARE: ']';
LCURL: '{' -> pushMode(DEFAULT_MODE);
RCURL: '}' -> popMode;
MULT: '*' ;
MOD: '%' ;
DIV: '/' ;
ADD: '+' ;
SUB: '-' ;
INCR: '++' ;
DECR: '--' ;
CONJ: '&&' ;
DISJ: '||' ;
EXCL: '!';
COLON: ':' ;
SEMICOLON: ';' ;
ASSIGNMENT: '=' ;
ADD_ASSIGNMENT: '+=' ;
SUB_ASSIGNMENT: '-=' ;
MULT_ASSIGNMENT: '*=' ;
DIV_ASSIGNMENT: '/=' ;
MOD_ASSIGNMENT: '%=' ;
ARROW: '->' ;
DOUBLE_ARROW: '=>' ;
RANGE: '..' ;
//COLONCOLON: '::' ;
//DOUBLE_SEMICOLON: ';;' ;
HASH: '#' ;
AT: '@' ;
QUEST: '?' ;
LANGLE: '<' ;
RANGLE: '>' ;
LE: '<=' ;
GE: '>=' ;
EXCL_EQ: '!=' ;
//EXCL_EQEQ: '!==' ;
EQEQ: '==' ;
//EQEQEQ: '===' ;
SINGLE_QUOTE: '\'' ;
//RIGHT_SHIFT_ARITHMETIC:           '>>';
//LEFT_SHIFT_ARITHMETIC:            '<<';
//RIGHT_SHIFT_LOGICAL:              '>>>';
//BIT_AND:                         '&';
//BIT_X_OR:                         '^';
//BIT_OR:                          '|';
//LEFT_SHIFT_ARITHMETIC_ASSIGN:      '<<=';
//RIGHT_SHIFT_ARITHMETIC_ASSIGN:     '>>=';
//RIGHT_SHIFT_LOGICAL_ASSIGN:        '>>>=';
//BIT_AND_ASSIGN:                   '&=';
//BIT_XOR_ASSIGN:                   '^=';
//BIT_OR_ASSIGN:                    '|=';
TRIPLE_QUOTE_OPEN: '"""' -> pushMode(MultiLineString) ;

//KeyWords
THIS:'this';
SUPER:'super';
IF:'if';
ELSE:'else';
WHILE:'while';
FOR:'for';
DO:'do';
TRY:'try';
STORE:'store';
AS:'as';
FROM:'from';

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
TEMPLATE:'template';
FUNCTION:'func';

GLOBAL:'global';
VAR:'var';

NAMESPACE:  'namespace';
VEC:        'vec';
INT:        'int';
ENTITY:     'entity';
BOOL:       'bool';
FLOAT:      'float';
SELECTOR:   'selector';
STRING:     'string';
JTEXT:      'jtext';
NBT:        'nbt';
ANY:        'any';
TYPE:       'type';
VOID:       'void';
LIST:       'list';
MAP:        'map';
DICT:       'dict';

//Identifiers
TargetSelector
    :   '@' ('a'|'r'|'p'|'s'|'e')
    ;

fragment DigitSequence: [0-9]+;
fragment HexSequence: [0-9a-fA-F]+;
fragment OctalSequence: [0-7]+;

fragment DecimalConstant: DigitSequence;
fragment HexadecimalConstant: '0x' HexSequence;
fragment OctalConstant: '0' | '0' OctalSequence;
fragment FractionalConstant: DigitSequence DOT DigitSequence;

fragment ExponentPart
    :   [e|E] (ADD|SUB)? DigitSequence;

fragment IntConstant : DecimalConstant|HexadecimalConstant|OctalConstant;

IntegerConstant: IntConstant;

FloatConstant: FractionalConstant ExponentPart?;

BooleanConstant
    :   'true'
    |   'false'
    ;

Identifier
    : (Letter | '_') (Letter | '_' | UnicodeDigit)*
    ;

Letter
    : UNICODE_CLASS_LU
    | UNICODE_CLASS_LL
    | UNICODE_CLASS_LT
    | UNICODE_CLASS_LM
    | UNICODE_CLASS_LO
    ;

UnicodeDigit
  : UNICODE_CLASS_ND
  ;

NBT_BYTE_ARRAY_BEGIN: '[B;';
NBT_INT_ARRAY_BEGIN: '[I;';
NBT_LONG_ARRAY_BEGIN: '[L;';

NBTByteSuffix: [b|B];
NBTShortSuffix: [s|S];
NBTLongSuffix: [l|L];
NBTFloatSuffix: [f|F];
NBTDoubleSuffix: [d|D];

NBTByte: IntConstant NBTByteSuffix;
NBTShort: IntConstant NBTShortSuffix;
NBTInt: IntConstant;
NBTLong: IntConstant NBTLongSuffix;
NBTFloat: FractionalConstant ExponentPart?;
NBTDouble: FractionalConstant ExponentPart? NBTDoubleSuffix;

LineString: ('"' .*? '"' )|( '\'' .*? '\'' );

OrgCommand
    :   '/' [a-z]* ([ ][a-z:._{}\\[0-9A-Z\]]*)+
    ;

WS  :  [ \t\r\n\u000C]+ -> skip
    ;

DOC_COMMENT
    :   '#{' .*? '}#'
    ;

BLOCK_COMMENT
    :   '##' .*? '##' -> skip
    ;

LINE_COMMENT
    :   '#' ~[\r\n]* -> skip
    ;




mode MultiLineString ;

TRIPLE_QUOTE_CLOSE
    : MultiLineStringQuote? '"""' -> popMode
    ;

MultiLineStringQuote
    : '"'+
    ;


MultiLineStrExprStart
    : '${' -> pushMode(DEFAULT_MODE)
    ;


MultiLineStrText
    :  ~('"' | '$')+ | '$'
    ;
