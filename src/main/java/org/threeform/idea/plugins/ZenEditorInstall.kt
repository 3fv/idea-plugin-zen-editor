package org.threeform.idea.plugins

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JLabel

/**
 * @author Jonathan Glanz
 */
class ZenEditorInstall(
    private val myProject: Project,
    private val myFile: VirtualFile, private val myEditor: FileEditor) {
    private val baselineVersion = ApplicationInfo.getInstance().build.baselineVersion
    private val filenameLabel = JBLabel().apply {
        val filePath = myFile.toNioPath().toAbsolutePath()
        var filePathStr = filePath.toString().replace('\\','/')
        val filePathDirStr = filePath.parent?.toString()?.replace('\\','/')
        val projectPath = myProject.basePath

        if (projectPath != null && filePathStr.startsWith(projectPath)) {
            filePathStr = filePathStr.substring(projectPath.length + 1)
        }

        font = font.deriveFont(font.size + 2f)
        text = """
            <html>
            <div style="font-family: '${font.name}'; font-size: ${font.size}pt; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
            <b>${myFile.name}</b>&nbsp;&nbsp;&nbsp;
            <i style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">(<span style="color: #61AFEF99;font-size: ${font.size - 1.0}pt;">${filePathStr}</span>)</i>
            </div>
            </html>""".trimIndent()

        minimumSize = DEFAULT_SIZE
        size = DEFAULT_SIZE
        preferredSize = DEFAULT_SIZE

        background = JBColor.PanelBackground
        foreground = JBColor.foreground()

        horizontalAlignment = JLabel.CENTER
        horizontalTextPosition = JLabel.CENTER
        verticalTextPosition = JLabel.CENTER

    }

    private val headerComponent = JBUI.Panels.simplePanel().apply {
        size = DEFAULT_SIZE
        minimumSize = DEFAULT_SIZE
        preferredSize  = DEFAULT_SIZE
        background = JBColor.PanelBackground

        insets.set(10,10,10,10)

        layout = BorderLayout()
        add(filenameLabel, BorderLayout.CENTER)
    }

    init {
        FileEditorManager.getInstance(myProject).addTopComponent(myEditor, headerComponent)
    }

    fun dispose() {
        FileEditorManager.getInstance(myProject).removeTopComponent(myEditor, headerComponent)
    }

    companion object {
        private val log = Logger.getInstance(
            ZenEditorInstall::class.java
        )
        private const val ACTION_ID_PREFIX = "ZenEditor"
        private val PLUGIN_ID = PluginId.getId("org.threeform.idea.plugins.zen_editor")
        private val DEFAULT_SIZE = java.awt.Dimension(200,50)
    }
}


