plugins {
    id("java-library")
}

dependencies {
    compileOnly("net.bytebuddy:byte-buddy:${rootProject.extra["byteBuddyVersion"]}")
}

tasks.shadowJar {
    archiveBaseName.set("kirisame-api")
    archiveClassifier.set("all")
}
