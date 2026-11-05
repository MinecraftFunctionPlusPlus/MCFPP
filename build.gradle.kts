import org.gradle.kotlin.dsl.get
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.4.10"
    groovy
    application
    antlr
    id("org.jetbrains.dokka") version "2.1.0"
    java
    id("maven-publish")
    id("com.gradleup.shadow") version "9.2.2"
}

val GROUP = "top.mcfpp"
val VERSION = "0.1.0"

group = GROUP
version = VERSION

val releaseCompression by configurations.creating

repositories {
    mavenLocal {
        content { includeGroup("moe.afox.mcfpm") }
    }
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://maven.aliyun.com/nexus/content/groups/public/")
    maven("https://libraries.minecraft.net")
    maven("https://nexus.mcfpp.top/repository/maven-releases/")
}

dependencies {
    releaseCompression("com.github.luccappellaro:jzopfli:0.0.4") { isTransitive = false }
    releaseCompression("org.apache.commons:commons-compress:1.28.0")
    releaseCompression("org.tukaani:xz:1.10")
    // The package-manager SDK is loaded through its Java facade at runtime.
    val mcfpmVersion = providers.gradleProperty("mcfpm.version").orElse("0.1.0-SNAPSHOT").get()
    runtimeOnly("moe.afox.mcfpm:core:$mcfpmVersion")
    runtimeOnly("moe.afox.mcfpm:mcfpp-adapter:$mcfpmVersion")
    runtimeOnly("moe.afox.mcfpm:repository-maven:$mcfpmVersion")
    implementation("org.tomlj:tomlj:1.1.1")
    implementation("com.fasterxml.jackson.core:jackson-core:2.20.2")
    // Kotlin
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.5.2")
    testImplementation(kotlin("test"))
    testImplementation(kotlin("script-runtime"))

    // Apache
    implementation("org.apache.groovy:groovy-all:4.0.11")
    implementation("org.apache.logging.log4j:log4j-api:2.20.0")
    implementation("org.apache.logging.log4j:log4j-core:2.25.3")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.20.0")
    implementation("org.apache.commons:commons-lang3:3.18.0")

    // Google
    implementation("com.google.guava:guava:33.4.5-jre")

    // Mojang
    implementation("com.mojang:brigadier:1.3.10")
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
    implementation("info.picocli:picocli:4.7.7")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.debug:0.22.0")
    antlr("org.antlr:antlr4:4.13.1")
}

tasks.shadowJar {
    archiveClassifier.set("") // 生成的jar不带classifier，方便运行
    manifest {
        attributes("Main-Class" to "top.mcfpp.MCFPPKt", "Implementation-Version" to VERSION)
    }
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    mergeServiceFiles()
    minimize{
        exclude(dependency("org.apache.logging.log4j:.*"))
        // Kotlin loads the reflection implementation dynamically from the runtime.
        exclude(dependency("org.jetbrains.kotlin:kotlin-reflect:.*"))
        // The reflected package-manager facade also needs the complete Kotlin runtime.
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib.*:.*"))
        // The Java facade is reflected; its SDK and transitive runtime classes are roots.
        exclude(dependency("moe.afox.mcfpm:.*:.*"))
    }
}

