package com.mousegesture

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.mousegesture.services.GestureOrchestratorService

class GesturePluginStartup : StartupActivity.DumbAware {

    override fun runActivity(project: Project) {
        ApplicationManager.getApplication().service<GestureOrchestratorService>().start()
    }
}
