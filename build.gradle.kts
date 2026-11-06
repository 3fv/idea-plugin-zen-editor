import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask
import java.util.Properties

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.20.0"
}

group = "org.threeform.idea.plugins"

val pluginVersion: String = run {
    val raw = providers.environmentVariable("PLUGIN_VERSION").orNull
        ?.takeIf { it.isNotBlank() }
        ?: runCatching {
            providers.exec {
                commandLine("git", "describe", "--tags", "--abbrev=0", "--match", "v*")
                isIgnoreExitValue = true
            }.standardOutput.asText.get().trim()
        }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: "0.0.0-SNAPSHOT"

    raw.removePrefix("v")
}

version = pluginVersion

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.3")
    }
}

intellijPlatform {
    buildSearchableOptions = true

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

tasks {
    patchPluginXml {
        pluginVersion.set(project.version.toString())

        sinceBuild.set("253")
    }

    buildSearchableOptions {
        // Load only this plugin (+ its declared platform deps) so bundled
        // Grazie doesn't start its GrazieLoginManager, which otherwise blocks
        // headless shutdown for 10s on a JBA auth call.
        systemProperty("idea.required.plugins.id", "org.threeform.idea.plugins.zen_editor")
    }

    signPlugin {
        val envVars = System.getenv()
        val secrets = listOf("PRIVATE_KEY_PASSWORD", "PRIVATE_KEY","CERTIFICATE_CHAIN").map { envVars[it] }
        if (secrets.all { !it.isNullOrBlank() } ) {
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

