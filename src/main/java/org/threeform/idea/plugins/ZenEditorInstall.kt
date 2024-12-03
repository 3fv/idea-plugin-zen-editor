package org.threeform.idea.plugins

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor

/**
 * @author Jonathan Glanz
 */
class ZenEditorInstall(
    val myProject: Project,
    val myFile: VirtualFile,
    val myEditor: FileEditor
) {
    private val baselineVersion = ApplicationInfo.getInstance().build.baselineVersion

    private val headerContent = ZenEditorHeader(myProject, myFile).apply {
        size = DEFAULT_SIZE
        minimumSize = DEFAULT_SIZE
        preferredSize = DEFAULT_SIZE
        background = JBColor.PanelBackground

    }

    val myPath = myFile.path

    init {
        FileEditorManager.getInstance(myProject).addTopComponent(myEditor, headerContent)
    }

    fun dispose() {
        FileEditorManager.getInstance(myProject).removeTopComponent(myEditor, headerContent)
    }

    companion object {
        @Suppress("unused")
        private val log = Logger.getInstance(
            ZenEditorInstall::class.java
        )
        private const val ACTION_ID_PREFIX = "ZenEditor"
        private val PLUGIN_ID = PluginId.getId("org.threeform.idea.plugins.zen_editor")
        private val DEFAULT_SIZE = java.awt.Dimension(200, 50)
    }
}


