import kotlin.io.path.Path

plugins {
    kotlin("jvm") version "2.1.0"
    id("fabric-loom") version "1.9.2"
    id("top.mcfpp.gradle") version "1.0-SNAPSHOT"
}

group = "org.example"
version = "1.0-SNAPSHOT"

mcfpp {
    version = "1.21"
    description = "qwq"
//    targetPath = Path("./run/saves/${project.properties["save_name"]}/datapacks")
    targetPath = Path("./build/datapacks/")

}

loom{
    runs {
        this["client"].apply {
            programArg("--username=Dev" )
        }
    }
}

fabricApi {
    configureDataGeneration {
        client = true
    }
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

    modImplementation ("net.fabricmc.fabric-api:fabric-resource-loader-v0:${project.properties["fabric_resource_loader_version"]}")
    modImplementation ("net.fabricmc:fabric-language-kotlin:${project.properties["fabric_kotlin_version"]}")
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
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

base {
    archivesName = project.properties["archives_base_name"].toString()
}

tasks.register<Copy>("copy"){
    from(mcfpp.targetPath?.resolve(mcfpp.name))
    into("./build/resources/main")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.named("mcfppCompile"){
    finalizedBy("copy")
}
