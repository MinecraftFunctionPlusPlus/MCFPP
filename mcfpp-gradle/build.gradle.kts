plugins {
    kotlin("jvm")
    id("java-gradle-plugin")
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
    testImplementation(kotlin("test"))
    implementation(project(":"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

tasks.shadowJar {
    minimize()
}

gradlePlugin {
    plugins {
        create("mcfpp-gradle") {
            id = "top.mcfpp.gradle"
            implementationClass = "top.mcfpp.gradle.MCFPPGradlePlugin"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = GROUP
            artifactId = "mcfpp-gradle"
            version = VERSION
        }
    }

    repositories {
        mavenLocal()
    }
}
