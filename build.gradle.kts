plugins {
    kotlin("jvm") version "1.8.0"
    groovy
    application
    antlr
    id("org.jetbrains.dokka") version "1.8.10"
}

group = "top.mcfpp"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        setUrl("https://jitpack.io")
    }
    maven {
        setUrl("https://maven.aliyun.com/nexus/content/groups/public/")
    }
    maven {
        setUrl("https://jitpack.io/")
    }
    maven {
        setUrl("https://libraries.minecraft.net")
    }
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
    antlr("org.antlr:antlr4:4.12.0")
    implementation(kotlin("reflect"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-visitor", "-long-messages")
//    outputDirectory =  File("src/gen")
}

tasks.jar{
    manifest{
        attributes("Main-Class" to "MCFPPKt")
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    from(configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) })
}

kotlin {
    jvmToolchain(8)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    dependsOn(tasks.generateGrammarSource)
}


application {
    mainClass.set("MCFPPKt")
}