import kotlin.io.path.Path

plugins {
    kotlin("jvm") version "1.9.23"
    id("fabric-loom") version "1.9.2"
    id("top.mcfpp.gradle") version "1.0-SNAPSHOT"
}

group = "org.example"
version = "1.0-SNAPSHOT"

base {
    archivesName = project.properties["archives_base_name"].toString()
}

repositories {
    mavenCentral()
    mavenLocal()
}


dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    implementation("top.mcfpp:mcfpp:1.0-SNAPSHOT")

    minecraft("com.mojang:minecraft:${project.properties["minecraft_version"]}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${project.properties["loader_version"]}")

}

fabricApi {
    configureDataGeneration {
        client = true
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<JavaCompile>().configureEach {
    this.options.release = 21
}

java {
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

mcfpp {
    version = "1.21"
    description = "qwq"
    targetPath = Path("./run/saves/MCFPP_TEST/datapacks")
}
