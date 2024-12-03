package org.threeform.idea.plugins

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import java.util.*
import javax.swing.JComponent


/**
 * ZenEditorActivity is responsible for both
 * immediately decorating all editors and listening
 * for new editors and closing editors
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
                super.fileOpened(source, file)
                setupAllEditors(source.project)
            }

            override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                setupAllEditors(source.project)
                super.fileClosed(source, file)

            }
        })

        connect.subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val editorsToSetup: MutableList<Pair<Project, ZenEditorInstall>> = mutableListOf()
                    events.forEach { ev ->
                        if (ev !is VFilePropertyChangeEvent || ev.propertyName != "name") return@forEach


                        activeEditors.forEach { (_, pair) ->
                            val install = pair.second
                            if (install.myFile == ev.file || install.myFile.path == ev.file.path || install.myFile.path == ev.oldPath) {
                                editorsToSetup.add(Pair(install.myProject, install))
                            }
                        }
                    }
                    editorsToSetup.forEach { (project, install) ->
                        setupEditor(project, install.myEditor)
                    }

                }
            })
    }

    /**
     * Iterate all editors for a project,
     * dispose of decorations that have been removed,
     * and decorate new editors
     */
    private fun setupAllEditors(project: Project) {
        // GET ALL CURRENT EDITORS
        val allEditors = FileEditorManager.getInstance(project).allEditors
        val editorComponents = allEditors.map { it.component }

        // FIND THE REMOVED EDITORS (WHICH WERE DECORATED) &
        // REMOVE THE DECORATIONS TO AVOID LEAKS
        activeEditors.keys
            .filter { !editorComponents.contains(it) }
            .forEach {
                activeEditors[it]?.second?.dispose()
                activeEditors.remove(it)
            }

        // FINALLY ENSURE ALL ACTIVE EDITORS ARE DECORATED
        allEditors.forEach {
            setupEditor(project, it)
        }
    }

    /**
     * Setup an individual editor/header
     */
    private fun setupEditor(project: Project, editor: FileEditor?) {
        if (log.isDebugEnabled)
            log.debug("Setting up editor: ${editor?.file?.name ?: "null"}")

        if (editor == null) {
            return
        }

        if (activeEditors.containsKey(editor.component)) {
            val install = activeEditors[editor.component]!!.second
            if (install.myPath === editor.file.path && install.myFile == editor.file && install.myFile.path == editor.file.path) {
                return
            }

            install.dispose()
            activeEditors.remove(editor.component)
        }

        activeEditors[editor.component] = Pair(project, ZenEditorInstall(project, editor.file, editor))


    }

    override suspend fun execute(project: Project) {
        log.info("Setting up zen_editor for project: ${project.name}")
        setupAllEditors(project)
    }

    companion object {
        private val log = Logger.getInstance(
            ZenEditorInstall::class.java
        )
    }
}
