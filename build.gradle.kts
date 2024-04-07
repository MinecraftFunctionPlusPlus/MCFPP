import org.gradle.kotlin.dsl.cpp

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
        attributes("Main-Class" to "MCFPPKt")
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
    commandLine("g++", "-I", "${System.getProperty("java.home")}/include", "-I", "${System.getProperty("java.home")}/include/win32", "-I", "$buildDir/generated/jni", "-shared", "-o", "$buildDir/dll/native.dll", "*.cpp")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
    dependsOn(tasks.generateGrammarSource)
}

application {
    mainClass.set("MCFPPKt")
}