package org.threeform.idea.plugins

import com.intellij.openapi.options.Configurable
import javax.swing.*
import java.awt.GraphicsEnvironment
import java.awt.GridBagConstraints
import java.awt.GridBagLayout

class ZenEditorSettingsConfigurable : Configurable {

    private var panel: JPanel? = null
    private lateinit var heightSlider: JSlider
    private lateinit var fontCombo: JComboBox<String>
    private lateinit var fontSizeSpinner: JSpinner

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

            val heightLabel = JLabel("Header height (px):")
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
            panel!!.add(JLabel("Font size:"), c)

            c.gridy++
            fontSizeSpinner = JSpinner(SpinnerNumberModel(12, ZenEditorSettings.MIN_FONT_SIZE, ZenEditorSettings.MAX_FONT_SIZE, 1))
            panel!!.add(fontSizeSpinner, c)

            reset()
        }
        return panel as JPanel
    }

    override fun isModified(): Boolean {
        val s = ZenEditorSettings.getInstance().state
        return heightSlider.value != s.headerHeight ||
                fontCombo.selectedItem != s.fontFamily ||
                (fontSizeSpinner.value as Int) != s.fontSize
    }

    override fun apply() {
        val settings = ZenEditorSettings.getInstance()
        val newState = ZenEditorSettings.State(
            headerHeight = heightSlider.value,
            fontFamily = fontCombo.selectedItem as String,
            fontSize = fontSizeSpinner.value as Int
        )
        settings.setAndNotify(newState)
    }

    override fun reset() {
        val s = ZenEditorSettings.getInstance().state
        heightSlider.value = s.headerHeight
        fontCombo.selectedItem = s.fontFamily
        fontSizeSpinner.value = s.fontSize
    }
}
