import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.10"
}

val mcfppApiJar = providers.gradleProperty("mcfppApiJar")
    .orElse(layout.projectDirectory.file("../../build/libs/mcfpp-1.0.2-SNAPSHOT.jar").asFile.absolutePath)

dependencies {
    // The MCFPP runtime owns the API classes and Kotlin runtime. Keep them out of this provider JAR.
    compileOnly(files(mcfppApiJar))
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget("21"))
    }
}

tasks.jar {
    archiveFileName.set("kotlin-mni-example.jar")
}
