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
        filenameLabel.font = baseFont.deriveFont((baseFont.size + 2).toFloat())
        pathLabel.font = baseFont
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
     * Filename width
     */
    private val preferredFilenameWidth: Int
        get() = filenameLabel.preferredSize.width

    /**
     * The width required to show the complete relative path
     */
    private val preferredPathWidth: Int
        get() = pathLabel.preferredSize.width

    /**
     * All padding (left + middle + center)
     */
    private val totalPadding: Int
        get() = (PADDING_WIDTH * 3)

    /**
     * Width of the panel used to contain
     * content and enable fitment
     */
    private val availableWidth: Int
        get() = size.width

    /**
     * Width of filename label (same as preferred for the moment)
     */
    private val filenameWidth: Int
        get() = preferredFilenameWidth

    /**
     * Ideal content width to avoid shortening the path
     */
    private val preferredContentWidth: Int
        get() = filenameWidth + preferredPathWidth + totalPadding

    /**
     * Only used as a measurement
     */
    private val ellipsisWidth: Int
        get() = ellipsisLabel.preferredSize.width

    /**
     * Actual path width
     */
    private val pathWidth: Int
        get() {
            if (preferredContentWidth <= availableWidth) {
                return preferredPathWidth
            }
            val staticWidth = filenameWidth + totalPadding
            return (availableWidth - staticWidth).coerceAtLeast(ellipsisWidth)
        }

    /**
     * Actual content width
     */
    private val contentWidth: Int
        get() = filenameWidth + pathWidth + totalPadding

    /**
     * Whether to truncate the path label
     */
    private val shouldTruncatePath: Boolean
        get() = preferredContentWidth > availableWidth

    /**
     * Overridden paint allows for us to
     * check the calculated size of the path
     * label & adjust the content via ellipsis
     * if needed
     */
    override fun paint(g: Graphics) {
        super.paint(g)

        if (log.isDebugEnabled)
            log.debug("Painting zen_editor header, size=${this.size}")

        val shouldTruncatePath = this.shouldTruncatePath
        val contentWidth = this.contentWidth
        val pathLabelHeight = pathLabel.preferredSize.height

        // DETERMINE START COORDINATE
        val startOffsetX = if (shouldTruncatePath) {
            PADDING_WIDTH
        } else {
            val middleX = size.width / 2
            val startX = middleX - (contentWidth / 2)
            if (log.isDebugEnabled)
                log.debug("Centering header: width=${size.width}, middleX=$middleX, startX=$startX, contentWidth=$contentWidth")
            startX
        }


        val startOffsetY = (size.height - pathLabelHeight) / 2

        // TRANSLATE TO START COORDINATE & PAINT
        g.translate(startOffsetX, startOffsetY)
        filenameLabel.size = filenameLabel.preferredSize
        filenameLabel.paint(g)

        // MOVE INTO POSITION TO DRAW THE PATH LABEL
        g.translate(filenameWidth + PADDING_WIDTH, 2)

        // SET THE CORRECT SIZE FOR THE PATH LABEL
        pathLabel.size = if (!shouldTruncatePath) {
             pathLabel.preferredSize
        } else {
            val endOffset = pathWidth - 1
            Dimension(endOffset, pathLabelHeight)

        }

        pathLabel.paint(g)
    }

    companion object {
        /**
         * Before / middle / end padding width
         */
        private const val PADDING_WIDTH = 5
    }
}