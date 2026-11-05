import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.10"
}

val mcfppApiJar = providers.gradleProperty("mcfppApiJar")
    .orElse(layout.projectDirectory.file("../../build/libs/MCFPP-0.1.0.jar").asFile.absolutePath)

dependencies {
    // The MCFPP runtime owns the API classes and Kotlin runtime. Keep them out of this provider JAR.
    compileOnly(files(mcfppApiJar))
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

tasks.jar {
    archiveFileName.set("kotlin-mni-example.jar")
}
