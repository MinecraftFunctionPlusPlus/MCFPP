package top.mcfpp.test

import top.mcfpp.Project
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.PropertyVar
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.compound.DataTemplate
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.type.MCFPPBaseType
import kotlin.test.*
import kotlin.test.Test

class TemplateFieldInferenceTest {
    private fun compile(source: String, errors: Int = 0): Function {
        fun sourceCalls() = Function.extraFunction.commands.analyzeAll().filter { "function default.test:" in it }
        val discardedCalls = sourceCalls()
        MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
        assertEquals(errors, Project.errorCount)
        assertEquals(discardedCalls, sourceCalls(), "Declaration binding must not execute a discarded source call")
        return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
    }

    private fun template(name: String = "Box"): DataTemplate =
        GlobalScope.localNamespaces.getValue("default.test").scope.template.getValue(name)

    private fun execute(main: Function): ScoreCommandExecutor {
        val functions = LinkedHashMap<String, List<String>>()
        fun collect(function: Function) {
            functions[function.namespaceID.toString()] = function.commands.analyzeAll()
            function.compiledFunctions.values.forEach(::collect)
        }
        GlobalScope.localNamespaces.values.forEach { namespace ->
            namespace.scope.functions.values.flatten().forEach(::collect)
            namespace.scope.template.values.forEach { template ->
                template.constructors.forEach(::collect)
                template.scope.forEachFunction(::collect)
            }
        }
        functions.putAll(Project.macroFunction.mapKeys { "mcfpp:dynamic/${it.key}" }.mapValues { listOf(it.value) })
        return ScoreCommandExecutor(main.commands.analyzeAll(), functions).also { assertEquals(0, it.stackDepth) }
    }

    @Test fun forwardCallsAndIncomingParametersBindBeforeBodiesAndExecutePerInstance() {
        val main = compile("""
            data Box {
                @DataOnly
                const value = produce(initial)
                var next = this.value + 1
                constructor(initial: int){}
            }
            func produce(value: int): int {
                /say produced
                return value
            }
            func inspect(box: Box): int { return box.next; }
            func main(){
                var first = Box(3)
                var second = Box(6)
                external result = inspect(first)*10 + inspect(second)
            }
        """)
        val box = template()
        assertTrue(box.deferredFields.isEmpty())
        assertEquals(MCFPPBaseType.Int.typeId, box.scope.getVar("value")!!.type.typeId)
        assertTrue((box.scope.getVar("value") as MCInt).isDataOnly)
        val machine = execute(main)
        assertEquals(listOf("produced", "produced"), machine.messages)
        assertEquals(47, machine.read(main.scope.getVar("result") as MCInt))
    }

