package top.mcfpp.test

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.analysis.InheritanceContractValidator
import top.mcfpp.antlr.MCFPPFieldVisitor
import top.mcfpp.antlr.mcfppLexer
import top.mcfpp.antlr.mcfppParser
import top.mcfpp.core.lang.MCFPPTypeVar
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPDataTemplateType
import top.mcfpp.util.LogProcessor
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DataInheritanceContractPhaseTest {
    @Test fun missingOverrideWaitsForAllDeclarationsAndIsIndependentOfSourceOrder() {
        val sourceDeclarations = listOf(
            "data Parent{func f(value:int):int{return value;}}",
            "data Child:Parent{func f(value:int):int{return value;}}"
        )
        for (order in listOf(sourceDeclarations, sourceDeclarations.reversed())) {
            declarations(order.joinToString("\n")) { messages ->
                assertDiagnostic(messages, "requires override", "fixture.phase:Child", "fixture.phase:Parent")
            }
        }
    }

    @Test fun validOverrideHasNoTransientForwardParentDiagnostic() {
        val sourceDeclarations = listOf(
            "data Parent{func f(value:int):int{return value;}}",
            "data Child:Parent{override func f(value:int):int{return value;}}"
        )
        for (order in listOf(sourceDeclarations, sourceDeclarations.reversed())) {
            declarations(order.joinToString("\n")) { messages ->
                assertEquals(0, Project.errorCount, messages.joinToString("\n"))
                assertTrue(InheritanceContractValidator.check(template("Child")).isValid)
                assertTrue(template("Child").scope.functions.getValue("f").single().isOverride)
            }
        }
    }

    @Test fun unusedConcreteTypesCannotKeepAnInheritedOrLocalAbstractSlot() {
        for (child in listOf("data Child:Parent{}", "object data Child:Parent{}")) {
            val sources = listOf("abstract data Parent{abstract func f():int;}", child)
            for (order in listOf(sources, sources.reversed())) {
                declarations(order.joinToString("\n")) { messages ->
                    assertDiagnostic(messages, "Abstract function 'f'", "fixture.phase:Child", "fixture.phase:Parent")
                }
            }
        }
        declarations("data Concrete{abstract func f():int;}") { messages ->
            assertDiagnostic(messages, "Abstract function 'f'", "fixture.phase:Concrete")
        }
    }

    @Test fun abstractIntermediatesAndConcreteImplementationsPassWithoutInstantiation() {
        declarations(
            """
            data Child:Middle{override func f():int{return 2;}}
            abstract data Middle:Parent{}
            abstract data Parent{abstract func f():int;}
        """
        ) { messages ->
            assertEquals(0, Project.errorCount, messages.joinToString("\n"))
            assertTrue(InheritanceContractValidator.check(template("Child")).isValid)
        }
    }

    @Test fun abstractDataObligationsUseTheSameBarrierForUnusedConcreteChildren() {
        for (body in listOf("", "override func f():int{return 2;}")) {
            declarations("data Child:Contract{$body}\nabstract data Contract{abstract func f():int;}") { messages ->
                if (body.isEmpty()) assertDiagnostic(messages, "Abstract function 'f'", "fixture.phase:Child", "fixture.phase:Contract")
                else assertEquals(0, Project.errorCount, messages.joinToString("\n"))
            }
        }
    }

    @Test fun unusedUnrelatedParentConflictsAreDiagnosedInBothParentOrders() {
        for (parents in listOf("Left,Right", "Right,Left")) {
            declarations(
                """
                data Child:$parents{}
                data Left{func f():int{return 1;}}
                data Right{func f():int{return 2;}}
            """
            ) { messages ->
                assertDiagnostic(messages, "Ambiguous inherited function 'f'", "fixture.phase:Child", "fixture.phase:Left", "fixture.phase:Right")
            }
        }
    }

    @Test fun theBarrierChecksOverrideTargetsReturnsAndAccessWithoutCompilingBodies() {
        for ((child, reason) in listOf(
            "data Child:Parent{override func f():bool{return true;}}" to "return type",
            "data Child:Parent{private override func f():int{return 2;}}" to "access",
            "data Child{override func f():int{return 2;}}" to "overrides nothing"
        )) {
            declarations("$child\ndata Parent{func f():int{return 1;}}") { messages ->
                assertDiagnostic(messages, reason, "fixture.phase:Child")
                assertTrue(template("Child").scope.functions.getValue("f").none { it.bodyCompiled })
            }
        }
    }

    @Test fun anonymousTypesInUncalledSignaturesUseTheSameCompleteParentView() {
        for (modifier in listOf("", "override ")) {
            declarations(
                """
                func consume(value:data:Parent{${modifier}func f():int{return 2;}}){}
                data Parent{func f():int{return 1;}}
            """
            ) { messages ->
                val consume = GlobalScope.localNamespaces.getValue("fixture.phase").scope.functions.getValue("consume").single()
                val anonymous = assertIs<MCFPPDataTemplateType>(consume.normalParams.single().type).template
                if (modifier.isEmpty()) assertDiagnostic(messages, "requires override", "fixture.phase:Parent")
                else {
                    assertEquals(0, Project.errorCount, messages.joinToString("\n"))
                    assertTrue(InheritanceContractValidator.check(anonymous).isValid)
                }
            }
        }
        declarations(
            """
            func consume(value:data:Parent{}){}
            abstract data Parent{abstract func f():int;}
        """
        ) { messages -> assertDiagnostic(messages, "Abstract function 'f'", "fixture.phase:Parent") }
    }

    @Test fun boundGenericsInUncalledSignaturesAreCheckedAfterForwardParentSignatures() {
        for (modifier in listOf("", "override ")) {
            val parents = listOf(
                "data Parent<T>{func f(value:T):T{return value;}}",
                "data Child<T>:Parent<(T)>{${modifier}func f(value:T):T{return value;}}"
            )
            for (order in listOf(parents, parents.reversed())) {
                declarations("func consume(value:Child<int>){}\n" + order.joinToString("\n")) { messages ->
                    val head = assertIs<GenericDataTemplate>(template("Child"))
                    val child = head.materializedInstances().single()
                    if (modifier.isEmpty()) {
                        assertDiagnostic(messages, "requires override", "Child_", "Parent_")
                        assertFalse(child.hasCompletedDeclaration)
                        assertTrue(head.compiledTemplates.isEmpty())
                    } else {
                        assertEquals(0, Project.errorCount, messages.joinToString("\n"))
                        assertTrue(InheritanceContractValidator.check(child).isValid)
                        assertTrue(child.hasCompletedDeclaration)
                        assertSame(child, head.compiledTemplates.values.single())
                    }
                }
            }
        }
    }

    @Test fun anUnusedOrdinaryChildStillChecksItsConcreteGenericAbstractParent() {
        declarations(
            """
            data Used:Child<int>{}
            data Child<T>:Parent<(T)>{}
            abstract data Parent<T>{abstract func f(value:T):T;}
        """
        ) { messages ->
            assertDiagnostic(messages, "Abstract function 'f'", "fixture.phase:Used", "Parent_")
        }
    }

    @Test fun lateGenericBindingsShareTheBarrierAndDoNotInventBindingsForUnusedHeads() {
        declarations(
            """
            data Child<T>:Parent<(T)>{func f(value:T):T{return value;}}
            data Parent<T>{func f(value:T):T{return value;}}
        """
        ) { messages ->
            assertEquals(0, Project.errorCount, messages.joinToString("\n"))
            val head = assertIs<GenericDataTemplate>(template("Child"))
            assertTrue(head.compiledTemplates.isEmpty())
            val bound = assertNotNull(head.compile(listOf(MCFPPTypeVar(MCFPPBaseType.Int))))
            assertEquals(MCFPPBaseType.Int.typeId, bound.scope.functions.getValue("f").single().returnType.typeId)
            assertDiagnostic(messages, "requires override", "Child_", "Parent_")
        }
    }

    @Test fun lateAnonymousDeclarationsAreValidatedWhenTheirWholeBodyIsCollected() {
        declarations("data Parent{func f():int{return 1;}}") { messages ->
            assertEquals(0, Project.errorCount)
            val parser = mcfppParser(
                CommonTokenStream(
                    mcfppLexer(
                        CharStreams.fromString(
                            "data:Parent{func f():int{return 2;}}"
                        )
                    )
                )
            )
            val syntax = parser.anonymousTemplateType()
            assertEquals(0, parser.numberOfSyntaxErrors)
            template("Parent").declarationFile!!.withDeclarationContext {
                MCFPPFieldVisitor().visitAnonymousTemplateType(syntax)
            }
            assertDiagnostic(messages, "requires override", "fixture.phase:Parent")
        }
    }

    @Test fun importedForwardParentsHaveStableDiagnosticsAcrossFileOrders() {
        val child = "namespace fixture.phase.child\nimport fixture.phase.parent:Parent;\ndata Child:Parent{func f():int{return 2;}}"
        val parent = "namespace fixture.phase.parent\ndata Parent{func f():int{return 1;}}"
        for (files in listOf(listOf(child, parent), listOf(parent, child))) {
            declarations(*files.toTypedArray(), addNamespace = false) { messages ->
                assertDiagnostic(messages, "requires override", "fixture.phase.child:Child", "fixture.phase.parent:Parent")
            }
        }
    }

    @Test fun repeatedCompletionDoesNotRepeatTheSameContractDiagnostic() {
        declarations("data Child:Parent{func f():int{return 2;}}\ndata Parent{func f():int{return 1;}}") { messages ->
            assertDiagnostic(messages, "requires override", "fixture.phase:Child")
            val errors = Project.errorCount
            val diagnostics = messages.count { "requires override" in it }
            Project.completeTemplateDeclarations()
            assertEquals(errors, Project.errorCount)
            assertEquals(diagnostics, messages.count { "requires override" in it })
        }
    }

    private fun template(name: String): DataTemplate = assertNotNull(GlobalScope.localNamespaces.getValue("fixture.phase").scope.getTemplate(name))

    private fun assertDiagnostic(messages: List<String>, vararg expected: String) {
        assertTrue(Project.errorCount > 0)
        assertTrue(messages.any { message -> expected.all { it in message } }, messages.joinToString("\n"))
    }

    private fun declarations(vararg sources: String, addNamespace: Boolean = true, check: (List<String>) -> Unit) {
        val root = Files.createTempDirectory("mcfpp-contract-phase-")
        val config = Project.config
        val ignore = CompileSettings.ignoreStdLib
        val logger = assertIs<Logger>(LogProcessor.logger)
        val messages = arrayListOf<String>()
        val appender = object : AbstractAppender("contract-phase", null, null, false, Property.EMPTY_ARRAY) {
            override fun append(event: LogEvent) {
                messages.add(event.message.formattedMessage)
            }
        }
        appender.start()
        logger.addAppender(appender)
        try {
            sources.forEachIndexed { index, source ->
                Files.writeString(root.resolve("%02d.mcfpp".format(index)), if (addNamespace) "namespace fixture.phase\n${source.trimIndent()}" else source)
            }
            CompileSettings.ignoreStdLib = true
            Project.config = ProjectConfig(version = "26.3", sourcePath = root, rootNamespace = "fixture")
            Project.init()
            Project.readProject()
            Project.indexType()
            Project.resolveField()
            Project.runAnnotation()
            assertEquals(0, Project.errorCount, "Contracts must wait for the complete-signature barrier:\n" + messages.joinToString("\n"))
            Project.completeTemplateDeclarations()
            check(messages)
        } finally {
            logger.removeAppender(appender)
            appender.stop()
            Project.config = config
            CompileSettings.ignoreStdLib = ignore
            root.toFile().deleteRecursively()
        }
    }
}
