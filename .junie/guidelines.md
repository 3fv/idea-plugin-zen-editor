# Project Guidelines — Zen Editor (IntelliJ Platform Plugin)

## Overview
Zen Editor is an IntelliJ IDEA plugin intended to enhance focus while editing by providing a minimal, distraction‑reduced editing experience. The project is built with Gradle (Kotlin DSL) using the IntelliJ Platform Gradle Plugin.

These guidelines tell Junie how to build, run, test, and validate changes, and summarize key parts of the project.

## Project structure
- build.gradle.kts — Gradle build script (Kotlin DSL) with IntelliJ Platform configuration
- settings.gradle.kts — Gradle settings
- gradle.properties, gradle wrapper — Build tooling
- src/main/java/org/threeform/idea/plugins/
  - ZenEditorActivity.kt
  - ZenEditorHeader.kt
  - ZenEditorInstall.kt
  - ZenEditorShrinkableLabel.kt
- src/main/resources/META-INF/
  - plugin.xml — Plugin metadata and registrations
  - pluginIcon.svg — Plugin icon
- assets/screenshots/ — Screenshots for README/Marketplace
- README.md, LICENSE

## Build and run
Prerequisites:
- JDK 21 available to Gradle (sourceCompatibility 21; Kotlin/JVM target 17)
- Internet access for Gradle dependencies

Common tasks:
- Run the plugin in a sandbox IDE: ./gradlew runIde
- Build the plugin distribution ZIP: ./gradlew buildPlugin
- Assemble only: ./gradlew jar

Key build settings (from build.gradle.kts):
- IntelliJ Platform: IC 2025.2
- sinceBuild: 242, untilBuild: 253.*
- Kotlin: 2.2.0; Kotlin JVM target: 17
- Custom task copy_jar copies the built jar into the runIde sandbox plugin folder

## Testing
There are currently no unit tests in this repository.
- If you add tests, place them under src/test and run: ./gradlew test
- For changes affecting behavior, prefer manual verification via ./gradlew runIde.

## Publishing and signing
- To publish to JetBrains Marketplace: ./gradlew publishPlugin
  - Provide token via env var PUBLISH_TOKEN or JB_PLUGIN_TOKEN
- Signing (optional, currently commented out in build.gradle.kts):
  - CERTIFICATE_CHAIN, PRIVATE_KEY, PRIVATE_KEY_PASSWORD environment variables

## Code style and conventions
- Kotlin and Java code should follow standard JetBrains/Google Kotlin style and standard Java conventions.
- Keep plugin.xml registrations minimal and accurate.
- Prefer Kotlin for new logic unless Java interop is specifically required.

## Verification checklist for Junie
Use this when making changes:
1. Does the change require a build or runtime check?
   - For code changes: run ./gradlew build or ./gradlew runIde as appropriate.
   - For documentation-only changes: building is not required.
2. If plugin metadata changed (plugin.xml or build.gradle.kts):
   - Ensure sinceBuild/untilBuild remain compatible.
   - Ensure pluginVersion in patchPluginXml is set from project.version.
3. If adding dependencies or changing IntelliJ version:
   - Update intellijPlatform block accordingly; verify runIde starts.
4. If adding tests:
   - Place under src/test; ensure ./gradlew test passes locally.
5. Before submitting:
   - Re-run affected tasks (build/runIde/tests) and update the PR description with what was verified.

## Notes for local development
- The sandbox used by runIde is managed by the Gradle IntelliJ plugin; no manual IDE installation is required.
- Assets/screenshots are non-functional; avoid packaging them into the final plugin beyond README usage.

## README alignment
If you change user-facing behavior, update README.md and screenshots as needed.