tasks.test {
    useJUnitPlatform()
    doFirst { systemProperty("mcfpp.compiler.classpath", sourceSets.main.get().runtimeClasspath.asPath) }
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
    // Keep the ordinary JAR from overwriting the standalone runtime.
    archiveClassifier.set("plain")
    manifest{
        attributes("Main-Class" to "top.mcfpp.MCFPPKt", "Implementation-Version" to VERSION)
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    isZip64 = true
}

tasks.withType<JavaCompile> {
    // 关键点：通过 forkOptions 设置 JVM 参数
    options.forkOptions.jvmArgs = listOf(
        "-Duser.language=en",
        "-Duser.country=US"
    )
    // 确保启用 fork 模式
    options.isFork = true
}

kotlin {
    jvmToolchain(25)
    sourceSets.named("test") {
        // Compile the actual Kotlin examples used by the source-integration checks.
        kotlin.srcDirs("examples/mni/damage/provider/kotlin", "examples/mni/repeat/provider/kotlin",
            "examples/mni/raycast/provider/kotlin", "examples/mni/score-mapping/provider/kotlin",
            "examples/mni/comparison/provider/kotlin")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    dependsOn(tasks.generateGrammarSource)
}

// Generate closed, immutable import directories on the desktop JVM. The browser keeps the
// complete stdlib bundle; only its currently supported Playground target needs a prepared plan.
val builtinPlanResources = layout.buildDirectory.dir("generated/builtin-plans")
val prepareBuiltinImportPlan by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Prepare Playground symbol/signature/layout/native-binding indexes."
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
    from(builtinPlanResources)
}

tasks.withType<JavaCompile>{
    options.encoding = "UTF-8"
}

application {
    applicationName = "mcfpp"
    mainClass.set("top.mcfpp.MCFPPKt")
    applicationDistribution.from("LICENSE", "INSTALL.md", "RELEASE-NOTES.md")
}

tasks.named<org.gradle.api.tasks.application.CreateStartScripts>("startScripts") {
    outputDir = layout.buildDirectory.dir("launchers/mcfpp-dev").get().asFile
}

tasks.named<org.gradle.api.tasks.application.CreateStartScripts>("startShadowScripts") {
    outputDir = layout.buildDirectory.dir("launchers/mcfpp").get().asFile
}

tasks.register<Jar>("mniExamplesJar") {
    group = "build"
    description = "Package the Kotlin MNI example providers, including the comparison bootstrap."
    dependsOn(tasks.compileTestKotlin)
    archiveFileName.set("mni-example-providers.jar")
    from(layout.buildDirectory.dir("classes/kotlin/test")) { include("example/**") }
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from("src/main/java")
    from("src/main/kotlin")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

application.applicationDistribution.from(sourcesJar) { into("lib") }

distributions {
    main { distributionBaseName.set("mcfpp-dev") }
    named("shadow") { distributionBaseName.set("mcfpp") }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val releaseSources by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Freeze the current release sources, including files not yet committed to Git."
    archiveBaseName.set("mcfpp")
    archiveClassifier.set("source")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    into("mcfpp-$VERSION-source") {
        from(projectDir) {
            include("src/**", "gradle/**", "gradlew", "gradlew.bat", "build.gradle.kts", "settings.gradle.kts",
                "README.md", "README_CN.md", "LICENSE", "INSTALL.md", "RELEASE-NOTES.md", "docs/**", "examples/**",
                "mcfpp.json", "mcfpp.toml", "mcfpp-packages.json", ".gitignore", ".editorconfig", "build-support/**")
            exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**", "**/.mcfpp/**")
        }
    }
}

val releaseShadowJar by tasks.registering(ShadowJar::class) {
    description = "Shrink runtime dependencies using production classes only."
    archiveFileName.set("MCFPP-$VERSION.jar")
    destinationDirectory.set(layout.buildDirectory.dir("release-intermediates/runtime"))
    from(sourceSets.main.get().output)
    configurations.set(setOf(project.configurations.runtimeClasspath.get()))
    sourceSetsClassesDirs.setFrom(sourceSets.main.get().output.classesDirs.filter { it.isDirectory })
    manifest { attributes("Main-Class" to "top.mcfpp.MCFPPKt", "Implementation-Version" to VERSION) }
    exclude("META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA",
        "META-INF/versions/**/module-info.class", "module-info.class")
    filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
    mergeServiceFiles()
    minimize()
    // Retain reflection/service entry points as roots. Shadow's minimize.exclude also
    // excludes their entire dependency subtrees, including most unused fastutil classes.
    apiJars.setFrom(provider {
        project.configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts.filter {
            val module = it.moduleVersion.id
            module.group == "org.apache.logging.log4j" || module.group == "moe.afox.mcfpm" ||
                module.group == "org.jetbrains.kotlin" &&
                    (module.name == "kotlin-reflect" || module.name.startsWith("kotlin-stdlib")) ||
                module.group == "top.mcfpp" && module.name == "nbt" || module.group == "org.openjdk.nashorn"
        }.map { it.file }
    })
    toMinimize.setFrom(project.configurations.runtimeClasspath.get() - apiJars)
}

val compileReleaseTools by tasks.registering(JavaCompile::class) {
    source(fileTree("build-support") { include("*.java") })
    classpath = releaseCompression
    destinationDirectory.set(layout.buildDirectory.dir("release-tools/classes"))
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}

val releaseJava = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }
val releaseCache = layout.buildDirectory.dir("release-compression-cache")

fun registerReleaseCompression(name: String, mode: String, input: Provider<RegularFile>, output: Provider<RegularFile>) =
    tasks.register<JavaExec>(name) {
        group = "distribution"
        description = "Compress $name for minimum distribution size."
        dependsOn(compileReleaseTools)
        classpath = files(compileReleaseTools.flatMap { it.destinationDirectory }) + releaseCompression
        mainClass.set("ReleasePackager")
        javaLauncher.set(releaseJava)
        maxHeapSize = "2g"
        inputs.file(input)
        inputs.property("mode", mode)
        outputs.file(output)
        doFirst { args = listOf(mode, input.get().asFile.absolutePath, output.get().asFile.absolutePath,
            releaseCache.get().asFile.absolutePath) }
    }

val releaseRuntime = layout.buildDirectory.file("release-objects/MCFPP-$VERSION.jar")
val playgroundRuntime = layout.buildDirectory.file("playground/MCFPP-$VERSION.jar")
// Preserve the source shadow JAR and every stdlib target; verify the derived runtime losslessly.
val playgroundJar = registerReleaseCompression("playgroundJar", "browser-jar", tasks.shadowJar.flatMap { it.archiveFile }, playgroundRuntime)
playgroundJar.configure {
    val provenance = layout.buildDirectory.file("playground/MCFPP-$VERSION.jar.source.json")
    outputs.file(provenance)
    doLast {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                DigestOutputStream(OutputStream.nullOutputStream(), digest).use { input.copyTo(it) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        provenance.get().asFile.writeText(groovy.json.JsonOutput.toJson(mapOf(
            "schema" to 1, "packaging" to "stdlib-chunks/1",
            "sourceSha256" to sha256(tasks.shadowJar.get().archiveFile.get().asFile),
            "sha256" to sha256(playgroundRuntime.get().asFile)
        )) + "\n")
    }
}
val releaseJar = registerReleaseCompression("releaseJar", "jar", releaseShadowJar.flatMap { it.archiveFile }, releaseRuntime)

val releaseStartScripts by tasks.registering(org.gradle.api.tasks.application.CreateStartScripts::class) {
    dependsOn(releaseJar)
    applicationName = "mcfpp"
    mainClass.set(application.mainClass)
    classpath = files(releaseRuntime)
    outputDir = layout.buildDirectory.dir("launchers/mcfpp-release").get().asFile
}

distributions.create("release") {
    distributionBaseName.set("mcfpp")
    contents {
        from("LICENSE", "INSTALL.md", "RELEASE-NOTES.md")
        into("lib") { from(files(releaseRuntime).builtBy(releaseJar)) }
        into("bin") {
            from(releaseStartScripts)
            filePermissions { unix("rwxr-xr-x") }
        }
    }
}

val releaseDistZip = tasks.named<Zip>("releaseDistZip") {
    destinationDirectory.set(layout.buildDirectory.dir("release-intermediates/distributions"))
}
val releaseDistTar = tasks.named<Tar>("releaseDistTar") {
    destinationDirectory.set(layout.buildDirectory.dir("release-intermediates/distributions"))
}
val compactReleaseZip = layout.buildDirectory.file("release-objects/mcfpp-$VERSION.zip")
val compactReleaseTar = layout.buildDirectory.file("release-objects/mcfpp-$VERSION.tar.xz")
val releaseSourceTar by tasks.registering(Tar::class) {
    dependsOn(releaseSources)
    archiveFileName.set("mcfpp-$VERSION-source.tar")
    destinationDirectory.set(layout.buildDirectory.dir("release-intermediates/distributions"))
    from(provider { zipTree(releaseSources.get().archiveFile.get().asFile) })
}
val compactSourceArchive = layout.buildDirectory.file("release-objects/mcfpp-$VERSION-source.tar.xz")
val compactSourcesJar = layout.buildDirectory.file("release-objects/MCFPP-$VERSION-sources.jar")
val releaseZip = registerReleaseCompression("releaseZip", "zip", releaseDistZip.flatMap { it.archiveFile }, compactReleaseZip)
val releaseTar = registerReleaseCompression("releaseTar", "xz", releaseDistTar.flatMap { it.archiveFile }, compactReleaseTar)
val releaseSourceArchive = registerReleaseCompression("releaseSourceArchive", "xz", releaseSourceTar.flatMap { it.archiveFile }, compactSourceArchive)
val releaseApiSources = registerReleaseCompression("releaseApiSources", "zip", sourcesJar.flatMap { it.archiveFile }, compactSourcesJar)

tasks.register<Sync>("release") {
    group = "distribution"
    description = "Stage the 0.1.0 CLI, archives, frozen sources and SHA-256 checksums."
    dependsOn(releaseJar, releaseZip, releaseTar, releaseSourceArchive, releaseApiSources)
    from(releaseRuntime, compactReleaseZip, compactReleaseTar, compactSourceArchive, compactSourcesJar)
    into(layout.buildDirectory.dir("releases/$VERSION"))
    doLast {
        val artifacts = destinationDir.listFiles()!!.filter {
            it.extension in setOf("jar", "zip", "xz")
        }.sortedBy { it.name }
        val hashes = artifacts.associate { artifact ->
            val digest = MessageDigest.getInstance("SHA-256")
            artifact.inputStream().use { input ->
                DigestOutputStream(OutputStream.nullOutputStream(), digest).use { output -> input.copyTo(output) }
            }
            artifact.name to digest.digest().joinToString("") { "%02x".format(it) }
        }
        destinationDir.resolve("SHA256SUMS").writeText(hashes.entries.joinToString("") { (name, hash) -> "$hash  $name\n" })
        destinationDir.resolve("release-manifest.json").writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(
            mapOf("schema" to 1, "version" to VERSION, "java" to 25, "gradle" to gradle.gradleVersion,
                "sourceArchive" to compactSourceArchive.get().asFile.name, "sha256" to hashes)
        )) + "\n")
    }
}

tasks.register<Exec>("verifyDistribution") {
    group = "verification"
    description = "Compile a generic NBT reference with the standalone distribution JAR."
    dependsOn(tasks.shadowJar)
    val smokeRoot = layout.buildDirectory.dir("distribution-smoke")
    val launcher = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }
    doFirst {
        val root = smokeRoot.get().asFile.apply { mkdirs() }
        root.resolve("mcfpp.toml").writeText("""
            schema = 1
            name = "distribution-smoke"
            namespace = "distribution_smoke"
            version = "1.21.8"
        """.trimIndent() + "\n")
        root.resolve("src/main/mcfpp").mkdirs()
        file("src/test/resources/distribution/nbt-reference.mcfpp")
            .copyTo(root.resolve("src/main/mcfpp/main.mcfpp"), overwrite = true)
        workingDir(root)
        commandLine(launcher.get().executablePath.asFile, "-jar", tasks.shadowJar.get().archiveFile.get().asFile,
            "build", "--offline")
    }
}

val isSnapshotVersion = provider { project.version.toString().endsWith("-SNAPSHOT") }
val nexusBaseUrl = providers.gradleProperty("nexusBaseUrl").orElse("https://nexus.mcfpp.top")
val nexusReleasesRepository = providers.gradleProperty("nexusReleasesRepository").orElse("maven-releases")
val nexusSnapshotsRepository = providers.gradleProperty("nexusSnapshotsRepository").orElse("maven-snapshots")
// Keep secrets out of the repository: provide nexusUsername/nexusPassword in ~/.gradle/gradle.properties
// or via ORG_GRADLE_PROJECT_nexusUsername / ORG_GRADLE_PROJECT_nexusPassword.
val nexusRepositoryUrl = provider {
    val repositoryName = if(isSnapshotVersion.get()) nexusSnapshotsRepository.get() else nexusReleasesRepository.get()
    // remove trailing slashes from base URL and append repository path
    "${nexusBaseUrl.get().replace(Regex("/+$"), "")}/repository/${repositoryName}/"
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(sourcesJar)
            groupId = GROUP
            artifactId = "mcfpp"
            version = VERSION
            pom {
                name.set("mcfpp")
                description.set("MCFPP is a minecraft function programming language.")
            }
        }
    }
    repositories {
        mavenLocal()
        maven {
            name = "privateNexus"
            url = uri(nexusRepositoryUrl.get())
            isAllowInsecureProtocol = false
            credentials {
                username = providers.gradleProperty("nexusUsername")
                    .orElse(providers.environmentVariable("NEXUS_USERNAME"))
                    .orNull
                password = providers.gradleProperty("nexusPassword")
                    .orElse(providers.environmentVariable("NEXUS_PASSWORD"))
                    .orNull
            }
        }
    }
}

