import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.1.0"
//    id("org.jetbrains.intellij.platform.module") version "2.1.0"
}

group = "org.threeform.idea.plugins"

val pluginVersion: String = run {
    providers.environmentVariable("PLUGIN_VERSION").orNull
        ?.takeIf { it.isNotBlank() }
        ?.let { return@run it }

    val gitTag = runCatching {
        providers.exec {
            commandLine("git", "describe", "--tags", "--abbrev=0", "--match", "v*")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim().removePrefix("v")
    }.getOrNull().orEmpty()

    gitTag.takeIf { it.isNotBlank() } ?: "0.0.0-SNAPSHOT"
}

version = pluginVersion

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create("IC", "2025.2.2")
        instrumentationTools()
    }
}

intellijPlatform {
    buildSearchableOptions = false
}

tasks {
    patchPluginXml {
        pluginVersion.set(project.version.toString())

        sinceBuild.set("242")
        untilBuild.set("253.*")
    }

    signPlugin {
        val envVars = System.getenv()
        val secrets = listOf("PRIVATE_KEY_PASSWORD", "PRIVATE_KEY","CERTIFICATE_CHAIN").map { envVars[it] }
        if (secrets.all { it != null && it.isNotBlank() } ) {
            certificateChain.set(secrets[2])
            privateKey.set(secrets[1])
            password.set(secrets[0])
        }
    }

     publishPlugin {
         val envVars = System.getenv()
         val secrets = listOf("PUBLISH_TOKEN", "JB_PLUGIN_TOKEN").map { envVars[it] }.filter { it != null && it.isNotBlank() }
         if (secrets.isNotEmpty() ) {
             token.set(secrets[0])
         }
     }

     register<Copy>("copy_jar") {
         dependsOn(":jar")
         val sourceFile: File = getByName<Jar>("jar").archiveFile.get().asFile
         from(sourceFile)
         into(getByName<RunIdeTask>("runIde").sandboxPluginsDirectory.get().asFile.absolutePath + "/${project.name}_/lib/")
     }
}

