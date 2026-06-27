plugins {
    id("java")
    id("application")
}

application {
    mainClass.set("org.kirisame.mc.core.KirisameFramework")
}

val byteBuddyVersion: String by rootProject.extra

dependencies {
    implementation(project(":framework-api"))
    implementation(project(":framework-agent"))
    compileOnly("net.bytebuddy:byte-buddy:$byteBuddyVersion")

    implementation("com.google.code.gson:gson:${rootProject.extra["gsonVersion"]}")
    implementation("io.github.classgraph:classgraph:${rootProject.extra["classGraphVersion"]}")
    implementation("org.tinylog:tinylog-api:${rootProject.extra["tinylogVersion"]}")
    implementation("org.tinylog:tinylog-impl:${rootProject.extra["tinylogVersion"]}")
}

tasks.shadowJar {
    archiveBaseName.set("kirisame-core")
    archiveClassifier.set("all")
    manifest {
        attributes("Main-Class" to "org.kirisame.mc.core.KirisameFramework")
    }
}

tasks.named<JavaExec>("run") {
    workingDir = file("${rootProject.projectDir}/workdir")
    dependsOn(":framework-agent:shadowJar")
    val agentJar = project(":framework-agent").tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").get().archiveFile.get().asFile
    jvmArgs("-javaagent:${agentJar.absolutePath}")
}
