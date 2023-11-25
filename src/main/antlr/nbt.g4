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

grammar nbt;

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
nbtKeyValuePair: KEY ':' nbtValue;
nbtCompound: '{'( nbtKeyValuePair (',' nbtKeyValuePair)* )*'}';

INT: [0-9]+;
FLOAT: [0-9]+ '.' [0-9]+;
NBTSTRING: ('"' .*? '"' )|( '\'' .*? '\'' );
KEY : [a-zA-Z_]+ ;
WS: [ \t\r\n]+ -> skip;