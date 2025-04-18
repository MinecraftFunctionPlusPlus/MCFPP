import java.nio.file.Files
import java.util.Optional

plugins {
    kotlin("jvm") version "1.9.20"
    groovy
    application
    antlr
    id("org.jetbrains.dokka") version "1.9.20"
    java
    cpp
    id("maven-publish")
    id("com.gradleup.shadow") version "8.3.5"
}

val GROUP = "top.mcfpp"
val VERSION = "1.0-SNAPSHOT"

group = GROUP
version = VERSION

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://maven.aliyun.com/nexus/content/groups/public/")
    maven("https://libraries.minecraft.net")
    mavenLocal()
}

dependencies {
    // Kotlin
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
    testImplementation(kotlin("script-runtime"))

    // Apache
    implementation("org.apache.groovy:groovy-all:4.0.11")
    implementation("org.apache.logging.log4j:log4j-api:2.20.0")
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.20.0")
    implementation("org.apache.commons:commons-lang3:3.17.0")

    // Google
    implementation("com.google.guava:guava:33.2.0-jre")
    implementation("com.google.guava:guava:33.4.5-jre") // 注意版本冲突

    // Mojang
    implementation("com.mojang:brigadier:1.0.18")
    implementation("com.mojang:brigadier:1.3.10") // 注意版本冲突
    implementation("com.mojang:datafixerupper:8.0.16")

    // CommonMark
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("org.commonmark:commonmark-ext-heading-anchor:0.21.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.20.0")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.21.0")
    implementation("fr.brouillard.oss:commonmark-ext-notifications:1.1.0")

    // Others
    implementation("org.openjdk.nashorn:nashorn-core:15.4")
    implementation("info.debatty:java-string-similarity:2.0.0")
    implementation("com.alibaba.fastjson2:fastjson2:2.0.28")
    implementation("com.esotericsoftware:kryo:5.6.2")
    implementation("top.mcfpp:nbt:1.0")
    antlr("org.antlr:antlr4:4.13.1")
}

tasks.shadowJar {
    minimize()
}

tasks.test {
    useJUnitPlatform()
}

tasks.compileTestKotlin {
    dependsOn("generateTestGrammarSource")
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

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    isZip64 = true
}

val jniSourceDir = file("src/main/java/top/mcfpp/jni")
val cppSourceDir = file("src/main/cpp")

tasks.withType<JavaCompile> {
    // 关键点：通过 forkOptions 设置 JVM 参数
    options.forkOptions.jvmArgs = listOf(
        "-Duser.language=en",
        "-Duser.country=US"
    )
    // 确保启用 fork 模式
    options.isFork = true
}

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
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "21"
    dependsOn(tasks.generateGrammarSource)
}

tasks.withType<JavaCompile>{
    options.encoding = "UTF-8"
}

application {
    mainClass.set("top.mcfpp.MCFPPKt")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = GROUP
            artifactId = "mcfpp"
            version = VERSION
        }
    }
    repositories {
        mavenLocal()
    }
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