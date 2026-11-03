package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.CompilerValue
import top.mcfpp.analysis.DeclarationBinding
import top.mcfpp.analysis.StorageAccess
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.compound.UnionDataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.type.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import top.mcfpp.antlr.mcfppParser as Parser

class MniTypeSyntaxResolutionTest {
    @Test fun bothLegacyEntrypointsKeepPrimitiveIdentities() {
        val main = compile()
        val expected = mapOf(
            "int" to MCFPPBaseType.Int, "float" to MCFPPBaseType.Float,
            "bool" to MCFPPBaseType.Bool, "string" to MCFPPBaseType.String,
            "object" to MCFPPBaseType.Object, "any" to MCFPPBaseType.Any,
            "text" to MCFPPBaseType.JsonText, "entity" to MCFPPEntityType.NormalSelector,
            "Type" to MCFPPConcreteType.Type, "type" to MCFPPConcreteType.Type, "byte" to MCFPPNBTType.Byte,
            "short" to MCFPPNBTType.Short, "long" to MCFPPNBTType.Long,
            "double" to MCFPPNBTType.Double, "nbt" to MCFPPNBTType.NBT,
            "ByteArray" to MCFPPNBTType.ByteArray, "IntArray" to MCFPPNBTType.IntArray,
            "LongArray" to MCFPPNBTType.LongArray
        )
        for ((syntax, type) in expected) {
            assertSame(type, legacy(main, syntax), syntax)
            assertSame(type, declaration(main, syntax), syntax)
        }
        assertEquals(0, Project.errorCount)
    }

    @Test fun nestedContainerBranchesAreSharedAndKeepTheirOwnKinds() {
        val main = compile()
        val expected = listOf(
            "list<dict<bool>>" to MCFPPListType(MCFPPDictType(MCFPPBaseType.Bool)),
            "ImmutableList<map<int>>" to MCFPPImmutableListType(MCFPPMapType(MCFPPBaseType.Int)),
            "dict<list<float>>" to MCFPPDictType(MCFPPListType(MCFPPBaseType.Float)),
            "map<ImmutableList<string>>" to MCFPPMapType(MCFPPImmutableListType(MCFPPBaseType.String))
        )
        for ((syntax, type) in expected) {
            assertEquals(type.typeId, legacy(main, syntax).typeId, syntax)
            assertEquals(type.typeId, declaration(main, syntax).typeId, syntax)
        }
    }

    @Test fun declarationTypeBindingsWinBeforePrimitiveAndNestedLookup() {
        val main = compile()
        main.scope.putType("T", MCFPPBaseType.Int)
        val binding = DeclarationBinding(
            main,
            emptyMap(),
            typeBindings = mapOf(
                "T" to MCFPPBaseType.Float,
                "int" to MCFPPBaseType.Bool
            )
        )
        assertSame(MCFPPBaseType.Int, legacy(main, "T"))
        assertSame(MCFPPBaseType.Float, binding.type(parse("T")))
        assertSame(MCFPPBaseType.Bool, binding.type(parse("int")))
        val nested = assertIs<MCFPPListType>(binding.type(parse("list<T>")))
        assertSame(MCFPPBaseType.Float, nested.generic.single())
        val union = assertIs<MCFPPUnionType>(binding.type(parse("(T | int)")))
        assertEquals(setOf(MCFPPBaseType.Float.typeId, MCFPPBaseType.Bool.typeId), union.types.map { it.typeId }.toSet())
    }

    @Test fun independentContextResolvesBuiltinStructureWithoutLegacyLookup() {
        compile()
        val context = object : TypeSyntaxContext() {
            override fun named(namespace: String?, identifier: String, site: Parser.TypeBodyContext): MCFPPType? = error("Builtin syntax must not perform declaration lookup")
        }
        val expected = MCFPPListType(MCFPPUnionType(MCFPPBaseType.Int, MCFPPBaseType.Bool))
        assertEquals(expected.typeId, TypeSyntaxResolver.resolve(parse("list<(int | bool)>"), context)!!.typeId)
        assertSame(MCFPPEntityType.NormalSelector, TypeSyntaxResolver.resolve(parse("entity"), context))
        val failure = assertFailsWith<TypeSyntaxFailure> { TypeSyntaxResolver.resolve(parse("entity<2>"), context) }
        assertTrue(failure.message.orEmpty().contains("use entity"))
    }

