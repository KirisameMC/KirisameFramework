plugins {
    id("java")
    id("com.gradleup.shadow") version "9.2.2" apply false
}

allprojects {
    group = "org.kirisame.mc"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.gradleup.shadow")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        testImplementation(platform("org.junit:junit-bom:5.10.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.test {
        useJUnitPlatform()
    }
}

// --- Shared dependency versions ---
val byteBuddyVersion = "1.18.1"
val gsonVersion = "2.13.2"
val classGraphVersion = "4.8.184"
val tinylogVersion = "2.7.0"

extra["byteBuddyVersion"] = byteBuddyVersion
extra["gsonVersion"] = gsonVersion
extra["classGraphVersion"] = classGraphVersion
extra["tinylogVersion"] = tinylogVersion

// --- Workdir tasks ---
tasks.register("prepareWorkdir") {
    group = "kirisame"
    description = "Creates the workdir directory with plugins/ subdirectory"
    doLast {
        val workdir = file("${rootProject.projectDir}/workdir")
        File(workdir, "plugins").mkdirs()
        println("Workdir prepared at: ${workdir.absolutePath}")
    }
}

tasks.register("beforeRunning") {
    group = "kirisame"
    description = "Copies framework JARs and plugin JARs into workdir"
    dependsOn(":framework-agent:shadowJar", ":framework-core:shadowJar",
             ":example-plugin:shadowJar", ":example-addon:shadowJar")
    doLast {
        val workdir = File("${rootProject.projectDir}/workdir")
        workdir.mkdirs()
        val pluginsDir = File(workdir, "plugins")
        pluginsDir.mkdirs()

        val agentJar = project(":framework-agent").tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").get().archiveFile.get().asFile
        val coreJar = project(":framework-core").tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").get().archiveFile.get().asFile

        agentJar.copyTo(File(workdir, agentJar.name), overwrite = true)
        coreJar.copyTo(File(workdir, coreJar.name), overwrite = true)
        println("Copied: ${agentJar.name}, ${coreJar.name} -> workdir/")

        // Copy plugin JARs to workdir/plugins/
        val pluginProjects = listOf(":example-plugin", ":example-addon")
        for (proj in pluginProjects) {
            val pluginJar = project(proj).tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").get().archiveFile.get().asFile
            pluginJar.copyTo(File(pluginsDir, pluginJar.name), overwrite = true)
            println("Copied plugin: ${pluginJar.name} -> workdir/plugins/")
        }
    }
}
