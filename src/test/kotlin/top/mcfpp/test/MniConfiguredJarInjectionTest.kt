package top.mcfpp.test

import top.mcfpp.CompileSettings
import top.mcfpp.Project
import top.mcfpp.annotations.MNIFunction
import top.mcfpp.annotations.MNIMacro
import top.mcfpp.io.info.MacroLoadingSnapshot
import top.mcfpp.mni.api.*
import top.mcfpp.model.function.Function
import top.mcfpp.model.function.NativeFunction
import top.mcfpp.model.scope.GlobalScope
import top.mcfpp.type.MCFPPBaseType
import top.mcfpp.type.MCFPPPrivateType
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MniConfiguredJarInjectionTest {
    @Test fun sourceDirectiveLoadsOnlyConfiguredJarProvidersDiscoversMacrosAndInvokesLegacyMni() {
        val root = Files.createTempDirectory("mcfpp-configured-mni-")
        val previousConfig = Project.config
        val previousIgnoreStdLib = CompileSettings.ignoreStdLib
        var projectLoader: URLClassLoader? = null
        try {
            val jar = buildProviderJar(root)
            val sourceRoot = Files.createDirectories(root.resolve("src"))
            val instruction = sourceRoot.resolve(".mcfpp")
            Files.writeString(instruction, "//> injectedBy jarfixture.JarProvider\n")
            Files.writeString(sourceRoot.resolve("macros.mcfpp"),
                "func jarAlias(value: int): int = jarfixture.JarProvider.jarMacro\n")
            assertEquals("", instruction.toFile().nameWithoutExtension)
            val configFile = root.resolve("mcfpp.toml")
            fun jsonPath(path: Path) = path.toAbsolutePath().toString().replace('\\', '/')
            top.mcfpp.test.util.writeProjectFixture(
                configFile,
                """
                {
                  "root": "${jsonPath(root)}",
                  "sourcePath": "${jsonPath(sourceRoot)}",
                  "namespace": "fixture.configured",
                  "version": "26.3",
                  "jars": ["${jsonPath(jar)}"]
                }
                """.trimIndent()
            )

            Project.config = Project.readConfig(top.mcfpp.CompilerOptions().apply { file = configFile.toFile() })
            Project.init()
            CompileSettings.ignoreStdLib = false
            assertFailsToLoadFromDefaultClasspath()

            Project.readProject()
            projectLoader = assertIs<URLClassLoader>(Project.classLoader)
            Project.indexType()

            assertTrue(GlobalScope.localNamespaces.getValue("fixture.configured").macroDeclarations().isEmpty(),
                "injectedBy must not expose macros before source declarations are indexed")
            Project.resolveField()

            val namespace = GlobalScope.localNamespaces.getValue("fixture.configured")
            val providerClass = Project.classLoader.loadClass("jarfixture.JarProvider")
            assertEquals(Project.classLoader, providerClass.classLoader)
            assertTrue(namespace.macroDeclarations().any { it.methodName == "jarMacro" })
            val bases = listOf(
                MCFPPBaseType.Int,
                MCFPPBaseType.Bool,
                MCFPPBaseType.Float,
                MCFPPBaseType.String,
                MCFPPBaseType.Object,
                MCFPPBaseType.Any,
                MCFPPPrivateType.Void
            )
            val prepared = MacroLoadingSnapshot.prepare(listOf(namespace), bases, loader = Project.classLoader, report = {})
            assertTrue(prepared.diagnostics.isEmpty(), prepared.diagnostics.joinToString { it.message })
            assertEquals(1, prepared.namespaces.getValue(namespace.identifier).find("jarAlias").size)
            assertTrue(prepared.namespaces.getValue(namespace.identifier).find("jarMacro").isEmpty())

            val legacy = assertIs<NativeFunction>(namespace.scope.functions.getValue("jarLegacy").single())
            assertFalse(namespace.scope.functions.containsKey("jarInvalid"))
            assertEquals(1, Project.errorCount, "The invalid legacy JVM signature should be diagnosed")
            val caller = Function("configured_jar_call", namespace.identifier, null)
            caller.runInFunction {
                val result = legacy.invoke(emptyList(), emptyList(), null)
                assertFalse(result.isError)
            }
            assertEquals(1, providerClass.getField("legacyCalls").getInt(null))
            assertEquals(1, Project.errorCount)
        } finally {
            Project.config = previousConfig
            Project.init()
            CompileSettings.ignoreStdLib = previousIgnoreStdLib
            projectLoader?.close()
            remove(root)
        }
    }

    private fun assertFailsToLoadFromDefaultClasspath() {
        try {
            Class.forName("jarfixture.JarProvider", false, javaClass.classLoader)
            error("The temporary provider must not be visible on the test runtime classpath")
        } catch (_: ClassNotFoundException) {
            // Provider visibility must come from Project's configured jar loader.
        }
    }

    private fun buildProviderJar(root: Path): Path {
        val sourceRoot = Files.createDirectories(root.resolve("provider-src"))
        val source = sourceRoot.resolve("jarfixture/JarProvider.java")
        Files.createDirectories(source.parent)
        Files.writeString(
            source,
            """
            package jarfixture;
            import top.mcfpp.annotations.MNIFunction;
            import top.mcfpp.annotations.MNIMacro;
                        import top.mcfpp.mni.api.Block;
            import top.mcfpp.mni.api.Expr;
            import top.mcfpp.mni.api.IntT;
            import top.mcfpp.mni.api.MacroContext;
            import top.mcfpp.mni.api.Value;

            public final class JarProvider {
                public static int legacyCalls = 0;

                @MNIMacro(normalParams = {"int"}, returnType = "int")
                public static Expr<IntT> jarMacro(MacroContext context, Value<IntT> value) {
                    throw new AssertionError("macro discovery must not invoke provider code");
                }

                @MNIFunction(identifier = "jarLegacy")
                public static void legacy() { legacyCalls++; }

                @MNIFunction(identifier = "jarInvalid")
                public static int invalidLegacy() { return 1; }
            }
            """.trimIndent()
        )
        val classes = Files.createDirectories(root.resolve("provider-classes"))
        val compiler = assertNotNull(ToolProvider.getSystemJavaCompiler(), "Tests require a JDK compiler")
        val classpath = listOf(
            MNIFunction::class.java,
            MNIMacro::class.java,
            MacroContext::class.java,
            Block::class.java,
            Expr::class.java,
            IntT::class.java,
            Value::class.java,
            kotlin.Unit::class.java
        ).map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }
            .distinct().joinToString(File.pathSeparator)
        val result = compiler.run(
            null, null, null, "-proc:none", "-classpath", classpath,
            "-d", classes.toString(), source.toString()
        )
        assertEquals(0, result, "The external Java provider should compile against the public MNI API")
        val jar = root.resolve("configured-provider.jar")
        JarOutputStream(Files.newOutputStream(jar)).use { output ->
            Files.walk(classes).use { files ->
                files.filter(Files::isRegularFile).sorted().forEach { file ->
                    val name = classes.relativize(file).joinToString("/") { it.toString() }
                    output.putNextEntry(JarEntry(name))
                    Files.copy(file, output)
                    output.closeEntry()
                }
            }
        }
        return jar
    }

    private fun remove(root: Path) {
        if (!Files.exists(root)) return
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
}
