plugins {
    id("java")
}

dependencies {
    implementation(project(":framework-api"))
    implementation("net.bytebuddy:byte-buddy:${rootProject.extra["byteBuddyVersion"]}")
}

tasks.shadowJar {
    archiveBaseName.set("kirisame-agent")
    archiveClassifier.set("all")
    manifest {
        attributes("Premain-Class" to "org.kirisame.mc.agent.Agent")
        attributes("Can-Retransform-Classes" to "true")
        attributes("Can-Redefine-Classes" to "true")
    }
}
