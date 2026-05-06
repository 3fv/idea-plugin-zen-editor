@file:Suppress("DuplicatedCode")

package org.threeform.idea.plugins

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBusConnection
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Font
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.roundToInt

class ZenEditorHeader(
    val myProject: Project,
    val myFile: VirtualFile
) : JPanel() {

    /**
     * Logger
     */
    private val log = thisLogger()

    /**
     * File-type icon shown to the left of the filename
     */
    private val iconLabel: JBLabel

    /**
     * Just the filename
     */
    private val filenameLabel: JBLabel

    /**
     * Relative path to project root
     */
    private val pathLabel: JLabel

    /**
     * Only used as a measurement
     */
    private val ellipsisLabel: JLabel = JBLabel("...")

    private var busConnection: MessageBusConnection? = null
    private var appBusConnection: MessageBusConnection? = null
    private var currentState: ZenEditorSettings.State = ZenEditorSettings.getInstance().state

    init {
        val filePath = try {
            myFile.toNioPath().toAbsolutePath()
        } catch (e: Exception) {
            "NOT_AVAILABLE"
        }
        var filePathStr = filePath.toString().replace('\\', '/')
        val projectPath = myProject.basePath

        if (projectPath != null && filePathStr.startsWith(projectPath)) {
            filePathStr = filePathStr.substring(projectPath.length + 1)
        }

        val fileIcon: Icon? = try {
            myFile.fileType.icon
        } catch (e: Exception) {
            null
        }
        iconLabel = JBLabel().apply { fileIcon?.let { icon = it } }

        filenameLabel = JBLabel(myFile.name)

        val pathColor = Color.decode("#61AFEF99")

        pathLabel = JBLabel(filePathStr).apply { foreground = pathColor }

        ellipsisLabel.foreground = pathColor

        // Apply initial settings and subscribe to changes
        applySettings(ZenEditorSettings.getInstance().state)

        busConnection = myProject.messageBus.connect()
        busConnection?.subscribe(ZenEditorSettings.TOPIC, ZenSettingsListener { state ->
            applySettings(state)
        })

        appBusConnection = ApplicationManager.getApplication().messageBus.connect()
        appBusConnection?.subscribe(UISettingsListener.TOPIC, UISettingsListener {
            applySettings(currentState)
        })
    }

    override fun removeNotify() {
        super.removeNotify()
        appBusConnection?.disconnect()
        appBusConnection = null
    }

    private fun ideScale(): Float = UISettings.getInstance().fontScale

    private fun applySettings(state: ZenEditorSettings.State) {
        currentState = state
        val scale = ideScale()
        val scaledSize = (state.fontSize * scale).roundToInt()
            .coerceIn(ZenEditorSettings.MIN_FONT_SIZE, ZenEditorSettings.MAX_FONT_SIZE)
        val baseFont = try {
            Font(state.fontFamily, Font.PLAIN, scaledSize)
        } catch (e: Exception) {
            font
        }
        filenameLabel.font = baseFont.deriveFont(Font.BOLD, (baseFont.size + 5).toFloat())
        pathLabel.font = baseFont.deriveFont((baseFont.size - 1).coerceAtLeast(8).toFloat())
        revalidate()
        repaint()
    }

    override fun getPreferredSize(): Dimension {
        val s = super.getPreferredSize()
        val base = ZenEditorSettings.getInstance().state.headerHeight.coerceIn(
            ZenEditorSettings.MIN_HEIGHT,
            ZenEditorSettings.MAX_HEIGHT
        )
        val h = (base * ideScale()).roundToInt()
        return Dimension(s.width, h)
    }

    override fun getMinimumSize(): Dimension {
        val s = super.getMinimumSize()
        val h = ZenEditorSettings.MIN_HEIGHT
        return Dimension(s.width, h)
    }

    /**
     * Width of the panel used to contain
     * content and enable fitment
     */
    private val availableWidth: Int
        get() = size.width

    /**
     * Spacing scales with the filename font so the visual
     * rhythm holds across user-configured font sizes.
     */
    private val filenameFontSize: Int
        get() = filenameLabel.font.size

    /** Outer left/right padding — gives the banner real edge breathing room. */
    private val edgePadding: Int
        get() = filenameFontSize.coerceAtLeast(12)

    /** Gap between the file icon and the filename. */
    private val iconGap: Int
        get() = (filenameFontSize * 0.5f).roundToInt().coerceAtLeast(6)

    /** Gap between the filename and the relative path. */
    private val elementGap: Int
        get() = filenameFontSize.coerceAtLeast(10)

    private val iconWidth: Int
        get() = iconLabel.icon?.iconWidth ?: 0

    private val iconHeight: Int
        get() = iconLabel.icon?.iconHeight ?: 0

    private val filenameWidth: Int
        get() = filenameLabel.preferredSize.width

    private val preferredPathWidth: Int
        get() = pathLabel.preferredSize.width

    private val ellipsisWidth: Int
        get() = ellipsisLabel.preferredSize.width

    /** Width before the path: edge + icon + iconGap + filename + elementGap. */
    private val staticLeadingWidth: Int
        get() = edgePadding + iconWidth + (if (iconWidth > 0) iconGap else 0) +
            filenameWidth + elementGap

    /** Full preferred container width (icon + gaps + labels + edges). */
    private val preferredContentWidth: Int
        get() = staticLeadingWidth + preferredPathWidth + edgePadding

    /** Path width — shrinks with available space, never below the ellipsis. */
    private val pathWidth: Int
        get() {
            if (preferredContentWidth <= availableWidth) return preferredPathWidth
            return (availableWidth - staticLeadingWidth - edgePadding)
                .coerceAtLeast(ellipsisWidth)
        }

    private val shouldTruncatePath: Boolean
        get() = preferredContentWidth > availableWidth

    /**
     * Overridden paint draws icon → filename → path with vertical
     * centering done independently per element so the bold filename and
     * the smaller path align cleanly regardless of size.
     */
    override fun paint(g: Graphics) {
        super.paint(g)

        if (log.isDebugEnabled)
            log.debug("Painting zen_editor header, size=${this.size}")

        val shouldTruncate = shouldTruncatePath
        val pathLabelHeight = pathLabel.preferredSize.height
        val filenameLabelHeight = filenameLabel.preferredSize.height

        // Inner content width (no edge padding) — used for non-left alignments.
        val innerContentWidth = staticLeadingWidth - edgePadding +
            (if (shouldTruncate) pathWidth else preferredPathWidth)

        val startX = when {
            // When content can't fit fully, alignment is moot — pin to the left.
            shouldTruncate -> edgePadding
            else -> when (currentState.headerAlignment) {
                HeaderAlignment.LEFT -> edgePadding
                HeaderAlignment.CENTER ->
                    ((size.width - innerContentWidth) / 2).coerceAtLeast(edgePadding)
                HeaderAlignment.RIGHT ->
                    (size.width - innerContentWidth - edgePadding).coerceAtLeast(edgePadding)
            }
        }

        var x = startX

        // ICON — vertically centered on the panel
        if (iconWidth > 0) {
            val iconY = (size.height - iconHeight) / 2
            iconLabel.icon?.paintIcon(this, g, x, iconY)
            x += iconWidth + iconGap
        }

        // FILENAME — vertically centered on the panel
        val filenameY = (size.height - filenameLabelHeight) / 2
        val gFilename = g.create(x, filenameY, filenameWidth, filenameLabelHeight)
        try {
            filenameLabel.size = filenameLabel.preferredSize
            filenameLabel.paint(gFilename)
        } finally {
            gFilename.dispose()
        }
        x += filenameWidth + elementGap

        // PATH — vertically centered, truncated by clipping when needed
        val pathPaintWidth = if (shouldTruncate) pathWidth - 1 else preferredPathWidth
        val pathY = (size.height - pathLabelHeight) / 2
        val gPath = g.create(x, pathY, pathPaintWidth, pathLabelHeight)
        try {
            pathLabel.size = Dimension(pathPaintWidth, pathLabelHeight)
            pathLabel.paint(gPath)
        } finally {
            gPath.dispose()
        }
    }
}