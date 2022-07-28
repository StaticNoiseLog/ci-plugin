package com.staticnoiselog.gradle.plugin.ci

import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class DockerPushImageTask : DockerTask() {
    @Internal
    var configuration: Configuration? = null

    @TaskAction
    override fun exec() {
        commandLine(
            "docker",
            "push",
            "--all-tags", // includes "latest"
            "${configuration?.value(CiPluginExtension::dockerRepository)}/${project.group}/${project.name}"
        )

        execWithBenefits()
    }
}