// Regenerate serialized language signatures after type, parser, or MNI metadata changes.
// Run before check; processResources then copies the updated index into the runtime output.
tasks.register<JavaExec>("regenerateStdlib") {
    group = "build"
    description = "Recompile the bundled MCFPP standard library index without rewriting datapacks."
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("top.mcfpp.MCFPPKt")
    val indexDirectory = layout.buildDirectory.dir("stdlib-index")
    val indexConfig = layout.buildDirectory.file("stdlib-index.toml")
    doFirst {
        val configFile = indexConfig.get().asFile
        configFile.parentFile.mkdirs()
        configFile.writeText("schema = 1\n" + mapOf(
            "sourcePath" to file("src/main/mcfpp").absolutePath,
            "targetPath" to indexDirectory.get().asFile.absolutePath,
            "description" to "MCFPP Standard Library",
            "namespace" to "mcfpp",
            "noDatapack" to true,
            "copyImport" to false,
            "args" to listOf("-ignoreStdLib", "-isLib", "-level=info")
        ).entries.joinToString("\n") { (key, value) -> "$key = ${groovy.json.JsonOutput.toJson(value)}" } + "\n")
        setArgs(listOf("build", "--project", configFile.absolutePath))
    }
    doLast {
        copy {
            from(indexDirectory.get().file("bin.mclib"))
            into("src/main/resources/datapack")
        }
    }
}
