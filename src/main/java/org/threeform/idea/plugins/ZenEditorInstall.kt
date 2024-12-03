package org.threeform.idea.plugins

import com.intellij.ui.dsl.builder.panel
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Row
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JLabel

/**
 * @author Jonathan Glanz
 */
class ZenEditorInstall(
  private val myProject: Project,
  private val myFile: VirtualFile, private val myEditor: FileEditor
) {
  private val baselineVersion = ApplicationInfo.getInstance().build.baselineVersion

  private val filenameLabel = JBLabel().apply {
    val filePath = myFile.toNioPath().toAbsolutePath()
    var filePathStr = filePath.toString().replace('\\', '/')
    val filePathDirStr = filePath.parent?.toString()?.replace('\\', '/')
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

//    private val headerComponent = JBUI.Panels.simplePanel().apply {
//        size = DEFAULT_SIZE
//        minimumSize = DEFAULT_SIZE
//        preferredSize  = DEFAULT_SIZE
//        background = JBColor.PanelBackground
//
//        insets.set(10,10,10,10)
//
//        layout = BorderLayout()
//        add(filenameLabel, BorderLayout.CENTER)
//    }
private val headerContent = ZenEditorHeader(myProject, myFile).apply {
  size = DEFAULT_SIZE
  minimumSize = DEFAULT_SIZE
  preferredSize = DEFAULT_SIZE
  background = JBColor.PanelBackground

}
//  private val headerContent = JBUI.Panels.simplePanel().apply {
//    size = DEFAULT_SIZE
//    minimumSize = DEFAULT_SIZE
//    preferredSize = DEFAULT_SIZE
//    background = JBColor.PanelBackground
//
//    insets.set(10, 10, 10, 10)
//    val gbLayout = GridBagLayout().apply {
//      insets.set(10, 10, 10, 10)
//    }
//    layout = gbLayout
//
//    val filePath = myFile.toNioPath().toAbsolutePath()
//    var filePathStr = filePath.toString().replace('\\', '/')
////    val filePathDirStr = filePath.parent?.toString()?.replace('\\', '/')
//    val projectPath = myProject.basePath
//
//    if (projectPath != null && filePathStr.startsWith(projectPath)) {
//      filePathStr = filePathStr.substring(projectPath.length + 1)
//    }
//    val pathLabel = ZenEditorShrinkableLabel(filePathStr).apply {
//      foreground = Color.decode("#61AFEF99")
//      border = JBUI.Borders.emptyTop(2)
//      minimumSize = minimumSize.apply {
//        width = 10
//      }
//    }
//    val filenameLabel = JBLabel(myFile.name).apply {
//      font = font.deriveFont(font.size + 2f)
//      border = JBUI.Borders.emptyRight(5)
//    }
//
////        minimumSize = DEFAULT_SIZE
////        size = DEFAULT_SIZE
////        preferredSize = DEFAULT_SIZE
//
//    background = JBColor.PanelBackground
//    foreground = JBColor.foreground()
//
////        horizontalAlignment = JLabel.CENTER
////        horizontalTextPosition = JLabel.CENTER
////        verticalTextPosition = JLabel.CENTER
//
//    add(filenameLabel, GridBagConstraints().apply {
////      weightx = 1.0
////            fill = GridBagConstraints.HORIZONTAL
////            anchor = GridBagConstraints.EAST
//      gridx = 0
//      gridy = 0
//
//      fill = GridBagConstraints.NONE
//    })
//
////    add(JBLabel(" ("), GridBagConstraints().apply {
//////            weightx = 0.0
//////            fill = GridBagConstraints.HORIZONTAL
//////            anchor = GridBagConstraints.EAST
////      gridx = 1
////      gridy = 0
////      fill = GridBagConstraints.NONE
////    })
//
//    add(pathLabel, GridBagConstraints().apply {
////      weightx = 0.5
////            anchor = GridBagConstraints.WEST
////            fill = GridBagConstraints.HORIZONTAL
//      gridx = 1
//      gridy = 0
//      fill = GridBagConstraints.NONE
//    })
//
////    add(JBLabel(") "), GridBagConstraints().apply {
//////            weightx = 0.0
//////            fill = GridBagConstraints.HORIZONTAL
//////            anchor = GridBagConstraints.EAST
////      gridx = 3
////      gridy = 0
////      fill = GridBagConstraints.NONE
////    })
//
//    val filenameWidth = filenameLabel.preferredSize.width
//    gbLayout.columnWidths = intArrayOf(
//      filenameWidth,
//      0
//    )
//
//  }
//    private val headerComponent = JBUI.Panels.simplePanel().apply {
//        size = DEFAULT_SIZE
//        minimumSize = DEFAULT_SIZE
//        preferredSize  = DEFAULT_SIZE
//        background = JBColor.PanelBackground
//
//        insets.set(10,10,10,10)
//        layout = BorderLayout()
//
//        background = JBColor.PanelBackground
//        foreground = JBColor.foreground()
//
//        add(headerContent, BorderLayout.CENTER)
//    }

  init {
//        FileEditorManager.getInstance(myProject).addTopComponent(myEditor, headerComponent)
    FileEditorManager.getInstance(myProject).addTopComponent(myEditor, headerContent)
  }

  fun dispose() {
//        FileEditorManager.getInstance(myProject).removeTopComponent(myEditor, headerComponent)
    FileEditorManager.getInstance(myProject).removeTopComponent(myEditor, headerContent)
  }

  companion object {
    private val log = Logger.getInstance(
      ZenEditorInstall::class.java
    )
    private const val ACTION_ID_PREFIX = "ZenEditor"
    private val PLUGIN_ID = PluginId.getId("org.threeform.idea.plugins.zen_editor")
    private val DEFAULT_SIZE = java.awt.Dimension(200, 50)
  }
}


