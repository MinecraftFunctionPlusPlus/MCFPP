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
            pom {
                name.set("mcfpp-gradle")
                description.set("Gradle support for MCFPP")
            }
        }
    }
    repositories {
        mavenLocal()
        maven {
            val baseUrl = "https://nexus.mcfpp.top"
            url = if (version.toString().endsWith("SNAPSHOT")) {
                uri("$baseUrl/repository/maven-snapshots/")
            }else{
                uri("$baseUrl/repository/maven-releases/")
            }
            credentials {
                username = project.findProperty("NEXUS_USERNAME") as String
                password = project.findProperty("NEXUS_PASSWORD") as String
            }
        }
    }
}
