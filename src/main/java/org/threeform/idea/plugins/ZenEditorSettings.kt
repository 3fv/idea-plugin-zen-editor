package org.threeform.idea.plugins

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.messages.Topic
import java.awt.Font
import javax.swing.UIManager

@State(name = "ZenEditorSettings", storages = [Storage("zen-editor.xml")])
@Service(Service.Level.APP)
class ZenEditorSettings : PersistentStateComponent<ZenEditorSettings.State> {

    data class State(
        var headerHeight: Int = 80,
        var fontFamily: String = defaultFont().family,
        var fontSize: Int = defaultFont().size,
        var zenModeEnabled: Boolean = true
    )

    private var myState: State = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
        clampValues()
    }

    private fun clampValues() {
        myState.headerHeight = myState.headerHeight.coerceIn(MIN_HEIGHT, MAX_HEIGHT)
        myState.fontSize = myState.fontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
    }

    fun setAndNotify(newState: State) {
        myState = newState
        clampValues()
        ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).settingsChanged(myState)
    }

    companion object {
        const val MIN_HEIGHT = 40
        const val MAX_HEIGHT = 100

        const val MIN_FONT_SIZE = 8
        const val MAX_FONT_SIZE = 48

        val TOPIC: Topic<ZenSettingsListener> = Topic.create(
            "ZenEditorSettingsChanged",
            ZenSettingsListener::class.java
        )

        fun getInstance(): ZenEditorSettings = service()

        private fun defaultFont(): Font {
            val f = UIManager.getFont("Label.font")
            return f ?: Font("Dialog", Font.PLAIN, 12)
        }
    }
}

fun interface ZenSettingsListener {
    fun settingsChanged(state: ZenEditorSettings.State)
}
