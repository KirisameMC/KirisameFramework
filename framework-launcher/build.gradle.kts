plugins {
    id("java")
    id("application")
}

application {
    mainClass.set("org.kirisame.mc.launcher.LauncherMain")
}

dependencies {
    implementation("com.google.code.gson:gson:${rootProject.extra["gsonVersion"]}")
}

tasks.shadowJar {
    archiveBaseName.set("kirisame-launcher")
    archiveClassifier.set("all")
    manifest {
        attributes("Main-Class" to "org.kirisame.mc.launcher.LauncherMain")
    }
}

tasks.named<JavaExec>("run") {
    workingDir = file("${rootProject.projectDir}/workdir")
}
