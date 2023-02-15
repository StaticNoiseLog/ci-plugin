package com.staticnoiselog.gradle.plugin.ci

import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.*
import org.gradle.jvm.tasks.Jar
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

        val jarTaskCollection: TaskCollection<Jar> = project.tasks.withType(Jar::class.java)
        val jarTask = jarTaskCollection.findByName(JavaPlugin.JAR_TASK_NAME)
        val jarNameRaw =
            "./${jarTask?.archiveFileName?.orNull}" // syntax for Docker COPY requires leading period and slash as separator
        val fatJarName = stripPlain(jarNameRaw)

        val dockerImageBaseName =
            "${configuration?.value(CiPluginExtension::dockerRepository)}/${project.group}/${project.name}"
        val projectVersionShortForm = segmentBeforePlusSign("${project.version}")

        commandLine(
            "docker",
            "build",
            "--iidfile",
            DOCKER_IMAGE_ID_FILENAME,
            "--build-arg",
            "ARTIFACT_FILE=$fatJarName",
            "-t",
            "$dockerImageBaseName:$projectVersionShortForm",
            "-t",
            "$dockerImageBaseName:latest",
            "."
        )

        execWithBenefits()
    }
}

/**
 * Since Spring Boot 2.5.0 two JAR archives are created by default. One is the fat JAR that can be run on its own
 * because it contains all module dependencies in addition to the application's classes and resources. The other one is
 * a thin JAR which has `-plain` in the name, for example `myapp-plain.jar`. To create Docker images of microservices we
 * want the fat JAR. This method makes sure that we use the correct name by removing the `-plain` part from the
 * [providedArtifactPath] if necessary.
 */
fun stripPlain(providedArtifactPath: String?): String? {
    return providedArtifactPath?.replace("-plain.jar", ".jar")
}

/**
 * On Git branches, the semver plugin adds the branch name and the short Git hash to the project version. The separator
 * used before the Git hash is a plus sign and that is not compatible with "docker build -t name:tag". The Git hash
 * is also harder to determine in the scripts for the build pipeline. So this function simply removes the first plus
 * sign and everything that follows.
 */
fun segmentBeforePlusSign(s: String?): String? {
    if (s == null) {
        return null
    }
    val dashIndex = s.indexOf("+")
    if (dashIndex == -1) {
        return s
    }
    return s.substring(0, dashIndex)
}