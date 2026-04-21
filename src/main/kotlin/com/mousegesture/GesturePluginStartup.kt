package com.mousegesture

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.mousegesture.services.GestureOrchestratorService

class GesturePluginStartup : ProjectActivity {

    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().service<GestureOrchestratorService>()
    }
}
