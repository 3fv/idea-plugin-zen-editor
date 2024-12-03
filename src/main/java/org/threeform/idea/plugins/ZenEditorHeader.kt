package org.threeform.idea.plugins

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.text.BadLocationException

class ZenEditorHeader(
  private val myProject: Project,
  private val myFile: VirtualFile
) : JPanel() {
  private val filenameLabel: JBLabel
  private val pathLabel: JLabel
  private val ellipsisLabel: JLabel = JBLabel("...")
  
  init {
    val filePath = myFile.toNioPath().toAbsolutePath()
    var filePathStr = filePath.toString().replace('\\', '/')
//    val filePathDirStr = filePath.parent?.toString()?.replace('\\', '/')
    val projectPath = myProject.basePath

    if (projectPath != null && filePathStr.startsWith(projectPath)) {
      filePathStr = filePathStr.substring(projectPath.length + 1)
    }
    
    filenameLabel = JBLabel(myFile.name).apply {
      font = font.deriveFont(font.size + 2f)
      border = JBUI.Borders.emptyRight(5)
    }
    
    val pathColor = Color.decode("#61AFEF99")
    
    pathLabel = ZenEditorShrinkableLabel(filePathStr).apply {
      foreground = pathColor
      border = JBUI.Borders.emptyTop(2)
    }
    
    ellipsisLabel.foreground = pathColor
    
  }
  
  val preferredFilenameWidth: Int
    get() = filenameLabel.preferredSize.width

  val preferredPathWidth: Int
    get() = pathLabel.preferredSize.width

  val totalPadding: Int
    get() =  (PADDING_WIDTH * 3)

  val filenameWidth: Int
    get() = preferredFilenameWidth + (PADDING_WIDTH * 2)

  val availableWidth: Int
    get() = size.width - (PADDING_WIDTH * 3)

  val minPathWidth: Int
    get() = ellipsisLabel.preferredSize.width


  override fun paint(g: Graphics) {

    super.paint(g)
    thisLogger().info("Painting zen_editor header, size=${this.size}")

    val size = this.size

    val paintEllipsis = this.preferredSize.width > size.width
    if (!paintEllipsis) {
      super.paint(g)
    } else {
      ellipsisLabel.foreground = foreground
      val ellipsisSize: Dimension = ellipsisLabel.getPreferredSize()
      var endOffset = size.width - ellipsisSize.width

      try {
        endOffset = Point(endOffset, this.height / 2).x - 1
      } catch (_: BadLocationException) {
      }

      val oldClip = g.clip
      g.clipRect(0, 0, endOffset, size.height)
      super.paint(g)
      g.clip = oldClip
      g.translate(endOffset, 0)
      ellipsisLabel.size = ellipsisSize
      ellipsisLabel.paint(g)
      g.translate(-endOffset, 0)
    }
  }

  companion object {
    const val PADDING_WIDTH = 5
  }
}