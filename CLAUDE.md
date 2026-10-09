# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An IntelliJ Platform plugin (`org.threeform.idea.plugins.zen_editor`) that gives a VSCode-style "zen" editor: the editor tab strip is hidden and each editor gets a header banner showing file icon, filename, project-relative path and absolute path (click a path to copy it). Kotlin sources live under `src/main/java/` (not `src/main/kotlin/`).

## Commands

- `./gradlew runIde` — launch a sandbox IDE with the plugin (the main way to verify behavior; there are no tests).
- `./gradlew buildPlugin` — build the distributable ZIP under `build/distributions/`.
- `./gradlew build` — compile + checks.
- `./gradlew copy_jar` — copy the built jar into the `runIde` sandbox plugin dir (faster iteration against an existing sandbox).
- `./gradlew publishPlugin` — publish to JetBrains Marketplace (token from `PUBLISH_TOKEN` or `JB_PLUGIN_TOKEN`; signing uses `CERTIFICATE_CHAIN`/`PRIVATE_KEY`/`PRIVATE_KEY_PASSWORD` when all three are set).

There is no `src/test`; if tests are added, `./gradlew test --tests '<FQN>'` runs a single one.

## Build facts

- Targets IntelliJ IDEA `2025.3.5`, `sinceBuild = 253` (no `untilBuild`). JDK 21 (CI uses Temurin 21).
- Version is derived, never hard-coded: `PLUGIN_VERSION` env var → latest `v*` git tag → `0.0.0-SNAPSHOT`, with the `v` prefix stripped.
- `buildSearchableOptions` sets `idea.required.plugins.id` to this plugin's id so bundled Grazie doesn't load and hang headless shutdown on a JetBrains Account auth call. Keep it if touching that task.
- Gradle configuration cache and build cache are enabled (`gradle.properties`); build-script logic must stay configuration-cache compatible (use `providers.*`, not eager `exec`).

## Release

`.github/workflows/release.yml` runs on **every push to `master`**: it bumps the patch of the latest GitHub release, creates the tag + release, and runs `publishPlugin` to the Marketplace. Pushing to `master` is a public release.

## Architecture

All registrations are in `src/main/resources/META-INF/plugin.xml`: one `postStartupActivity`, one `applicationConfigurable`, and the `ZenEditor.ToggleZenMode` action (View menu).

- **`ZenEditorSettings`** — app-level `PersistentStateComponent` (stored in `zen-editor.xml`). `State` holds `zenModeEnabled`, header height/font/alignment and optional `#RRGGBB` color overrides. All changes go through `setAndNotify()`, which clamps values and publishes on `ZenEditorSettings.TOPIC`. The toggle action and the settings page (`ZenEditorSettingsConfigurable`) both write via this path.
- **`ZenEditorActivity`** — the central controller. Its `init` subscribes (app message bus) to file-editor open/close/selection, app activation, VFS rename events, and the settings topic; each triggers `reconcile(project)`, which diffs open editors against `activeEditors` (identity map keyed by editor `JComponent`, since one file can have several editors), installs/disposes banners, and toggles the tab strip.
  - Tabs are hidden via the per-widget `JBTabs.presentation.isHideTabs`, found by walking the Swing parent chain. This deliberately avoids `UISettings.editorTabPlacement = TABS_NONE`, which would force `tabLimit = 1` and break multi-file tab management.
  - The IDE flips `isHideTabs` back on its own and `setHideTabs` fires no property event, so a 500 ms Swing `Timer` watchdog re-enforces it on tracked widgets while zen mode is on, plus immediate re-application on selection change and window activation.
  - The message-bus connection is not tied to a `Disposable`, so the plugin is not dynamic-unload safe.
- **`ZenEditorInstall`** — one per decorated editor; adds/removes the header via `FileEditorManager.addTopComponent`/`removeTopComponent`.
- **`ZenEditorHeader`** — custom `JPanel` that paints icon → filename → relative path / absolute path itself (labels are used only as paint delegates and for measurement), scales with `UISettings.fontScale`, truncates with an ellipsis, caches painted path bounds for click-to-copy hit-testing, and listens to both the settings topic and `UISettingsListener`. Connections are disconnected in `removeNotify`. The absolute path line is hidden when the file is outside the project root (it would duplicate the relative one).

## Notes

`.junie/guidelines.md` is partially stale (it cites IC 2025.2 and sinceBuild 242); `build.gradle.kts` is authoritative. If user-facing behavior changes, update `README.md` and the `plugin.xml` description/change-notes.
