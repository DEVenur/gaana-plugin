plugins {
    `java-library`
    `maven-publish`
}

project.group = "com.github.notdeltaxd"
project.version = findProperty("version") as String
val archivesBaseName = "gaana"

tasks {
    publish {
        dependsOn(publishToMavenLocal)
    }
}

dependencies {
    compileOnly(libs.lavaplayer)
    compileOnly("org.jetbrains:annotations:24.1.0")
    compileOnly("com.github.topi314.lavasearch:lavasearch:1.0.0")
    implementation(libs.logger)
    implementation(libs.commonsIo)

    testImplementation(libs.lavaplayer)
    testImplementation(libs.logger.impl)
}

val jar: Jar by tasks
val build: Task by tasks
val clean: Task by tasks

val sourcesJar = task<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allJava)
}

build.apply {
    dependsOn(jar)
    dependsOn(sourcesJar)

    jar.mustRunAfter(clean)
    sourcesJar.mustRunAfter(jar)
}

publishing {
    publications {
        create<MavenPublication>("main") {
            from(components["java"])
        }
    }
}
