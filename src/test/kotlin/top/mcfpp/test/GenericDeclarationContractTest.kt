package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.core.lang.MCInt
import top.mcfpp.core.lang.obj.DataTemplateObject
import top.mcfpp.io.DatapackCreator
import top.mcfpp.command.Commands
import top.mcfpp.model.compound.CompiledGenericDataTemplate
import top.mcfpp.model.compound.DeclarationVariance
import top.mcfpp.model.compound.GenericDataTemplate
import top.mcfpp.model.Member.AccessModifier
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ScoreCommandExecutor
import top.mcfpp.test.util.compilerDiagnostics
import top.mcfpp.type.TypeId
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class GenericDeclarationContractTest {
    @Test fun declarationVariancePersistsAndChecksNestedMemberPositions() = isolated { output ->
        compile("""
            namespace fixture.variance
            data Base {}
            data Child:Base {}
            abstract data Producer<out T> { abstract func read():T; }
            abstract data Consumer<in T> { abstract func accept(value: T); }
            abstract data Nested<out T> { abstract func read():Producer<T>; }
            data Invariant<T> {}
        """, output)
        assertEquals(0, Project.errorCount)
        val original = GlobalScope.getTemplate("fixture.variance", "Producer") as GenericDataTemplate
        Project.config.includes = arrayListOf(output.toString())
        compile("""
            import fixture.variance:*
            func main() {}
        """)
        assertEquals(0, Project.errorCount)
        val producer = GlobalScope.getTemplate("fixture.variance", "Producer") as GenericDataTemplate
        assertNotSame(original, producer)
        assertEquals(DeclarationVariance.OUT, producer.readOnlyParams.single().variance)
        val base = GlobalScope.getTemplate("fixture.variance", "Base")!!.getType()
        val child = GlobalScope.getTemplate("fixture.variance", "Child")!!.getType()
        fun specialize(name: String, type: top.mcfpp.type.MCFPPType) =
            (GlobalScope.getTemplate("fixture.variance", name) as GenericDataTemplate)
                .compile(listOf(top.mcfpp.core.lang.MCFPPTypeVar(type)))!!.getType()
        assertTrue(specialize("Producer", child).isSubOf(specialize("Producer", base)))
        assertTrue(specialize("Consumer", base).isSubOf(specialize("Consumer", child)))
        assertFalse(specialize("Invariant", child).isSubOf(specialize("Invariant", base)))
        for (source in listOf(
            "abstract data Bad<out T>{abstract func accept(value: T);}",
            "abstract data Bad<in T>{abstract func read():T;}",
            "data Invariant<T>{}\ndata abstract Bad<out T>{abstract func read():Invariant<T>;}",
            "data Invariant<T>{}\ndata Bad<out T>:Invariant<T>{}",
            "data Bad<out N: int>{}",
            "data Bad<T>{}\nfunc main(){var item: Bad<*>;}",
            "data Duplicate<T>{}\ndata Duplicate<N: int>{}",
            "object data Duplicate<T>{}\nobject data Duplicate<N: int>{}"
        )) {
            Project.config.includes = arrayListOf()
            compile(source)
            assertTrue(Project.errorCount > 0, source)
        }
    }

    @Test fun companionsPairActualArgumentsAndKeepPrivateOwnersAndIndependentState() = isolated { output ->
        val declarations = """
            namespace fixture.companions
            data Box<N: int> {
                private var value: int
                constructor(value: int){this.value=value;}
                func read():int{return this.value;}
                func companionRead():int{return Box<N>.read();}
            }
            object data Box<N: int> {
                private var value: int
                constructor(){}
                func setValue(value: int){Box<N>.value=value;}
                private func read():int{return Box<N>.value;}
                func inspect(item: Box<N>):int{return item.value;}
            }
            data Shared<N: int>{func read():int{return Shared.read();}}
            object data Shared {
                private var value: int
                constructor(){}
                func setValue(value: int){Shared.value=value;}
                private func read():int{return Shared.value;}
            }
        """
        val main = """
            func main(){
                var local: int
                local=3
                local+=2
                external localResult=local
                var first=Box<4>(4);var second=Box<9>(9);var third=Box<4>(8)
                Box<4>.setValue(4);Box<9>.setValue(9)
                external firstResult=first.companionRead()
                external secondResult=second.companionRead()
                external firstOwn=Box<4>.inspect(first)
                external secondOwn=Box<9>.inspect(second)
                external thirdResult=third.read()
                Shared.setValue(7)
                var shared=Shared<2>();var otherShared=Shared<3>()
                external sharedResult=shared.read();external otherSharedResult=otherShared.read()
            }
        """
        compile("$declarations\n$main", output)
        assertEquals(0, Project.errorCount)
        val original = GlobalScope.getTemplate("fixture.companions", "Box")!!
        val sourceMain = GlobalScope.localNamespaces.getValue("fixture.companions").scope.functions.getValue("main").single()
        val sourceFirst = (sourceMain.scope.getVar("first") as DataTemplateObject).templateType
        val sourceFirstId = assertIs<TypeId.Specialized>(sourceFirst.getType().typeId)
        val sourceCompanionId = assertIs<TypeId.Specialized>(sourceFirst.companionObject!!.getType().typeId)
        fun initializeAndExecute(mainFunction: Function, executionOutput: Path): ScoreCommandExecutor {
            val companions = listOf("first", "second", "shared").map { name ->
                (mainFunction.scope.getVar(name) as DataTemplateObject).templateType.companionObject!!
            }
            val initializer = Function("initialize", mainFunction.namespace, null)
            initializer.runInFunction {
                Function.addCommand(Commands.stackIn())
                companions.forEach { it.constructors.single().invoke(emptyList(), null) }
                Function.addCommand(Commands.stackOut())
            }
            mainFunction.commands.addAll(0, initializer.commands)
            assertEquals(0, Project.errorCount)
            val machine = execute(mainFunction, executionOutput)
            for ((name, expected) in listOf("localResult" to 5, "firstResult" to 4, "secondResult" to 9,
                "firstOwn" to 4, "secondOwn" to 9, "thirdResult" to 8,
                "sharedResult" to 7, "otherSharedResult" to 7)) {
                assertEquals(expected, machine.read(mainFunction.scope.getVar(name) as MCInt))
            }
            return machine
        }
        initializeAndExecute(sourceMain, output.resolve("source"))
        Project.config.includes = arrayListOf(output.toString())
        val messages = compilerDiagnostics { compile("import fixture.companions:*;\n$main") }
        assertEquals(0, Project.errorCount, messages.joinToString("\n"))
        val receiving = assertNotNull(GlobalScope.getTemplate("fixture.companions", "Box"))
        assertNotSame(original, receiving)
        val function = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val first = (function.scope.getVar("first") as DataTemplateObject).templateType as CompiledGenericDataTemplate
        val second = (function.scope.getVar("second") as DataTemplateObject).templateType as CompiledGenericDataTemplate
        assertSame(first, (function.scope.getVar("third") as DataTemplateObject).templateType)
        assertNotSame(first.companionObject, second.companionObject)
        assertEquals(AccessModifier.PRIVATE, first.getAccess(first.companionObject!!))
        assertEquals(AccessModifier.PUBLIC, first.getAccess(second.companionObject!!))
        assertSame(receiving, first.originTemplate)
        val receivingCompanion = assertNotNull(receiving.companionObject)
        val companion = assertIs<CompiledGenericDataTemplate>(first.companionObject)
        assertSame(receivingCompanion, companion.originTemplate)
        assertEquals(TypeId.Specialized(receiving.getType().typeId, sourceFirstId.arguments), first.getType().typeId)
        assertEquals(TypeId.Specialized(receivingCompanion.getType().typeId, sourceCompanionId.arguments), companion.getType().typeId)
        assertNotEquals(sourceFirstId, first.getType().typeId, "Receiving Box has a fresh nominal identity with the original readonly arguments")
        assertNotEquals(sourceCompanionId, companion.getType().typeId, "Receiving companion has a fresh nominal identity with the original readonly arguments")
        assertNotSame(sourceFirst.companionObject, companion)
        assertNotSame(sourceFirst, first)
        val shared = (function.scope.getVar("shared") as DataTemplateObject).templateType
        val otherShared = (function.scope.getVar("otherShared") as DataTemplateObject).templateType
        assertSame(shared.companionObject, otherShared.companionObject)
        assertSame(first.companionObject, first.companionObject!!.scope.getVar("value")!!.declaredParentTemplate)
        initializeAndExecute(function, output.resolve("fresh"))
        for (source in listOf(
            "import fixture.companions:*;\nfunc main(){Box<4>.value;}",
            "import fixture.companions:*;\nfunc main(){var item=Box<9>(9);Box<4>.inspect(item);}",
            "data Bad<T>{}\nobject data Bad<N: int>{}",
            "data Bad<T>{}\nobject data Bad<T,N: int>{}"
        )) {
            compile(source)
            assertTrue(Project.errorCount > 0, source)
        }
    }

    private fun compile(source: String, output: Path? = null) {
        MCFPPStringTest.readFromString(source.trimIndent(), targetPath = output?.toString(), version = "26.3")
    }

    private fun execute(main: Function, output: Path): ScoreCommandExecutor {
        val target = output.resolve("consumer")
        DatapackCreator.createDatapack(target.toString())
        val data = target.resolve(Project.config.name).resolve("data")
        val functions = LinkedHashMap<String, List<String>>()
        Files.walk(data).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".mcfunction") }.forEach { file ->
                val path = data.relativize(file)
                if (path.nameCount >= 3 && path.getName(1).toString() == "function") {
                    functions["${path.getName(0)}:${path.subpath(2, path.nameCount).joinToString("/").removeSuffix(".mcfunction")}"] = Files.readAllLines(file)
                }
            }
        }
        return ScoreCommandExecutor(functions.getValue(main.namespaceID.toString()), functions).also {
            assertEquals(0, it.stackDepth)
        }
    }

    private fun isolated(action: (Path) -> Unit) {
        val output = Files.createTempDirectory("mcfpp-generic-contract-")
        val config = Project.config
        val settings = listOf(CompileSettings.isDebug, CompileSettings.ignoreStdLib, CompileSettings.isLib,
            CompileSettings.printAll, CompileSettings.foldIRConstants)
        val maxInline = CompileSettings.maxWhileInline
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = true
            action(output)
        } finally {
            Project.config = config
            CompileSettings.isDebug = settings[0]
            CompileSettings.ignoreStdLib = settings[1]
            CompileSettings.isLib = settings[2]
            CompileSettings.printAll = settings[3]
            CompileSettings.foldIRConstants = settings[4]
            CompileSettings.maxWhileInline = maxInline
            output.toFile().deleteRecursively()
        }
    }
}
