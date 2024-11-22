package org.threeform.idea.plugins

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
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
    private val activeEditors = Collections.synchronizedMap(IdentityHashMap<JComponent, ZenEditorInstall>())

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
                activeEditors[it]?.dispose()
                activeEditors.remove(it)
            }

        // FINALLY ENSURE ALL ACTIVE EDITORS ARE DECORATED
        allEditors.forEach {
            setupEditor(project, it)
        }
    }

    private fun setupEditor(project: Project, editor: FileEditor?) {
        val fem = FileEditorManager.getInstance(project)
        log.info("Setting up editor: ${editor?.file?.name ?: "null"}")

        if (editor == null || activeEditors.containsKey(editor.component)) {
            return
        }

        activeEditors[editor.component] = ZenEditorInstall(project, editor.file, editor)

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
