package org.threeform.idea.plugins

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.ui.tabs.JBTabs
import java.awt.Component
import java.util.*
import javax.swing.JComponent


/**
 * ZenEditorActivity is responsible for both
 * immediately decorating all editors and listening
 * for new editors and closing editors.
 *
 * In addition to managing the banner header it now reconciles every open
 * editor against the `zenModeEnabled` flag (toggled via
 * [ZenEditorToggleAction]):
 *
 *  - the banner top-component is installed when enabled, removed when
 *    disabled
 *  - the enclosing `JBTabs` strip is hidden via the per-component
 *    `JBTabsPresentation.isHideTabs` flag — unlike
 *    `UISettings.editorTabPlacement = TABS_NONE`, this does NOT force
 *    `tabLimit` to 1, so multi-tab management (recent files, no
 *    force-save on switch) keeps working
 *
 * @author Jonathan Glanz
 */
class ZenEditorActivity : ProjectActivity {
    /**
     * A map to keep track of all decorated `FileEditor`
     * instances.
     *
     * > NOTE: `JComponent` (the editor UI) is used as the key because a single
     * >  file can have multiple open editors
     */
    private val activeEditors =
        Collections.synchronizedMap(IdentityHashMap<JComponent, Pair<Project, ZenEditorInstall>>())

    init {
        // GET CONNECTION TO MESSAGE BUS
        val connect = ApplicationManager.getApplication().messageBus.connect()

        // ADD FILE EDITOR LISTENER
        connect.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                reconcile(source.project)
            }

            override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                reconcile(source.project)
            }
        })

        // ADD VFS LISTENER — refresh banners when their underlying file is renamed
        connect.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val toRefresh = mutableListOf<Pair<Project, ZenEditorInstall>>()
                events.forEach { ev ->
                    if (ev !is VFilePropertyChangeEvent || ev.propertyName != "name") return@forEach
                    activeEditors.forEach { (_, pair) ->
                        val install = pair.second
                        if (install.myFile == ev.file ||
                            install.myFile.path == ev.file.path ||
                            install.myFile.path == ev.oldPath
                        ) {
                            toRefresh.add(Pair(install.myProject, install))
                        }
                    }
                }
                toRefresh.forEach { (project, install) ->
                    refreshEditor(project, install.myEditor)
                }
            }
        })

        // ADD SETTINGS LISTENER — reconcile every open project when the
        // toggle action (or any other settings change) fires
        connect.subscribe(ZenEditorSettings.TOPIC, ZenSettingsListener { _ ->
            ProjectManager.getInstance().openProjects.forEach { p ->
                if (!p.isDisposed) reconcile(p)
            }
        })
    }

    /**
     * Iterate all editors for a project, dispose of decorations
     * for editors that have been removed, and decorate (or
     * undecorate) the survivors based on the current
     * `zenModeEnabled` flag.
     *
     * Also toggles the enclosing `JBTabs` strip via
     * `JBTabsPresentation.isHideTabs` so the tab UI hides/shows
     * in lock-step with the banner.
     */
    private fun reconcile(project: Project) {
        val enabled = ZenEditorSettings.getInstance().state.zenModeEnabled

        // GET ALL CURRENT EDITORS
        val allEditors = FileEditorManager.getInstance(project).allEditors
        val liveComponents = allEditors.mapTo(mutableSetOf<JComponent>()) { it.component }

        // FIND THE REMOVED EDITORS (WHICH WERE DECORATED) &
        // REMOVE THE DECORATIONS TO AVOID LEAKS
        activeEditors.entries.toList()
            .filter { it.value.first == project && it.key !in liveComponents }
            .forEach { entry ->
                entry.value.second.dispose()
                activeEditors.remove(entry.key)
            }

        if (enabled) {
            // FINALLY ENSURE ALL ACTIVE EDITORS ARE DECORATED
            allEditors.forEach { editor -> ensureBanner(project, editor) }
        } else {
            // ZEN MODE OFF — STRIP DECORATIONS FROM EVERY EDITOR IN THIS PROJECT
            activeEditors.entries.toList()
                .filter { it.value.first == project }
                .forEach { entry ->
                    entry.value.second.dispose()
                    activeEditors.remove(entry.key)
                }
        }

        applyTabVisibility(allEditors.toList(), enabled)
    }

    /**
     * Toggle the tab strip on every distinct `JBTabs` widget that hosts one
     * of the supplied editors. Uses the public per-component
     * `JBTabsPresentation.isHideTabs` flag, which does NOT touch
     * `UISettings.editorTabPlacement` and therefore leaves `tabLimit` and
     * the tab model intact.
     */
    private fun applyTabVisibility(editors: List<FileEditor>, hide: Boolean) {
        val seen = mutableSetOf<JBTabs>()
        editors.forEach { editor ->
            val tabs = findEnclosingTabs(editor.component) ?: return@forEach
            if (seen.add(tabs)) {
                try {
                    tabs.presentation.isHideTabs = hide
                } catch (t: Throwable) {
                    log.warn("Failed to toggle JBTabs.isHideTabs", t)
                }
            }
        }
    }

    /**
     * Setup an individual editor/header. Idempotent: if the
     * editor is already decorated for the current file/path the
     * call is a no-op.
     */
    private fun ensureBanner(project: Project, editor: FileEditor) {
        val existing = activeEditors[editor.component]?.second
        if (existing != null &&
            existing.myFile == editor.file &&
            existing.myPath == editor.file.path
        ) {
            return
        }
        existing?.dispose()
        activeEditors[editor.component] = Pair(project, ZenEditorInstall(project, editor.file, editor))
    }

    /**
     * Force re-creation of an editor's decoration — used after a
     * rename so the banner picks up the new path. No-op when zen
     * mode is disabled.
     */
    private fun refreshEditor(project: Project, editor: FileEditor) {
        if (!ZenEditorSettings.getInstance().state.zenModeEnabled) return
        activeEditors[editor.component]?.second?.dispose()
        activeEditors.remove(editor.component)
        ensureBanner(project, editor)
    }

    override suspend fun execute(project: Project) {
        log.info("Setting up zen_editor for project: ${project.name}")
        reconcile(project)
    }

    /**
     * Walk the Swing parent chain looking for the enclosing tabs widget.
     * Talks only to the public `JBTabs` interface — no reflection, no cast
     * to internal `JBTabsImpl`.
     */
    private fun findEnclosingTabs(component: Component): JBTabs? {
        var c: Component? = component
        while (c != null) {
            if (c is JBTabs) return c
            c = c.parent
        }
        return null
    }

    companion object {
        private val log = Logger.getInstance(ZenEditorInstall::class.java)
    }
}
