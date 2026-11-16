lexer grammar mcfppLexer;

import unicodeClass;

@members {
boolean afterNewline = true;

@Override
public Token nextToken() {
    // Command blocks use the existing command tokens. Supply the implicit slash
    // before each command line without consuming any of its text.
    if (_mode == CommandBlock) {
        while (_input.LA(1) == ' ' || _input.LA(1) == '\t' || _input.LA(1) == '\f') {
            getInterpreter().consume(_input);
        }
        int next = _input.LA(1);
        if (next != '/' && next != '}' && next != '\r' && next != '\n' && next != IntStream.EOF) {
            afterNewline = false;
            pushMode(OrgCommand);
            return _factory.create(_tokenFactorySourcePair, SLASH, "/", DEFAULT_TOKEN_CHANNEL,
                getCharIndex(), getCharIndex() - 1, getLine(), getCharPositionInLine());
        }
    }
    Token token = super.nextToken();
    if (token.getType() != NL && token.getType() != WS) {
        afterNewline = false;
    }
    return token;
}
}

//Base Character Set
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
SLASH
    : '/' {if(afterNewline){afterNewline = false; pushMode(OrgCommand);}}
    ;
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
DOUBLE_ARROW: '=>' ;
RANGE: '..' ;
COLONCOLON: '::' ;
//DOUBLE_SEMICOLON: ';;' ;
HASH: '#' ;
AT: '@' ;
QUEST: '?' ;
SAFE_DOT: '?.' ;
ELVIS: '?:' ;
LANGLE: '<' ;
RANGLE: '>' ;
LE: '<=' ;
GE: '>=' ;
EXCL_EQ: '!=' ;
//EXCL_EQEQ: '!==' ;
EQEQ: '==' ;
//EQEQEQ: '===' ;
WVEQ: '~=';
SINGLE_QUOTE: '\'' ;
//RIGHT_SHIFT_ARITHMETIC:           '>>';
//LEFT_SHIFT_ARITHMETIC:            '<<';
//RIGHT_SHIFT_LOGICAL:              '>>>';
UNION:                         '&';
//BIT_X_OR:                         '^';
//BIT_OR:                          '|';
//LEFT_SHIFT_ARITHMETIC_ASSIGN:      '<<=';
//RIGHT_SHIFT_ARITHMETIC_ASSIGN:     '>>=';
//RIGHT_SHIFT_LOGICAL_ASSIGN:        '>>>=';
//BIT_AND_ASSIGN:                   '&=';
//BIT_XOR_ASSIGN:                   '^=';
//BIT_OR_ASSIGN:                    '|=';
PIPE: '|';
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
EXECUTE:'execute';
CONTEXT:'context';

BREAK:'break';
CONTINUE:'continue';
RETURN:'return';

EXTENDS:'extends';
NATIVE:'native';
CONCRETE:'concrete';
FINAL:'final';

PUBLIC:'public';
PROTECTED:'protected';
PRIVATE:'private';

OVERRIDE: 'override';
ABSTRACT: 'abstract';
IMPL: 'impl';

CONST:'const';
EXTERNAL:'external';
IMPORT: 'import';

INLINE:'inline';

OBJECT:'object';
DATA:'data';
FUNCTION:'func';
ENUM:'enum';
OPERATOR:'operator';
TYPEALIAS: 'typealias';
OUT: 'out';
IN: 'in';

CONSTRUCTOR:'constructor';
NEW:'new';

GLOBAL:'global';
VAR:'var';

GET:'get';
SET:'set';

NAMESPACE:  'namespace';
VEC:        'vec';
INT:        'int';
ENTITY:     'entity';
BOOL:       'bool';
BYTE:       'byte';
SHORT:      'short';
LONG:       'long';
FLOAT:      'float';
DOUBLE:     'double';
SELECTOR:   'selector';
STRING:     'string';
JTEXT:      'text';
NBT:        'nbt';
ANY:        'any';
VOID:       'void';
LIST:       'list';
IMMUTABLE_LIST: 'ImmutableList';
MAP:        'map';
DICT:       'dict';
TYPE:       'Type';
BYTEARRAY:  'ByteArray';
INTARRAY:   'IntArray';
LONGARRAY:  'LongArray';

