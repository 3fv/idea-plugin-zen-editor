package org.threeform.idea.plugins

import com.intellij.openapi.options.Configurable
import com.intellij.ui.ColorPanel
import javax.swing.*
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.GridBagConstraints
import java.awt.GridBagLayout

class ZenEditorSettingsConfigurable : Configurable {

    private var panel: JPanel? = null
    private lateinit var heightSlider: JSlider
    private lateinit var fontCombo: JComboBox<String>
    private lateinit var fontSizeSpinner: JSpinner
    private lateinit var alignmentCombo: JComboBox<HeaderAlignment>
    private lateinit var filenameColorCheck: JCheckBox
    private lateinit var filenameColorPanel: ColorPanel
    private lateinit var relativePathColorCheck: JCheckBox
    private lateinit var relativePathColorPanel: ColorPanel
    private lateinit var absolutePathColorCheck: JCheckBox
    private lateinit var absolutePathColorPanel: ColorPanel

    override fun getDisplayName(): String = "Zen Editor"

    override fun createComponent(): JComponent {
        if (panel == null) {
            panel = JPanel(GridBagLayout())
            val c = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
                gridx = 0
                gridy = 0
                ipadx = 4
                ipady = 4
                anchor = GridBagConstraints.NORTHWEST
            }

            val heightLabel = JLabel("Header height (base px, scales with IDE zoom):")
            panel!!.add(heightLabel, c)

            c.gridy++
            heightSlider = JSlider(ZenEditorSettings.MIN_HEIGHT, ZenEditorSettings.MAX_HEIGHT)
            heightSlider.majorTickSpacing = 10
            heightSlider.minorTickSpacing = 5
            heightSlider.paintTicks = true
            heightSlider.paintLabels = true
            panel!!.add(heightSlider, c)

            c.gridy++
            panel!!.add(JLabel("Font family:"), c)

            c.gridy++
            val families = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
            fontCombo = JComboBox(families)
            fontCombo.maximumRowCount = 20
            panel!!.add(fontCombo, c)

            c.gridy++
            panel!!.add(JLabel("Font size (base pt, scales with IDE zoom):"), c)

            c.gridy++
            fontSizeSpinner = JSpinner(SpinnerNumberModel(12, ZenEditorSettings.MIN_FONT_SIZE, ZenEditorSettings.MAX_FONT_SIZE, 1))
            panel!!.add(fontSizeSpinner, c)

            c.gridy++
            panel!!.add(JLabel("Header alignment:"), c)

            c.gridy++
            alignmentCombo = JComboBox(HeaderAlignment.values())
            panel!!.add(alignmentCombo, c)

            c.gridy++
            panel!!.add(Box.createVerticalStrut(8), c)

            c.gridy++
            panel!!.add(JLabel("Color overrides:").apply {
                font = font.deriveFont(Font.BOLD)
            }, c)

            filenameColorCheck = JCheckBox("Filename")
            filenameColorPanel = ColorPanel()
            c.gridy++
            panel!!.add(buildColorRow(filenameColorCheck, filenameColorPanel), c)

            relativePathColorCheck = JCheckBox("Relative path")
            relativePathColorPanel = ColorPanel()
            c.gridy++
            panel!!.add(buildColorRow(relativePathColorCheck, relativePathColorPanel), c)

            absolutePathColorCheck = JCheckBox("Absolute path")
            absolutePathColorPanel = ColorPanel()
            c.gridy++
            panel!!.add(buildColorRow(absolutePathColorCheck, absolutePathColorPanel), c)

            reset()
        }
        return panel as JPanel
    }

    private fun buildColorRow(check: JCheckBox, colorPanel: ColorPanel): JPanel {
        val row = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        row.add(check)
        row.add(colorPanel)
        check.addActionListener { colorPanel.isEnabled = check.isSelected }
        return row
    }

    override fun isModified(): Boolean {
        val s = ZenEditorSettings.getInstance().state
        return heightSlider.value != s.headerHeight ||
                fontCombo.selectedItem != s.fontFamily ||
                (fontSizeSpinner.value as Int) != s.fontSize ||
                alignmentCombo.selectedItem != s.headerAlignment ||
                effectiveOverride(filenameColorCheck, filenameColorPanel) != s.filenameColor ||
                effectiveOverride(relativePathColorCheck, relativePathColorPanel) != s.relativePathColor ||
                effectiveOverride(absolutePathColorCheck, absolutePathColorPanel) != s.absolutePathColor
    }

    override fun apply() {
        val settings = ZenEditorSettings.getInstance()
        val current = settings.state
        val newState = current.copy(
            headerHeight = heightSlider.value,
            fontFamily = fontCombo.selectedItem as String,
            fontSize = fontSizeSpinner.value as Int,
            headerAlignment = alignmentCombo.selectedItem as HeaderAlignment,
            filenameColor = effectiveOverride(filenameColorCheck, filenameColorPanel),
            relativePathColor = effectiveOverride(relativePathColorCheck, relativePathColorPanel),
            absolutePathColor = effectiveOverride(absolutePathColorCheck, absolutePathColorPanel)
        )
        settings.setAndNotify(newState)
    }

    override fun reset() {
        val s = ZenEditorSettings.getInstance().state
        heightSlider.value = s.headerHeight
        fontCombo.selectedItem = s.fontFamily
        fontSizeSpinner.value = s.fontSize
        alignmentCombo.selectedItem = s.headerAlignment
        loadOverride(filenameColorCheck, filenameColorPanel, s.filenameColor)
        loadOverride(relativePathColorCheck, relativePathColorPanel, s.relativePathColor)
        loadOverride(absolutePathColorCheck, absolutePathColorPanel, s.absolutePathColor)
    }

    private fun effectiveOverride(check: JCheckBox, colorPanel: ColorPanel): String? {
        if (!check.isSelected) return null
        val c = colorPanel.selectedColor ?: return null
        return "#%06X".format(c.rgb and 0xFFFFFF)
    }

    private fun loadOverride(check: JCheckBox, colorPanel: ColorPanel, value: String?) {
        val parsed = value?.trim()?.takeIf { it.isNotEmpty() }?.let {
            try { Color.decode(it) } catch (e: Exception) { null }
        }
        check.isSelected = parsed != null
        colorPanel.selectedColor = parsed
        colorPanel.isEnabled = check.isSelected
    }
}
