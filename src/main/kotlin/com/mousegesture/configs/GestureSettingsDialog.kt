package com.mousegesture.configs

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.Dimension
import javax.swing.JComponent

class GestureSettingsDialog(
    project: Project?,
    private val config: Configurable
) : DialogWrapper(project, true) {

    init {
        title = "Mouse Gestures"
        init()
    }

    override fun createCenterPanel(): JComponent = config.createComponent()!!
    override fun getInitialSize(): Dimension = Dimension(860, 620)
    override fun getPreferredFocusedComponent(): JComponent? = null

    override fun doOKAction() {
        config.apply()
        super.doOKAction()
    }

    override fun dispose() {
        config.disposeUIResources()
        super.dispose()
    }
}
