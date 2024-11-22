import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.1.0"
    id("org.jetbrains.intellij.platform.module") version "2.1.0"
}

group = "org.threeform.idea.plugins"

version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create("IC", "2024.2.2")
        instrumentationTools()
    }
}

intellijPlatform {
    buildSearchableOptions = false
}

tasks {
    patchPluginXml {
        sinceBuild.set("242")
        untilBuild.set("243.*")
    }

//    signPlugin {
//        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
//        privateKey.set(System.getenv("PRIVATE_KEY"))
//        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
//    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }

    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    register<Copy>("copy_jar") {
        dependsOn(":jar")
        val sourceFile: File = getByName<Jar>("jar").archiveFile.get().asFile
        from(sourceFile)
        into(getByName<RunIdeTask>("runIde").sandboxPluginsDirectory.get().asFile.absolutePath + "/${project.name}_/lib/")
    }
}

