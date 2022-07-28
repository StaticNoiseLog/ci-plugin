package com.staticnoiselog.gradle.plugin.ci

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class DockerRemoveImageTask : DockerTask() {
    @InputFile // cause the task to be considered out-of-date when the file path or contents have changed
    abstract fun getImageIdFile(): RegularFileProperty

    @InputDirectory
    var dockerBuildImageWorkingDir: File? = null

    @TaskAction
    override fun exec() {
        workingDir = dockerBuildImageWorkingDir!!

        val imageId = getImageIdFile().asFile.get().readText().substring(7)

        commandLine(
            "docker",
            "rmi",
            "-f", // includes "latest"
            imageId
        )

        execWithBenefits()

        if (executionResult.orNull?.exitValue == 0) {
            // deleting the working directory reverts the work of DOCKER_PREPARE_CONTEXT_TASK_NAME
            dockerBuildImageWorkingDir?.deleteRecursively()
        }
    }
}