    @Test fun erasedReturnEvidenceAndOrdinaryConstructorValuesDoNotChangeFieldConstraints() {
        val main = compile("""
            data Box {
                var erased: any = produce(initial)
                const value = (this.erased as int) + 1
                constructor(initial: int){}
            }
            func produce(value: int): any { return value; }
            func main(){
                var first = Box(3)
                var second = Box(6)
                external result = first.value*10 + second.value
            }
        """)
        val value = template().scope.getVar("value")!!
        assertEquals(MCFPPBaseType.Int.typeId, value.type.typeId)
        assertFalse(value.symbol!!.mutable)
        assertEquals(47, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun inconsistentConstructorsAndPendingReadsDiagnoseWithoutChoosingTheFirstCall() {
        compile("""
            data Box {
                const value = initial
                constructor(initial: int){}
                constructor(initial: bool){}
            }
            func main(){}
        """, errors = 1)
        assertNull(template().scope.getVar("value"))
        for (fields in listOf("var value = this.value + 1;", "var value = this.later + 1; var later = 4;")) {
            compile("data Box {$fields}\nfunc main(){}", errors = 1)
            assertNull(template().scope.getVar("value"))
        }
    }

    @Test fun inheritedInferredFieldsAreAvailableToUncalledTemplateSignatures() {
        compile("""
            data Parent { var value = 4; }
            data Child: Parent {}
            func inspect(child: Child): int { return child.value; }
            func copy(child: Child): Child { return child; }
            func main(){}
        """)
        assertEquals(MCFPPBaseType.Int.typeId, template("Child").scope.getVar("value")!!.type.typeId)
        val copy = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("copy").single()
        assertNotNull(copy.returnVar.getMemberVar("value", top.mcfpp.model.Member.AccessModifier.PUBLIC).first)
    }

    @Test fun anonymousTemplateSignaturesAlsoWaitForForwardFunctionDeclarations() {
        compile("""
            func inspect(box: data { const value = produce(); }) : int { return box.value; }
            func produce(): int { return 4; }
            func main(){}
        """)
        val inspect = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("inspect").single()
        val parameter = inspect.scope.getVar("box")!!
        assertEquals(parameter.symbol!!.id, parameter.storageBinding!!.place.root)
        assertNotNull(parameter.getMemberVar("value", top.mcfpp.model.Member.AccessModifier.PUBLIC).first)
    }

    @Test fun compatibleConstOverridesKeepTheirOwnInitializerAnnotationsAndInstanceContract() {
        val main = compile("""
            @DataOnly
            data Box { var value = 4; }
            data Parent { const value: int = 1; }
            data Child: Parent {
                @DataOnly
                const value = 8
            }
            data Sibling: Parent {}
            func main(){var child=Child();external result=child.value;}
        """)
        assertTrue((template().scope.getVar("value") as MCInt).isDataOnly)
        val parent = template("Parent").scope.getVar("value") as MCInt
        val child = template("Child").scope.getVar("value") as MCInt
        val sibling = template("Sibling").scope.getVar("value") as MCInt
        assertNotSame(parent, child)
        assertNotSame(parent, sibling)
        assertSame(template("Child"), child.declaredParentTemplate)
        assertTrue(listOf(parent, child, sibling).all { it.isConst })
        assertFalse(parent.isDataOnly)
        assertFalse(sibling.isDataOnly)
        assertTrue(child.isDataOnly)
        assertTrue(parent.annotations.isEmpty())
        assertTrue(sibling.annotations.isEmpty())
        assertTrue(child.annotations.any { it is top.mcfpp.mni.annotation.DataOnly })
        val receiver = main.scope.getVar("child") as DataTemplateObject
        val member = receiver.getMemberVar("value", AccessModifier.PUBLIC).first as PropertyVar
        assertTrue(member.isConst)
        assertTrue(member.field.isConst)
        assertTrue((member.field as MCInt).isDataOnly)
        assertFalse(member.field.symbol!!.mutable)
        assertEquals(8, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun middleTemplateAnnotationsReachGrandchildrenWithoutChangingParentsOrSiblings() {
        val main = compile("""
            data Parent { var value: int = 4; }
            @DataOnly
            data Middle: Parent {}
            data Grand: Middle {}
            data Sibling: Parent {}
            func main(){var grand=Grand();external result=grand.value;}
        """)
        val fields = listOf("Parent", "Middle", "Grand", "Sibling").map { template(it).scope.getVar("value") as MCInt }
        assertEquals(listOf(false, true, true, false), fields.map { it.isDataOnly })
        fields.forEachIndexed { i, field -> fields.drop(i + 1).forEach { assertNotSame(field, it) } }
        val grand = main.scope.getVar("grand") as DataTemplateObject
        assertTrue(((grand.getMemberVar("value", AccessModifier.PUBLIC).first as PropertyVar).field as MCInt).isDataOnly)
        assertEquals(4, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun inheritedAnnotationObjectsAndTheirMutableGraphsAreIndependentThroughThreeLevels() {
        compile("""
            data Parent { @DataOnly var value: int = 4; }
            data Middle: Parent {}
            data Grand: Middle {}
            data Sibling: Parent {}
            func main(){}
        """)
        val fields = listOf("Parent", "Middle", "Grand", "Sibling").map { template(it).scope.getVar("value") as MCInt }
        assertTrue(fields.all { it.isDataOnly })
        val annotations = fields.map { it.annotations.single() }
        annotations.forEachIndexed { i, annotation -> annotations.drop(i + 1).forEach { assertNotSame(annotation, it) } }
        annotations[1].params.add("middle-only")
        annotations[1].parents.add(top.mcfpp.mni.annotation.DataOnly())
        assertTrue(annotations[0].params.isEmpty())
        assertTrue(annotations[2].params.isEmpty())
        assertTrue(annotations[3].params.isEmpty())
        assertTrue(annotations[0].parents.isEmpty())
        assertTrue(annotations[2].parents.isEmpty())
        assertTrue(annotations[3].parents.isEmpty())

        // Exercise the actual Serializable graph, including a mutable subclass payload,
        // repeated references and a parent cycle, through instance member access.
        val custom = MutableFieldAnnotation().apply { values.add("original"); parents.add(this) }
        fields[2].annotations.add(custom)
        fields[2].annotations.add(custom)
        val receiver = DataTemplateObject(template("Grand"), "model-grand")
        val instance = (receiver.getMemberVar("value", AccessModifier.PUBLIC).first as PropertyVar).field
        val copied = instance.annotations[1] as MutableFieldAnnotation
        assertNotSame(custom, copied)
        assertSame(copied, instance.annotations[2])
        assertSame(copied, copied.parents.single())
        copied.values.add("instance-only")
        copied.params.add("instance-only")
        assertEquals(listOf("original"), custom.values)
        assertTrue(custom.params.isEmpty())
    }

    @Test fun instancePropertiesAndCachedMetadataNeverRebindTheirDeclarationOrSibling() {
        val main = compile("""
            data Box { @DataOnly var value: int = 4; }
            func main(){var first=Box();var second=Box();first.value=9;external result=first.value*10+second.value;}
        """)
        val declaration = template().scope.getProperty("value")!!
        assertNull(declaration.parent)
        val first = main.scope.getVar("first") as DataTemplateObject
        val second = main.scope.getVar("second") as DataTemplateObject
        val left = first.getMemberVar("value", AccessModifier.PUBLIC).first as PropertyVar
        val right = second.getMemberVar("value", AccessModifier.PUBLIC).first as PropertyVar
        assertNotSame(declaration, left.property)
        assertNotSame(left.property, right.property)
        assertSame(first, left.property.parent)
        assertSame(second, right.property.parent)
        assertNull(declaration.parent)
        left.property.isFinal = true
        left.field.annotations.single().params.add("first-only")
        assertFalse(right.property.isFinal)
        assertFalse(declaration.isFinal)
        assertTrue(right.field.annotations.single().params.isEmpty())
        assertTrue(template().scope.getVar("value")!!.annotations.single().params.isEmpty())
        assertNotSame(first.instanceField.getProperty("value"), second.instanceField.getProperty("value"))
        assertNotSame(declaration, first.instanceField.getProperty("value"))
        assertSame(first, first.instanceField.getProperty("value")!!.parent)
        assertTrue(first.instanceField.getVar("value")!!.annotations.isNotEmpty())
        assertNotSame(template().scope.getVar("value")!!.annotations.single(), first.instanceField.getVar("value")!!.annotations.single())
        assertEquals(94, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun constantInstanceWritesAreRejectedAndDoNotReplaceTheLocalInitializer() {
        MCFPPStringTest.readFromString("""
            data Parent { const value: int = 1; }
            data Child: Parent { const value: int = 8; }
            func main(){var child=Child();child.value=9;external result=child.value;}
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0)
        assertTrue(template("Child").scope.getVar("value")!!.isConst)
        val main = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        assertEquals(8, execute(main).read(main.scope.getVar("result") as MCInt))
    }

    @Test fun incompatibleConstOverridesRemainRejectedWithoutChangingEitherDeclaration() {
        MCFPPStringTest.readFromString("""
            data Parent { var value: int = 1; }
            data Child: Parent { @DataOnly const value: int = 8; }
            func main(){}
        """.trimIndent(), version = "26.3")
        assertTrue(Project.errorCount > 0)
        val parent = template("Parent").scope.getVar("value") as MCInt
        val child = template("Child").scope.getVar("value") as MCInt
        assertFalse(parent.isConst)
        assertFalse(parent.isDataOnly)
        assertTrue(child.isConst)
        assertTrue(child.isDataOnly)
    }

    @Test fun nonSerializableAnnotationPayloadIsDiagnosedInsteadOfSharedOrCrashing() {
        compile("data Box{var value:int=4;}\nfunc main(){}")
        val field = template().scope.getVar("value")!!
        val annotation = NonSerializableFieldAnnotation()
        field.annotations.add(annotation)
        val receiver = DataTemplateObject(template(), "unsupported-annotation")
        val member = receiver.getMemberVar("value", AccessModifier.PUBLIC).first as PropertyVar
        assertTrue(Project.errorCount > 0)
        assertTrue(member.field.annotations.isEmpty())
        assertSame(annotation, field.annotations.single())
    }

    class MutableFieldAnnotation : top.mcfpp.model.annotation.Annotation("MutableField", "test") {
        val values = arrayListOf<String>()
        override fun forField(field: top.mcfpp.core.lang.Var<*>) {}
        override fun forFunction(function: Function) {}
        override fun forDataTemplate(data: DataTemplate) {}
    }

    class NonSerializableFieldAnnotation : top.mcfpp.model.annotation.Annotation("NonSerializableField", "test") {
        val payload = Any()
        override fun forField(field: top.mcfpp.core.lang.Var<*>) {}
        override fun forFunction(function: Function) {}
        override fun forDataTemplate(data: DataTemplate) {}
    }
}
