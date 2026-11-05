plugins {
    kotlin("jvm") version "2.4.10"
    java
    antlr
}

group = "top.mcfpp"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://libraries.minecraft.net")
    maven("https://nexus.mcfpp.top/repository/maven-releases/")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.5.2")
    implementation("org.apache.logging.log4j:log4j-api:2.20.0")
    implementation("org.apache.logging.log4j:log4j-core:2.25.3")
    implementation("com.google.guava:guava:33.4.5-jre")
    implementation("com.mojang:brigadier:1.3.10")
    implementation("com.mojang:datafixerupper:8.0.16")
    implementation("info.debatty:java-string-similarity:2.0.0")
    implementation("com.alibaba.fastjson2:fastjson2:2.0.28")
    implementation("top.mcfpp:nbt:1.0")
    antlr("org.antlr:antlr4:4.13.1")
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(25) }

tasks.generateGrammarSource {
    arguments = arguments + listOf("-visitor", "-long-messages", "-package", "top.mcfpp.antlr")
    outputDirectory = file("build/generated-src/antlr/main/top/mcfpp/antlr")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    dependsOn(tasks.generateGrammarSource)
}
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.test { useJUnitPlatform() }

// The browser consumes one complete, prebuilt target. Index generation runs on the build JVM;
// parsing, type checking, optimization and datapack generation still run in the browser.
val builtinPlanResources = layout.buildDirectory.dir("generated/builtin-plans")
val prepareBuiltinImportPlan by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Prepare bundled symbol, signature, inheritance and native-binding indexes."
    dependsOn(tasks.compileKotlin, tasks.compileJava)
    classpath = files(sourceSets.main.get().output.classesDirs) + configurations.runtimeClasspath.get()
    mainClass.set("top.mcfpp.library.compiler.BuiltinImportPlanGenerator")
    inputs.file("src/main/resources/datapack/targets/1.21.8/bin.mclib")
    inputs.files(sourceSets.main.get().output.classesDirs).withPropertyName("compilerClasses")
    outputs.dir(builtinPlanResources)
    args(file("src/main/resources").absolutePath, builtinPlanResources.get().asFile.absolutePath, "1.21.8")
}

tasks.processResources {
    dependsOn(prepareBuiltinImportPlan)
    include("datapack/targets/1.21.8/**", "lang/**", "log4j2.xml")
    from(builtinPlanResources)
}
