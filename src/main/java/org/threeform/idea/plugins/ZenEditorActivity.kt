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
 * ZenEditorActivity reconciles every open editor against the current
 * `zenModeEnabled` flag:
 *  - banner top-component is installed/removed
 *  - the enclosing `JBTabs` strip is hidden via the per-component
 *    `JBTabsPresentation.isHideTabs` flag (which, unlike
 *    `UISettings.editorTabPlacement = TABS_NONE`, does NOT force tabLimit
 *    to 1 — multi-tab management keeps working)
 *
 * @author Jonathan Glanz
 */
class ZenEditorActivity : ProjectActivity {
    private val activeEditors =
        Collections.synchronizedMap(IdentityHashMap<JComponent, Pair<Project, ZenEditorInstall>>())

    init {
        val connect = ApplicationManager.getApplication().messageBus.connect()

        connect.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                reconcile(source.project)
            }

            override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                reconcile(source.project)
            }
        })

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

        connect.subscribe(ZenEditorSettings.TOPIC, ZenSettingsListener { _ ->
            ProjectManager.getInstance().openProjects.forEach { p ->
                if (!p.isDisposed) reconcile(p)
            }
        })
    }

    private fun reconcile(project: Project) {
        val enabled = ZenEditorSettings.getInstance().state.zenModeEnabled
        val allEditors = FileEditorManager.getInstance(project).allEditors
        val liveComponents = allEditors.mapTo(mutableSetOf<JComponent>()) { it.component }

        // Drop entries for editors that closed while we weren't looking
        activeEditors.entries.toList()
            .filter { it.value.first == project && it.key !in liveComponents }
            .forEach { entry ->
                entry.value.second.dispose()
                activeEditors.remove(entry.key)
            }

        if (enabled) {
            allEditors.forEach { editor -> ensureBanner(project, editor) }
        } else {
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
     * `UISettings.editorTabPlacement` and therefore leaves tabLimit and the
     * tab model intact.
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