TRUE:   'true';
FALSE:  'false';
NULL:   'null';

VecType: VEC DigitSequence;

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

Identifier
    : (Letter | '_') (Letter | '_' | UnicodeDigit)*
      {
          // Keep the old argument-prefix diagnostic while permitting variables named ref.
          if (getText().equals("ref") && (_input.LA(1) == ' ' || _input.LA(1) == '\t')) {
              int next = 1;
              while (_input.LA(next) == ' ' || _input.LA(next) == '\t') next++;
              int c = _input.LA(next);
              if (Character.isLetter(c) || c == '_' || c == '@') setType(REF);
          }
      }
    ;

Letter
    : UNICODE_CLASS_LU
    | UNICODE_CLASS_LL
    | UNICODE_CLASS_LT
    | UNICODE_CLASS_LM
    | UNICODE_CLASS_LO
    ;

fragment UnicodeDigit
  : UNICODE_CLASS_ND
  ;

NBT_BYTE_ARRAY_BEGIN: '[B;';
NBT_INT_ARRAY_BEGIN: '[I;';
NBT_LONG_ARRAY_BEGIN: '[L;';

fragment NBTByteSuffix: [bB];
fragment NBTShortSuffix: [sS];
fragment NBTLongSuffix: [lL];
fragment NBTFloatSuffix: [fF];
fragment NBTDoubleSuffix: [dD];

NBTByte: IntConstant NBTByteSuffix;
NBTShort: IntConstant NBTShortSuffix;
NBTInt: IntConstant;
NBTLong: IntConstant NBTLongSuffix;
NBTFloat: (DigitSequence|FractionalConstant) ExponentPart? NBTFloatSuffix? ;
NBTDouble: (DigitSequence|FractionalConstant) ExponentPart? NBTDoubleSuffix;
NBTBool: BooleanConstant;

FloatConstant
    : DigitSequence NBTFloatSuffix
    | FractionalConstant ExponentPart? NBTFloatSuffix?
    ;

RelativeValue
    :   ('~' | '^') (('+' | '-')? (IntConstant | FloatConstant))?
    ;

BooleanConstant
    :   TRUE
    |   FALSE
    ;

LineString
    : '"' ('\\' ["\\] | ~["\\\r\n])* '"'
    | '\'' ('\\' ['\\] | ~['\\\r\n])* '\''
    ;

NL
    :   ('\r'? '\n')+ {
            afterNewline = true;
            if (!_modeStack.isEmpty() && _modeStack.peek() == CommandBlock) popMode();
        }
    ;

WS  :  [ \t\u000C]+ -> skip
    ;

DOC_COMMENT
    :   '///' ~[\r\n]* ('\r'? '\n' [ \t]* '///' ~[\r\n]*)*
    ;

LINE_COMMENT
    :   '//' ~[\r\n]* -> skip
    ;

mode OrgCommand ;

OrgCommandText
    :   ~[$\r\n]+ | '$'
    ;

OrgCommandExprStart
    :   '${' -> pushMode(DEFAULT_MODE)
    ;

OrgCommandEnd
    :   ('\r'? '\n')+ {afterNewline = true;} -> type(NL), popMode
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

// Serialized library parse trees retain token numbers. Allocate new keywords
// after every existing mode; Identifier emits them in DEFAULT_MODE.
mode AddedKeywords;
REF: '\u0000ref'; // Compatibility token; ref is an ordinary source identifier.

// Appended token: keep existing token numbers stable for serialized parse trees.
mode DEFAULT_MODE;
ARROW: '->' ;

// Append tokens and modes to preserve serialized library token numbers.
COMMAND_BLOCK_OPEN: '/{' -> pushMode(CommandBlock);

mode CommandBlock;
CommandBlockEnd: '}' -> type(RCURL), popMode;
CommandBlockNewline: ('\r'? '\n')+ {afterNewline = true;} -> type(NL);
CommandBlockStatement: '/' {afterNewline = false;} -> skip, pushMode(DEFAULT_MODE);
