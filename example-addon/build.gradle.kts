plugins {
    id("java")
}

dependencies {
    compileOnly(project(":framework-api"))
    compileOnly(project(":example-plugin"))
    compileOnly("net.bytebuddy:byte-buddy:${rootProject.extra["byteBuddyVersion"]}")
}

tasks.shadowJar {
    archiveBaseName.set("kirisame-example-addon")
    archiveClassifier.set("all")
}
