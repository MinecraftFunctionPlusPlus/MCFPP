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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import kotlin.test.Test

class GenericDeclarationContractTest {
    @Test fun declarationVariancePersistsAndChecksNestedMemberPositions() = isolated { output ->
        compile("""
            namespace fixture.variance;
            data Base {}
            data Child:Base {}
            data abstract Producer<out T as type> { abstract func read()->T; }
            data abstract Consumer<in T as type> { abstract func accept(value as T); }
            data abstract Nested<out T as type> { abstract func read()->Producer<T>; }
            data Invariant<T as type> {}
        """, output)
        assertEquals(0, Project.errorCount)
        val original = GlobalScope.getTemplate("fixture.variance", "Producer") as GenericDataTemplate
        Project.config.includes = arrayListOf(output.toString())
        compile("""
            import fixture.variance:*;
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
            "data abstract Bad<out T as type>{abstract func accept(value as T);}",
            "data abstract Bad<in T as type>{abstract func read()->T;}",
            "data Invariant<T as type>{}\ndata abstract Bad<out T as type>{abstract func read()->Invariant<T>;}",
            "data Invariant<T as type>{}\ndata Bad<out T as type>:Invariant<T>{}",
            "data Bad<out N as int>{}",
            "data Bad<T as type>{}\nfunc main(){var item as Bad<*>;}",
            "data Duplicate<T as type>{}\ndata Duplicate<N as int>{}",
            "object data Duplicate<T as type>{}\nobject data Duplicate<N as int>{}"
        )) {
            Project.config.includes = arrayListOf()
            compile(source)
            assertTrue(Project.errorCount > 0, source)
        }
    }

    @Test fun companionsPairActualArgumentsAndKeepPrivateOwnersAndIndependentState() = isolated { output ->
        val declarations = """
            namespace fixture.companions;
            data Box<N as int> {
                private value as int;
                constructor(value as int){this.value=value;}
                func read()->int{return this.value;}
                func companionRead()->int{return Box<N>.read();}
            }
            object data Box<N as int> {
                private value as int;
                constructor(){}
                func setValue(value as int){Box<N>.value=value;}
                private func read()->int{return Box<N>.value;}
                func inspect(item as Box<N>)->int{return item.value;}
            }
            data Shared<N as int>{func read()->int{return Shared.read();}}
            object data Shared {
                private value as int;
                constructor(){}
                func setValue(value as int){Shared.value=value;}
                private func read()->int{return Shared.value;}
            }
        """
        val main = """
            func main(){
                var local as int;
                local=3;
                local+=2;
                dynamic var localResult=local;
                var first=Box<4>(4);var second=Box<9>(9);var third=Box<4>(8);
                Box<4>.setValue(4);Box<9>.setValue(9);
                dynamic var firstResult=first.companionRead();
                dynamic var secondResult=second.companionRead();
                dynamic var firstOwn=Box<4>.inspect(first);
                dynamic var secondOwn=Box<9>.inspect(second);
                dynamic var thirdResult=third.read();
                Shared.setValue(7);
                var shared=Shared<2>();var otherShared=Shared<3>();
                dynamic var sharedResult=shared.read();dynamic var otherSharedResult=otherShared.read();
            }
        """
        compile("$declarations\n$main", output)
        assertEquals(0, Project.errorCount)
        val original = GlobalScope.getTemplate("fixture.companions", "Box")!!
        val sourceMain = GlobalScope.localNamespaces.getValue("fixture.companions").scope.functions.getValue("main").single()
        val sourceFirst = (sourceMain.scope.getVar("first") as DataTemplateObject).templateType
        val sourceFirstId = sourceFirst.getType().typeId
        val sourceCompanionId = sourceFirst.companionObject!!.getType().typeId
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
        compile("import fixture.companions:*;\n$main")
        assertEquals(0, Project.errorCount)
        assertNotSame(original, GlobalScope.getTemplate("fixture.companions", "Box"))
        val function = GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        val first = (function.scope.getVar("first") as DataTemplateObject).templateType as CompiledGenericDataTemplate
        val second = (function.scope.getVar("second") as DataTemplateObject).templateType as CompiledGenericDataTemplate
        assertSame(first, (function.scope.getVar("third") as DataTemplateObject).templateType)
        assertNotSame(first.companionObject, second.companionObject)
        assertEquals(AccessModifier.PRIVATE, first.getAccess(first.companionObject!!))
        assertEquals(AccessModifier.PUBLIC, first.getAccess(second.companionObject!!))
        assertEquals(sourceFirstId, first.getType().typeId)
        assertEquals(sourceCompanionId, first.companionObject!!.getType().typeId)
        assertNotSame(sourceFirst, first)
        val shared = (function.scope.getVar("shared") as DataTemplateObject).templateType
        val otherShared = (function.scope.getVar("otherShared") as DataTemplateObject).templateType
        assertSame(shared.companionObject, otherShared.companionObject)
        assertSame(first.companionObject, first.companionObject!!.scope.getVar("value")!!.declaredParentTemplate)
        initializeAndExecute(function, output.resolve("fresh"))
        for (source in listOf(
            "import fixture.companions:*;\nfunc main(){Box<4>.value;}",
            "import fixture.companions:*;\nfunc main(){var item=Box<9>(9);Box<4>.inspect(item);}",
            "data Bad<T as type>{}\nobject data Bad<N as int>{}",
            "data Bad<T as type>{}\nobject data Bad<T as type,N as int>{}"
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
