package top.mcfpp.test.entity

import top.mcfpp.Project
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.test.util.MCFPPStringTest
import top.mcfpp.test.util.ContextCommandExecutor
import top.mcfpp.io.DatapackCreator
import top.mcfpp.io.LibBinWriter
import java.nio.file.Files
import top.mcfpp.util.LogProcessor
import kotlin.test.*

class EntityTemplateTest {
    private val declarations = """
        namespace fixture.entitytemplates
        import mcfpp.minecraft.entity:*
        entity StoneDisplay: BlockDisplay {
            var initialCount: int = 10
            entity var count: int = initialCount
            private entity var consumed: int = 0
            constructor(n: nbt): BlockDisplay({block_state: {Name: "minecraft:stone"}}.merge(n))
            entity func consume() {
                this.count -= 1
                this.consumed += 1
            }
            entity func consumedCount(): int { return this.consumed }
            entity func sayImStone() {
                /say Im stone
            }
            func configure(n: int) { this.initialCount = n }
        }
        entity PolishedStoneDisplay: StoneDisplay {
            constructor(n: nbt): StoneDisplay(n)
        }
    """.trimIndent()

    @Test fun templatesConstructAndSummonTypedReferences() {
        MCFPPStringTest.readFromString(declarations + "\n" + """
            func keep(ref: EntityRef<StoneDisplay>): EntityRef<BlockDisplay> { return ref }
            func inherited() {
                var child = PolishedStoneDisplay({})
                var ref = child.summon()
                keep(ref)
            }
            func main() {
                var template = StoneDisplay({block_state: {Name: "minecraft:stone"}})
                template.configure(20)
                var a = template.summon()
                var b = template.summon()
                a.consume()
                a.sayImStone()
                tp(a, 1 2 3)
                var count: int = a.count
                keep(b)
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val template = GlobalScope.localNamespaces.getValue("fixture.entitytemplates").scope.getTemplate("StoneDisplay")!!
        assertTrue(template.isEntityTemplate)
        assertTrue(template.scope.getVar("count")!!.isEntityMember)
        assertFalse(template.scope.getVar("initialCount")!!.isEntityMember)
    }

    @Test fun summonCanBeOverriddenAndMergesCallerDataLast() {
        MCFPPStringTest.readFromString("""
            namespace fixture.entityoverride
            import mcfpp.minecraft.entity:*
            entity BaseStone: BlockDisplay {}
            entity CustomStone: BaseStone {
                override func summon(d: nbt): EntityRef<CustomStone> {
                    return super.summon({foo: 1, nested: {keep: 2, replace: 3}}.merge(d))
                }
                entity func speak() {
                    /say custom
                }
            }
            entity InheritedStone: CustomStone {}
            func spawn(d: nbt) {
                var ref = CustomStone().summon(d)
                ref.speak()
            }
            func main() {
                spawn({foo: 4, nested: {replace: 5}})
                var child = InheritedStone().summon({foo: 6})
                child.speak()
                var empty = CustomStone().summon()
                empty.speak()
                var plain = BaseStone().summon({foo: 7})
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val machine = execute("fixture.entityoverride:main")
        assertEquals(4, machine.entities.size)
        val first = machine.entities.first().name
        val second = machine.entities[1].name
        assertEquals(top.mcfpp.nbt.tags.primitive.IntTag(4), machine.readEntity(first, "foo"))
        assertEquals(top.mcfpp.nbt.tags.primitive.IntTag(2), machine.readEntity(first, "nested.keep"))
        assertEquals(top.mcfpp.nbt.tags.primitive.IntTag(5), machine.readEntity(first, "nested.replace"))
        assertEquals(top.mcfpp.nbt.tags.primitive.IntTag(6), machine.readEntity(second, "foo"))
        assertEquals(top.mcfpp.nbt.tags.primitive.IntTag(2), machine.readEntity(second, "nested.keep"))
        assertEquals(top.mcfpp.nbt.tags.primitive.IntTag(1), machine.readEntity(machine.entities[2].name, "foo"))
        assertEquals(top.mcfpp.nbt.tags.primitive.IntTag(7), machine.readEntity(machine.entities[3].name, "foo"))
        assertEquals(3, machine.messages.count { it == "custom" })
    }

    private fun execute(entry: String): ContextCommandExecutor {
        val output = Files.createTempDirectory("mcfpp-entity-summon-")
        DatapackCreator.createDatapack(output.toString())
        val functions = linkedMapOf<String, List<String>>()
        Files.walk(output).use { paths -> paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfunction") }.forEach { file ->
            val names = output.relativize(file).map { it.toString() }
            val data = names.indexOf("data")
            if (data >= 0 && names.getOrNull(data + 2) == "function")
                functions["${names[data + 1]}:${names.drop(data + 3).joinToString("/").removeSuffix(".mcfunction")}"] = Files.readAllLines(file)
        } }
        return ContextCommandExecutor(listOf("function $entry"), functions,
            macros = Project.macroFunction.toMap(), commandBudget = 60_000)
    }

    @Test fun nbtMergeReturnsAnIndependentRecursiveCopy() {
        MCFPPStringTest.readFromString("""
            namespace fixture.nbtmerge
            func merge(base: nbt, incoming: nbt) {
                var memberResult = base.merge(incoming)
                /data modify storage fixture:merge original set value ${'$'}{base}
                /data modify storage fixture:merge incoming set value ${'$'}{incoming}
                /data modify storage fixture:merge member set value ${'$'}{memberResult}
            }
            func main() {
                merge({nested: {keep: 1, replace: 2}, values: [1, 2]}, {nested: {replace: 3}, values: [4]})
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val machine = execute("fixture.nbtmerge:main")
        assertEquals(top.mcfpp.nbt.tags.primitive.IntTag(2), machine.readStorage("fixture:merge", "original.nested.replace"))
        assertEquals(top.mcfpp.nbt.tags.primitive.IntTag(3), machine.readStorage("fixture:merge", "incoming.nested.replace"))
        assertEquals(2, (machine.readStorage("fixture:merge", "original.values").value as List<*>).size)
        assertEquals(top.mcfpp.nbt.tags.primitive.IntTag(1), machine.readStorage("fixture:merge", "member.nested.keep"))
        assertEquals(top.mcfpp.nbt.tags.primitive.IntTag(3), machine.readStorage("fixture:merge", "member.nested.replace"))
        assertEquals(top.mcfpp.nbt.tags.primitive.IntTag(4), machine.readStorage("fixture:merge", "member.values[0]"))
        assertEquals(1, (machine.readStorage("fixture:merge", "member.values").value as List<*>).size)
    }

    @Test fun templateAndEntityMembersRequireTheCorrectReceiver() {
        for (invalid in listOf("template.consume()", "template.count = 1", "ref.configure(1)", "ref.initialCount = 1", "ref.consumed = 1")) {
            val diagnostics = LogProcessor.beginDiagnosticCapture().use { capture ->
                MCFPPStringTest.readFromString(declarations + "\n" + """
                    func main() {
                        var template = StoneDisplay({})
                        var ref = template.summon()
                        $invalid
                    }
                """.trimIndent(), version = "26.3")
                capture.seal().diagnostics
            }
            assertTrue(Project.errorCount > 0, invalid)
            assertTrue(diagnostics.isNotEmpty(), invalid)
        }
    }

    @Test fun summonsCopyCurrentTemplateStateAndMethodsUseTheReferencedEntity() {
        MCFPPStringTest.readFromString(declarations + "\n" + """
            func main() {
                var template = StoneDisplay({data: {qwq: 1}})
                template.initialCount = 20
                var a = template.summon()
                template.initialCount = 30
                var b = template.summon()
                a.consume()
                a.sayImStone()
                var aCount = a.count
                var bCount = b.count
                var consumed = a.consumedCount()
                /scoreboard players set a fixture ${'$'}{aCount}
                /scoreboard players set b fixture ${'$'}{bCount}
                /scoreboard players set consumed fixture ${'$'}{consumed}
                if (a.checkType<StoneDisplay>() && a.checkType<BlockDisplay>()) {
                    /say types-good
                }
            }
        """.trimIndent(), version = "26.3")
        assertEquals(0, Project.errorCount)
        val output = Files.createTempDirectory("mcfpp-entity-template-")
        DatapackCreator.createDatapack(output.toString())
        val functions = linkedMapOf<String, List<String>>()
        Files.walk(output).use { paths -> paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".mcfunction") }.forEach { file ->
            val names = output.relativize(file).map { it.toString() }
            val data = names.indexOf("data")
            if (data >= 0 && names.getOrNull(data + 2) == "function")
                functions["${names[data + 1]}:${names.drop(data + 3).joinToString("/").removeSuffix(".mcfunction")}"] = Files.readAllLines(file)
        } }
        val machine = ContextCommandExecutor(listOf("function fixture.entitytemplates:main"), functions,
            macros = Project.macroFunction.toMap(), commandBudget = 60_000)
        assertEquals(19, machine.values["a fixture"])
        assertEquals(30, machine.values["b fixture"])
        assertEquals(1, machine.values["consumed fixture"])
        assertEquals(2, machine.entities.count { it.type == "minecraft:block_display" })
        for (entity in machine.entities) assertEquals(top.mcfpp.nbt.tags.primitive.IntTag(1), machine.readEntity(entity.name, "data.qwq"))
        for (entity in machine.entities) assertEquals(top.mcfpp.nbt.tags.primitive.StringTag("minecraft:stone"), machine.readEntity(entity.name, "block_state.Name"))
        assertContains(machine.messages, "Im stone")
        assertContains(machine.messages, "types-good")
    }

    @Test fun exportedTemplatesKeepEntityMembersAndParentConstructorDelegation() {
        val includes = Project.config.includes
        try {
            MCFPPStringTest.readFromString(declarations + "\n" + """
                func spawn(n: nbt): EntityRef<StoneDisplay> {
                    return StoneDisplay(n).summon()
                }
            """.trimIndent(), version = "26.3")
            assertEquals(0, Project.errorCount)
            val output = Files.createTempDirectory("mcfpp-entity-template-library-")
            LibBinWriter.write(output.toString())
            Project.config.includes = arrayListOf(output.toString())
            MCFPPStringTest.readFromString("""
                namespace fixture.consumer
                import fixture.entitytemplates:*
                func main() {
                    var ref = spawn({})
                    ref.consume()
                    var template = PolishedStoneDisplay({})
                    var another = template.summon()
                    another.sayImStone()
                }
            """.trimIndent(), version = "26.3")
            assertEquals(0, Project.errorCount)
        } finally {
            Project.config.includes = includes
        }
    }

    @Test fun exportedOverridesRetainTheGeneratedParentSummon() {
        val includes = Project.config.includes
        try {
            MCFPPStringTest.readFromString("""
                namespace fixture.entityoverrideexport
                import mcfpp.minecraft.entity:*
                entity Base: BlockDisplay {}
                entity Child: Base {
                    override func summon(d: nbt): EntityRef<Child> {
                        return super.summon({foo: 1}.merge(d))
                    }
                }
                func make(): EntityRef<Child> { return Child().summon({}) }
            """.trimIndent(), version = "26.3")
            assertEquals(0, Project.errorCount)
            val output = Files.createTempDirectory("mcfpp-entity-override-library-")
            LibBinWriter.write(output.toString())
            Project.config.includes = arrayListOf(output.toString())
            MCFPPStringTest.readFromString("""
                namespace fixture.entityoverrideconsumer
                import fixture.entityoverrideexport:*
                func main() { var ref = Child().summon({bar: 2}) }
            """.trimIndent(), version = "26.3")
            assertEquals(0, Project.errorCount)
        } finally {
            Project.config.includes = includes
        }
    }
}
