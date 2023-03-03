package com.staticnoiselog.gradle.plugin.ci

import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class DockerBuildImageTask : DockerTask() {
    @InputDirectory
    var dockerBuildImageWorkingDir: File? = null

    @OutputFile
    var imageIdFile: File? = null

    @Internal
    var configuration: Configuration? = null

    @TaskAction
    override fun exec() {
        workingDir = dockerBuildImageWorkingDir!!

        val dockerImageBaseName =
            "${configuration?.value(CiPluginExtension::dockerRepository)}/${project.group}/${project.name}"
        val sanitizedProjectVersion = replaceInvalidDockerTagCharacters("${project.version}")

        commandLine(
            "docker",
            "build",
            "--iidfile",
            DOCKER_IMAGE_ID_FILENAME,
            "--build-arg",
            "ARTIFACT_FILE=./${configuration?.value(CiPluginExtension::dockerArtifactFile)}", // syntax for Docker COPY requires leading period and slash as separator
            "-t",
            "$dockerImageBaseName:$sanitizedProjectVersion",
            "-t",
            "$dockerImageBaseName:latest",
            "."
        )

        execWithBenefits()
    }
}

/**
 * On Git branches, the semver plugin adds the branch name and the short Git hash to the project version. The separator
 * used before the Git hash is a plus sign and that is not compatible with "docker build -t name:tag". This function
 * replaces all characters that are not compatible with Docker tag naming conventions by an underscore.
 */
private val DOCKER_TAG_REGEX = Regex("[^a-zA-Z\\d_.-]")
fun replaceInvalidDockerTagCharacters(s: String?): String? {
    if (s == null) {
        return null
    }
    return DOCKER_TAG_REGEX.replace(s, "_") // replace any illegal characters with an underscore
}