package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.ProjectConfig
import top.mcfpp.core.lang.MCInt
import top.mcfpp.model.function.Function
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.compilerDiagnostics
import top.mcfpp.test.util.executePublishedFunctions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ParentQualifiedFactoryTest {
    @Test fun genericIndirectParentFactoryBindsTheReceiverInMembersAndExtensions() {
        val main = compile(
            """
            data Parent<T>{var value:T;new(source:{value:T}){return{value:source.value};}}
            data Middle:Parent<int>{}
            data Child:Middle{
                new(value:int){return{value};}
                func memberCopy():Parent<int>{return this.Parent.new();}
            }
            func Child.extensionCopy():Parent<int>{return this.Parent.new();}
            func main(){
                var child=Child(7);
                var member=child.memberCopy();
                var extension=child.extensionCopy();
                external result=member.value*10+extension.value;
            }
        """
        )
        assertEquals(0, Project.errorCount)
        assertEquals(77, executePublishedFunctions(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
        val child = GlobalScope.getTemplate("default.test", "Child")!!
        val parent = child.nominalAncestorsNamed("Parent").single()
        assertTrue(parent.identifier != "Parent", "The bound generic declaration has an emitted specialization name")
    }

    @Test fun differentBindingsWithTheSameParentSourceNameRequireAnUnambiguousQualifier() {
        val messages = compilerDiagnostics {
            compile(
                """
                data Parent<T>{var value:int;new(source:{value:int}){return{value:source.value};}}
                data Child:Parent<int>,Parent<bool>{
                    new(value:int){return{value};}
                    func copy(){this.Parent.new();}
                }
                func main(){var child=Child(7);child.copy();}
            """
            )
        }.joinToString("\n")
        assertTrue(Project.errorCount > 0)
        assertTrue("Nominal parent qualifier 'Parent' is ambiguous" in messages, messages)
    }

    @Test fun receiverBoundParentFactoryRequiresAThisContext() {
        val messages = compilerDiagnostics {
            compile(
                """
                data Parent{new(source:{}){return{};}}
                func copy():Parent{return this.Parent.new();}
                func main(){copy();}
            """
            )
        }.joinToString("\n")
        assertTrue(Project.errorCount > 0)
        assertTrue("this" in messages, messages)
    }

    private fun compile(source: String): Function {
        val config = Project.config
        val ignore = CompileSettings.ignoreStdLib
        try {
            Project.config = ProjectConfig()
            CompileSettings.ignoreStdLib = true
            MCFPPStringTest.readFromString(source.trimIndent(), version = "26.3")
            return GlobalScope.localNamespaces.getValue("default.test").scope.functions.getValue("main").single()
        } finally { Project.config = config; CompileSettings.ignoreStdLib = ignore }
    }
}
