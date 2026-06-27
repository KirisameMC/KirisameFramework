plugins {
    id("java")
}

dependencies {
    compileOnly(project(":framework-api"))
    compileOnly("net.bytebuddy:byte-buddy:${rootProject.extra["byteBuddyVersion"]}")
}

tasks.shadowJar {
    archiveBaseName.set("kirisame-example-plugin")
    archiveClassifier.set("all")
}