    @Test fun vectorEntityAndValueUnionSyntaxUseBothEntrypoints() {
        val main = compile()
        for (syntax in listOf(
            "vec3",
            "entity",
            "(int | list<bool>)"
        )) {
            assertEquals(legacy(main, syntax).typeId, declaration(main, syntax).typeId, syntax)
        }
        assertEquals(3, assertIs<MCFPPVectorType>(declaration(main, "vec3")).dimension)
        assertSame(MCFPPEntityType.NormalSelector, declaration(main, "entity"))
        assertEquals(
            setOf(MCFPPBaseType.Int.typeId, MCFPPListType(MCFPPBaseType.Bool).typeId),
            assertIs<MCFPPUnionType>(legacy(main, "(int | list<bool>)")).types.map { it.typeId }.toSet()
        )
    }

    @Test fun aliasesQualifiedNamesAndTemplateIntersectionsKeepDeclarationOwners() {
        val main = compile(
            """
            namespace fixture.walk
            typealias list<int> as Numbers
            data A {}
            data B {}
            func main(){}
        """
        )
        for (syntax in listOf("Numbers", "fixture.walk:A", "(A & B)")) {
            val left = legacy(main, syntax)
            val right = declaration(main, syntax)
            if (syntax == "(A & B)") {
                val union = assertIs<UnionDataTemplate>(assertIs<MCFPPDataTemplateType>(right).template)
                val owners = GlobalScope.localNamespaces.getValue("fixture.walk").scope
                assertTrue(union.parent.any { it === owners.getTemplate("A") })
                assertTrue(union.parent.any { it === owners.getTemplate("B") })
                assertIs<UnionDataTemplate>(assertIs<MCFPPDataTemplateType>(left).template)
            } else assertEquals(left.typeId, right.typeId)
        }
        assertEquals(MCFPPListType(MCFPPBaseType.Int).typeId, declaration(main, "Numbers").typeId)
        assertEquals(0, Project.errorCount)
    }

