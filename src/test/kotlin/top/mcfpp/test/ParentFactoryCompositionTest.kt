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

class ParentFactoryCompositionTest {
    @Test fun genericFactoriesComposeChildFieldsAndMethodsKeepTheirOrdinaryThis() {
        val main = compile(
            """
            data Parent<T>{var value:T;new(value:T){return{value};}}
            data Middle:Parent<int>{}
            data Child:Middle{
                var extra:int;
                new(value:int,extra:int){
                    var parent=Parent<int>.new(value);
                    return{value:parent.value,extra:extra};
                }
                func memberCopy():Parent<int>{return Parent<int>.new(this.value);}
            }
            func Child.extensionCopy():Parent<int>{return Parent<int>.new(this.value);}
            func main(){
                var child=Child.new(7,3);
                var member=child.memberCopy();
                var extension=child.extensionCopy();
                member.value=8;
                external result=child.value*1000+child.extra*100+member.value*10+extension.value;
            }
        """
        )
        assertEquals(0, Project.errorCount)
        assertEquals(7387, executePublishedFunctions(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
    }

    @Test fun ordinaryFactoryCallsDoNotRequireAnAncestorRelationship() {
        val main = compile("""
            data Parent{var value:int;new(source:{value:int},amount:int){return{value:source.value+amount};}}
            data Other{
                var value:int;
                new(value:int){return{value};}
                func copy():Parent{return Parent.new(this,2);}
            }
            func main(){
                var other=Other.new(5);
                var copied=other.copy();
                var direct=Parent.new(other,3);
                copied.value=9;
                external result=other.value*100+copied.value*10+direct.value;
            }
        """)
        assertEquals(0, Project.errorCount)
        assertEquals(598, executePublishedFunctions(main).read(assertIs<MCInt>(main.scope.getVar("result"))))
    }

    @Test fun factoriesCheckOrdinaryArgumentTypesAndDoNotSupplyAReceiver() {
        for (arguments in listOf("", "\"wrong\"")) {
            compile("""
                data Parent{var value:int;new(value:int){return{value};}}
                func main(){var value=Parent.new($arguments);}
            """)
            assertTrue(Project.errorCount > 0)
        }
    }

    @Test fun aChildFactoryMustReturnAllOfItsOwnRequiredFields() {
        compile("""
            data Parent{var value:int;new(value:int){return{value};}}
            data Child:Parent{
                var extra:int;
                new(value:int){return Parent.new(value);}
            }
            func main(){var child=Child.new(7);}
        """)
        assertTrue(Project.errorCount > 0)
    }

    @Test fun removedReceiverBoundFactorySyntaxIsRejectedInsideAnInstanceMethod() {
        val messages = compilerDiagnostics {
            compile(
                """
                data Parent{
                    var value:int;
                    new(source:{value:int},amount:int){return{value:source.value+amount};}
                }
                data Child:Parent{
                    new(value:int){return{value};}
                    func copy():Parent{return this.Parent.new(2);}
                }
                func main(){var child=Child.new(7);var copied=child.copy();}
            """
            )
        }.joinToString("\n")
        assertTrue(Project.errorCount > 0)
        assertTrue("Parent" in messages, messages)
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
