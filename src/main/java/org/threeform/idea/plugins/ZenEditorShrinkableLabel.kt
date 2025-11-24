package org.threeform.idea.plugins

import com.intellij.ui.components.JBLabel
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point
import javax.swing.JLabel
import javax.swing.text.BadLocationException

class ZenEditorShrinkableLabel(
  text: String,
) : JBLabel(text) {

  private val ellipsisLabel: JLabel = JBLabel("...")

//  override fun setForeground(fg: Color?) {
//    super.setForeground(fg)
//    ellipsisLabel.foreground = fg
//  }


  override fun paint(g: Graphics) {

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


}