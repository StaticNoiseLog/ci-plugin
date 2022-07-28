package com.staticnoiselog.gradle.plugin.ci

import org.gradle.api.tasks.Exec
import java.io.ByteArrayOutputStream

abstract class DockerTask : Exec() {
    fun execWithBenefits() {
        project.logger.lifecycle("[command line]")
        project.logger.lifecycle("$ ${commandLine.joinToString(separator = " ")}")

        standardOutput = ByteArrayOutputStream() // capture output to display it through Gradle's logger
        super.exec()
        project.logger.lifecycle("$standardOutput")
        project.logger.lifecycle("executionResult: ${executionResult.orNull?.exitValue}")
    }
}