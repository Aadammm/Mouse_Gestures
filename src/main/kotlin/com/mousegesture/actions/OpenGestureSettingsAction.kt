package com.mousegesture.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.mousegesture.configs.GestureSettingsConfig
import com.mousegesture.configs.GestureSettingsDialog
import com.mousegesture.services.GestureManagerService
import javax.swing.SwingUtilities

class MouseGesturesActionGroup : DefaultActionGroup() {

    init {
        templatePresentation.text = "Mouse Gestures"
        isPopup = true
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val manager = ApplicationManager.getApplication().service<GestureManagerService>()

        val openSettings = object : AnAction("Settings...") {
            override fun actionPerformed(ev: AnActionEvent) {
                SwingUtilities.invokeLater {
                    GestureSettingsDialog(ev.project, GestureSettingsConfig()).showAndGet()
                }
            }

            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        }

        val togglePlugin = object : AnAction() {
            override fun actionPerformed(ev: AnActionEvent) {
                manager.isPluginEnabled = !manager.isPluginEnabled
            }

            override fun update(ev: AnActionEvent) {
                ev.presentation.text = if (manager.isPluginEnabled) "Disable Plugin" else "Enable Plugin"
            }

            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        }

        return arrayOf(openSettings, Separator.getInstance(), togglePlugin)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = true
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
