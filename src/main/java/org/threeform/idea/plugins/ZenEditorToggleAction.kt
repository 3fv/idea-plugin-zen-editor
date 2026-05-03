package org.threeform.idea.plugins

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction

class ZenEditorToggleAction : DumbAwareToggleAction("Toggle VSCode Zen Editor Mode") {

    override fun isSelected(e: AnActionEvent): Boolean =
        ZenEditorSettings.getInstance().state.zenModeEnabled

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val settings = ZenEditorSettings.getInstance()
        val current = settings.state
        if (current.zenModeEnabled == state) return
        settings.setAndNotify(current.copy(zenModeEnabled = state))
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
