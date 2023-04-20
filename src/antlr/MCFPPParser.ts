// Generated from ./antlr/MCFPP.g4 by ANTLR 4.9.0-SNAPSHOT


import { ATN } from "antlr4ts/atn/ATN.js";
import { ATNDeserializer } from "antlr4ts/atn/ATNDeserializer.js";
import { FailedPredicateException } from "antlr4ts/FailedPredicateException.js";
import { NotNull } from "antlr4ts/Decorators.js";
import { NoViableAltException } from "antlr4ts/NoViableAltException.js";
import { Override } from "antlr4ts/Decorators.js";
import { Parser } from "antlr4ts/Parser.js";
import { ParserRuleContext } from "antlr4ts/ParserRuleContext.js";
import { ParserATNSimulator } from "antlr4ts/atn/ParserATNSimulator.js";
import { ParseTreeListener } from "antlr4ts/tree/ParseTreeListener.js";
import { ParseTreeVisitor } from "antlr4ts/tree/ParseTreeVisitor.js";
import { RecognitionException } from "antlr4ts/RecognitionException.js";
import { RuleContext } from "antlr4ts/RuleContext.js";
//import { RuleVersion } from "antlr4ts/RuleVersion.js";
import { TerminalNode } from "antlr4ts/tree/TerminalNode.js";
import { Token } from "antlr4ts/Token.js";
import { TokenStream } from "antlr4ts/TokenStream.js";
import { Vocabulary } from "antlr4ts/Vocabulary.js";
import { VocabularyImpl } from "antlr4ts/VocabularyImpl.js";

import * as Utils from "antlr4ts/misc/Utils.js";

import { MCFPPListener } from "./MCFPPListener.js";
import { MCFPPVisitor } from "./MCFPPVisitor.js";


export class MCFPPParser extends Parser {
	public static readonly T__0 = 1;
	public static readonly T__1 = 2;
	public static readonly T__2 = 3;
	public static readonly T__3 = 4;
	public static readonly T__4 = 5;
	public static readonly T__5 = 6;
	public static readonly T__6 = 7;
	public static readonly T__7 = 8;
	public static readonly T__8 = 9;
	public static readonly T__9 = 10;
	public static readonly T__10 = 11;
	public static readonly T__11 = 12;
	public static readonly T__12 = 13;
	public static readonly T__13 = 14;
	public static readonly T__14 = 15;
	public static readonly T__15 = 16;
	public static readonly T__16 = 17;
	public static readonly T__17 = 18;
	public static readonly T__18 = 19;
	public static readonly T__19 = 20;
	public static readonly T__20 = 21;
	public static readonly T__21 = 22;
	public static readonly T__22 = 23;
	public static readonly T__23 = 24;
	public static readonly T__24 = 25;
	public static readonly T__25 = 26;
	public static readonly T__26 = 27;
	public static readonly T__27 = 28;
	public static readonly T__28 = 29;
	public static readonly T__29 = 30;
	public static readonly T__30 = 31;
	public static readonly T__31 = 32;
	public static readonly T__32 = 33;
	public static readonly T__33 = 34;
	public static readonly T__34 = 35;
	public static readonly OrgCommand = 36;
	public static readonly TargetSelector = 37;
	public static readonly THIS = 38;
	public static readonly SUPER = 39;
	public static readonly IF = 40;
	public static readonly ELSE = 41;
	public static readonly WHILE = 42;
	public static readonly FOR = 43;
	public static readonly DO = 44;
	public static readonly TRY = 45;
	public static readonly STORE = 46;
	public static readonly BREAK = 47;
	public static readonly CONTINUE = 48;
	public static readonly STATIC = 49;
	public static readonly EXTENDS = 50;
	public static readonly NATIVE = 51;
	public static readonly CONCRETE = 52;
	public static readonly FINAL = 53;
	public static readonly PUBLIC = 54;
	public static readonly PROTECTED = 55;
	public static readonly PRIVATE = 56;
	public static readonly InsideClass = 57;
	public static readonly INT = 58;
	public static readonly DECIMAL = 59;
	public static readonly VEC = 60;
	public static readonly WAVE = 61;
	public static readonly Identifier = 62;
	public static readonly ClassIdentifier = 63;
	public static readonly NORMALSTRING = 64;
	public static readonly STRING = 65;
	public static readonly WS = 66;
	public static readonly COMMENT = 67;
	public static readonly LINE_COMMENT = 68;
	public static readonly RULE_compilationUnit = 0;
	public static readonly RULE_namespaceDeclaration = 1;
	public static readonly RULE_typeDeclaration = 2;
	public static readonly RULE_classOrFunctionDeclaration = 3;
	public static readonly RULE_classDeclaration = 4;
	public static readonly RULE_classBody = 5;
	public static readonly RULE_staticClassMemberDeclaration = 6;
	public static readonly RULE_classMemberDeclaration = 7;
	public static readonly RULE_classMember = 8;
	public static readonly RULE_classFunctionDeclaration = 9;
	public static readonly RULE_functionDeclaration = 10;
	public static readonly RULE_namespaceID = 11;
	public static readonly RULE_nativeDeclaration = 12;
	public static readonly RULE_javaRefer = 13;
	public static readonly RULE_stringName = 14;
	public static readonly RULE_accessModifier = 15;
	public static readonly RULE_constructorDeclaration = 16;
	public static readonly RULE_nativeConstructorDeclaration = 17;
	public static readonly RULE_constructorCall = 18;
	public static readonly RULE_fieldDeclaration = 19;
	public static readonly RULE_parameterList = 20;
	public static readonly RULE_parameter = 21;
	public static readonly RULE_expression = 22;
	public static readonly RULE_statementExpression = 23;
	public static readonly RULE_conditionalExpression = 24;
	public static readonly RULE_conditionalOrExpression = 25;
	public static readonly RULE_conditionalAndExpression = 26;
	public static readonly RULE_equalityExpression = 27;
	public static readonly RULE_relationalExpression = 28;
	public static readonly RULE_relationalOp = 29;
	public static readonly RULE_additiveExpression = 30;
	public static readonly RULE_multiplicativeExpression = 31;
	public static readonly RULE_unaryExpression = 32;
	public static readonly RULE_basicExpression = 33;
	public static readonly RULE_castExpression = 34;
	public static readonly RULE_primary = 35;
	public static readonly RULE_varWithSelector = 36;
	public static readonly RULE_var = 37;
	public static readonly RULE_identifierSuffix = 38;
	public static readonly RULE_selector = 39;
	public static readonly RULE_arguments = 40;
	public static readonly RULE_functionBody = 41;
	public static readonly RULE_functionCall = 42;
	public static readonly RULE_statement = 43;
	public static readonly RULE_orgCommand = 44;
	public static readonly RULE_controlStatement = 45;
	public static readonly RULE_ifStatement = 46;
	public static readonly RULE_elseIfStatement = 47;
	public static readonly RULE_ifBlock = 48;
	public static readonly RULE_forStatement = 49;
	public static readonly RULE_forBlock = 50;
	public static readonly RULE_forControl = 51;
	public static readonly RULE_forInit = 52;
	public static readonly RULE_forUpdate = 53;
	public static readonly RULE_whileStatement = 54;
	public static readonly RULE_whileBlock = 55;
	public static readonly RULE_doWhileStatement = 56;
	public static readonly RULE_doWhileBlock = 57;
	public static readonly RULE_selfAddOrMinusStatement = 58;
	public static readonly RULE_tryStoreStatement = 59;
	public static readonly RULE_block = 60;
	public static readonly RULE_selfAddOrMinusExpression = 61;
	public static readonly RULE_expressionList = 62;
	public static readonly RULE_type = 63;
	public static readonly RULE_value = 64;
	public static readonly RULE_className = 65;
	public static readonly RULE_functionTag = 66;
	// tslint:disable:no-trailing-whitespace
	public static readonly ruleNames: string[] = [
		"compilationUnit", "namespaceDeclaration", "typeDeclaration", "classOrFunctionDeclaration", 
		"classDeclaration", "classBody", "staticClassMemberDeclaration", "classMemberDeclaration", 
		"classMember", "classFunctionDeclaration", "functionDeclaration", "namespaceID", 
		"nativeDeclaration", "javaRefer", "stringName", "accessModifier", "constructorDeclaration", 
		"nativeConstructorDeclaration", "constructorCall", "fieldDeclaration", 
		"parameterList", "parameter", "expression", "statementExpression", "conditionalExpression", 
		"conditionalOrExpression", "conditionalAndExpression", "equalityExpression", 
		"relationalExpression", "relationalOp", "additiveExpression", "multiplicativeExpression", 
		"unaryExpression", "basicExpression", "castExpression", "primary", "varWithSelector", 
		"var", "identifierSuffix", "selector", "arguments", "functionBody", "functionCall", 
		"statement", "orgCommand", "controlStatement", "ifStatement", "elseIfStatement", 
		"ifBlock", "forStatement", "forBlock", "forControl", "forInit", "forUpdate", 
		"whileStatement", "whileBlock", "doWhileStatement", "doWhileBlock", "selfAddOrMinusStatement", 
		"tryStoreStatement", "block", "selfAddOrMinusExpression", "expressionList", 
		"type", "value", "className", "functionTag",
	];

	private static readonly _LITERAL_NAMES: Array<string | undefined> = [
		undefined, "'namespace'", "';'", "'class'", "'{'", "'}'", "'func'", "'('", 
		"')'", "':'", "'->'", "'.'", "'new'", "'='", "','", "'?'", "'||'", "'&&'", 
		"'=='", "'!='", "'<='", "'>='", "'<'", "'>'", "'+'", "'-'", "'*'", "'/'", 
		"'%'", "'!'", "'['", "']'", "'++'", "'--'", "'int'", "'bool'", undefined, 
		undefined, "'this'", "'super'", "'if'", "'else'", "'while'", "'for'", 
		"'do'", "'try'", "'store'", "'break'", "'continue'", "'static'", "'extends'", 
		"'native'", "'concrete'", "'final '", "'public'", "'protected'", "'private'", 
		undefined, undefined, undefined, "'vec'", "'~'",
	];
	private static readonly _SYMBOLIC_NAMES: Array<string | undefined> = [
		undefined, undefined, undefined, undefined, undefined, undefined, undefined, 
		undefined, undefined, undefined, undefined, undefined, undefined, undefined, 
		undefined, undefined, undefined, undefined, undefined, undefined, undefined, 
		undefined, undefined, undefined, undefined, undefined, undefined, undefined, 
		undefined, undefined, undefined, undefined, undefined, undefined, undefined, 
		undefined, "OrgCommand", "TargetSelector", "THIS", "SUPER", "IF", "ELSE", 
		"WHILE", "FOR", "DO", "TRY", "STORE", "BREAK", "CONTINUE", "STATIC", "EXTENDS", 
		"NATIVE", "CONCRETE", "FINAL", "PUBLIC", "PROTECTED", "PRIVATE", "InsideClass", 
		"INT", "DECIMAL", "VEC", "WAVE", "Identifier", "ClassIdentifier", "NORMALSTRING", 
		"STRING", "WS", "COMMENT", "LINE_COMMENT",
	];
	public static readonly VOCABULARY: Vocabulary = new VocabularyImpl(MCFPPParser._LITERAL_NAMES, MCFPPParser._SYMBOLIC_NAMES, []);

	// @Override
	// @NotNull
	public get vocabulary(): Vocabulary {
		return MCFPPParser.VOCABULARY;
	}
	// tslint:enable:no-trailing-whitespace

	// @Override
	public get grammarFileName(): string { return "MCFPP.g4"; }

	// @Override
	public get ruleNames(): string[] { return MCFPPParser.ruleNames; }

	// @Override
	public get serializedATN(): string { return MCFPPParser._serializedATN; }

	protected createFailedPredicateException(predicate?: string, message?: string): FailedPredicateException {
		return new FailedPredicateException(this, predicate, message);
	}

	constructor(input: TokenStream) {
		super(input);
		this._interp = new ParserATNSimulator(MCFPPParser._ATN, this);
	}
	// @RuleVersion(0)
	public compilationUnit(): CompilationUnitContext {
		let _localctx: CompilationUnitContext = new CompilationUnitContext(this._ctx, this.state);
		this.enterRule(_localctx, 0, MCFPPParser.RULE_compilationUnit);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 135;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === MCFPPParser.T__0) {
				{
				this.state = 134;
				this.namespaceDeclaration();
				}
			}

