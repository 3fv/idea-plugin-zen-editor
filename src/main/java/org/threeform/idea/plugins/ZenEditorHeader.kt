@file:Suppress("DuplicatedCode")

package org.threeform.idea.plugins

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.TabInfo
import com.intellij.util.messages.MessageBusConnection
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ToolTipManager
import kotlin.math.max
import kotlin.math.min
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
     * Absolute path on disk, rendered under the relative path à la VSCode
     */
    private val absPathLabel: JLabel

    /**
     * Only used as a measurement
     */
    private val ellipsisLabel: JLabel = JBLabel("...")

    private val relativePathStr: String
    private val absolutePathStr: String

    /**
     * The filename's pre-override foreground — captured once so override
     * removal cleanly restores the L&F-supplied color rather than `null`.
     */
    private val defaultFilenameForeground: Color

    /**
     * False when the file has no on-disk path (non-file editors such as an
     * AI agent thread view) — the path column is not rendered at all.
     */
    private val showPaths: Boolean

    /**
     * False when the file lives outside the project root (or path lookup
     * failed) and the two strings would render identically.
     */
    private val showAbsolutePath: Boolean

    /** Last painted screen-region of each path — used for click hit-testing. */
    private val relativePathBounds = Rectangle()
    private val absolutePathBounds = Rectangle()

    private var busConnection: MessageBusConnection? = null
    private var appBusConnection: MessageBusConnection? = null
    private var currentState: ZenEditorSettings.State = ZenEditorSettings.getInstance().state

    /** Tab whose title this header mirrors (non-file editors only); bound while displayed. */
    private var boundTab: TabInfo? = null
    private val tabTitleListener = PropertyChangeListener {
        ApplicationManager.getApplication().invokeLater {
            boundTab?.let { setTitle(resolveTitle(it.text)) }
        }
    }

    init {
        val absPath = try {
            myFile.toNioPath().toAbsolutePath().toString().replace('\\', '/')
        } catch (e: Exception) {
            null
        }
        showPaths = absPath != null
        absolutePathStr = absPath ?: ""

        val projectPath = myProject.basePath
        relativePathStr = if (absPath != null && projectPath != null && absPath.startsWith(projectPath)) {
            absPath.substring(projectPath.length + 1)
        } else {
            absolutePathStr
        }

        showAbsolutePath = showPaths && relativePathStr != absolutePathStr

        val fileIcon: Icon? = try {
            myFile.fileType.icon
        } catch (e: Exception) {
            null
        }
        iconLabel = JBLabel().apply { fileIcon?.let { icon = it } }

        filenameLabel = JBLabel(resolveTitle(null))
        defaultFilenameForeground = filenameLabel.foreground

        pathLabel = JBLabel(relativePathStr)
        absPathLabel = JBLabel(absolutePathStr)

        ellipsisLabel.foreground = DEFAULT_RELATIVE_PATH_COLOR

        // Register for dynamic tooltips (text supplied by getToolTipText(MouseEvent))
        ToolTipManager.sharedInstance().registerComponent(this)

        val mouseHandler = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                when {
                    relativePathBounds.contains(e.point) ->
                        copyToClipboard(relativePathStr, e)
                    showAbsolutePath && absolutePathBounds.contains(e.point) ->
                        copyToClipboard(absolutePathStr, e)
                }
            }

            override fun mouseMoved(e: MouseEvent) {
                val overPath = relativePathBounds.contains(e.point) ||
                    (showAbsolutePath && absolutePathBounds.contains(e.point))
                cursor = if (overPath) {
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                } else {
                    Cursor.getDefaultCursor()
                }
            }

            override fun mouseExited(e: MouseEvent) {
                cursor = Cursor.getDefaultCursor()
            }
        }
        addMouseListener(mouseHandler)
        addMouseMotionListener(mouseHandler)

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

    override fun getToolTipText(event: MouseEvent): String? = when {
        relativePathBounds.contains(event.point) -> "Click to copy relative path"
        showAbsolutePath && absolutePathBounds.contains(event.point) -> "Click to copy absolute path"
        else -> null
    }

    private fun copyToClipboard(text: String, e: MouseEvent) {
        CopyPasteManager.getInstance().setContents(StringSelection(text))
        val origin = RelativePoint(this, Point(e.x, e.y))
        JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder("Copied path", null, null, null)
            .setFadeoutTime(1200)
            .createBalloon()
            .show(origin, Balloon.Position.below)
    }

    /**
     * Regular on-disk files show their filename. Non-file editors (e.g. an AI
     * agent thread view) back onto a light file with an internal name like
     * `threadView-…`; their title comes from, in order:
     *  1. a public `getThreadTitle()` on the file — the agent plugin's full,
     *     untruncated session title (its tab title is capped at 32 chars);
     *     looked up reflectively so there is no compile-time dependency
     *  2. [tabText], the live text of the enclosing editor tab
     *  3. `EditorTabTitleProvider`, i.e. what the tab would show
     *  4. the file's presentable name
     * The header itself ellipsizes to the available width.
     */
    private fun resolveTitle(tabText: String?): String {
        if (myFile.isInLocalFileSystem) return myFile.name
        fullTitle()?.let { return it }
        tabText?.takeIf { it.isNotBlank() }?.let { return it }
        val providerTitle = try {
            EditorTabPresentationUtil.getEditorTabTitle(myProject, myFile)
        } catch (e: Exception) {
            log.warn("Failed to resolve editor tab title for ${myFile.name}", e)
            null
        }
        return providerTitle?.takeIf { it.isNotBlank() } ?: myFile.presentableName
    }

    private fun fullTitle(): String? = try {
        (myFile.javaClass.getMethod("getThreadTitle").invoke(myFile) as? String)
            ?.replace(WHITESPACE_RUN, " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    } catch (e: ReflectiveOperationException) {
        null
    }

    private fun setTitle(title: String) {
        if (title.isBlank() || title == filenameLabel.text) return
        filenameLabel.text = title
        revalidate()
        repaint()
    }

    override fun addNotify() {
        super.addNotify()
        if (!myFile.isInLocalFileSystem) bindTabTitle()
    }

    override fun removeNotify() {
        super.removeNotify()
        unbindTabTitle()
        appBusConnection?.disconnect()
        appBusConnection = null
    }

    /**
     * Mirror the enclosing tab's text. Non-file editor titles are often
     * resolved asynchronously or change later (a session gets named after
     * its first prompt), and the tab is the one place the IDE keeps current.
     * The tab strip is hidden in zen mode, but its `TabInfo` stays live.
     */
    private fun bindTabTitle() {
        unbindTabTitle()
        val ancestors = generateSequence<Component>(this) { it.parent }.toList()
        val tabsIndex = ancestors.indexOfFirst { it is JBTabs }
        if (tabsIndex < 0) return
        val tabs = ancestors[tabsIndex] as JBTabs
        val info = ancestors.subList(0, tabsIndex).firstNotNullOfOrNull { tabs.findInfo(it) } ?: return
        info.changeSupport.addPropertyChangeListener(TabInfo.TEXT, tabTitleListener)
        boundTab = info
        setTitle(resolveTitle(info.text))
    }

    private fun unbindTabTitle() {
        boundTab?.changeSupport?.removePropertyChangeListener(TabInfo.TEXT, tabTitleListener)
        boundTab = null
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
        pathLabel.font = baseFont.deriveFont((baseFont.size * 0.75f).coerceAtLeast(8f))
        absPathLabel.font = baseFont.deriveFont((baseFont.size * 0.625f).coerceAtLeast(8f))

        filenameLabel.foreground = parseColor(state.filenameColor) ?: defaultFilenameForeground
        pathLabel.foreground = parseColor(state.relativePathColor)
            ?.let { withAlpha(it, RELATIVE_PATH_ALPHA) }
            ?: DEFAULT_RELATIVE_PATH_COLOR
        absPathLabel.foreground = parseColor(state.absolutePathColor)
            ?.let { withAlpha(it, ABSOLUTE_PATH_ALPHA) }
            ?: DEFAULT_ABSOLUTE_PATH_COLOR
        ellipsisLabel.foreground = pathLabel.foreground

        revalidate()
        repaint()
    }

    private fun parseColor(hex: String?): Color? {
        val s = hex?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            Color.decode(s)
        } catch (e: Exception) {
            null
        }
    }

    private fun withAlpha(c: Color, alpha: Float): Color =
        Color(c.red, c.green, c.blue, (255 * alpha).roundToInt())

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

    /** Gap between the filename and the path column. */
    private val elementGap: Int
        get() = filenameFontSize.coerceAtLeast(10)

    private val iconWidth: Int
        get() = iconLabel.icon?.iconWidth ?: 0

    private val iconHeight: Int
        get() = iconLabel.icon?.iconHeight ?: 0

    private val filenameWidth: Int
        get() = filenameLabel.preferredSize.width

    /** Width before the filename: edge + icon + iconGap. */
    private val leadingWidth: Int
        get() = edgePadding + iconWidth + (if (iconWidth > 0) iconGap else 0)

    /**
     * Filename width actually painted — the preferred width, shrunk (and
     * ellipsized by the label) only once the path column is already down to
     * its ellipsis, or when there is no path column and the title alone
     * overflows.
     */
    private val filenamePaintWidth: Int
        get() {
            val reserved = leadingWidth + edgePadding +
                (if (showPaths) elementGap + ellipsisWidth else 0)
            return min(filenameWidth, (availableWidth - reserved).coerceAtLeast(ellipsisWidth))
        }

    private val preferredRelativePathWidth: Int
        get() = pathLabel.preferredSize.width

    private val preferredAbsolutePathWidth: Int
        get() = if (showAbsolutePath) absPathLabel.preferredSize.width else 0

    /** Path column hugs the wider of the two stacked path labels. */
    private val preferredPathColumnWidth: Int
        get() = max(preferredRelativePathWidth, preferredAbsolutePathWidth)

    private val ellipsisWidth: Int
        get() = ellipsisLabel.preferredSize.width

    /** Width before the path column: edge + icon + iconGap + filename + elementGap. */
    private val staticLeadingWidth: Int
        get() = leadingWidth + filenamePaintWidth + (if (showPaths) elementGap else 0)

    /** Full preferred container width (icon + gaps + labels + edges). */
    private val preferredContentWidth: Int
        get() = leadingWidth + filenameWidth +
            (if (showPaths) elementGap + preferredPathColumnWidth else 0) + edgePadding

    /** Path column width — shrinks with available space, never below the ellipsis. */
    private val pathColumnWidth: Int
        get() {
            if (preferredContentWidth <= availableWidth) return preferredPathColumnWidth
            return (availableWidth - staticLeadingWidth - edgePadding)
                .coerceAtLeast(ellipsisWidth)
        }

    private val shouldTruncatePath: Boolean
        get() = preferredContentWidth > availableWidth

    /**
     * Overridden paint draws icon → filename → [relative path / absolute
     * path] with vertical centering done independently per element. The
     * two paths are stacked vertically next to the filename so the bold
     * filename and the smaller path labels align cleanly regardless of
     * size. The painted bounds of each path are cached for hit-testing
     * by the click-to-copy mouse handler.
     */
    override fun paint(g: Graphics) {
        super.paint(g)

        if (log.isDebugEnabled)
            log.debug("Painting zen_editor header, size=${this.size}")

        val shouldTruncate = shouldTruncatePath
        val pathLabelHeight = pathLabel.preferredSize.height
        val absPathLabelHeight = if (showAbsolutePath) absPathLabel.preferredSize.height else 0
        val filenameLabelHeight = filenameLabel.preferredSize.height

        val pathColumn = when {
            !showPaths -> 0
            shouldTruncate -> pathColumnWidth
            else -> preferredPathColumnWidth
        }

        // Inner content width (no edge padding) — used for non-left alignments.
        val innerContentWidth = staticLeadingWidth - edgePadding + pathColumn

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
        val filenamePaint = filenamePaintWidth
        val gFilename = g.create(x, filenameY, filenamePaint, filenameLabelHeight)
        try {
            filenameLabel.size = Dimension(filenamePaint, filenameLabelHeight)
            filenameLabel.paint(gFilename)
        } finally {
            gFilename.dispose()
        }
        x += filenamePaint + elementGap

        if (!showPaths) {
            relativePathBounds.setBounds(0, 0, 0, 0)
            absolutePathBounds.setBounds(0, 0, 0, 0)
            return
        }

        // PATH STACK — relative path above absolute path, the pair centered vertically
        val stackGap = if (showAbsolutePath) (pathLabelHeight * 0.1f).roundToInt() else 0
        val pathStackHeight = pathLabelHeight + stackGap + absPathLabelHeight
        val pathStackTop = ((size.height - pathStackHeight) / 2).coerceAtLeast(0)

        val pathPaintWidth = if (shouldTruncate) pathColumn - 1 else pathColumn

        // RELATIVE PATH
        val relativeY = pathStackTop
        val gPath = g.create(x, relativeY, pathPaintWidth, pathLabelHeight)
        try {
            pathLabel.size = Dimension(pathPaintWidth, pathLabelHeight)
            pathLabel.paint(gPath)
        } finally {
            gPath.dispose()
        }
        relativePathBounds.setBounds(x, relativeY, pathPaintWidth, pathLabelHeight)

        // ABSOLUTE PATH
        if (showAbsolutePath) {
            val absoluteY = pathStackTop + pathLabelHeight + stackGap
            val gAbs = g.create(x, absoluteY, pathPaintWidth, absPathLabelHeight)
            try {
                absPathLabel.size = Dimension(pathPaintWidth, absPathLabelHeight)
                absPathLabel.paint(gAbs)
            } finally {
                gAbs.dispose()
            }
            absolutePathBounds.setBounds(x, absoluteY, pathPaintWidth, absPathLabelHeight)
        } else {
            absolutePathBounds.setBounds(0, 0, 0, 0)
        }
    }

    companion object {
        private const val RELATIVE_PATH_ALPHA = 0.85f
        private const val ABSOLUTE_PATH_ALPHA = 0.75f
        private val DEFAULT_RELATIVE_PATH_COLOR =
            Color(0x61, 0xAF, 0xEF, (255 * RELATIVE_PATH_ALPHA).roundToInt())
        private val DEFAULT_ABSOLUTE_PATH_COLOR =
            Color(0x61, 0xAF, 0xEF, (255 * ABSOLUTE_PATH_ALPHA).roundToInt())
        private val WHITESPACE_RUN = Regex("\\s+")
    }
}
