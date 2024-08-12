import org.gradle.kotlin.dsl.cpp
import java.nio.file.Files
import java.util.Optional

plugins {
    kotlin("jvm") version "1.8.0"
    groovy
    application
    antlr
    id("org.jetbrains.dokka") version "1.8.10"
    java
    cpp
}

group = "top.mcfpp"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://maven.aliyun.com/nexus/content/groups/public/")
    maven("https://jitpack.io/")
    maven("https://libraries.minecraft.net")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.apache.groovy:groovy-all:4.0.11")
    implementation("com.alibaba.fastjson2:fastjson2:2.0.28")
    implementation("org.apache.logging.log4j:log4j-api:2.20.0")
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")
    implementation("org.openjdk.nashorn:nashorn-core:15.4")
    implementation("com.github.Querz:NBT:6.1")
    implementation("com.mojang:brigadier:1.0.18")
    implementation("org.commonmark:commonmark:0.20.0")
    implementation("org.commonmark:commonmark-ext-heading-anchor:0.21.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.20.0")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.21.0")
    implementation("fr.brouillard.oss:commonmark-ext-notifications:1.1.0")
    antlr("org.antlr:antlr4:4.12.0")
    implementation(kotlin("reflect"))
    testImplementation(kotlin("script-runtime"))
    implementation("com.google.guava:guava:33.2.0-jre")
}

tasks.test {
    useJUnitPlatform()
}

tasks.generateGrammarSource {
    mkdir("build")
    arguments = arguments +
            listOf("-visitor", "-long-messages") +
            listOf( "-package", "top.mcfpp.antlr")
    outputDirectory =  File("build/generated-src/antlr/main/top/mcfpp/antlr")
}

tasks.jar{
    manifest{
        attributes("Main-Class" to "top.mcfpp.MCFPPKt")
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    from(configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) })

    from("build/dll"){
        into("native")
        include("**/*.dll")
    }
}

val jniSourceDir = file("src/main/java/top/mcfpp/jni")
val cppSourceDir = file("src/main/cpp")

tasks.register<JavaCompile>("generateJni") {
    group = "build"
    destinationDirectory.set(file("$buildDir/generated/jni"))
    source = fileTree(jniSourceDir)
    classpath = files()
    options.compilerArgs = listOf("-h", "$buildDir/generated/jni")
}

tasks.register<Exec>("compileCpp") {
    group = "build"
    workingDir(cppSourceDir)

    val osOptional = OperatingSystem.current()
    if (osOptional.isEmpty)
        throw RuntimeException("Failed to compile JNI source due to unsupported operating system.")
    val os = osOptional.get()

    fun dirName(): String {
        return when (os) {
            OperatingSystem.WINDOWS -> "win32"
            OperatingSystem.MACOS -> "darwin"
            OperatingSystem.LINUX -> "linux"
        }
    }
    fun extName(): String {
        return when (os) {
            OperatingSystem.WINDOWS -> ".dll"
            OperatingSystem.MACOS -> ".dylib"
            OperatingSystem.LINUX -> ".so"
        }
    }

    val outputDirStr = "$buildDir/dll"
    val outputDir = File(outputDirStr)
    if (!outputDir.exists()) {
        outputDir.mkdir()
    }

    val cmdArgs = ArrayList<String>()
    cmdArgs.addAll(listOf(
        "g++",
        "-fPIC",
        "-I",
        "${System.getProperty("java.home")}/include",
        "-I",
        "${System.getProperty("java.home")}/include/${dirName()}",
        "-I",
        "$buildDir/generated/jni",
        "-shared",
        "-o",
        "$outputDirStr/native${extName()}"
    ))

    val cppPath = cppSourceDir.toPath()
    Files.walk(cppPath)
        .map { it.toAbsolutePath().toString() }
        .filter { it.endsWith(".cpp") }
        .forEach { cmdArgs.add(it) }

    commandLine(cmdArgs)
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
    dependsOn(tasks.generateGrammarSource)
}

tasks.withType<JavaCompile>{
    options.encoding = "UTF-8"
}

application {
    mainClass.set("top.mcfpp.MCFPPKt")
}

enum class OperatingSystem {
    WINDOWS,
    LINUX,
    MACOS;
    companion object {
        private fun isWindows(): Boolean {
            val osName = System.getProperty("os.name")
            return osName != null && osName.startsWith("Windows")
        }

        private fun isMacOs(): Boolean {
            val osName = System.getProperty("os.name")
            return osName != null && osName.startsWith("Mac")
        }

        private fun isLinux(): Boolean {
            val osName = System.getProperty("os.name")
            return osName != null && osName.startsWith("Linux")
        }

        fun current(): Optional<OperatingSystem> {
            return when {
                isWindows() -> Optional.of(WINDOWS)
                isMacOs() -> Optional.of(MACOS)
                isLinux() -> Optional.of(LINUX)
                else -> Optional.empty()
            }
        }
    }
}