			this.state = 140;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === MCFPPParser.T__2 || _la === MCFPPParser.T__5 || ((((_la - 49)) & ~0x1F) === 0 && ((1 << (_la - 49)) & ((1 << (MCFPPParser.STATIC - 49)) | (1 << (MCFPPParser.NATIVE - 49)) | (1 << (MCFPPParser.FINAL - 49)) | (1 << (MCFPPParser.PUBLIC - 49)) | (1 << (MCFPPParser.PROTECTED - 49)) | (1 << (MCFPPParser.PRIVATE - 49)) | (1 << (MCFPPParser.Identifier - 49)))) !== 0)) {
				{
				{
				this.state = 137;
				this.typeDeclaration();
				}
				}
				this.state = 142;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			this.state = 143;
			this.match(MCFPPParser.EOF);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public namespaceDeclaration(): NamespaceDeclarationContext {
		let _localctx: NamespaceDeclarationContext = new NamespaceDeclarationContext(this._ctx, this.state);
		this.enterRule(_localctx, 2, MCFPPParser.RULE_namespaceDeclaration);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 145;
			this.match(MCFPPParser.T__0);
			this.state = 146;
			this.match(MCFPPParser.Identifier);
			this.state = 147;
			this.match(MCFPPParser.T__1);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public typeDeclaration(): TypeDeclarationContext {
		let _localctx: TypeDeclarationContext = new TypeDeclarationContext(this._ctx, this.state);
		this.enterRule(_localctx, 4, MCFPPParser.RULE_typeDeclaration);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 149;
			this.classOrFunctionDeclaration();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public classOrFunctionDeclaration(): ClassOrFunctionDeclarationContext {
		let _localctx: ClassOrFunctionDeclarationContext = new ClassOrFunctionDeclarationContext(this._ctx, this.state);
		this.enterRule(_localctx, 6, MCFPPParser.RULE_classOrFunctionDeclaration);
		try {
			this.state = 154;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case MCFPPParser.T__2:
			case MCFPPParser.STATIC:
			case MCFPPParser.FINAL:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 151;
				this.classDeclaration();
				}
				break;
			case MCFPPParser.T__5:
			case MCFPPParser.Identifier:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 152;
				this.functionDeclaration();
				}
				break;
			case MCFPPParser.NATIVE:
			case MCFPPParser.PUBLIC:
			case MCFPPParser.PROTECTED:
			case MCFPPParser.PRIVATE:
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 153;
				this.nativeDeclaration();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public classDeclaration(): ClassDeclarationContext {
		let _localctx: ClassDeclarationContext = new ClassDeclarationContext(this._ctx, this.state);
		this.enterRule(_localctx, 8, MCFPPParser.RULE_classDeclaration);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 157;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === MCFPPParser.STATIC) {
				{
				this.state = 156;
				this.match(MCFPPParser.STATIC);
				}
			}

			this.state = 160;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === MCFPPParser.FINAL) {
				{
				this.state = 159;
				this.match(MCFPPParser.FINAL);
				}
			}

			this.state = 162;
			this.match(MCFPPParser.T__2);
			this.state = 163;
			this.className();
			this.state = 166;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === MCFPPParser.EXTENDS) {
				{
				this.state = 164;
				this.match(MCFPPParser.EXTENDS);
				this.state = 165;
				this.className();
				}
			}

			this.state = 168;
			this.classBody();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public classBody(): ClassBodyContext {
		let _localctx: ClassBodyContext = new ClassBodyContext(this._ctx, this.state);
		this.enterRule(_localctx, 10, MCFPPParser.RULE_classBody);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 170;
			this.match(MCFPPParser.T__3);
			this.state = 175;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === MCFPPParser.T__5 || ((((_la - 34)) & ~0x1F) === 0 && ((1 << (_la - 34)) & ((1 << (MCFPPParser.T__33 - 34)) | (1 << (MCFPPParser.T__34 - 34)) | (1 << (MCFPPParser.STATIC - 34)) | (1 << (MCFPPParser.NATIVE - 34)) | (1 << (MCFPPParser.PUBLIC - 34)) | (1 << (MCFPPParser.PROTECTED - 34)) | (1 << (MCFPPParser.PRIVATE - 34)) | (1 << (MCFPPParser.InsideClass - 34)) | (1 << (MCFPPParser.Identifier - 34)) | (1 << (MCFPPParser.ClassIdentifier - 34)))) !== 0)) {
				{
				this.state = 173;
				this._errHandler.sync(this);
				switch ( this.interpreter.adaptivePredict(this._input, 6, this._ctx) ) {
				case 1:
					{
					this.state = 171;
					this.classMemberDeclaration();
					}
					break;

				case 2:
					{
					this.state = 172;
					this.staticClassMemberDeclaration();
					}
					break;
				}
				}
				this.state = 177;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			this.state = 178;
			this.match(MCFPPParser.T__4);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public staticClassMemberDeclaration(): StaticClassMemberDeclarationContext {
		let _localctx: StaticClassMemberDeclarationContext = new StaticClassMemberDeclarationContext(this._ctx, this.state);
		this.enterRule(_localctx, 12, MCFPPParser.RULE_staticClassMemberDeclaration);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 181;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (((((_la - 54)) & ~0x1F) === 0 && ((1 << (_la - 54)) & ((1 << (MCFPPParser.PUBLIC - 54)) | (1 << (MCFPPParser.PROTECTED - 54)) | (1 << (MCFPPParser.PRIVATE - 54)))) !== 0)) {
				{
				this.state = 180;
				this.accessModifier();
				}
			}

			this.state = 183;
			this.match(MCFPPParser.STATIC);
			this.state = 184;
			this.classMember();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public classMemberDeclaration(): ClassMemberDeclarationContext {
		let _localctx: ClassMemberDeclarationContext = new ClassMemberDeclarationContext(this._ctx, this.state);
		this.enterRule(_localctx, 14, MCFPPParser.RULE_classMemberDeclaration);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 187;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 9, this._ctx) ) {
			case 1:
				{
				this.state = 186;
				this.accessModifier();
				}
				break;
			}
			this.state = 189;
			this.classMember();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public classMember(): ClassMemberContext {
		let _localctx: ClassMemberContext = new ClassMemberContext(this._ctx, this.state);
		this.enterRule(_localctx, 16, MCFPPParser.RULE_classMember);
		try {
			this.state = 198;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 10, this._ctx) ) {
			case 1:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 191;
				this.classFunctionDeclaration();
				}
				break;

			case 2:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 192;
				this.fieldDeclaration();
				this.state = 193;
				this.match(MCFPPParser.T__1);
				}
				break;

			case 3:
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 195;
				this.constructorDeclaration();
				}
				break;

			case 4:
				this.enterOuterAlt(_localctx, 4);
				{
				this.state = 196;
				this.nativeDeclaration();
				}
				break;

			case 5:
				this.enterOuterAlt(_localctx, 5);
				{
				this.state = 197;
				this.nativeConstructorDeclaration();
				}
				break;
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public classFunctionDeclaration(): ClassFunctionDeclarationContext {
		let _localctx: ClassFunctionDeclarationContext = new ClassFunctionDeclarationContext(this._ctx, this.state);
		this.enterRule(_localctx, 18, MCFPPParser.RULE_classFunctionDeclaration);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 200;
			this.match(MCFPPParser.T__5);
			this.state = 201;
			this.match(MCFPPParser.Identifier);
			this.state = 202;
			this.match(MCFPPParser.T__6);
			this.state = 204;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (((((_la - 34)) & ~0x1F) === 0 && ((1 << (_la - 34)) & ((1 << (MCFPPParser.T__33 - 34)) | (1 << (MCFPPParser.T__34 - 34)) | (1 << (MCFPPParser.STATIC - 34)) | (1 << (MCFPPParser.CONCRETE - 34)) | (1 << (MCFPPParser.InsideClass - 34)) | (1 << (MCFPPParser.Identifier - 34)) | (1 << (MCFPPParser.ClassIdentifier - 34)))) !== 0)) {
				{
				this.state = 203;
				this.parameterList();
				}
			}

			this.state = 206;
			this.match(MCFPPParser.T__7);
			this.state = 207;
			this.match(MCFPPParser.T__3);
			this.state = 208;
			this.functionBody();
			this.state = 209;
			this.match(MCFPPParser.T__4);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public functionDeclaration(): FunctionDeclarationContext {
		let _localctx: FunctionDeclarationContext = new FunctionDeclarationContext(this._ctx, this.state);
		this.enterRule(_localctx, 20, MCFPPParser.RULE_functionDeclaration);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 212;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === MCFPPParser.Identifier) {
				{
				this.state = 211;
				this.functionTag();
				}
			}

			this.state = 214;
			this.match(MCFPPParser.T__5);
			this.state = 215;
			this.namespaceID();
			this.state = 216;
			this.match(MCFPPParser.T__6);
			this.state = 218;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (((((_la - 34)) & ~0x1F) === 0 && ((1 << (_la - 34)) & ((1 << (MCFPPParser.T__33 - 34)) | (1 << (MCFPPParser.T__34 - 34)) | (1 << (MCFPPParser.STATIC - 34)) | (1 << (MCFPPParser.CONCRETE - 34)) | (1 << (MCFPPParser.InsideClass - 34)) | (1 << (MCFPPParser.Identifier - 34)) | (1 << (MCFPPParser.ClassIdentifier - 34)))) !== 0)) {
				{
				this.state = 217;
				this.parameterList();
				}
			}

			this.state = 220;
			this.match(MCFPPParser.T__7);
			this.state = 221;
			this.match(MCFPPParser.T__3);
			this.state = 222;
			this.functionBody();
			this.state = 223;
			this.match(MCFPPParser.T__4);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public namespaceID(): NamespaceIDContext {
		let _localctx: NamespaceIDContext = new NamespaceIDContext(this._ctx, this.state);
		this.enterRule(_localctx, 22, MCFPPParser.RULE_namespaceID);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 227;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 14, this._ctx) ) {
			case 1:
				{
				this.state = 225;
				this.match(MCFPPParser.Identifier);
				this.state = 226;
				this.match(MCFPPParser.T__8);
				}
				break;
			}
			this.state = 229;
			this.match(MCFPPParser.Identifier);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public nativeDeclaration(): NativeDeclarationContext {
		let _localctx: NativeDeclarationContext = new NativeDeclarationContext(this._ctx, this.state);
		this.enterRule(_localctx, 24, MCFPPParser.RULE_nativeDeclaration);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 232;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (((((_la - 54)) & ~0x1F) === 0 && ((1 << (_la - 54)) & ((1 << (MCFPPParser.PUBLIC - 54)) | (1 << (MCFPPParser.PROTECTED - 54)) | (1 << (MCFPPParser.PRIVATE - 54)))) !== 0)) {
				{
				this.state = 231;
				this.accessModifier();
				}
			}

			this.state = 234;
			this.match(MCFPPParser.NATIVE);
			this.state = 235;
			this.match(MCFPPParser.T__5);
			this.state = 236;
			this.match(MCFPPParser.Identifier);
			this.state = 237;
			this.match(MCFPPParser.T__6);
			this.state = 239;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (((((_la - 34)) & ~0x1F) === 0 && ((1 << (_la - 34)) & ((1 << (MCFPPParser.T__33 - 34)) | (1 << (MCFPPParser.T__34 - 34)) | (1 << (MCFPPParser.STATIC - 34)) | (1 << (MCFPPParser.CONCRETE - 34)) | (1 << (MCFPPParser.InsideClass - 34)) | (1 << (MCFPPParser.Identifier - 34)) | (1 << (MCFPPParser.ClassIdentifier - 34)))) !== 0)) {
				{
				this.state = 238;
				this.parameterList();
				}
			}

			this.state = 241;
			this.match(MCFPPParser.T__7);
			this.state = 242;
			this.match(MCFPPParser.T__9);
			this.state = 243;
			this.javaRefer();
			this.state = 244;
			this.match(MCFPPParser.T__1);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public javaRefer(): JavaReferContext {
		let _localctx: JavaReferContext = new JavaReferContext(this._ctx, this.state);
		this.enterRule(_localctx, 26, MCFPPParser.RULE_javaRefer);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 246;
			this.stringName();
			this.state = 251;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === MCFPPParser.T__10) {
				{
				{
				this.state = 247;
				this.match(MCFPPParser.T__10);
				this.state = 248;
				this.stringName();
				}
				}
				this.state = 253;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public stringName(): StringNameContext {
		let _localctx: StringNameContext = new StringNameContext(this._ctx, this.state);
		this.enterRule(_localctx, 28, MCFPPParser.RULE_stringName);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 254;
			_la = this._input.LA(1);
			if (!(((((_la - 62)) & ~0x1F) === 0 && ((1 << (_la - 62)) & ((1 << (MCFPPParser.Identifier - 62)) | (1 << (MCFPPParser.ClassIdentifier - 62)) | (1 << (MCFPPParser.NORMALSTRING - 62)))) !== 0))) {
			this._errHandler.recoverInline(this);
			} else {
				if (this._input.LA(1) === Token.EOF) {
					this.matchedEOF = true;
				}

				this._errHandler.reportMatch(this);
				this.consume();
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public accessModifier(): AccessModifierContext {
		let _localctx: AccessModifierContext = new AccessModifierContext(this._ctx, this.state);
		this.enterRule(_localctx, 30, MCFPPParser.RULE_accessModifier);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 256;
			_la = this._input.LA(1);
			if (!(((((_la - 54)) & ~0x1F) === 0 && ((1 << (_la - 54)) & ((1 << (MCFPPParser.PUBLIC - 54)) | (1 << (MCFPPParser.PROTECTED - 54)) | (1 << (MCFPPParser.PRIVATE - 54)))) !== 0))) {
			this._errHandler.recoverInline(this);
			} else {
				if (this._input.LA(1) === Token.EOF) {
					this.matchedEOF = true;
				}

				this._errHandler.reportMatch(this);
				this.consume();
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public constructorDeclaration(): ConstructorDeclarationContext {
		let _localctx: ConstructorDeclarationContext = new ConstructorDeclarationContext(this._ctx, this.state);
		this.enterRule(_localctx, 32, MCFPPParser.RULE_constructorDeclaration);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 258;
			this.className();
			this.state = 259;
			this.match(MCFPPParser.T__6);
			this.state = 261;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (((((_la - 34)) & ~0x1F) === 0 && ((1 << (_la - 34)) & ((1 << (MCFPPParser.T__33 - 34)) | (1 << (MCFPPParser.T__34 - 34)) | (1 << (MCFPPParser.STATIC - 34)) | (1 << (MCFPPParser.CONCRETE - 34)) | (1 << (MCFPPParser.InsideClass - 34)) | (1 << (MCFPPParser.Identifier - 34)) | (1 << (MCFPPParser.ClassIdentifier - 34)))) !== 0)) {
				{
				this.state = 260;
				this.parameterList();
				}
			}

			this.state = 263;
			this.match(MCFPPParser.T__7);
			this.state = 264;
			this.match(MCFPPParser.T__3);
			this.state = 265;
			this.functionBody();
			this.state = 266;
			this.match(MCFPPParser.T__4);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public nativeConstructorDeclaration(): NativeConstructorDeclarationContext {
		let _localctx: NativeConstructorDeclarationContext = new NativeConstructorDeclarationContext(this._ctx, this.state);
		this.enterRule(_localctx, 34, MCFPPParser.RULE_nativeConstructorDeclaration);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 269;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (((((_la - 54)) & ~0x1F) === 0 && ((1 << (_la - 54)) & ((1 << (MCFPPParser.PUBLIC - 54)) | (1 << (MCFPPParser.PROTECTED - 54)) | (1 << (MCFPPParser.PRIVATE - 54)))) !== 0)) {
				{
				this.state = 268;
				this.accessModifier();
				}
			}

			this.state = 272;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === MCFPPParser.NATIVE) {
				{
				this.state = 271;
				this.match(MCFPPParser.NATIVE);
				}
			}

			this.state = 274;
			this.className();
			this.state = 275;
			this.match(MCFPPParser.T__6);
			this.state = 277;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (((((_la - 34)) & ~0x1F) === 0 && ((1 << (_la - 34)) & ((1 << (MCFPPParser.T__33 - 34)) | (1 << (MCFPPParser.T__34 - 34)) | (1 << (MCFPPParser.STATIC - 34)) | (1 << (MCFPPParser.CONCRETE - 34)) | (1 << (MCFPPParser.InsideClass - 34)) | (1 << (MCFPPParser.Identifier - 34)) | (1 << (MCFPPParser.ClassIdentifier - 34)))) !== 0)) {
				{
				this.state = 276;
				this.parameterList();
				}
			}

			this.state = 279;
			this.match(MCFPPParser.T__7);
			this.state = 280;
			this.match(MCFPPParser.T__9);
			this.state = 281;
			this.javaRefer();
			this.state = 282;
			this.match(MCFPPParser.T__1);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public constructorCall(): ConstructorCallContext {
		let _localctx: ConstructorCallContext = new ConstructorCallContext(this._ctx, this.state);
		this.enterRule(_localctx, 36, MCFPPParser.RULE_constructorCall);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 284;
			this.match(MCFPPParser.T__11);
			this.state = 285;
			this.className();
			this.state = 286;
			this.arguments();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public fieldDeclaration(): FieldDeclarationContext {
		let _localctx: FieldDeclarationContext = new FieldDeclarationContext(this._ctx, this.state);
		this.enterRule(_localctx, 38, MCFPPParser.RULE_fieldDeclaration);
		try {
			this.state = 296;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 22, this._ctx) ) {
			case 1:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 288;
				this.type();
				this.state = 289;
				this.match(MCFPPParser.Identifier);
				}
				break;

			case 2:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 291;
				this.type();
				this.state = 292;
				this.match(MCFPPParser.Identifier);
				this.state = 293;
				this.match(MCFPPParser.T__12);
				this.state = 294;
				this.expression();
				}
				break;
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public parameterList(): ParameterListContext {
		let _localctx: ParameterListContext = new ParameterListContext(this._ctx, this.state);
		this.enterRule(_localctx, 40, MCFPPParser.RULE_parameterList);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 298;
			this.parameter();
			this.state = 303;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === MCFPPParser.T__13) {
				{
				{
				this.state = 299;
				this.match(MCFPPParser.T__13);
				this.state = 300;
				this.parameter();
				}
				}
				this.state = 305;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public parameter(): ParameterContext {
		let _localctx: ParameterContext = new ParameterContext(this._ctx, this.state);
		this.enterRule(_localctx, 42, MCFPPParser.RULE_parameter);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 307;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === MCFPPParser.STATIC) {
				{
				this.state = 306;
				this.match(MCFPPParser.STATIC);
				}
			}

			this.state = 310;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === MCFPPParser.CONCRETE) {
				{
				this.state = 309;
				this.match(MCFPPParser.CONCRETE);
				}
			}

			this.state = 312;
			this.type();
			this.state = 313;
			this.match(MCFPPParser.Identifier);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public expression(): ExpressionContext {
		let _localctx: ExpressionContext = new ExpressionContext(this._ctx, this.state);
		this.enterRule(_localctx, 44, MCFPPParser.RULE_expression);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 315;
			this.conditionalOrExpression();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public statementExpression(): StatementExpressionContext {
		let _localctx: StatementExpressionContext = new StatementExpressionContext(this._ctx, this.state);
		this.enterRule(_localctx, 46, MCFPPParser.RULE_statementExpression);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 317;
			this.varWithSelector();
			this.state = 318;
			this.match(MCFPPParser.T__12);
			this.state = 319;
			this.expression();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public conditionalExpression(): ConditionalExpressionContext {
		let _localctx: ConditionalExpressionContext = new ConditionalExpressionContext(this._ctx, this.state);
		this.enterRule(_localctx, 48, MCFPPParser.RULE_conditionalExpression);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 321;
			this.conditionalOrExpression();
			this.state = 327;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === MCFPPParser.T__14) {
				{
				this.state = 322;
				this.match(MCFPPParser.T__14);
				this.state = 323;
				this.expression();
				this.state = 324;
				this.match(MCFPPParser.T__8);
				this.state = 325;
				this.expression();
				}
			}

			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public conditionalOrExpression(): ConditionalOrExpressionContext {
		let _localctx: ConditionalOrExpressionContext = new ConditionalOrExpressionContext(this._ctx, this.state);
		this.enterRule(_localctx, 50, MCFPPParser.RULE_conditionalOrExpression);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 329;
			this.conditionalAndExpression();
			this.state = 334;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === MCFPPParser.T__15) {
				{
				{
				this.state = 330;
				this.match(MCFPPParser.T__15);
				this.state = 331;
				this.conditionalAndExpression();
				}
				}
				this.state = 336;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public conditionalAndExpression(): ConditionalAndExpressionContext {
		let _localctx: ConditionalAndExpressionContext = new ConditionalAndExpressionContext(this._ctx, this.state);
		this.enterRule(_localctx, 52, MCFPPParser.RULE_conditionalAndExpression);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 337;
			this.equalityExpression();
			this.state = 342;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === MCFPPParser.T__16) {
				{
				{
				this.state = 338;
				this.match(MCFPPParser.T__16);
				this.state = 339;
				this.equalityExpression();
				}
				}
				this.state = 344;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public equalityExpression(): EqualityExpressionContext {
		let _localctx: EqualityExpressionContext = new EqualityExpressionContext(this._ctx, this.state);
		this.enterRule(_localctx, 54, MCFPPParser.RULE_equalityExpression);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 345;
			this.relationalExpression();
			this.state = 350;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === MCFPPParser.T__17 || _la === MCFPPParser.T__18) {
				{
				{
				this.state = 346;
				_localctx._op = this._input.LT(1);
				_la = this._input.LA(1);
				if (!(_la === MCFPPParser.T__17 || _la === MCFPPParser.T__18)) {
					_localctx._op = this._errHandler.recoverInline(this);
				} else {
					if (this._input.LA(1) === Token.EOF) {
						this.matchedEOF = true;
					}

					this._errHandler.reportMatch(this);
					this.consume();
				}
				this.state = 347;
				this.relationalExpression();
				}
				}
				this.state = 352;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public relationalExpression(): RelationalExpressionContext {
		let _localctx: RelationalExpressionContext = new RelationalExpressionContext(this._ctx, this.state);
		this.enterRule(_localctx, 56, MCFPPParser.RULE_relationalExpression);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 353;
			this.additiveExpression();
			this.state = 357;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if ((((_la) & ~0x1F) === 0 && ((1 << _la) & ((1 << MCFPPParser.T__19) | (1 << MCFPPParser.T__20) | (1 << MCFPPParser.T__21) | (1 << MCFPPParser.T__22))) !== 0)) {
				{
				this.state = 354;
				this.relationalOp();
				this.state = 355;
				this.additiveExpression();
				}
			}

			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public relationalOp(): RelationalOpContext {
		let _localctx: RelationalOpContext = new RelationalOpContext(this._ctx, this.state);
		this.enterRule(_localctx, 58, MCFPPParser.RULE_relationalOp);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 359;
			_la = this._input.LA(1);
			if (!((((_la) & ~0x1F) === 0 && ((1 << _la) & ((1 << MCFPPParser.T__19) | (1 << MCFPPParser.T__20) | (1 << MCFPPParser.T__21) | (1 << MCFPPParser.T__22))) !== 0))) {
			this._errHandler.recoverInline(this);
			} else {
				if (this._input.LA(1) === Token.EOF) {
					this.matchedEOF = true;
				}

				this._errHandler.reportMatch(this);
				this.consume();
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public additiveExpression(): AdditiveExpressionContext {
		let _localctx: AdditiveExpressionContext = new AdditiveExpressionContext(this._ctx, this.state);
		this.enterRule(_localctx, 60, MCFPPParser.RULE_additiveExpression);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 361;
			this.multiplicativeExpression();
			this.state = 366;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === MCFPPParser.T__23 || _la === MCFPPParser.T__24) {
				{
				{
				this.state = 362;
				_localctx._op = this._input.LT(1);
				_la = this._input.LA(1);
				if (!(_la === MCFPPParser.T__23 || _la === MCFPPParser.T__24)) {
					_localctx._op = this._errHandler.recoverInline(this);
				} else {
					if (this._input.LA(1) === Token.EOF) {
						this.matchedEOF = true;
					}

					this._errHandler.reportMatch(this);
					this.consume();
				}
				this.state = 363;
				this.multiplicativeExpression();
				}
				}
				this.state = 368;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public multiplicativeExpression(): MultiplicativeExpressionContext {
		let _localctx: MultiplicativeExpressionContext = new MultiplicativeExpressionContext(this._ctx, this.state);
		this.enterRule(_localctx, 62, MCFPPParser.RULE_multiplicativeExpression);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 369;
			this.unaryExpression();
			this.state = 374;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while ((((_la) & ~0x1F) === 0 && ((1 << _la) & ((1 << MCFPPParser.T__25) | (1 << MCFPPParser.T__26) | (1 << MCFPPParser.T__27))) !== 0)) {
				{
				{
				this.state = 370;
				_localctx._op = this._input.LT(1);
				_la = this._input.LA(1);
				if (!((((_la) & ~0x1F) === 0 && ((1 << _la) & ((1 << MCFPPParser.T__25) | (1 << MCFPPParser.T__26) | (1 << MCFPPParser.T__27))) !== 0))) {
					_localctx._op = this._errHandler.recoverInline(this);
				} else {
					if (this._input.LA(1) === Token.EOF) {
						this.matchedEOF = true;
					}

					this._errHandler.reportMatch(this);
					this.consume();
				}
				this.state = 371;
				this.unaryExpression();
				}
				}
				this.state = 376;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public unaryExpression(): UnaryExpressionContext {
		let _localctx: UnaryExpressionContext = new UnaryExpressionContext(this._ctx, this.state);
		this.enterRule(_localctx, 64, MCFPPParser.RULE_unaryExpression);
		try {
			this.state = 381;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 33, this._ctx) ) {
			case 1:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 377;
				this.match(MCFPPParser.T__28);
				this.state = 378;
				this.unaryExpression();
				}
				break;

			case 2:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 379;
				this.castExpression();
				}
				break;

			case 3:
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 380;
				this.basicExpression();
				}
				break;
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public basicExpression(): BasicExpressionContext {
		let _localctx: BasicExpressionContext = new BasicExpressionContext(this._ctx, this.state);
		this.enterRule(_localctx, 66, MCFPPParser.RULE_basicExpression);
		try {
			this.state = 385;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 34, this._ctx) ) {
			case 1:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 383;
				this.primary();
				}
				break;

			case 2:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 384;
				this.varWithSelector();
				}
				break;
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public castExpression(): CastExpressionContext {
		let _localctx: CastExpressionContext = new CastExpressionContext(this._ctx, this.state);
		this.enterRule(_localctx, 68, MCFPPParser.RULE_castExpression);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 387;
			this.match(MCFPPParser.T__6);
			this.state = 388;
			this.type();
			this.state = 389;
			this.match(MCFPPParser.T__7);
			this.state = 390;
			this.unaryExpression();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public primary(): PrimaryContext {
		let _localctx: PrimaryContext = new PrimaryContext(this._ctx, this.state);
		this.enterRule(_localctx, 70, MCFPPParser.RULE_primary);
		try {
			this.state = 394;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case MCFPPParser.T__6:
			case MCFPPParser.T__11:
			case MCFPPParser.TargetSelector:
			case MCFPPParser.THIS:
			case MCFPPParser.SUPER:
			case MCFPPParser.Identifier:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 392;
				this.var();
				}
				break;
			case MCFPPParser.INT:
			case MCFPPParser.DECIMAL:
			case MCFPPParser.STRING:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 393;
				this.value();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public varWithSelector(): VarWithSelectorContext {
		let _localctx: VarWithSelectorContext = new VarWithSelectorContext(this._ctx, this.state);
		this.enterRule(_localctx, 72, MCFPPParser.RULE_varWithSelector);
		let _la: number;
		try {
			this.state = 409;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 38, this._ctx) ) {
			case 1:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 396;
				this.var();
				this.state = 400;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				while (_la === MCFPPParser.T__10) {
					{
					{
					this.state = 397;
					this.selector();
					}
					}
					this.state = 402;
					this._errHandler.sync(this);
					_la = this._input.LA(1);
				}
				}
				break;

			case 2:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 403;
				this.className();
				this.state = 405;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				do {
					{
					{
					this.state = 404;
					this.selector();
					}
					}
					this.state = 407;
					this._errHandler.sync(this);
					_la = this._input.LA(1);
				} while (_la === MCFPPParser.T__10);
				}
				break;
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public var(): VarContext {
		let _localctx: VarContext = new VarContext(this._ctx, this.state);
		this.enterRule(_localctx, 74, MCFPPParser.RULE_var);
		let _la: number;
		try {
			this.state = 426;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case MCFPPParser.T__6:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 411;
				this.match(MCFPPParser.T__6);
				this.state = 412;
				this.expression();
				this.state = 413;
				this.match(MCFPPParser.T__7);
				}
				break;
			case MCFPPParser.Identifier:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 415;
				this.match(MCFPPParser.Identifier);
				this.state = 419;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				while (_la === MCFPPParser.T__29) {
					{
					{
					this.state = 416;
					this.identifierSuffix();
					}
					}
					this.state = 421;
					this._errHandler.sync(this);
					_la = this._input.LA(1);
				}
				}
				break;
			case MCFPPParser.THIS:
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 422;
				this.match(MCFPPParser.THIS);
				}
				break;
			case MCFPPParser.SUPER:
				this.enterOuterAlt(_localctx, 4);
				{
				this.state = 423;
				this.match(MCFPPParser.SUPER);
				}
				break;
			case MCFPPParser.T__11:
				this.enterOuterAlt(_localctx, 5);
				{
				this.state = 424;
				this.constructorCall();
				}
				break;
			case MCFPPParser.TargetSelector:
				this.enterOuterAlt(_localctx, 6);
				{
				this.state = 425;
				this.match(MCFPPParser.TargetSelector);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public identifierSuffix(): IdentifierSuffixContext {
		let _localctx: IdentifierSuffixContext = new IdentifierSuffixContext(this._ctx, this.state);
		this.enterRule(_localctx, 76, MCFPPParser.RULE_identifierSuffix);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 428;
			this.match(MCFPPParser.T__29);
			this.state = 429;
			this.conditionalExpression();
			this.state = 430;
			this.match(MCFPPParser.T__30);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public selector(): SelectorContext {
		let _localctx: SelectorContext = new SelectorContext(this._ctx, this.state);
		this.enterRule(_localctx, 78, MCFPPParser.RULE_selector);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 432;
			this.match(MCFPPParser.T__10);
			this.state = 433;
			this.var();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public arguments(): ArgumentsContext {
		let _localctx: ArgumentsContext = new ArgumentsContext(this._ctx, this.state);
		this.enterRule(_localctx, 80, MCFPPParser.RULE_arguments);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 435;
			this.match(MCFPPParser.T__6);
			this.state = 437;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if ((((_la) & ~0x1F) === 0 && ((1 << _la) & ((1 << MCFPPParser.T__6) | (1 << MCFPPParser.T__11) | (1 << MCFPPParser.T__28))) !== 0) || ((((_la - 37)) & ~0x1F) === 0 && ((1 << (_la - 37)) & ((1 << (MCFPPParser.TargetSelector - 37)) | (1 << (MCFPPParser.THIS - 37)) | (1 << (MCFPPParser.SUPER - 37)) | (1 << (MCFPPParser.InsideClass - 37)) | (1 << (MCFPPParser.INT - 37)) | (1 << (MCFPPParser.DECIMAL - 37)) | (1 << (MCFPPParser.Identifier - 37)) | (1 << (MCFPPParser.ClassIdentifier - 37)) | (1 << (MCFPPParser.STRING - 37)))) !== 0)) {
				{
				this.state = 436;
				this.expressionList();
				}
			}

			this.state = 439;
			this.match(MCFPPParser.T__7);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public functionBody(): FunctionBodyContext {
		let _localctx: FunctionBodyContext = new FunctionBodyContext(this._ctx, this.state);
		this.enterRule(_localctx, 82, MCFPPParser.RULE_functionBody);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 444;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while ((((_la) & ~0x1F) === 0 && ((1 << _la) & ((1 << MCFPPParser.T__1) | (1 << MCFPPParser.T__6) | (1 << MCFPPParser.T__11))) !== 0) || ((((_la - 34)) & ~0x1F) === 0 && ((1 << (_la - 34)) & ((1 << (MCFPPParser.T__33 - 34)) | (1 << (MCFPPParser.T__34 - 34)) | (1 << (MCFPPParser.OrgCommand - 34)) | (1 << (MCFPPParser.TargetSelector - 34)) | (1 << (MCFPPParser.THIS - 34)) | (1 << (MCFPPParser.SUPER - 34)) | (1 << (MCFPPParser.IF - 34)) | (1 << (MCFPPParser.WHILE - 34)) | (1 << (MCFPPParser.FOR - 34)) | (1 << (MCFPPParser.DO - 34)) | (1 << (MCFPPParser.TRY - 34)) | (1 << (MCFPPParser.BREAK - 34)) | (1 << (MCFPPParser.CONTINUE - 34)) | (1 << (MCFPPParser.InsideClass - 34)) | (1 << (MCFPPParser.INT - 34)) | (1 << (MCFPPParser.DECIMAL - 34)) | (1 << (MCFPPParser.Identifier - 34)) | (1 << (MCFPPParser.ClassIdentifier - 34)) | (1 << (MCFPPParser.STRING - 34)))) !== 0)) {
				{
				{
				this.state = 441;
				this.statement();
				}
				}
				this.state = 446;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public functionCall(): FunctionCallContext {
		let _localctx: FunctionCallContext = new FunctionCallContext(this._ctx, this.state);
		this.enterRule(_localctx, 84, MCFPPParser.RULE_functionCall);
		try {
			this.state = 457;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 43, this._ctx) ) {
			case 1:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 447;
				this.namespaceID();
				this.state = 448;
				this.arguments();
				}
				break;

			case 2:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 450;
				this.match(MCFPPParser.THIS);
				this.state = 451;
				this.arguments();
				}
				break;

			case 3:
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 452;
				this.match(MCFPPParser.SUPER);
				this.state = 453;
				this.arguments();
				}
				break;

			case 4:
				this.enterOuterAlt(_localctx, 4);
				{
				this.state = 454;
				this.basicExpression();
				this.state = 455;
				this.arguments();
				}
				break;
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public statement(): StatementContext {
		let _localctx: StatementContext = new StatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 86, MCFPPParser.RULE_statement);
		try {
			this.state = 483;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 44, this._ctx) ) {
			case 1:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 459;
				this.fieldDeclaration();
				this.state = 460;
				this.match(MCFPPParser.T__1);
				}
				break;

			case 2:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 462;
				this.statementExpression();
				this.state = 463;
				this.match(MCFPPParser.T__1);
				}
				break;

			case 3:
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 465;
				this.functionCall();
				this.state = 466;
				this.match(MCFPPParser.T__1);
				}
				break;

			case 4:
				this.enterOuterAlt(_localctx, 4);
				{
				this.state = 468;
				this.ifStatement();
				}
				break;

			case 5:
				this.enterOuterAlt(_localctx, 5);
				{
				this.state = 469;
				this.forStatement();
				}
				break;

			case 6:
				this.enterOuterAlt(_localctx, 6);
				{
				this.state = 470;
				this.whileStatement();
				}
				break;

			case 7:
				this.enterOuterAlt(_localctx, 7);
				{
				this.state = 471;
				this.doWhileStatement();
				this.state = 472;
				this.match(MCFPPParser.T__1);
				}
				break;

			case 8:
				this.enterOuterAlt(_localctx, 8);
				{
				this.state = 474;
				this.match(MCFPPParser.T__1);
				}
				break;

			case 9:
				this.enterOuterAlt(_localctx, 9);
				{
				this.state = 475;
				this.selfAddOrMinusStatement();
				this.state = 476;
				this.match(MCFPPParser.T__1);
				}
				break;

			case 10:
				this.enterOuterAlt(_localctx, 10);
				{
				this.state = 478;
				this.tryStoreStatement();
				}
				break;

			case 11:
				this.enterOuterAlt(_localctx, 11);
				{
				this.state = 479;
				this.controlStatement();
				this.state = 480;
				this.match(MCFPPParser.T__1);
				}
				break;

			case 12:
				this.enterOuterAlt(_localctx, 12);
				{
				this.state = 482;
				this.orgCommand();
				}
				break;
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public orgCommand(): OrgCommandContext {
		let _localctx: OrgCommandContext = new OrgCommandContext(this._ctx, this.state);
		this.enterRule(_localctx, 88, MCFPPParser.RULE_orgCommand);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 485;
			this.match(MCFPPParser.OrgCommand);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public controlStatement(): ControlStatementContext {
		let _localctx: ControlStatementContext = new ControlStatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 90, MCFPPParser.RULE_controlStatement);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 487;
			_la = this._input.LA(1);
			if (!(_la === MCFPPParser.BREAK || _la === MCFPPParser.CONTINUE)) {
			this._errHandler.recoverInline(this);
			} else {
				if (this._input.LA(1) === Token.EOF) {
					this.matchedEOF = true;
				}

				this._errHandler.reportMatch(this);
				this.consume();
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public ifStatement(): IfStatementContext {
		let _localctx: IfStatementContext = new IfStatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 92, MCFPPParser.RULE_ifStatement);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 489;
			this.match(MCFPPParser.IF);
			this.state = 490;
			this.match(MCFPPParser.T__6);
			this.state = 491;
			this.expression();
			this.state = 492;
			this.match(MCFPPParser.T__7);
			this.state = 493;
			this.ifBlock();
			this.state = 499;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if (_la === MCFPPParser.ELSE) {
				{
				this.state = 494;
				this.match(MCFPPParser.ELSE);
				this.state = 497;
				this._errHandler.sync(this);
				switch (this._input.LA(1)) {
				case MCFPPParser.IF:
					{
					this.state = 495;
					this.elseIfStatement();
					}
					break;
				case MCFPPParser.T__3:
					{
					this.state = 496;
					this.ifBlock();
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
			}

			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public elseIfStatement(): ElseIfStatementContext {
		let _localctx: ElseIfStatementContext = new ElseIfStatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 94, MCFPPParser.RULE_elseIfStatement);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 501;
			this.ifStatement();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public ifBlock(): IfBlockContext {
		let _localctx: IfBlockContext = new IfBlockContext(this._ctx, this.state);
		this.enterRule(_localctx, 96, MCFPPParser.RULE_ifBlock);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 503;
			this.block();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public forStatement(): ForStatementContext {
		let _localctx: ForStatementContext = new ForStatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 98, MCFPPParser.RULE_forStatement);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 505;
			this.match(MCFPPParser.FOR);
			this.state = 506;
			this.match(MCFPPParser.T__6);
			this.state = 507;
			this.forControl();
			this.state = 508;
			this.match(MCFPPParser.T__7);
			this.state = 509;
			this.forBlock();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public forBlock(): ForBlockContext {
		let _localctx: ForBlockContext = new ForBlockContext(this._ctx, this.state);
		this.enterRule(_localctx, 100, MCFPPParser.RULE_forBlock);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 511;
			this.block();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public forControl(): ForControlContext {
		let _localctx: ForControlContext = new ForControlContext(this._ctx, this.state);
		this.enterRule(_localctx, 102, MCFPPParser.RULE_forControl);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 516;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === MCFPPParser.T__6 || _la === MCFPPParser.T__11 || ((((_la - 34)) & ~0x1F) === 0 && ((1 << (_la - 34)) & ((1 << (MCFPPParser.T__33 - 34)) | (1 << (MCFPPParser.T__34 - 34)) | (1 << (MCFPPParser.TargetSelector - 34)) | (1 << (MCFPPParser.THIS - 34)) | (1 << (MCFPPParser.SUPER - 34)) | (1 << (MCFPPParser.InsideClass - 34)) | (1 << (MCFPPParser.Identifier - 34)) | (1 << (MCFPPParser.ClassIdentifier - 34)))) !== 0)) {
				{
				{
				this.state = 513;
				this.forInit();
				}
				}
				this.state = 518;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			this.state = 519;
			this.match(MCFPPParser.T__1);
			this.state = 521;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			if ((((_la) & ~0x1F) === 0 && ((1 << _la) & ((1 << MCFPPParser.T__6) | (1 << MCFPPParser.T__11) | (1 << MCFPPParser.T__28))) !== 0) || ((((_la - 37)) & ~0x1F) === 0 && ((1 << (_la - 37)) & ((1 << (MCFPPParser.TargetSelector - 37)) | (1 << (MCFPPParser.THIS - 37)) | (1 << (MCFPPParser.SUPER - 37)) | (1 << (MCFPPParser.InsideClass - 37)) | (1 << (MCFPPParser.INT - 37)) | (1 << (MCFPPParser.DECIMAL - 37)) | (1 << (MCFPPParser.Identifier - 37)) | (1 << (MCFPPParser.ClassIdentifier - 37)) | (1 << (MCFPPParser.STRING - 37)))) !== 0)) {
				{
				this.state = 520;
				this.expression();
				}
			}

			this.state = 523;
			this.match(MCFPPParser.T__1);
			this.state = 524;
			this.forUpdate();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public forInit(): ForInitContext {
		let _localctx: ForInitContext = new ForInitContext(this._ctx, this.state);
		this.enterRule(_localctx, 104, MCFPPParser.RULE_forInit);
		try {
			this.state = 528;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 49, this._ctx) ) {
			case 1:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 526;
				this.fieldDeclaration();
				}
				break;

			case 2:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 527;
				this.statementExpression();
				}
				break;
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public forUpdate(): ForUpdateContext {
		let _localctx: ForUpdateContext = new ForUpdateContext(this._ctx, this.state);
		this.enterRule(_localctx, 106, MCFPPParser.RULE_forUpdate);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 533;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === MCFPPParser.T__6 || _la === MCFPPParser.T__11 || ((((_la - 37)) & ~0x1F) === 0 && ((1 << (_la - 37)) & ((1 << (MCFPPParser.TargetSelector - 37)) | (1 << (MCFPPParser.THIS - 37)) | (1 << (MCFPPParser.SUPER - 37)) | (1 << (MCFPPParser.InsideClass - 37)) | (1 << (MCFPPParser.Identifier - 37)) | (1 << (MCFPPParser.ClassIdentifier - 37)))) !== 0)) {
				{
				{
				this.state = 530;
				this.statementExpression();
				}
				}
				this.state = 535;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public whileStatement(): WhileStatementContext {
		let _localctx: WhileStatementContext = new WhileStatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 108, MCFPPParser.RULE_whileStatement);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 536;
			this.match(MCFPPParser.WHILE);
			this.state = 537;
			this.match(MCFPPParser.T__6);
			this.state = 538;
			this.expression();
			this.state = 539;
			this.match(MCFPPParser.T__7);
			this.state = 540;
			this.whileBlock();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public whileBlock(): WhileBlockContext {
		let _localctx: WhileBlockContext = new WhileBlockContext(this._ctx, this.state);
		this.enterRule(_localctx, 110, MCFPPParser.RULE_whileBlock);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 542;
			this.block();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public doWhileStatement(): DoWhileStatementContext {
		let _localctx: DoWhileStatementContext = new DoWhileStatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 112, MCFPPParser.RULE_doWhileStatement);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 544;
			this.match(MCFPPParser.DO);
			this.state = 545;
			this.doWhileBlock();
			this.state = 546;
			this.match(MCFPPParser.WHILE);
			this.state = 547;
			this.match(MCFPPParser.T__6);
			this.state = 548;
			this.expression();
			this.state = 549;
			this.match(MCFPPParser.T__7);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public doWhileBlock(): DoWhileBlockContext {
		let _localctx: DoWhileBlockContext = new DoWhileBlockContext(this._ctx, this.state);
		this.enterRule(_localctx, 114, MCFPPParser.RULE_doWhileBlock);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 551;
			this.block();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public selfAddOrMinusStatement(): SelfAddOrMinusStatementContext {
		let _localctx: SelfAddOrMinusStatementContext = new SelfAddOrMinusStatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 116, MCFPPParser.RULE_selfAddOrMinusStatement);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 553;
			this.selfAddOrMinusExpression();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public tryStoreStatement(): TryStoreStatementContext {
		let _localctx: TryStoreStatementContext = new TryStoreStatementContext(this._ctx, this.state);
		this.enterRule(_localctx, 118, MCFPPParser.RULE_tryStoreStatement);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 555;
			this.match(MCFPPParser.TRY);
			this.state = 556;
			this.block();
			this.state = 557;
			this.match(MCFPPParser.STORE);
			this.state = 558;
			this.match(MCFPPParser.T__6);
			this.state = 559;
			this.match(MCFPPParser.Identifier);
			this.state = 560;
			this.match(MCFPPParser.T__7);
			this.state = 561;
			this.match(MCFPPParser.T__1);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public block(): BlockContext {
		let _localctx: BlockContext = new BlockContext(this._ctx, this.state);
		this.enterRule(_localctx, 120, MCFPPParser.RULE_block);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 563;
			this.match(MCFPPParser.T__3);
			this.state = 567;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while ((((_la) & ~0x1F) === 0 && ((1 << _la) & ((1 << MCFPPParser.T__1) | (1 << MCFPPParser.T__6) | (1 << MCFPPParser.T__11))) !== 0) || ((((_la - 34)) & ~0x1F) === 0 && ((1 << (_la - 34)) & ((1 << (MCFPPParser.T__33 - 34)) | (1 << (MCFPPParser.T__34 - 34)) | (1 << (MCFPPParser.OrgCommand - 34)) | (1 << (MCFPPParser.TargetSelector - 34)) | (1 << (MCFPPParser.THIS - 34)) | (1 << (MCFPPParser.SUPER - 34)) | (1 << (MCFPPParser.IF - 34)) | (1 << (MCFPPParser.WHILE - 34)) | (1 << (MCFPPParser.FOR - 34)) | (1 << (MCFPPParser.DO - 34)) | (1 << (MCFPPParser.TRY - 34)) | (1 << (MCFPPParser.BREAK - 34)) | (1 << (MCFPPParser.CONTINUE - 34)) | (1 << (MCFPPParser.InsideClass - 34)) | (1 << (MCFPPParser.INT - 34)) | (1 << (MCFPPParser.DECIMAL - 34)) | (1 << (MCFPPParser.Identifier - 34)) | (1 << (MCFPPParser.ClassIdentifier - 34)) | (1 << (MCFPPParser.STRING - 34)))) !== 0)) {
				{
				{
				this.state = 564;
				this.statement();
				}
				}
				this.state = 569;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			this.state = 570;
			this.match(MCFPPParser.T__4);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public selfAddOrMinusExpression(): SelfAddOrMinusExpressionContext {
		let _localctx: SelfAddOrMinusExpressionContext = new SelfAddOrMinusExpressionContext(this._ctx, this.state);
		this.enterRule(_localctx, 122, MCFPPParser.RULE_selfAddOrMinusExpression);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 572;
			this.match(MCFPPParser.Identifier);
			this.state = 573;
			_localctx._op = this._input.LT(1);
			_la = this._input.LA(1);
			if (!(_la === MCFPPParser.T__31 || _la === MCFPPParser.T__32)) {
				_localctx._op = this._errHandler.recoverInline(this);
			} else {
				if (this._input.LA(1) === Token.EOF) {
					this.matchedEOF = true;
				}

				this._errHandler.reportMatch(this);
				this.consume();
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public expressionList(): ExpressionListContext {
		let _localctx: ExpressionListContext = new ExpressionListContext(this._ctx, this.state);
		this.enterRule(_localctx, 124, MCFPPParser.RULE_expressionList);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 575;
			this.expression();
			this.state = 580;
			this._errHandler.sync(this);
			_la = this._input.LA(1);
			while (_la === MCFPPParser.T__13) {
				{
				{
				this.state = 576;
				this.match(MCFPPParser.T__13);
				this.state = 577;
				this.expression();
				}
				}
				this.state = 582;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public type(): TypeContext {
		let _localctx: TypeContext = new TypeContext(this._ctx, this.state);
		this.enterRule(_localctx, 126, MCFPPParser.RULE_type);
		try {
			this.state = 586;
			this._errHandler.sync(this);
			switch (this._input.LA(1)) {
			case MCFPPParser.T__33:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 583;
				this.match(MCFPPParser.T__33);
				}
				break;
			case MCFPPParser.T__34:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 584;
				this.match(MCFPPParser.T__34);
				}
				break;
			case MCFPPParser.InsideClass:
			case MCFPPParser.Identifier:
			case MCFPPParser.ClassIdentifier:
				this.enterOuterAlt(_localctx, 3);
				{
				this.state = 585;
				this.className();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public value(): ValueContext {
		let _localctx: ValueContext = new ValueContext(this._ctx, this.state);
		this.enterRule(_localctx, 128, MCFPPParser.RULE_value);
		let _la: number;
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 588;
			_la = this._input.LA(1);
			if (!(((((_la - 58)) & ~0x1F) === 0 && ((1 << (_la - 58)) & ((1 << (MCFPPParser.INT - 58)) | (1 << (MCFPPParser.DECIMAL - 58)) | (1 << (MCFPPParser.STRING - 58)))) !== 0))) {
			this._errHandler.recoverInline(this);
			} else {
				if (this._input.LA(1) === Token.EOF) {
					this.matchedEOF = true;
				}

				this._errHandler.reportMatch(this);
				this.consume();
			}
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public className(): ClassNameContext {
		let _localctx: ClassNameContext = new ClassNameContext(this._ctx, this.state);
		this.enterRule(_localctx, 130, MCFPPParser.RULE_className);
		let _la: number;
		try {
			this.state = 600;
			this._errHandler.sync(this);
			switch ( this.interpreter.adaptivePredict(this._input, 56, this._ctx) ) {
			case 1:
				this.enterOuterAlt(_localctx, 1);
				{
				this.state = 592;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === MCFPPParser.Identifier) {
					{
					this.state = 590;
					this.match(MCFPPParser.Identifier);
					this.state = 591;
					this.match(MCFPPParser.T__8);
					}
				}

				this.state = 594;
				this.match(MCFPPParser.ClassIdentifier);
				}
				break;

			case 2:
				this.enterOuterAlt(_localctx, 2);
				{
				this.state = 597;
				this._errHandler.sync(this);
				_la = this._input.LA(1);
				if (_la === MCFPPParser.Identifier) {
					{
					this.state = 595;
					this.match(MCFPPParser.Identifier);
					this.state = 596;
					this.match(MCFPPParser.T__8);
					}
				}

				this.state = 599;
				this.match(MCFPPParser.InsideClass);
				}
				break;
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}
	// @RuleVersion(0)
	public functionTag(): FunctionTagContext {
		let _localctx: FunctionTagContext = new FunctionTagContext(this._ctx, this.state);
		this.enterRule(_localctx, 132, MCFPPParser.RULE_functionTag);
		try {
			this.enterOuterAlt(_localctx, 1);
			{
			this.state = 602;
			this.namespaceID();
			}
		}
		catch (re) {
			if (re instanceof RecognitionException) {
				_localctx.exception = re;
				this._errHandler.reportError(this, re);
				this._errHandler.recover(this, re);
			} else {
				throw re;
			}
		}
		finally {
			this.exitRule();
		}
		return _localctx;
	}

	private static readonly _serializedATNSegments: number = 2;
	private static readonly _serializedATNSegment0: string =
		"\x03\uC91D\uCABA\u058D\uAFBA\u4F53\u0607\uEA8B\uC241\x03F\u025F\x04\x02" +
		"\t\x02\x04\x03\t\x03\x04\x04\t\x04\x04\x05\t\x05\x04\x06\t\x06\x04\x07" +
		"\t\x07\x04\b\t\b\x04\t\t\t\x04\n\t\n\x04\v\t\v\x04\f\t\f\x04\r\t\r\x04" +
		"\x0E\t\x0E\x04\x0F\t\x0F\x04\x10\t\x10\x04\x11\t\x11\x04\x12\t\x12\x04" +
		"\x13\t\x13\x04\x14\t\x14\x04\x15\t\x15\x04\x16\t\x16\x04\x17\t\x17\x04" +
		"\x18\t\x18\x04\x19\t\x19\x04\x1A\t\x1A\x04\x1B\t\x1B\x04\x1C\t\x1C\x04" +
		"\x1D\t\x1D\x04\x1E\t\x1E\x04\x1F\t\x1F\x04 \t \x04!\t!\x04\"\t\"\x04#" +
		"\t#\x04$\t$\x04%\t%\x04&\t&\x04\'\t\'\x04(\t(\x04)\t)\x04*\t*\x04+\t+" +
		"\x04,\t,\x04-\t-\x04.\t.\x04/\t/\x040\t0\x041\t1\x042\t2\x043\t3\x044" +
		"\t4\x045\t5\x046\t6\x047\t7\x048\t8\x049\t9\x04:\t:\x04;\t;\x04<\t<\x04" +
		"=\t=\x04>\t>\x04?\t?\x04@\t@\x04A\tA\x04B\tB\x04C\tC\x04D\tD\x03\x02\x05" +
		"\x02\x8A\n\x02\x03\x02\x07\x02\x8D\n\x02\f\x02\x0E\x02\x90\v\x02\x03\x02" +
		"\x03\x02\x03\x03\x03\x03\x03\x03\x03\x03\x03\x04\x03\x04\x03\x05\x03\x05" +
		"\x03\x05\x05\x05\x9D\n\x05\x03\x06\x05\x06\xA0\n\x06\x03\x06\x05\x06\xA3" +
		"\n\x06\x03\x06\x03\x06\x03\x06\x03\x06\x05\x06\xA9\n\x06\x03\x06\x03\x06" +
		"\x03\x07\x03\x07\x03\x07\x07\x07\xB0\n\x07\f\x07\x0E\x07\xB3\v\x07\x03" +
		"\x07\x03\x07\x03\b\x05\b\xB8\n\b\x03\b\x03\b\x03\b\x03\t\x05\t\xBE\n\t" +
		"\x03\t\x03\t\x03\n\x03\n\x03\n\x03\n\x03\n\x03\n\x03\n\x05\n\xC9\n\n\x03" +
		"\v\x03\v\x03\v\x03\v\x05\v\xCF\n\v\x03\v\x03\v\x03\v\x03\v\x03\v\x03\f" +
		"\x05\f\xD7\n\f\x03\f\x03\f\x03\f\x03\f\x05\f\xDD\n\f\x03\f\x03\f\x03\f" +
		"\x03\f\x03\f\x03\r\x03\r\x05\r\xE6\n\r\x03\r\x03\r\x03\x0E\x05\x0E\xEB" +
		"\n\x0E\x03\x0E\x03\x0E\x03\x0E\x03\x0E\x03\x0E\x05\x0E\xF2\n\x0E\x03\x0E" +
		"\x03\x0E\x03\x0E\x03\x0E\x03\x0E\x03\x0F\x03\x0F\x03\x0F\x07\x0F\xFC\n" +
		"\x0F\f\x0F\x0E\x0F\xFF\v\x0F\x03\x10\x03\x10\x03\x11\x03\x11\x03\x12\x03" +
		"\x12\x03\x12\x05\x12\u0108\n\x12\x03\x12\x03\x12\x03\x12\x03\x12\x03\x12" +
		"\x03\x13\x05\x13\u0110\n\x13\x03\x13\x05\x13\u0113\n\x13\x03\x13\x03\x13" +
		"\x03\x13\x05\x13\u0118\n\x13\x03\x13\x03\x13\x03\x13\x03\x13\x03\x13\x03" +
		"\x14\x03\x14\x03\x14\x03\x14\x03\x15\x03\x15\x03\x15\x03\x15\x03\x15\x03" +
		"\x15\x03\x15\x03\x15\x05\x15\u012B\n\x15\x03\x16\x03\x16\x03\x16\x07\x16" +
		"\u0130\n\x16\f\x16\x0E\x16\u0133\v\x16\x03\x17\x05\x17\u0136\n\x17\x03" +
		"\x17\x05\x17\u0139\n\x17\x03\x17\x03\x17\x03\x17\x03\x18\x03\x18\x03\x19" +
		"\x03\x19\x03\x19\x03\x19\x03\x1A\x03\x1A\x03\x1A\x03\x1A\x03\x1A\x03\x1A" +
		"\x05\x1A\u014A\n\x1A\x03\x1B\x03\x1B\x03\x1B\x07\x1B\u014F\n\x1B\f\x1B" +
		"\x0E\x1B\u0152\v\x1B\x03\x1C\x03\x1C\x03\x1C\x07\x1C\u0157\n\x1C\f\x1C" +
		"\x0E\x1C\u015A\v\x1C\x03\x1D\x03\x1D\x03\x1D\x07\x1D\u015F\n\x1D\f\x1D" +
		"\x0E\x1D\u0162\v\x1D\x03\x1E\x03\x1E\x03\x1E\x03\x1E\x05\x1E\u0168\n\x1E" +
		"\x03\x1F\x03\x1F\x03 \x03 \x03 \x07 \u016F\n \f \x0E \u0172\v \x03!\x03" +
		"!\x03!\x07!\u0177\n!\f!\x0E!\u017A\v!\x03\"\x03\"\x03\"\x03\"\x05\"\u0180" +
		"\n\"\x03#\x03#\x05#\u0184\n#\x03$\x03$\x03$\x03$\x03$\x03%\x03%\x05%\u018D" +
		"\n%\x03&\x03&\x07&\u0191\n&\f&\x0E&\u0194\v&\x03&\x03&\x06&\u0198\n&\r" +
		"&\x0E&\u0199\x05&\u019C\n&\x03\'\x03\'\x03\'\x03\'\x03\'\x03\'\x07\'\u01A4" +
		"\n\'\f\'\x0E\'\u01A7\v\'\x03\'\x03\'\x03\'\x03\'\x05\'\u01AD\n\'\x03(" +
		"\x03(\x03(\x03(\x03)\x03)\x03)\x03*\x03*\x05*\u01B8\n*\x03*\x03*\x03+" +
		"\x07+\u01BD\n+\f+\x0E+\u01C0\v+\x03,\x03,\x03,\x03,\x03,\x03,\x03,\x03" +
		",\x03,\x03,\x05,\u01CC\n,\x03-\x03-\x03-\x03-\x03-\x03-\x03-\x03-\x03" +
		"-\x03-\x03-\x03-\x03-\x03-\x03-\x03-\x03-\x03-\x03-\x03-\x03-\x03-\x03" +
		"-\x03-\x05-\u01E6\n-\x03.\x03.\x03/\x03/\x030\x030\x030\x030\x030\x03" +
		"0\x030\x030\x050\u01F4\n0\x050\u01F6\n0\x031\x031\x032\x032\x033\x033" +
		"\x033\x033\x033\x033\x034\x034\x035\x075\u0205\n5\f5\x0E5\u0208\v5\x03" +
		"5\x035\x055\u020C\n5\x035\x035\x035\x036\x036\x056\u0213\n6\x037\x077" +
		"\u0216\n7\f7\x0E7\u0219\v7\x038\x038\x038\x038\x038\x038\x039\x039\x03" +
		":\x03:\x03:\x03:\x03:\x03:\x03:\x03;\x03;\x03<\x03<\x03=\x03=\x03=\x03" +
		"=\x03=\x03=\x03=\x03=\x03>\x03>\x07>\u0238\n>\f>\x0E>\u023B\v>\x03>\x03" +
		">\x03?\x03?\x03?\x03@\x03@\x03@\x07@\u0245\n@\f@\x0E@\u0248\v@\x03A\x03" +
		"A\x03A\x05A\u024D\nA\x03B\x03B\x03C\x03C\x05C\u0253\nC\x03C\x03C\x03C" +
		"\x05C\u0258\nC\x03C\x05C\u025B\nC\x03D\x03D\x03D\x02\x02\x02E\x02\x02" +
		"\x04\x02\x06\x02\b\x02\n\x02\f\x02\x0E\x02\x10\x02\x12\x02\x14\x02\x16" +
		"\x02\x18\x02\x1A\x02\x1C\x02\x1E\x02 \x02\"\x02$\x02&\x02(\x02*\x02,\x02" +
		".\x020\x022\x024\x026\x028\x02:\x02<\x02>\x02@\x02B\x02D\x02F\x02H\x02" +
		"J\x02L\x02N\x02P\x02R\x02T\x02V\x02X\x02Z\x02\\\x02^\x02`\x02b\x02d\x02" +
		"f\x02h\x02j\x02l\x02n\x02p\x02r\x02t\x02v\x02x\x02z\x02|\x02~\x02\x80" +
		"\x02\x82\x02\x84\x02\x86\x02\x02\v\x03\x02@B\x03\x028:\x03\x02\x14\x15" +
		"\x03\x02\x16\x19\x03\x02\x1A\x1B\x03\x02\x1C\x1E\x03\x0212\x03\x02\"#" +
		"\x04\x02<=CC\x02\u026A\x02\x89\x03\x02\x02\x02\x04\x93\x03\x02\x02\x02" +
		"\x06\x97\x03\x02\x02\x02\b\x9C\x03\x02\x02\x02\n\x9F\x03\x02\x02\x02\f" +
		"\xAC\x03\x02\x02\x02\x0E\xB7\x03\x02\x02\x02\x10\xBD\x03\x02\x02\x02\x12" +
		"\xC8\x03\x02\x02\x02\x14\xCA\x03\x02\x02\x02\x16\xD6\x03\x02\x02\x02\x18" +
		"\xE5\x03\x02\x02\x02\x1A\xEA\x03\x02\x02\x02\x1C\xF8\x03\x02\x02\x02\x1E" +
		"\u0100\x03\x02\x02\x02 \u0102\x03\x02\x02\x02\"\u0104\x03\x02\x02\x02" +
		"$\u010F\x03\x02\x02\x02&\u011E\x03\x02\x02\x02(\u012A\x03\x02\x02\x02" +
		"*\u012C\x03\x02\x02\x02,\u0135\x03\x02\x02\x02.\u013D\x03\x02\x02\x02" +
		"0\u013F\x03\x02\x02\x022\u0143\x03\x02\x02\x024\u014B\x03\x02\x02\x02" +
		"6\u0153\x03\x02\x02\x028\u015B\x03\x02\x02\x02:\u0163\x03\x02\x02\x02" +
		"<\u0169\x03\x02\x02\x02>\u016B\x03\x02\x02\x02@\u0173\x03\x02\x02\x02" +
		"B\u017F\x03\x02\x02\x02D\u0183\x03\x02\x02\x02F\u0185\x03\x02\x02\x02" +
		"H\u018C\x03\x02\x02\x02J\u019B\x03\x02\x02\x02L\u01AC\x03\x02\x02\x02" +
		"N\u01AE\x03\x02\x02\x02P\u01B2\x03\x02\x02\x02R\u01B5\x03\x02\x02\x02" +
		"T\u01BE\x03\x02\x02\x02V\u01CB\x03\x02\x02\x02X\u01E5\x03\x02\x02\x02" +
		"Z\u01E7\x03\x02\x02\x02\\\u01E9\x03\x02\x02\x02^\u01EB\x03\x02\x02\x02" +
		"`\u01F7\x03\x02\x02\x02b\u01F9\x03\x02\x02\x02d\u01FB\x03\x02\x02\x02" +
		"f\u0201\x03\x02\x02\x02h\u0206\x03\x02\x02\x02j\u0212\x03\x02\x02\x02" +
		"l\u0217\x03\x02\x02\x02n\u021A\x03\x02\x02\x02p\u0220\x03\x02\x02\x02" +
		"r\u0222\x03\x02\x02\x02t\u0229\x03\x02\x02\x02v\u022B\x03\x02\x02\x02" +
		"x\u022D\x03\x02\x02\x02z\u0235\x03\x02\x02\x02|\u023E\x03\x02\x02\x02" +
		"~\u0241\x03\x02\x02\x02\x80\u024C\x03\x02\x02\x02\x82\u024E\x03\x02\x02" +
		"\x02\x84\u025A\x03\x02\x02\x02\x86\u025C\x03\x02\x02\x02\x88\x8A\x05\x04" +
		"\x03\x02\x89\x88\x03\x02\x02\x02\x89\x8A\x03\x02\x02\x02\x8A\x8E\x03\x02" +
		"\x02\x02\x8B\x8D\x05\x06\x04\x02\x8C\x8B\x03\x02\x02\x02\x8D\x90\x03\x02" +
		"\x02\x02\x8E\x8C\x03\x02\x02\x02\x8E\x8F\x03\x02\x02\x02\x8F\x91\x03\x02" +
		"\x02\x02\x90\x8E\x03\x02\x02\x02\x91\x92\x07\x02\x02\x03\x92\x03\x03\x02" +
		"\x02\x02\x93\x94\x07\x03\x02\x02\x94\x95\x07@\x02\x02\x95\x96\x07\x04" +
		"\x02\x02\x96\x05\x03\x02\x02\x02\x97\x98\x05\b\x05\x02\x98\x07\x03\x02" +
		"\x02\x02\x99\x9D\x05\n\x06\x02\x9A\x9D\x05\x16\f\x02\x9B\x9D\x05\x1A\x0E" +
		"\x02\x9C\x99\x03\x02\x02\x02\x9C\x9A\x03\x02\x02\x02\x9C\x9B\x03\x02\x02" +
		"\x02\x9D\t\x03\x02\x02\x02\x9E\xA0\x073\x02\x02\x9F\x9E\x03\x02\x02\x02" +
		"\x9F\xA0\x03\x02\x02\x02\xA0\xA2\x03\x02\x02\x02\xA1\xA3\x077\x02\x02" +
		"\xA2\xA1\x03\x02\x02\x02\xA2\xA3\x03\x02\x02\x02\xA3\xA4\x03\x02\x02\x02" +
		"\xA4\xA5\x07\x05\x02\x02\xA5\xA8\x05\x84C\x02\xA6\xA7\x074\x02\x02\xA7" +
		"\xA9\x05\x84C\x02\xA8\xA6\x03\x02\x02\x02\xA8\xA9\x03\x02\x02\x02\xA9" +
		"\xAA\x03\x02\x02\x02\xAA\xAB\x05\f\x07\x02\xAB\v\x03\x02\x02\x02\xAC\xB1" +
		"\x07\x06\x02\x02\xAD\xB0\x05\x10\t\x02\xAE\xB0\x05\x0E\b\x02\xAF\xAD\x03" +
		"\x02\x02\x02\xAF\xAE\x03\x02\x02\x02\xB0\xB3\x03\x02\x02\x02\xB1\xAF\x03" +
		"\x02\x02\x02\xB1\xB2\x03\x02\x02\x02\xB2\xB4\x03\x02\x02\x02\xB3\xB1\x03" +
		"\x02\x02\x02\xB4\xB5\x07\x07\x02\x02\xB5\r\x03\x02\x02\x02\xB6\xB8\x05" +
		" \x11\x02\xB7\xB6\x03\x02\x02\x02\xB7\xB8\x03\x02\x02\x02\xB8\xB9\x03" +
		"\x02\x02\x02\xB9\xBA\x073\x02\x02\xBA\xBB\x05\x12\n\x02\xBB\x0F\x03\x02" +
		"\x02\x02\xBC\xBE\x05 \x11\x02\xBD\xBC\x03\x02\x02\x02\xBD\xBE\x03\x02" +
		"\x02\x02\xBE\xBF\x03\x02\x02\x02\xBF\xC0\x05\x12\n\x02\xC0\x11\x03\x02" +
		"\x02\x02\xC1\xC9\x05\x14\v\x02\xC2\xC3\x05(\x15\x02\xC3\xC4\x07\x04\x02" +
		"\x02\xC4\xC9\x03\x02\x02\x02\xC5\xC9\x05\"\x12\x02\xC6\xC9\x05\x1A\x0E" +
		"\x02\xC7\xC9\x05$\x13\x02\xC8\xC1\x03\x02\x02\x02\xC8\xC2\x03\x02\x02" +
		"\x02\xC8\xC5\x03\x02\x02\x02\xC8\xC6\x03\x02\x02\x02\xC8\xC7\x03\x02\x02" +
		"\x02\xC9\x13\x03\x02\x02\x02\xCA\xCB\x07\b\x02\x02\xCB\xCC\x07@\x02\x02" +
		"\xCC\xCE\x07\t\x02\x02\xCD\xCF\x05*\x16\x02\xCE\xCD\x03\x02\x02\x02\xCE" +
		"\xCF\x03\x02\x02\x02\xCF\xD0\x03\x02\x02\x02\xD0\xD1\x07\n\x02\x02\xD1" +
		"\xD2\x07\x06\x02\x02\xD2\xD3\x05T+\x02\xD3\xD4\x07\x07\x02\x02\xD4\x15" +
		"\x03\x02\x02\x02\xD5\xD7\x05\x86D\x02\xD6\xD5\x03\x02\x02\x02\xD6\xD7" +
		"\x03\x02\x02\x02\xD7\xD8\x03\x02\x02\x02\xD8\xD9\x07\b\x02\x02\xD9\xDA" +
		"\x05\x18\r\x02\xDA\xDC\x07\t\x02\x02\xDB\xDD\x05*\x16\x02\xDC\xDB\x03" +
		"\x02\x02\x02\xDC\xDD\x03\x02\x02\x02\xDD\xDE\x03\x02\x02\x02\xDE\xDF\x07" +
		"\n\x02\x02\xDF\xE0\x07\x06\x02\x02\xE0\xE1\x05T+\x02\xE1\xE2\x07\x07\x02" +
		"\x02\xE2\x17\x03\x02\x02\x02\xE3\xE4\x07@\x02\x02\xE4\xE6\x07\v\x02\x02" +
		"\xE5\xE3\x03\x02\x02\x02\xE5\xE6\x03\x02\x02\x02\xE6\xE7\x03\x02\x02\x02" +
		"\xE7\xE8\x07@\x02\x02\xE8\x19\x03\x02\x02\x02\xE9\xEB\x05 \x11\x02\xEA" +
		"\xE9\x03\x02\x02\x02\xEA\xEB\x03\x02\x02\x02\xEB\xEC\x03\x02\x02\x02\xEC" +
		"\xED\x075\x02\x02\xED\xEE\x07\b\x02\x02\xEE\xEF\x07@\x02\x02\xEF\xF1\x07" +
		"\t\x02\x02\xF0\xF2\x05*\x16\x02\xF1\xF0\x03\x02\x02\x02\xF1\xF2\x03\x02" +
		"\x02\x02\xF2\xF3\x03\x02\x02\x02\xF3\xF4\x07\n\x02\x02\xF4\xF5\x07\f\x02" +
		"\x02\xF5\xF6\x05\x1C\x0F\x02\xF6\xF7\x07\x04\x02\x02\xF7\x1B\x03\x02\x02" +
		"\x02\xF8\xFD\x05\x1E\x10\x02\xF9\xFA\x07\r\x02\x02\xFA\xFC\x05\x1E\x10" +
		"\x02\xFB\xF9\x03\x02\x02\x02\xFC\xFF\x03\x02\x02\x02\xFD\xFB\x03\x02\x02" +
		"\x02\xFD\xFE\x03\x02\x02\x02\xFE\x1D\x03\x02\x02\x02\xFF\xFD\x03\x02\x02" +
		"\x02\u0100\u0101\t\x02\x02\x02\u0101\x1F\x03\x02\x02\x02\u0102\u0103\t" +
		"\x03\x02\x02\u0103!\x03\x02\x02\x02\u0104\u0105\x05\x84C\x02\u0105\u0107" +
		"\x07\t\x02\x02\u0106\u0108\x05*\x16\x02\u0107\u0106\x03\x02\x02\x02\u0107" +
		"\u0108\x03\x02\x02\x02\u0108\u0109\x03\x02\x02\x02\u0109\u010A\x07\n\x02" +
		"\x02\u010A\u010B\x07\x06\x02\x02\u010B\u010C\x05T+\x02\u010C\u010D\x07" +
		"\x07\x02\x02\u010D#\x03\x02\x02\x02\u010E\u0110\x05 \x11\x02\u010F\u010E" +
		"\x03\x02\x02\x02\u010F\u0110\x03\x02\x02\x02\u0110\u0112\x03\x02\x02\x02" +
		"\u0111\u0113\x075\x02\x02\u0112\u0111\x03\x02\x02\x02\u0112\u0113\x03" +
		"\x02\x02\x02\u0113\u0114\x03\x02\x02\x02\u0114\u0115\x05\x84C\x02\u0115" +
		"\u0117\x07\t\x02\x02\u0116\u0118\x05*\x16\x02\u0117\u0116\x03\x02\x02" +
		"\x02\u0117\u0118\x03\x02\x02\x02\u0118\u0119\x03\x02\x02\x02\u0119\u011A" +
		"\x07\n\x02\x02\u011A\u011B\x07\f\x02\x02\u011B\u011C\x05\x1C\x0F\x02\u011C" +
		"\u011D\x07\x04\x02\x02\u011D%\x03\x02\x02\x02\u011E\u011F\x07\x0E\x02" +
		"\x02\u011F\u0120\x05\x84C\x02\u0120\u0121\x05R*\x02\u0121\'\x03\x02\x02" +
		"\x02\u0122\u0123\x05\x80A\x02\u0123\u0124\x07@\x02\x02\u0124\u012B\x03" +
		"\x02\x02\x02\u0125\u0126\x05\x80A\x02\u0126\u0127\x07@\x02\x02\u0127\u0128" +
		"\x07\x0F\x02\x02\u0128\u0129\x05.\x18\x02\u0129\u012B\x03\x02\x02\x02" +
		"\u012A\u0122\x03\x02\x02\x02\u012A\u0125\x03\x02\x02\x02\u012B)\x03\x02" +
		"\x02\x02\u012C\u0131\x05,\x17\x02\u012D\u012E\x07\x10\x02\x02\u012E\u0130" +
		"\x05,\x17\x02\u012F\u012D\x03\x02\x02\x02\u0130\u0133\x03\x02\x02\x02" +
		"\u0131\u012F\x03\x02\x02\x02\u0131\u0132\x03\x02\x02\x02\u0132+\x03\x02" +
		"\x02\x02\u0133\u0131\x03\x02\x02\x02\u0134\u0136\x073\x02\x02\u0135\u0134" +
		"\x03\x02\x02\x02\u0135\u0136\x03\x02\x02\x02\u0136\u0138\x03\x02\x02\x02" +
		"\u0137\u0139\x076\x02\x02\u0138\u0137\x03\x02\x02\x02\u0138\u0139\x03" +
		"\x02\x02\x02\u0139\u013A\x03\x02\x02\x02\u013A\u013B\x05\x80A\x02\u013B" +
		"\u013C\x07@\x02\x02\u013C-\x03\x02\x02\x02\u013D\u013E\x054\x1B\x02\u013E" +
		"/\x03\x02\x02\x02\u013F\u0140\x05J&\x02\u0140\u0141\x07\x0F\x02\x02\u0141" +
		"\u0142\x05.\x18\x02\u01421\x03\x02\x02\x02\u0143\u0149\x054\x1B\x02\u0144" +
		"\u0145\x07\x11\x02\x02\u0145\u0146\x05.\x18\x02\u0146\u0147\x07\v\x02" +
		"\x02\u0147\u0148\x05.\x18\x02\u0148\u014A\x03\x02\x02\x02\u0149\u0144" +
		"\x03\x02\x02\x02\u0149\u014A\x03\x02\x02\x02\u014A3\x03\x02\x02\x02\u014B" +
		"\u0150\x056\x1C\x02\u014C\u014D\x07\x12\x02\x02\u014D\u014F\x056\x1C\x02" +
		"\u014E\u014C\x03\x02\x02\x02\u014F\u0152\x03\x02\x02\x02\u0150\u014E\x03" +
		"\x02\x02\x02\u0150\u0151\x03\x02\x02\x02\u01515\x03\x02\x02\x02\u0152" +
		"\u0150\x03\x02\x02\x02\u0153\u0158\x058\x1D\x02\u0154\u0155\x07\x13\x02" +
		"\x02\u0155\u0157\x058\x1D\x02\u0156\u0154\x03\x02\x02\x02\u0157\u015A" +
		"\x03\x02\x02\x02\u0158\u0156\x03\x02\x02\x02\u0158\u0159\x03\x02\x02\x02" +
		"\u01597\x03\x02\x02\x02\u015A\u0158\x03\x02\x02\x02\u015B\u0160\x05:\x1E" +
		"\x02\u015C\u015D\t\x04\x02\x02\u015D\u015F\x05:\x1E\x02\u015E\u015C\x03" +
		"\x02\x02\x02\u015F\u0162\x03\x02\x02\x02\u0160\u015E\x03\x02\x02\x02\u0160" +
		"\u0161\x03\x02\x02\x02\u01619\x03\x02\x02\x02\u0162\u0160\x03\x02\x02" +
		"\x02\u0163\u0167\x05> \x02\u0164\u0165\x05<\x1F\x02\u0165\u0166\x05> " +
		"\x02\u0166\u0168\x03\x02\x02\x02\u0167\u0164\x03\x02\x02\x02\u0167\u0168" +
		"\x03\x02\x02\x02\u0168;\x03\x02\x02\x02\u0169\u016A\t\x05\x02\x02\u016A" +
		"=\x03\x02\x02\x02\u016B\u0170\x05@!\x02\u016C\u016D\t\x06\x02\x02\u016D" +
		"\u016F\x05@!\x02\u016E\u016C\x03\x02\x02\x02\u016F\u0172\x03\x02\x02\x02" +
		"\u0170\u016E\x03\x02\x02\x02\u0170\u0171\x03\x02\x02\x02\u0171?\x03\x02" +
		"\x02\x02\u0172\u0170\x03\x02\x02\x02\u0173\u0178\x05B\"\x02\u0174\u0175" +
		"\t\x07\x02\x02\u0175\u0177\x05B\"\x02\u0176\u0174\x03\x02\x02\x02\u0177" +
		"\u017A\x03\x02\x02\x02\u0178\u0176\x03\x02\x02\x02\u0178\u0179\x03\x02" +
		"\x02\x02\u0179A\x03\x02\x02\x02\u017A\u0178\x03\x02\x02\x02\u017B\u017C" +
		"\x07\x1F\x02\x02\u017C\u0180\x05B\"\x02\u017D\u0180\x05F$\x02\u017E\u0180" +
		"\x05D#\x02\u017F\u017B\x03\x02\x02\x02\u017F\u017D\x03\x02\x02\x02\u017F" +
		"\u017E\x03\x02\x02\x02\u0180C\x03\x02\x02\x02\u0181\u0184\x05H%\x02\u0182" +
		"\u0184\x05J&\x02\u0183\u0181\x03\x02\x02\x02\u0183\u0182\x03\x02\x02\x02" +
		"\u0184E\x03\x02\x02\x02\u0185\u0186\x07\t\x02\x02\u0186\u0187\x05\x80" +
		"A\x02\u0187\u0188\x07\n\x02\x02\u0188\u0189\x05B\"\x02\u0189G\x03\x02" +
		"\x02\x02\u018A\u018D\x05L\'\x02\u018B\u018D\x05\x82B\x02\u018C\u018A\x03" +
		"\x02\x02\x02\u018C\u018B\x03\x02\x02\x02\u018DI\x03\x02\x02\x02\u018E" +
		"\u0192\x05L\'\x02\u018F\u0191\x05P)\x02\u0190\u018F\x03\x02\x02\x02\u0191" +
		"\u0194\x03\x02\x02\x02\u0192\u0190\x03\x02\x02\x02\u0192\u0193\x03\x02" +
		"\x02\x02\u0193\u019C\x03\x02\x02\x02\u0194\u0192\x03\x02\x02\x02\u0195" +
		"\u0197\x05\x84C\x02\u0196\u0198\x05P)\x02\u0197\u0196\x03\x02\x02\x02" +
		"\u0198\u0199\x03\x02\x02\x02\u0199\u0197\x03\x02\x02\x02\u0199\u019A\x03" +
		"\x02\x02\x02\u019A\u019C\x03\x02\x02\x02\u019B\u018E\x03\x02\x02\x02\u019B" +
		"\u0195\x03\x02\x02\x02\u019CK\x03\x02\x02\x02\u019D\u019E\x07\t\x02\x02" +
		"\u019E\u019F\x05.\x18\x02\u019F\u01A0\x07\n\x02\x02\u01A0\u01AD\x03\x02" +
		"\x02\x02\u01A1\u01A5\x07@\x02\x02\u01A2\u01A4\x05N(\x02\u01A3\u01A2\x03" +
		"\x02\x02\x02\u01A4\u01A7\x03\x02\x02\x02\u01A5\u01A3\x03\x02\x02\x02\u01A5" +
		"\u01A6\x03\x02\x02\x02\u01A6\u01AD\x03\x02\x02\x02\u01A7\u01A5\x03\x02" +
		"\x02\x02\u01A8\u01AD\x07(\x02\x02\u01A9\u01AD\x07)\x02\x02\u01AA\u01AD" +
		"\x05&\x14\x02\u01AB\u01AD\x07\'\x02\x02\u01AC\u019D\x03\x02\x02\x02\u01AC" +
		"\u01A1\x03\x02\x02\x02\u01AC\u01A8\x03\x02\x02\x02\u01AC\u01A9\x03\x02" +
		"\x02\x02\u01AC\u01AA\x03\x02\x02\x02\u01AC\u01AB\x03\x02\x02\x02\u01AD" +
		"M\x03\x02\x02\x02\u01AE\u01AF\x07 \x02\x02\u01AF\u01B0\x052\x1A\x02\u01B0" +
		"\u01B1\x07!\x02\x02\u01B1O\x03\x02\x02\x02\u01B2\u01B3\x07\r\x02\x02\u01B3" +
		"\u01B4\x05L\'\x02\u01B4Q\x03\x02\x02\x02\u01B5\u01B7\x07\t\x02\x02\u01B6" +
		"\u01B8\x05~@\x02\u01B7\u01B6\x03\x02\x02\x02\u01B7\u01B8\x03\x02\x02\x02" +
		"\u01B8\u01B9\x03\x02\x02\x02\u01B9\u01BA\x07\n\x02\x02\u01BAS\x03\x02" +
		"\x02\x02\u01BB\u01BD\x05X-\x02\u01BC\u01BB\x03\x02\x02\x02\u01BD\u01C0" +
		"\x03\x02\x02\x02\u01BE\u01BC\x03\x02\x02\x02\u01BE\u01BF\x03\x02\x02\x02" +
		"\u01BFU\x03\x02\x02\x02\u01C0\u01BE\x03\x02\x02\x02\u01C1\u01C2\x05\x18" +
		"\r\x02\u01C2\u01C3\x05R*\x02\u01C3\u01CC\x03\x02\x02\x02\u01C4\u01C5\x07" +
		"(\x02\x02\u01C5\u01CC\x05R*\x02\u01C6\u01C7\x07)\x02\x02\u01C7\u01CC\x05" +
		"R*\x02\u01C8\u01C9\x05D#\x02\u01C9\u01CA\x05R*\x02\u01CA\u01CC\x03\x02" +
		"\x02\x02\u01CB\u01C1\x03\x02\x02\x02\u01CB\u01C4\x03\x02\x02\x02\u01CB" +
		"\u01C6\x03\x02\x02\x02\u01CB\u01C8\x03\x02\x02\x02\u01CCW\x03\x02\x02" +
		"\x02\u01CD\u01CE\x05(\x15\x02\u01CE\u01CF\x07\x04\x02\x02\u01CF\u01E6" +
		"\x03\x02\x02\x02\u01D0\u01D1\x050\x19\x02\u01D1\u01D2\x07\x04\x02\x02" +
		"\u01D2\u01E6\x03\x02\x02\x02\u01D3\u01D4\x05V,\x02\u01D4\u01D5\x07\x04" +
		"\x02\x02\u01D5\u01E6\x03\x02\x02\x02\u01D6\u01E6\x05^0\x02\u01D7\u01E6" +
		"\x05d3\x02\u01D8\u01E6\x05n8\x02\u01D9\u01DA\x05r:\x02\u01DA\u01DB\x07" +
		"\x04\x02\x02\u01DB\u01E6\x03\x02\x02\x02\u01DC\u01E6\x07\x04\x02\x02\u01DD" +
		"\u01DE\x05v<\x02\u01DE\u01DF\x07\x04\x02\x02\u01DF\u01E6\x03\x02\x02\x02" +
		"\u01E0\u01E6\x05x=\x02\u01E1\u01E2\x05\\/\x02\u01E2\u01E3\x07\x04\x02" +
		"\x02\u01E3\u01E6\x03\x02\x02\x02\u01E4\u01E6\x05Z.\x02\u01E5\u01CD\x03" +
		"\x02\x02\x02\u01E5\u01D0\x03\x02\x02\x02\u01E5\u01D3\x03\x02\x02\x02\u01E5" +
		"\u01D6\x03\x02\x02\x02\u01E5\u01D7\x03\x02\x02\x02\u01E5\u01D8\x03\x02" +
		"\x02\x02\u01E5\u01D9\x03\x02\x02\x02\u01E5\u01DC\x03\x02\x02\x02\u01E5" +
		"\u01DD\x03\x02\x02\x02\u01E5\u01E0\x03\x02\x02\x02\u01E5\u01E1\x03\x02" +
		"\x02\x02\u01E5\u01E4\x03\x02\x02\x02\u01E6Y\x03\x02\x02\x02\u01E7\u01E8" +
		"\x07&\x02\x02\u01E8[\x03\x02\x02\x02\u01E9\u01EA\t\b\x02\x02\u01EA]\x03" +
		"\x02\x02\x02\u01EB\u01EC\x07*\x02\x02\u01EC\u01ED\x07\t\x02\x02\u01ED" +
		"\u01EE\x05.\x18\x02\u01EE\u01EF\x07\n\x02\x02\u01EF\u01F5\x05b2\x02\u01F0" +
		"\u01F3\x07+\x02\x02\u01F1\u01F4\x05`1\x02\u01F2\u01F4\x05b2\x02\u01F3" +
		"\u01F1\x03\x02\x02\x02\u01F3\u01F2\x03\x02\x02\x02\u01F4\u01F6\x03\x02" +
		"\x02\x02\u01F5\u01F0\x03\x02\x02\x02\u01F5\u01F6\x03\x02\x02\x02\u01F6" +
		"_\x03\x02\x02\x02\u01F7\u01F8\x05^0\x02\u01F8a\x03\x02\x02\x02\u01F9\u01FA" +
		"\x05z>\x02\u01FAc\x03\x02\x02\x02\u01FB\u01FC\x07-\x02\x02\u01FC\u01FD" +
		"\x07\t\x02\x02\u01FD\u01FE\x05h5\x02\u01FE\u01FF\x07\n\x02\x02\u01FF\u0200" +
		"\x05f4\x02\u0200e\x03\x02\x02\x02\u0201\u0202\x05z>\x02\u0202g\x03\x02" +
		"\x02\x02\u0203\u0205\x05j6\x02\u0204\u0203\x03\x02\x02\x02\u0205\u0208" +
		"\x03\x02\x02\x02\u0206\u0204\x03\x02\x02\x02\u0206\u0207\x03\x02\x02\x02" +
		"\u0207\u0209\x03\x02\x02\x02\u0208\u0206\x03\x02\x02\x02\u0209\u020B\x07" +
		"\x04\x02\x02\u020A\u020C\x05.\x18\x02\u020B\u020A\x03\x02\x02\x02\u020B" +
		"\u020C\x03\x02\x02\x02\u020C\u020D\x03\x02\x02\x02\u020D\u020E\x07\x04" +
		"\x02\x02\u020E\u020F\x05l7\x02\u020Fi\x03\x02\x02\x02\u0210\u0213\x05" +
		"(\x15\x02\u0211\u0213\x050\x19\x02\u0212\u0210\x03\x02\x02\x02\u0212\u0211" +
		"\x03\x02\x02\x02\u0213k\x03\x02\x02\x02\u0214\u0216\x050\x19\x02\u0215" +
		"\u0214\x03\x02\x02\x02\u0216\u0219\x03\x02\x02\x02\u0217\u0215\x03\x02" +
		"\x02\x02\u0217\u0218\x03\x02\x02\x02\u0218m\x03\x02\x02\x02\u0219\u0217" +
		"\x03\x02\x02\x02\u021A\u021B\x07,\x02\x02\u021B\u021C\x07\t\x02\x02\u021C" +
		"\u021D\x05.\x18\x02\u021D\u021E\x07\n\x02\x02\u021E\u021F\x05p9\x02\u021F" +
		"o\x03\x02\x02\x02\u0220\u0221\x05z>\x02\u0221q\x03\x02\x02\x02\u0222\u0223" +
		"\x07.\x02\x02\u0223\u0224\x05t;\x02\u0224\u0225\x07,\x02\x02\u0225\u0226" +
		"\x07\t\x02\x02\u0226\u0227\x05.\x18\x02\u0227\u0228\x07\n\x02\x02\u0228" +
		"s\x03\x02\x02\x02\u0229\u022A\x05z>\x02\u022Au\x03\x02\x02\x02\u022B\u022C" +
		"\x05|?\x02\u022Cw\x03\x02\x02\x02\u022D\u022E\x07/\x02\x02\u022E\u022F" +
		"\x05z>\x02\u022F\u0230\x070\x02\x02\u0230\u0231\x07\t\x02\x02\u0231\u0232" +
		"\x07@\x02\x02\u0232\u0233\x07\n\x02\x02\u0233\u0234\x07\x04\x02\x02\u0234" +
		"y\x03\x02\x02\x02\u0235\u0239\x07\x06\x02\x02\u0236\u0238\x05X-\x02\u0237" +
		"\u0236\x03\x02\x02\x02\u0238\u023B\x03\x02\x02\x02\u0239\u0237\x03\x02" +
		"\x02\x02\u0239\u023A\x03\x02\x02\x02\u023A\u023C\x03\x02\x02\x02\u023B" +
		"\u0239\x03\x02\x02\x02\u023C\u023D\x07\x07\x02\x02\u023D{\x03\x02\x02" +
		"\x02\u023E\u023F\x07@\x02\x02\u023F\u0240\t\t\x02\x02\u0240}\x03\x02\x02" +
		"\x02\u0241\u0246\x05.\x18\x02\u0242\u0243\x07\x10\x02\x02\u0243\u0245" +
		"\x05.\x18\x02\u0244\u0242\x03\x02\x02\x02\u0245\u0248\x03\x02\x02\x02" +
		"\u0246\u0244\x03\x02\x02\x02\u0246\u0247\x03";
	private static readonly _serializedATNSegment1: string =
		"\x02\x02\x02\u0247\x7F\x03\x02\x02\x02\u0248\u0246\x03\x02\x02\x02\u0249" +
		"\u024D\x07$\x02\x02\u024A\u024D\x07%\x02\x02\u024B\u024D\x05\x84C\x02" +
		"\u024C\u0249\x03\x02\x02\x02\u024C\u024A\x03\x02\x02\x02\u024C\u024B\x03" +
		"\x02\x02\x02\u024D\x81\x03\x02\x02\x02\u024E\u024F\t\n\x02\x02\u024F\x83" +
		"\x03\x02\x02\x02\u0250\u0251\x07@\x02\x02\u0251\u0253\x07\v\x02\x02\u0252" +
		"\u0250\x03\x02\x02\x02\u0252\u0253\x03\x02\x02\x02\u0253\u0254\x03\x02" +
		"\x02\x02\u0254\u025B\x07A\x02\x02\u0255\u0256\x07@\x02\x02\u0256\u0258" +
		"\x07\v\x02\x02\u0257\u0255\x03\x02\x02\x02\u0257\u0258\x03\x02\x02\x02" +
		"\u0258\u0259\x03\x02\x02\x02\u0259\u025B\x07;\x02\x02\u025A\u0252\x03" +
		"\x02\x02\x02\u025A\u0257\x03\x02\x02\x02\u025B\x85\x03\x02\x02\x02\u025C" +
		"\u025D\x05\x18\r\x02\u025D\x87\x03\x02\x02\x02;\x89\x8E\x9C\x9F\xA2\xA8" +
		"\xAF\xB1\xB7\xBD\xC8\xCE\xD6\xDC\xE5\xEA\xF1\xFD\u0107\u010F\u0112\u0117" +
		"\u012A\u0131\u0135\u0138\u0149\u0150\u0158\u0160\u0167\u0170\u0178\u017F" +
		"\u0183\u018C\u0192\u0199\u019B\u01A5\u01AC\u01B7\u01BE\u01CB\u01E5\u01F3" +
		"\u01F5\u0206\u020B\u0212\u0217\u0239\u0246\u024C\u0252\u0257\u025A";
	public static readonly _serializedATN: string = Utils.join(
		[
			MCFPPParser._serializedATNSegment0,
			MCFPPParser._serializedATNSegment1,
		],
		"",
	);
	public static __ATN: ATN;
	public static get _ATN(): ATN {
		if (!MCFPPParser.__ATN) {
			MCFPPParser.__ATN = new ATNDeserializer().deserialize(Utils.toCharArray(MCFPPParser._serializedATN));
		}

		return MCFPPParser.__ATN;
	}

}

export class CompilationUnitContext extends ParserRuleContext {
	public EOF(): TerminalNode { return this.getToken(MCFPPParser.EOF, 0); }
	public namespaceDeclaration(): NamespaceDeclarationContext | undefined {
		return this.tryGetRuleContext(0, NamespaceDeclarationContext);
	}
	public typeDeclaration(): TypeDeclarationContext[];
	public typeDeclaration(i: number): TypeDeclarationContext;
	public typeDeclaration(i?: number): TypeDeclarationContext | TypeDeclarationContext[] {
		if (i === undefined) {
			return this.getRuleContexts(TypeDeclarationContext);
		} else {
			return this.getRuleContext(i, TypeDeclarationContext);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_compilationUnit; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterCompilationUnit) {
			listener.enterCompilationUnit(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitCompilationUnit) {
			listener.exitCompilationUnit(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitCompilationUnit) {
			return visitor.visitCompilationUnit(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class NamespaceDeclarationContext extends ParserRuleContext {
	public Identifier(): TerminalNode { return this.getToken(MCFPPParser.Identifier, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_namespaceDeclaration; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterNamespaceDeclaration) {
			listener.enterNamespaceDeclaration(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitNamespaceDeclaration) {
			listener.exitNamespaceDeclaration(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitNamespaceDeclaration) {
			return visitor.visitNamespaceDeclaration(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class TypeDeclarationContext extends ParserRuleContext {
	public classOrFunctionDeclaration(): ClassOrFunctionDeclarationContext {
		return this.getRuleContext(0, ClassOrFunctionDeclarationContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_typeDeclaration; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterTypeDeclaration) {
			listener.enterTypeDeclaration(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitTypeDeclaration) {
			listener.exitTypeDeclaration(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitTypeDeclaration) {
			return visitor.visitTypeDeclaration(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ClassOrFunctionDeclarationContext extends ParserRuleContext {
	public classDeclaration(): ClassDeclarationContext | undefined {
		return this.tryGetRuleContext(0, ClassDeclarationContext);
	}
	public functionDeclaration(): FunctionDeclarationContext | undefined {
		return this.tryGetRuleContext(0, FunctionDeclarationContext);
	}
	public nativeDeclaration(): NativeDeclarationContext | undefined {
		return this.tryGetRuleContext(0, NativeDeclarationContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_classOrFunctionDeclaration; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterClassOrFunctionDeclaration) {
			listener.enterClassOrFunctionDeclaration(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitClassOrFunctionDeclaration) {
			listener.exitClassOrFunctionDeclaration(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitClassOrFunctionDeclaration) {
			return visitor.visitClassOrFunctionDeclaration(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ClassDeclarationContext extends ParserRuleContext {
	public className(): ClassNameContext[];
	public className(i: number): ClassNameContext;
	public className(i?: number): ClassNameContext | ClassNameContext[] {
		if (i === undefined) {
			return this.getRuleContexts(ClassNameContext);
		} else {
			return this.getRuleContext(i, ClassNameContext);
		}
	}
	public classBody(): ClassBodyContext {
		return this.getRuleContext(0, ClassBodyContext);
	}
	public STATIC(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.STATIC, 0); }
	public FINAL(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.FINAL, 0); }
	public EXTENDS(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.EXTENDS, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_classDeclaration; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterClassDeclaration) {
			listener.enterClassDeclaration(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitClassDeclaration) {
			listener.exitClassDeclaration(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitClassDeclaration) {
			return visitor.visitClassDeclaration(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ClassBodyContext extends ParserRuleContext {
	public classMemberDeclaration(): ClassMemberDeclarationContext[];
	public classMemberDeclaration(i: number): ClassMemberDeclarationContext;
	public classMemberDeclaration(i?: number): ClassMemberDeclarationContext | ClassMemberDeclarationContext[] {
		if (i === undefined) {
			return this.getRuleContexts(ClassMemberDeclarationContext);
		} else {
			return this.getRuleContext(i, ClassMemberDeclarationContext);
		}
	}
	public staticClassMemberDeclaration(): StaticClassMemberDeclarationContext[];
	public staticClassMemberDeclaration(i: number): StaticClassMemberDeclarationContext;
	public staticClassMemberDeclaration(i?: number): StaticClassMemberDeclarationContext | StaticClassMemberDeclarationContext[] {
		if (i === undefined) {
			return this.getRuleContexts(StaticClassMemberDeclarationContext);
		} else {
			return this.getRuleContext(i, StaticClassMemberDeclarationContext);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_classBody; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterClassBody) {
			listener.enterClassBody(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitClassBody) {
			listener.exitClassBody(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitClassBody) {
			return visitor.visitClassBody(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class StaticClassMemberDeclarationContext extends ParserRuleContext {
	public STATIC(): TerminalNode { return this.getToken(MCFPPParser.STATIC, 0); }
	public classMember(): ClassMemberContext {
		return this.getRuleContext(0, ClassMemberContext);
	}
	public accessModifier(): AccessModifierContext | undefined {
		return this.tryGetRuleContext(0, AccessModifierContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_staticClassMemberDeclaration; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterStaticClassMemberDeclaration) {
			listener.enterStaticClassMemberDeclaration(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitStaticClassMemberDeclaration) {
			listener.exitStaticClassMemberDeclaration(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitStaticClassMemberDeclaration) {
			return visitor.visitStaticClassMemberDeclaration(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ClassMemberDeclarationContext extends ParserRuleContext {
	public classMember(): ClassMemberContext {
		return this.getRuleContext(0, ClassMemberContext);
	}
	public accessModifier(): AccessModifierContext | undefined {
		return this.tryGetRuleContext(0, AccessModifierContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_classMemberDeclaration; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterClassMemberDeclaration) {
			listener.enterClassMemberDeclaration(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitClassMemberDeclaration) {
			listener.exitClassMemberDeclaration(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitClassMemberDeclaration) {
			return visitor.visitClassMemberDeclaration(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ClassMemberContext extends ParserRuleContext {
	public classFunctionDeclaration(): ClassFunctionDeclarationContext | undefined {
		return this.tryGetRuleContext(0, ClassFunctionDeclarationContext);
	}
	public fieldDeclaration(): FieldDeclarationContext | undefined {
		return this.tryGetRuleContext(0, FieldDeclarationContext);
	}
	public constructorDeclaration(): ConstructorDeclarationContext | undefined {
		return this.tryGetRuleContext(0, ConstructorDeclarationContext);
	}
	public nativeDeclaration(): NativeDeclarationContext | undefined {
		return this.tryGetRuleContext(0, NativeDeclarationContext);
	}
	public nativeConstructorDeclaration(): NativeConstructorDeclarationContext | undefined {
		return this.tryGetRuleContext(0, NativeConstructorDeclarationContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_classMember; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterClassMember) {
			listener.enterClassMember(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitClassMember) {
			listener.exitClassMember(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitClassMember) {
			return visitor.visitClassMember(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ClassFunctionDeclarationContext extends ParserRuleContext {
	public Identifier(): TerminalNode { return this.getToken(MCFPPParser.Identifier, 0); }
	public functionBody(): FunctionBodyContext {
		return this.getRuleContext(0, FunctionBodyContext);
	}
	public parameterList(): ParameterListContext | undefined {
		return this.tryGetRuleContext(0, ParameterListContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_classFunctionDeclaration; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterClassFunctionDeclaration) {
			listener.enterClassFunctionDeclaration(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitClassFunctionDeclaration) {
			listener.exitClassFunctionDeclaration(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitClassFunctionDeclaration) {
			return visitor.visitClassFunctionDeclaration(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class FunctionDeclarationContext extends ParserRuleContext {
	public namespaceID(): NamespaceIDContext {
		return this.getRuleContext(0, NamespaceIDContext);
	}
	public functionBody(): FunctionBodyContext {
		return this.getRuleContext(0, FunctionBodyContext);
	}
	public functionTag(): FunctionTagContext | undefined {
		return this.tryGetRuleContext(0, FunctionTagContext);
	}
	public parameterList(): ParameterListContext | undefined {
		return this.tryGetRuleContext(0, ParameterListContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_functionDeclaration; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterFunctionDeclaration) {
			listener.enterFunctionDeclaration(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitFunctionDeclaration) {
			listener.exitFunctionDeclaration(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitFunctionDeclaration) {
			return visitor.visitFunctionDeclaration(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class NamespaceIDContext extends ParserRuleContext {
	public Identifier(): TerminalNode[];
	public Identifier(i: number): TerminalNode;
	public Identifier(i?: number): TerminalNode | TerminalNode[] {
		if (i === undefined) {
			return this.getTokens(MCFPPParser.Identifier);
		} else {
			return this.getToken(MCFPPParser.Identifier, i);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_namespaceID; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterNamespaceID) {
			listener.enterNamespaceID(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitNamespaceID) {
			listener.exitNamespaceID(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitNamespaceID) {
			return visitor.visitNamespaceID(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class NativeDeclarationContext extends ParserRuleContext {
	public NATIVE(): TerminalNode { return this.getToken(MCFPPParser.NATIVE, 0); }
	public Identifier(): TerminalNode { return this.getToken(MCFPPParser.Identifier, 0); }
	public javaRefer(): JavaReferContext {
		return this.getRuleContext(0, JavaReferContext);
	}
	public accessModifier(): AccessModifierContext | undefined {
		return this.tryGetRuleContext(0, AccessModifierContext);
	}
	public parameterList(): ParameterListContext | undefined {
		return this.tryGetRuleContext(0, ParameterListContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_nativeDeclaration; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterNativeDeclaration) {
			listener.enterNativeDeclaration(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitNativeDeclaration) {
			listener.exitNativeDeclaration(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitNativeDeclaration) {
			return visitor.visitNativeDeclaration(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class JavaReferContext extends ParserRuleContext {
	public stringName(): StringNameContext[];
	public stringName(i: number): StringNameContext;
	public stringName(i?: number): StringNameContext | StringNameContext[] {
		if (i === undefined) {
			return this.getRuleContexts(StringNameContext);
		} else {
			return this.getRuleContext(i, StringNameContext);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_javaRefer; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterJavaRefer) {
			listener.enterJavaRefer(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitJavaRefer) {
			listener.exitJavaRefer(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitJavaRefer) {
			return visitor.visitJavaRefer(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class StringNameContext extends ParserRuleContext {
	public Identifier(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.Identifier, 0); }
	public ClassIdentifier(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.ClassIdentifier, 0); }
	public NORMALSTRING(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.NORMALSTRING, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_stringName; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterStringName) {
			listener.enterStringName(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitStringName) {
			listener.exitStringName(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitStringName) {
			return visitor.visitStringName(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class AccessModifierContext extends ParserRuleContext {
	public PRIVATE(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.PRIVATE, 0); }
	public PROTECTED(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.PROTECTED, 0); }
	public PUBLIC(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.PUBLIC, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_accessModifier; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterAccessModifier) {
			listener.enterAccessModifier(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitAccessModifier) {
			listener.exitAccessModifier(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitAccessModifier) {
			return visitor.visitAccessModifier(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ConstructorDeclarationContext extends ParserRuleContext {
	public className(): ClassNameContext {
		return this.getRuleContext(0, ClassNameContext);
	}
	public functionBody(): FunctionBodyContext {
		return this.getRuleContext(0, FunctionBodyContext);
	}
	public parameterList(): ParameterListContext | undefined {
		return this.tryGetRuleContext(0, ParameterListContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_constructorDeclaration; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterConstructorDeclaration) {
			listener.enterConstructorDeclaration(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitConstructorDeclaration) {
			listener.exitConstructorDeclaration(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitConstructorDeclaration) {
			return visitor.visitConstructorDeclaration(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class NativeConstructorDeclarationContext extends ParserRuleContext {
	public className(): ClassNameContext {
		return this.getRuleContext(0, ClassNameContext);
	}
	public javaRefer(): JavaReferContext {
		return this.getRuleContext(0, JavaReferContext);
	}
	public accessModifier(): AccessModifierContext | undefined {
		return this.tryGetRuleContext(0, AccessModifierContext);
	}
	public NATIVE(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.NATIVE, 0); }
	public parameterList(): ParameterListContext | undefined {
		return this.tryGetRuleContext(0, ParameterListContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_nativeConstructorDeclaration; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterNativeConstructorDeclaration) {
			listener.enterNativeConstructorDeclaration(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitNativeConstructorDeclaration) {
			listener.exitNativeConstructorDeclaration(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitNativeConstructorDeclaration) {
			return visitor.visitNativeConstructorDeclaration(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ConstructorCallContext extends ParserRuleContext {
	public className(): ClassNameContext {
		return this.getRuleContext(0, ClassNameContext);
	}
	public arguments(): ArgumentsContext {
		return this.getRuleContext(0, ArgumentsContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_constructorCall; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterConstructorCall) {
			listener.enterConstructorCall(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitConstructorCall) {
			listener.exitConstructorCall(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitConstructorCall) {
			return visitor.visitConstructorCall(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class FieldDeclarationContext extends ParserRuleContext {
	public type(): TypeContext {
		return this.getRuleContext(0, TypeContext);
	}
	public Identifier(): TerminalNode { return this.getToken(MCFPPParser.Identifier, 0); }
	public expression(): ExpressionContext | undefined {
		return this.tryGetRuleContext(0, ExpressionContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_fieldDeclaration; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterFieldDeclaration) {
			listener.enterFieldDeclaration(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitFieldDeclaration) {
			listener.exitFieldDeclaration(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitFieldDeclaration) {
			return visitor.visitFieldDeclaration(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ParameterListContext extends ParserRuleContext {
	public parameter(): ParameterContext[];
	public parameter(i: number): ParameterContext;
	public parameter(i?: number): ParameterContext | ParameterContext[] {
		if (i === undefined) {
			return this.getRuleContexts(ParameterContext);
		} else {
			return this.getRuleContext(i, ParameterContext);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_parameterList; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterParameterList) {
			listener.enterParameterList(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitParameterList) {
			listener.exitParameterList(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitParameterList) {
			return visitor.visitParameterList(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ParameterContext extends ParserRuleContext {
	public type(): TypeContext {
		return this.getRuleContext(0, TypeContext);
	}
	public Identifier(): TerminalNode { return this.getToken(MCFPPParser.Identifier, 0); }
	public STATIC(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.STATIC, 0); }
	public CONCRETE(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.CONCRETE, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_parameter; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterParameter) {
			listener.enterParameter(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitParameter) {
			listener.exitParameter(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitParameter) {
			return visitor.visitParameter(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ExpressionContext extends ParserRuleContext {
	public conditionalOrExpression(): ConditionalOrExpressionContext {
		return this.getRuleContext(0, ConditionalOrExpressionContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_expression; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterExpression) {
			listener.enterExpression(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitExpression) {
			listener.exitExpression(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitExpression) {
			return visitor.visitExpression(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class StatementExpressionContext extends ParserRuleContext {
	public varWithSelector(): VarWithSelectorContext {
		return this.getRuleContext(0, VarWithSelectorContext);
	}
	public expression(): ExpressionContext {
		return this.getRuleContext(0, ExpressionContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_statementExpression; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterStatementExpression) {
			listener.enterStatementExpression(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitStatementExpression) {
			listener.exitStatementExpression(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitStatementExpression) {
			return visitor.visitStatementExpression(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ConditionalExpressionContext extends ParserRuleContext {
	public conditionalOrExpression(): ConditionalOrExpressionContext {
		return this.getRuleContext(0, ConditionalOrExpressionContext);
	}
	public expression(): ExpressionContext[];
	public expression(i: number): ExpressionContext;
	public expression(i?: number): ExpressionContext | ExpressionContext[] {
		if (i === undefined) {
			return this.getRuleContexts(ExpressionContext);
		} else {
			return this.getRuleContext(i, ExpressionContext);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_conditionalExpression; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterConditionalExpression) {
			listener.enterConditionalExpression(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitConditionalExpression) {
			listener.exitConditionalExpression(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitConditionalExpression) {
			return visitor.visitConditionalExpression(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ConditionalOrExpressionContext extends ParserRuleContext {
	public conditionalAndExpression(): ConditionalAndExpressionContext[];
	public conditionalAndExpression(i: number): ConditionalAndExpressionContext;
	public conditionalAndExpression(i?: number): ConditionalAndExpressionContext | ConditionalAndExpressionContext[] {
		if (i === undefined) {
			return this.getRuleContexts(ConditionalAndExpressionContext);
		} else {
			return this.getRuleContext(i, ConditionalAndExpressionContext);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_conditionalOrExpression; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterConditionalOrExpression) {
			listener.enterConditionalOrExpression(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitConditionalOrExpression) {
			listener.exitConditionalOrExpression(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitConditionalOrExpression) {
			return visitor.visitConditionalOrExpression(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ConditionalAndExpressionContext extends ParserRuleContext {
	public equalityExpression(): EqualityExpressionContext[];
	public equalityExpression(i: number): EqualityExpressionContext;
	public equalityExpression(i?: number): EqualityExpressionContext | EqualityExpressionContext[] {
		if (i === undefined) {
			return this.getRuleContexts(EqualityExpressionContext);
		} else {
			return this.getRuleContext(i, EqualityExpressionContext);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_conditionalAndExpression; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterConditionalAndExpression) {
			listener.enterConditionalAndExpression(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitConditionalAndExpression) {
			listener.exitConditionalAndExpression(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitConditionalAndExpression) {
			return visitor.visitConditionalAndExpression(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class EqualityExpressionContext extends ParserRuleContext {
	public _op!: Token;
	public relationalExpression(): RelationalExpressionContext[];
	public relationalExpression(i: number): RelationalExpressionContext;
	public relationalExpression(i?: number): RelationalExpressionContext | RelationalExpressionContext[] {
		if (i === undefined) {
			return this.getRuleContexts(RelationalExpressionContext);
		} else {
			return this.getRuleContext(i, RelationalExpressionContext);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_equalityExpression; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterEqualityExpression) {
			listener.enterEqualityExpression(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitEqualityExpression) {
			listener.exitEqualityExpression(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitEqualityExpression) {
			return visitor.visitEqualityExpression(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class RelationalExpressionContext extends ParserRuleContext {
	public additiveExpression(): AdditiveExpressionContext[];
	public additiveExpression(i: number): AdditiveExpressionContext;
	public additiveExpression(i?: number): AdditiveExpressionContext | AdditiveExpressionContext[] {
		if (i === undefined) {
			return this.getRuleContexts(AdditiveExpressionContext);
		} else {
			return this.getRuleContext(i, AdditiveExpressionContext);
		}
	}
	public relationalOp(): RelationalOpContext | undefined {
		return this.tryGetRuleContext(0, RelationalOpContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_relationalExpression; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterRelationalExpression) {
			listener.enterRelationalExpression(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitRelationalExpression) {
			listener.exitRelationalExpression(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitRelationalExpression) {
			return visitor.visitRelationalExpression(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class RelationalOpContext extends ParserRuleContext {
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_relationalOp; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterRelationalOp) {
			listener.enterRelationalOp(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitRelationalOp) {
			listener.exitRelationalOp(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitRelationalOp) {
			return visitor.visitRelationalOp(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class AdditiveExpressionContext extends ParserRuleContext {
	public _op!: Token;
	public multiplicativeExpression(): MultiplicativeExpressionContext[];
	public multiplicativeExpression(i: number): MultiplicativeExpressionContext;
	public multiplicativeExpression(i?: number): MultiplicativeExpressionContext | MultiplicativeExpressionContext[] {
		if (i === undefined) {
			return this.getRuleContexts(MultiplicativeExpressionContext);
		} else {
			return this.getRuleContext(i, MultiplicativeExpressionContext);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_additiveExpression; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterAdditiveExpression) {
			listener.enterAdditiveExpression(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitAdditiveExpression) {
			listener.exitAdditiveExpression(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitAdditiveExpression) {
			return visitor.visitAdditiveExpression(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class MultiplicativeExpressionContext extends ParserRuleContext {
	public _op!: Token;
	public unaryExpression(): UnaryExpressionContext[];
	public unaryExpression(i: number): UnaryExpressionContext;
	public unaryExpression(i?: number): UnaryExpressionContext | UnaryExpressionContext[] {
		if (i === undefined) {
			return this.getRuleContexts(UnaryExpressionContext);
		} else {
			return this.getRuleContext(i, UnaryExpressionContext);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_multiplicativeExpression; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterMultiplicativeExpression) {
			listener.enterMultiplicativeExpression(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitMultiplicativeExpression) {
			listener.exitMultiplicativeExpression(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitMultiplicativeExpression) {
			return visitor.visitMultiplicativeExpression(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class UnaryExpressionContext extends ParserRuleContext {
	public unaryExpression(): UnaryExpressionContext | undefined {
		return this.tryGetRuleContext(0, UnaryExpressionContext);
	}
	public castExpression(): CastExpressionContext | undefined {
		return this.tryGetRuleContext(0, CastExpressionContext);
	}
	public basicExpression(): BasicExpressionContext | undefined {
		return this.tryGetRuleContext(0, BasicExpressionContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_unaryExpression; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterUnaryExpression) {
			listener.enterUnaryExpression(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitUnaryExpression) {
			listener.exitUnaryExpression(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitUnaryExpression) {
			return visitor.visitUnaryExpression(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class BasicExpressionContext extends ParserRuleContext {
	public primary(): PrimaryContext | undefined {
		return this.tryGetRuleContext(0, PrimaryContext);
	}
	public varWithSelector(): VarWithSelectorContext | undefined {
		return this.tryGetRuleContext(0, VarWithSelectorContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_basicExpression; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterBasicExpression) {
			listener.enterBasicExpression(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitBasicExpression) {
			listener.exitBasicExpression(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitBasicExpression) {
			return visitor.visitBasicExpression(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class CastExpressionContext extends ParserRuleContext {
	public type(): TypeContext {
		return this.getRuleContext(0, TypeContext);
	}
	public unaryExpression(): UnaryExpressionContext {
		return this.getRuleContext(0, UnaryExpressionContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_castExpression; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterCastExpression) {
			listener.enterCastExpression(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitCastExpression) {
			listener.exitCastExpression(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitCastExpression) {
			return visitor.visitCastExpression(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class PrimaryContext extends ParserRuleContext {
	public var(): VarContext | undefined {
		return this.tryGetRuleContext(0, VarContext);
	}
	public value(): ValueContext | undefined {
		return this.tryGetRuleContext(0, ValueContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_primary; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterPrimary) {
			listener.enterPrimary(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitPrimary) {
			listener.exitPrimary(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitPrimary) {
			return visitor.visitPrimary(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class VarWithSelectorContext extends ParserRuleContext {
	public var(): VarContext | undefined {
		return this.tryGetRuleContext(0, VarContext);
	}
	public selector(): SelectorContext[];
	public selector(i: number): SelectorContext;
	public selector(i?: number): SelectorContext | SelectorContext[] {
		if (i === undefined) {
			return this.getRuleContexts(SelectorContext);
		} else {
			return this.getRuleContext(i, SelectorContext);
		}
	}
	public className(): ClassNameContext | undefined {
		return this.tryGetRuleContext(0, ClassNameContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_varWithSelector; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterVarWithSelector) {
			listener.enterVarWithSelector(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitVarWithSelector) {
			listener.exitVarWithSelector(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitVarWithSelector) {
			return visitor.visitVarWithSelector(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class VarContext extends ParserRuleContext {
	public expression(): ExpressionContext | undefined {
		return this.tryGetRuleContext(0, ExpressionContext);
	}
	public Identifier(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.Identifier, 0); }
	public identifierSuffix(): IdentifierSuffixContext[];
	public identifierSuffix(i: number): IdentifierSuffixContext;
	public identifierSuffix(i?: number): IdentifierSuffixContext | IdentifierSuffixContext[] {
		if (i === undefined) {
			return this.getRuleContexts(IdentifierSuffixContext);
		} else {
			return this.getRuleContext(i, IdentifierSuffixContext);
		}
	}
	public THIS(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.THIS, 0); }
	public SUPER(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.SUPER, 0); }
	public constructorCall(): ConstructorCallContext | undefined {
		return this.tryGetRuleContext(0, ConstructorCallContext);
	}
	public TargetSelector(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.TargetSelector, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_var; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterVar) {
			listener.enterVar(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitVar) {
			listener.exitVar(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitVar) {
			return visitor.visitVar(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class IdentifierSuffixContext extends ParserRuleContext {
	public conditionalExpression(): ConditionalExpressionContext {
		return this.getRuleContext(0, ConditionalExpressionContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_identifierSuffix; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterIdentifierSuffix) {
			listener.enterIdentifierSuffix(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitIdentifierSuffix) {
			listener.exitIdentifierSuffix(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitIdentifierSuffix) {
			return visitor.visitIdentifierSuffix(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class SelectorContext extends ParserRuleContext {
	public var(): VarContext {
		return this.getRuleContext(0, VarContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_selector; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterSelector) {
			listener.enterSelector(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitSelector) {
			listener.exitSelector(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitSelector) {
			return visitor.visitSelector(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ArgumentsContext extends ParserRuleContext {
	public expressionList(): ExpressionListContext | undefined {
		return this.tryGetRuleContext(0, ExpressionListContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_arguments; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterArguments) {
			listener.enterArguments(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitArguments) {
			listener.exitArguments(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitArguments) {
			return visitor.visitArguments(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class FunctionBodyContext extends ParserRuleContext {
	public statement(): StatementContext[];
	public statement(i: number): StatementContext;
	public statement(i?: number): StatementContext | StatementContext[] {
		if (i === undefined) {
			return this.getRuleContexts(StatementContext);
		} else {
			return this.getRuleContext(i, StatementContext);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_functionBody; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterFunctionBody) {
			listener.enterFunctionBody(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitFunctionBody) {
			listener.exitFunctionBody(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitFunctionBody) {
			return visitor.visitFunctionBody(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class FunctionCallContext extends ParserRuleContext {
	public namespaceID(): NamespaceIDContext | undefined {
		return this.tryGetRuleContext(0, NamespaceIDContext);
	}
	public arguments(): ArgumentsContext {
		return this.getRuleContext(0, ArgumentsContext);
	}
	public THIS(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.THIS, 0); }
	public SUPER(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.SUPER, 0); }
	public basicExpression(): BasicExpressionContext | undefined {
		return this.tryGetRuleContext(0, BasicExpressionContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_functionCall; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterFunctionCall) {
			listener.enterFunctionCall(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitFunctionCall) {
			listener.exitFunctionCall(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitFunctionCall) {
			return visitor.visitFunctionCall(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class StatementContext extends ParserRuleContext {
	public fieldDeclaration(): FieldDeclarationContext | undefined {
		return this.tryGetRuleContext(0, FieldDeclarationContext);
	}
	public statementExpression(): StatementExpressionContext | undefined {
		return this.tryGetRuleContext(0, StatementExpressionContext);
	}
	public functionCall(): FunctionCallContext | undefined {
		return this.tryGetRuleContext(0, FunctionCallContext);
	}
	public ifStatement(): IfStatementContext | undefined {
		return this.tryGetRuleContext(0, IfStatementContext);
	}
	public forStatement(): ForStatementContext | undefined {
		return this.tryGetRuleContext(0, ForStatementContext);
	}
	public whileStatement(): WhileStatementContext | undefined {
		return this.tryGetRuleContext(0, WhileStatementContext);
	}
	public doWhileStatement(): DoWhileStatementContext | undefined {
		return this.tryGetRuleContext(0, DoWhileStatementContext);
	}
	public selfAddOrMinusStatement(): SelfAddOrMinusStatementContext | undefined {
		return this.tryGetRuleContext(0, SelfAddOrMinusStatementContext);
	}
	public tryStoreStatement(): TryStoreStatementContext | undefined {
		return this.tryGetRuleContext(0, TryStoreStatementContext);
	}
	public controlStatement(): ControlStatementContext | undefined {
		return this.tryGetRuleContext(0, ControlStatementContext);
	}
	public orgCommand(): OrgCommandContext | undefined {
		return this.tryGetRuleContext(0, OrgCommandContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_statement; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterStatement) {
			listener.enterStatement(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitStatement) {
			listener.exitStatement(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitStatement) {
			return visitor.visitStatement(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class OrgCommandContext extends ParserRuleContext {
	public OrgCommand(): TerminalNode { return this.getToken(MCFPPParser.OrgCommand, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_orgCommand; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterOrgCommand) {
			listener.enterOrgCommand(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitOrgCommand) {
			listener.exitOrgCommand(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitOrgCommand) {
			return visitor.visitOrgCommand(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ControlStatementContext extends ParserRuleContext {
	public BREAK(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.BREAK, 0); }
	public CONTINUE(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.CONTINUE, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_controlStatement; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterControlStatement) {
			listener.enterControlStatement(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitControlStatement) {
			listener.exitControlStatement(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitControlStatement) {
			return visitor.visitControlStatement(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class IfStatementContext extends ParserRuleContext {
	public IF(): TerminalNode { return this.getToken(MCFPPParser.IF, 0); }
	public expression(): ExpressionContext {
		return this.getRuleContext(0, ExpressionContext);
	}
	public ifBlock(): IfBlockContext[];
	public ifBlock(i: number): IfBlockContext;
	public ifBlock(i?: number): IfBlockContext | IfBlockContext[] {
		if (i === undefined) {
			return this.getRuleContexts(IfBlockContext);
		} else {
			return this.getRuleContext(i, IfBlockContext);
		}
	}
	public ELSE(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.ELSE, 0); }
	public elseIfStatement(): ElseIfStatementContext | undefined {
		return this.tryGetRuleContext(0, ElseIfStatementContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_ifStatement; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterIfStatement) {
			listener.enterIfStatement(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitIfStatement) {
			listener.exitIfStatement(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitIfStatement) {
			return visitor.visitIfStatement(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ElseIfStatementContext extends ParserRuleContext {
	public ifStatement(): IfStatementContext {
		return this.getRuleContext(0, IfStatementContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_elseIfStatement; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterElseIfStatement) {
			listener.enterElseIfStatement(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitElseIfStatement) {
			listener.exitElseIfStatement(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitElseIfStatement) {
			return visitor.visitElseIfStatement(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class IfBlockContext extends ParserRuleContext {
	public block(): BlockContext {
		return this.getRuleContext(0, BlockContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_ifBlock; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterIfBlock) {
			listener.enterIfBlock(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitIfBlock) {
			listener.exitIfBlock(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitIfBlock) {
			return visitor.visitIfBlock(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ForStatementContext extends ParserRuleContext {
	public FOR(): TerminalNode { return this.getToken(MCFPPParser.FOR, 0); }
	public forControl(): ForControlContext {
		return this.getRuleContext(0, ForControlContext);
	}
	public forBlock(): ForBlockContext {
		return this.getRuleContext(0, ForBlockContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_forStatement; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterForStatement) {
			listener.enterForStatement(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitForStatement) {
			listener.exitForStatement(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitForStatement) {
			return visitor.visitForStatement(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ForBlockContext extends ParserRuleContext {
	public block(): BlockContext {
		return this.getRuleContext(0, BlockContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_forBlock; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterForBlock) {
			listener.enterForBlock(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitForBlock) {
			listener.exitForBlock(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitForBlock) {
			return visitor.visitForBlock(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ForControlContext extends ParserRuleContext {
	public forUpdate(): ForUpdateContext {
		return this.getRuleContext(0, ForUpdateContext);
	}
	public forInit(): ForInitContext[];
	public forInit(i: number): ForInitContext;
	public forInit(i?: number): ForInitContext | ForInitContext[] {
		if (i === undefined) {
			return this.getRuleContexts(ForInitContext);
		} else {
			return this.getRuleContext(i, ForInitContext);
		}
	}
	public expression(): ExpressionContext | undefined {
		return this.tryGetRuleContext(0, ExpressionContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_forControl; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterForControl) {
			listener.enterForControl(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitForControl) {
			listener.exitForControl(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitForControl) {
			return visitor.visitForControl(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ForInitContext extends ParserRuleContext {
	public fieldDeclaration(): FieldDeclarationContext | undefined {
		return this.tryGetRuleContext(0, FieldDeclarationContext);
	}
	public statementExpression(): StatementExpressionContext | undefined {
		return this.tryGetRuleContext(0, StatementExpressionContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_forInit; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterForInit) {
			listener.enterForInit(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitForInit) {
			listener.exitForInit(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitForInit) {
			return visitor.visitForInit(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ForUpdateContext extends ParserRuleContext {
	public statementExpression(): StatementExpressionContext[];
	public statementExpression(i: number): StatementExpressionContext;
	public statementExpression(i?: number): StatementExpressionContext | StatementExpressionContext[] {
		if (i === undefined) {
			return this.getRuleContexts(StatementExpressionContext);
		} else {
			return this.getRuleContext(i, StatementExpressionContext);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_forUpdate; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterForUpdate) {
			listener.enterForUpdate(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitForUpdate) {
			listener.exitForUpdate(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitForUpdate) {
			return visitor.visitForUpdate(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class WhileStatementContext extends ParserRuleContext {
	public WHILE(): TerminalNode { return this.getToken(MCFPPParser.WHILE, 0); }
	public expression(): ExpressionContext {
		return this.getRuleContext(0, ExpressionContext);
	}
	public whileBlock(): WhileBlockContext {
		return this.getRuleContext(0, WhileBlockContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_whileStatement; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterWhileStatement) {
			listener.enterWhileStatement(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitWhileStatement) {
			listener.exitWhileStatement(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitWhileStatement) {
			return visitor.visitWhileStatement(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class WhileBlockContext extends ParserRuleContext {
	public block(): BlockContext {
		return this.getRuleContext(0, BlockContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_whileBlock; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterWhileBlock) {
			listener.enterWhileBlock(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitWhileBlock) {
			listener.exitWhileBlock(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitWhileBlock) {
			return visitor.visitWhileBlock(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class DoWhileStatementContext extends ParserRuleContext {
	public DO(): TerminalNode { return this.getToken(MCFPPParser.DO, 0); }
	public doWhileBlock(): DoWhileBlockContext {
		return this.getRuleContext(0, DoWhileBlockContext);
	}
	public WHILE(): TerminalNode { return this.getToken(MCFPPParser.WHILE, 0); }
	public expression(): ExpressionContext {
		return this.getRuleContext(0, ExpressionContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_doWhileStatement; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterDoWhileStatement) {
			listener.enterDoWhileStatement(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitDoWhileStatement) {
			listener.exitDoWhileStatement(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitDoWhileStatement) {
			return visitor.visitDoWhileStatement(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class DoWhileBlockContext extends ParserRuleContext {
	public block(): BlockContext {
		return this.getRuleContext(0, BlockContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_doWhileBlock; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterDoWhileBlock) {
			listener.enterDoWhileBlock(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitDoWhileBlock) {
			listener.exitDoWhileBlock(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitDoWhileBlock) {
			return visitor.visitDoWhileBlock(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class SelfAddOrMinusStatementContext extends ParserRuleContext {
	public selfAddOrMinusExpression(): SelfAddOrMinusExpressionContext {
		return this.getRuleContext(0, SelfAddOrMinusExpressionContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_selfAddOrMinusStatement; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterSelfAddOrMinusStatement) {
			listener.enterSelfAddOrMinusStatement(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitSelfAddOrMinusStatement) {
			listener.exitSelfAddOrMinusStatement(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitSelfAddOrMinusStatement) {
			return visitor.visitSelfAddOrMinusStatement(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class TryStoreStatementContext extends ParserRuleContext {
	public TRY(): TerminalNode { return this.getToken(MCFPPParser.TRY, 0); }
	public block(): BlockContext {
		return this.getRuleContext(0, BlockContext);
	}
	public STORE(): TerminalNode { return this.getToken(MCFPPParser.STORE, 0); }
	public Identifier(): TerminalNode { return this.getToken(MCFPPParser.Identifier, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_tryStoreStatement; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterTryStoreStatement) {
			listener.enterTryStoreStatement(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitTryStoreStatement) {
			listener.exitTryStoreStatement(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitTryStoreStatement) {
			return visitor.visitTryStoreStatement(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class BlockContext extends ParserRuleContext {
	public statement(): StatementContext[];
	public statement(i: number): StatementContext;
	public statement(i?: number): StatementContext | StatementContext[] {
		if (i === undefined) {
			return this.getRuleContexts(StatementContext);
		} else {
			return this.getRuleContext(i, StatementContext);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_block; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterBlock) {
			listener.enterBlock(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitBlock) {
			listener.exitBlock(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitBlock) {
			return visitor.visitBlock(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class SelfAddOrMinusExpressionContext extends ParserRuleContext {
	public _op!: Token;
	public Identifier(): TerminalNode { return this.getToken(MCFPPParser.Identifier, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_selfAddOrMinusExpression; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterSelfAddOrMinusExpression) {
			listener.enterSelfAddOrMinusExpression(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitSelfAddOrMinusExpression) {
			listener.exitSelfAddOrMinusExpression(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitSelfAddOrMinusExpression) {
			return visitor.visitSelfAddOrMinusExpression(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ExpressionListContext extends ParserRuleContext {
	public expression(): ExpressionContext[];
	public expression(i: number): ExpressionContext;
	public expression(i?: number): ExpressionContext | ExpressionContext[] {
		if (i === undefined) {
			return this.getRuleContexts(ExpressionContext);
		} else {
			return this.getRuleContext(i, ExpressionContext);
		}
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_expressionList; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterExpressionList) {
			listener.enterExpressionList(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitExpressionList) {
			listener.exitExpressionList(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitExpressionList) {
			return visitor.visitExpressionList(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class TypeContext extends ParserRuleContext {
	public className(): ClassNameContext | undefined {
		return this.tryGetRuleContext(0, ClassNameContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_type; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterType) {
			listener.enterType(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitType) {
			listener.exitType(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitType) {
			return visitor.visitType(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ValueContext extends ParserRuleContext {
	public INT(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.INT, 0); }
	public DECIMAL(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.DECIMAL, 0); }
	public STRING(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.STRING, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_value; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterValue) {
			listener.enterValue(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitValue) {
			listener.exitValue(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitValue) {
			return visitor.visitValue(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class ClassNameContext extends ParserRuleContext {
	public ClassIdentifier(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.ClassIdentifier, 0); }
	public Identifier(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.Identifier, 0); }
	public InsideClass(): TerminalNode | undefined { return this.tryGetToken(MCFPPParser.InsideClass, 0); }
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_className; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterClassName) {
			listener.enterClassName(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitClassName) {
			listener.exitClassName(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitClassName) {
			return visitor.visitClassName(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


export class FunctionTagContext extends ParserRuleContext {
	public namespaceID(): NamespaceIDContext {
		return this.getRuleContext(0, NamespaceIDContext);
	}
	constructor(parent: ParserRuleContext | undefined, invokingState: number) {
		super(parent, invokingState);
	}
	// @Override
	public get ruleIndex(): number { return MCFPPParser.RULE_functionTag; }
	// @Override
	public enterRule(listener: MCFPPListener): void {
		if (listener.enterFunctionTag) {
			listener.enterFunctionTag(this);
		}
	}
	// @Override
	public exitRule(listener: MCFPPListener): void {
		if (listener.exitFunctionTag) {
			listener.exitFunctionTag(this);
		}
	}
	// @Override
	public accept<Result>(visitor: MCFPPVisitor<Result>): Result {
		if (visitor.visitFunctionTag) {
			return visitor.visitFunctionTag(this);
		} else {
			return visitor.visitChildren(this);
		}
	}
}


