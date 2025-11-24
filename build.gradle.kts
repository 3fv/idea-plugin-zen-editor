plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.1.0"
    id("org.jetbrains.intellij.platform.module") version "2.1.0"
}

group = "org.threeform.idea.plugins"

version = "1.0.3"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create("IC", "2025.2")
        instrumentationTools()
    }
}

// intellijPlatform {
//     buildSearchableOptions = false
// }

tasks {
    // patchPluginXml {
    //     pluginVersion.set(project.version.toString())
    //
    //     sinceBuild.set("242")
    //     untilBuild.set("253.*")
    // }

//    signPlugin {
//        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
//        privateKey.set(System.getenv("PRIVATE_KEY"))
//        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
//    }

    // publishPlugin {
    //     val envToken = System.getenv("PUBLISH_TOKEN") ?: System.getenv("JB_PLUGIN_TOKEN")
    //     if (envToken != null) {
    //         token.set(envToken)
    //     }
    // }

    // Set the JVM compatibility versions
    // withType<JavaCompile> {
    //     sourceCompatibility = "21"
    //     targetCompatibility = "17"
    // }
    // withType<KotlinCompile> {
    //     // Migrate to compilerOptions DSL for Kotlin 2.x
    //     compilerOptions {
    //         jvmTarget.set(JVM_17)
    //     }
    // }

    // register<Copy>("copy_jar") {
    //     dependsOn(":jar")
    //     val sourceFile: File = getByName<Jar>("jar").archiveFile.get().asFile
    //     from(sourceFile)
    //     into(getByName<RunIdeTask>("runIde").sandboxPluginsDirectory.get().asFile.absolutePath + "/${project.name}_/lib/")
    // }
}