    @Test fun legacyGenericApplicationsStillEvaluateReadonlyAndCompileCanonicalInstances() {
        val main = compile(
            """
            namespace fixture.walk
            data Sized<N:int>{var value:int=N;}
            data Box<T>{var value:T;}
            func main(){}
        """
        )
        val scope = GlobalScope.localNamespaces.getValue("fixture.walk").scope
        val sized = assertIs<GenericDataTemplate>(scope.getTemplate("Sized"))
        assertTrue(sized.compiledTemplates.isEmpty())
        val legacy = assertIs<CompiledGenericDataTemplate>(assertIs<MCFPPDataTemplateType>(legacy(main, "Sized<2+3>")).template)
        assertSame(legacy, assertIs<MCFPPDataTemplateType>(declaration(main, "Sized<2+3>")).template)
        assertEquals(1, sized.compiledTemplates.size)
        assertEquals(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(5)), StorageAccess.snapshot(legacy.args.single()))
        val box = assertIs<CompiledGenericDataTemplate>(assertIs<MCFPPDataTemplateType>(legacy(main, "Box<int>")).template)
        assertSame(box, assertIs<MCFPPDataTemplateType>(declaration(main, "Box<int>")).template)
        assertSame(MCFPPBaseType.Int, box.scope.getVar("value")!!.type)
        assertEquals(0, Project.errorCount)
    }

    @Test fun declarationGenericArgumentsUseDeclarationFactsRatherThanReadonlyVisitorLookup() {
        val main = compile("namespace fixture.walk\ndata Sized<N:int>{var value:int=N;}\nfunc main(){}")
        val proof = DeclarationBinding.Bound(
            MCFPPBaseType.Int,
            CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(7))
        )
        val binding = DeclarationBinding(main, emptyMap(), values = mapOf("N" to proof))
        val instance = assertIs<CompiledGenericDataTemplate>(assertIs<MCFPPDataTemplateType>(binding.type(parse("Sized<N>"))).template)
        assertEquals(CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(7)), StorageAccess.snapshot(instance.args.single()))
        assertEquals(0, Project.errorCount)
    }

    @Test fun contextWithoutSpecializationSupportRejectsBeforeLegacyEvaluation() {
        val main = compile("namespace fixture.walk\ndata Box<N:int>{var value:int=N;}\nfunc main(){}")
        val head = assertIs<GenericDataTemplate>(GlobalScope.localNamespaces.getValue("fixture.walk").scope.getTemplate("Box"))
        val before = main.commands.toList()
        val context = object : TypeSyntaxContext() {
            override fun named(namespace: String?, identifier: String, site: Parser.TypeBodyContext): MCFPPType? = head.getType()
        }
        val source = "list<Box<missing()>>"
        val failure = assertFailsWith<TypeSyntaxFailure> { TypeSyntaxResolver.resolve(parse(source), context) }
        assertTrue(failure.message!!.contains("prepared declaration"))
        assertEquals("type-fixture.mcfpp", failure.source.file)
        assertEquals(source.indexOf("Box"), failure.source.startOffset)
        assertEquals(source.lastIndexOf('>'), failure.source.endOffset)
        assertTrue(head.compiledTemplates.isEmpty())
        assertEquals(before, main.commands.toList())
        assertEquals(0, Project.errorCount)
    }

    @Test fun contextWithoutAnonymousSupportRejectsWithItsExactSource() {
        compile()
        val context = object : TypeSyntaxContext() {
            override fun named(namespace: String?, identifier: String, site: Parser.TypeBodyContext): MCFPPType? = null
        }
        val source = "data {var value:int;}"
        val failure = assertFailsWith<TypeSyntaxFailure> { TypeSyntaxResolver.resolve(parse(source), context) }
        assertEquals("type-fixture.mcfpp", failure.source.file)
        assertEquals(0, failure.source.startOffset)
        assertEquals(source.length, failure.source.endOffset)
        assertEquals(0, Project.errorCount)
    }

    @Test fun unresolvedGenericWrappersAndTemplateIntersectionsNeverResolveInTheBaseContext() {
        val main = compile("namespace fixture.walk\ndata Box<N:int>{var value:int=N;}\nfunc main(){}")
        val head = assertIs<GenericDataTemplate>(GlobalScope.localNamespaces.getValue("fixture.walk").scope.getTemplate("Box"))
        val wrapper = MCFPPGenericDataTemplateType(
            DataTemplate("Box", "fixture.walk"),
            arrayListOf(),
            TypeId.Specialized(
                TypeId.Declaration("template", "fixture.walk", "Box"),
                listOf(
                    CompilerValue.Typed(MCFPPBaseType.Int.typeId, CompilerValue.Integral(5))
                )
            )
        )
        val before = main.commands.toList()
        val context = object : TypeSyntaxContext() {
            override fun known(body: Parser.TypeBodyContext): MCFPPType? = if (body.text == "Known") wrapper else null
            override fun named(namespace: String?, identifier: String, site: Parser.TypeBodyContext): MCFPPType? = wrapper
        }
        for (syntax in listOf("Opaque", "(Known & Known)")) {
            val failure = assertFailsWith<TypeSyntaxFailure> { TypeSyntaxResolver.resolve(parse(syntax), context) }
            assertEquals("type-fixture.mcfpp", failure.source.file)
            assertEquals(0, failure.source.startOffset)
            assertEquals(syntax.length, failure.source.endOffset)
            assertTrue(head.compiledTemplates.isEmpty(), "An unresolved .template getter would compile Box<5>")
            assertEquals(before, main.commands.toList())
        }
        // Positive control: this exact unresolved wrapper has a live production resolver.
        assertIs<CompiledGenericDataTemplate>(wrapper.template)
        assertEquals(1, head.compiledTemplates.size)
        assertEquals(0, Project.errorCount)
    }

    @Test fun unresolvedNamesStayUnresolvedAndOrdinaryReadonlyApplicationsStayErrors() {
        val main = compile("namespace fixture.walk\ndata Plain{}\nfunc main(){}")
        assertNull(MCFPPType.parseFromContext(parse("Missing"), main.scope, main))
        // JTEXT is the token name; the source spelling is text, not jtext.
        assertNull(MCFPPType.parseFromContext(parse("jtext"), main.scope, main))
        assertFailsWith<DeclarationBinding.Failure> { declaration(main, "Missing") }
        assertFailsWith<DeclarationBinding.Failure> { declaration(main, "jtext") }
        assertFailsWith<DeclarationBinding.Failure> { declaration(main, "list<Missing>") }
        assertNull(MCFPPType.parseFromContext(parse("Plain<1>"), main.scope, main))
        assertEquals(1, Project.errorCount)
    }

    private fun parse(source: String): Parser.TypeContext {
        val tokens = CommonTokenStream(mcfppLexer(CharStreams.fromString(source, "type-fixture.mcfpp")))
        val parser = Parser(tokens)
        val result = parser.type()
        assertEquals(0, parser.numberOfSyntaxErrors, source)
        assertEquals(Token.EOF, tokens.LA(1), source)
        return result
    }

    private fun legacy(main: Function, source: String) = assertNotNull(MCFPPType.parseFromContext(parse(source), main.scope, main), "Legacy type syntax: $source")
    private fun declaration(main: Function, source: String) = DeclarationBinding(main, emptyMap()).type(parse(source))

    private fun compile(source: String = "func main(){}"): Function {
        val config = Project.config
        val ignore = CompileSettings.ignoreStdLib
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = true
            MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
            assertEquals(0, Project.errorCount)
            return GlobalScope.localNamespaces.values.flatMap { it.scope.functions["main"].orEmpty() }
                .single().also { Function.currFunction = it }
        } finally {
            Project.config = config
            CompileSettings.ignoreStdLib = ignore
        }
    }
}
