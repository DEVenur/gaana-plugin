plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.lavalink)
    id("com.github.johnrengelman.shadow")
    id("com.github.breadmoirai.github-release")
}

val pluginVersion = findProperty("version") as String?
val commitSha = System.getenv("GITHUB_SHA")?.take(7) ?: "unknown"
val preRelease = System.getenv("PRERELEASE") == "true"
val verName = if (preRelease) commitSha else pluginVersion!!

group = "com.github.notdeltaxd"
version = verName
val archivesBaseName = "gaana-plugin"

lavalinkPlugin {
    name = "gaana-plugin"
    path = "$group.plugin"
    version = verName
    apiVersion = libs.versions.lavalink.api
    serverVersion = libs.versions.lavalink.server
    configurePublishing = false
}

dependencies {
    implementation(projects.main)
}

val impl = project.configurations.implementation.get()
impl.isCanBeResolved = true

tasks {
    jar {
        archiveBaseName.set(archivesBaseName)
        enabled = false
    }
    shadowJar {
        archiveBaseName.set(archivesBaseName)
        archiveClassifier.set("")
        archiveVersion.set(verName)
        configurations = listOf(impl)
    }
    build {
        dependsOn(processResources)
        dependsOn(compileJava)
        dependsOn(shadowJar)
    }
    publish {
        dependsOn(publishToMavenLocal)
        dependsOn(shadowJar)
    }
}

tasks.githubRelease {
    dependsOn(tasks.shadowJar)
    mustRunAfter(tasks.shadowJar)
}

data class Version(val major: Int, val minor: Int, val patch: Int) {
    override fun toString() = "$major.$minor.$patch"
}

publishing {
    publications {
        create<MavenPublication>("gaana-plugin") {
            groupId = "com.github.notdeltaxd"
            artifactId = "gaana-plugin"
            version = verName
            artifact(tasks.shadowJar.get())
            pom {
                packaging = "jar"
            }
        }
    }
}

githubRelease {
    token(System.getenv("GITHUB_TOKEN"))
    owner("notdeltaxd")
    repo("gaana-plugin")
    targetCommitish(System.getenv("RELEASE_TARGET"))
    releaseAssets(tasks.shadowJar.get().outputs.files.toList())
    tagName("$verName")
    releaseName(verName)
    overwrite(false)
    prerelease(preRelease)

    if (preRelease) {
        body("""Here is a pre-release version of the plugin. Please test it and report any issues you find.
            |Example:
            |```yml
            |lavalink:
            |    plugins:
            |        - dependency: "com.github.notdeltaxd:gaana-plugin:$verName"
            |          repository: https://jitpack.io
            |```
        """.trimMargin())
    } else {
        body(changelog())
    }
}
