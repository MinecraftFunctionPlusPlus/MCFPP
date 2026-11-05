plugins {
    java
    id("org.teavm") version "0.16.0"
}

repositories {
    mavenCentral()
    maven("https://libraries.minecraft.net")
    maven("https://nexus.mcfpp.top/repository/maven-releases/")
}

dependencies {
    compileOnly("org.teavm:teavm-core:0.16.0")
    compileOnly(files(rootProject.configurations.named("compileClasspath")))
    implementation("org.bouncycastle:bcprov-jdk18on:1.83")
    implementation(project(":"))
}

// Embed the complete library for the Playground's fixed Minecraft target, plus translations.
// A generated inventory makes ClassLoader streams usable without scanning JARs at runtime.
val resourceInventory = layout.buildDirectory.dir("generated/browser-resources")
val prepareBrowserResources by tasks.registering {
    dependsOn(rootProject.tasks.named("processResources"))
    inputs.dir(rootProject.layout.buildDirectory.dir("resources/main"))
    outputs.dir(resourceInventory)
    doLast {
        val root = rootProject.layout.buildDirectory.dir("resources/main").get().asFile
        val names = fileTree(root) {
            include("datapack/targets/1.21.8/**", "lang/**")
        }.files.map { it.relativeTo(root).invariantSeparatorsPath }.sorted()
        val output = resourceInventory.get().file("browser-resources.txt").asFile
        output.parentFile.mkdirs()
        output.writeText(names.joinToString("\n", postfix = "\n"))
    }
}
sourceSets.main { resources.srcDir(resourceInventory) }
tasks.processResources { dependsOn(prepareBrowserResources) }
val unicodeResources = layout.buildDirectory.dir("generated/unicode-resources")
val prepareUnicodeNames by tasks.registering(JavaExec::class) {
    dependsOn(tasks.compileJava)
    classpath = files(sourceSets.main.get().output.classesDirs)
    mainClass.set("top.mcfpp.browser.UnicodeNameGenerator")
    val output = unicodeResources.get().file("unicode-names.txt")
    outputs.file(output)
    args(output.asFile.absolutePath)
}
sourceSets.main { resources.srcDir(unicodeResources) }
tasks.processResources { dependsOn(prepareUnicodeNames) }

val nativeRoots = layout.buildDirectory.file("generated/native-roots/native-roots.txt")
val nativeSources = layout.buildDirectory.dir("generated/native-sources")
val generator by sourceSets.creating {
    val rootMain = rootProject.extensions.getByType<SourceSetContainer>().named("main").get()
    compileClasspath += rootMain.output + rootProject.configurations.named("compileClasspath").get()
    runtimeClasspath += rootMain.output + rootProject.configurations.named("runtimeClasspath").get()
}
tasks.named(generator.compileJavaTaskName) { dependsOn(rootProject.tasks.named("classes")) }
val prepareNativeRoots by tasks.registering(JavaExec::class) {
    dependsOn(tasks.named(generator.classesTaskName))
    classpath = generator.runtimeClasspath
    mainClass.set("top.mcfpp.browser.NativeRootsGenerator")
    val binary = rootProject.file("src/main/resources/datapack/targets/1.21.8/bin.mclib")
    val plan = rootProject.layout.buildDirectory.file("generated/builtin-plans/datapack/targets/1.21.8/import-plan.bin")
    inputs.files(binary, plan)
    inputs.files(generator.output.classesDirs)
    outputs.file(nativeRoots)
    val source = nativeSources.get().file("top/mcfpp/browser/GeneratedNativeRoots.java")
    outputs.file(source)
    args(binary.absolutePath, plan.get().asFile.absolutePath, nativeRoots.get().asFile.absolutePath, source.asFile.absolutePath)
}
tasks.named("generateWasmGC") {
    dependsOn(prepareNativeRoots)
    // TeaVM collects source directories for debug information, including ANTLR's test output.
    // Order it only when tests are in the same task graph; a browser build needs no test grammar.
    mustRunAfter(rootProject.tasks.named("generateTestGrammarSource"))
}
sourceSets.main { java.srcDir(nativeSources) }
tasks.compileJava { dependsOn(prepareNativeRoots) }

val benchmark by sourceSets.creating {
    compileClasspath += rootProject.extensions.getByType<SourceSetContainer>().named("main").get().output +
        rootProject.configurations.named("compileClasspath").get()
    runtimeClasspath += rootProject.extensions.getByType<SourceSetContainer>().named("main").get().output +
        rootProject.configurations.named("runtimeClasspath").get()
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
tasks.named(benchmark.compileJavaTaskName) { dependsOn(tasks.classes) }
tasks.register<JavaExec>("benchmarkKernel") {
    group = "verification"
    description = "Measure the native JVM kernel using the browser acceptance sources."
    dependsOn(tasks.named(benchmark.classesTaskName))
    classpath = benchmark.runtimeClasspath
    mainClass.set("top.mcfpp.browser.benchmark.NativeBenchmark")
    args(file("benchmarks/cases.json").absolutePath,
        layout.buildDirectory.file("reports/browser/native-results.json").get().asFile.absolutePath)
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }

teavm.wasmGC {
    mainClass.set("top.mcfpp.browser.BrowserCompiler")
    targetFileName.set("mcfpp.wasm")
    modularRuntime.set(true)
    debugInformation.set(true)
    obfuscated.set(false)
    outOfProcess.set(true)
    processMemory.set(6144)
    preservedClasses.addAll(providers.provider { nativeRoots.get().asFile.readLines() })
}
