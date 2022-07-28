package com.staticnoiselog.gradle.plugin.ci

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import org.gradle.api.tasks.SourceSet.TEST_SOURCE_SET_NAME
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.sonarqube.gradle.SonarQubeExtension
import org.sonarqube.gradle.SonarQubePlugin
import java.io.File
import java.nio.file.FileSystems

/**
 * A Gradle plugin that supports continuous integration for builds in a corporate environment.
 *
 * Main responsibilities:
 * - allow configuration of some aspects of the plugin and register a task for displaying the actual configuration
 * - create custom tasks to support the creation and publication of a Docker image of the build artifact
 * - declare a corporate Maven repository
 * - apply and configure the MavenPublishPlugin
 * - apply and configure the JacocoPlugin
 * - apply and configure the SonarQubePlugin
 *
 * For details see [README.md](../../../../../../../../../README.md).
 */
class CiPlugin : Plugin<Project> {
    private val fileSeparator = FileSystems.getDefault().separator

    override fun apply(project: Project) {
        project.logger.info("Applying the $CI_PLUGIN_TASK_GROUP (${CiPlugin::class.java.canonicalName})")

        createExtensionObjectWithDefaultValues(project)
        val configuration = Configuration(project)
        registerShowCiPluginConfigurationTask(project, configuration)

        registerDockerTasks(project, configuration)

        project.declareMavenRepository(configuration)
        project.setupMavenPublishPlugin(configuration)
        project.setupJacocoPlugin()
        project.setupSonarQubePlugin()
    }

    /**
     * A project extension is the main mechanism to make a Gradle plugin configurable for users.
     */
    private fun createExtensionObjectWithDefaultValues(project: Project) {
        val ciPluginExtension =
            project.extensions.create(CiPluginExtension::class.simpleName!!, CiPluginExtension::class.java)
        ciPluginExtension.mavenRepositoryUrl.set(MAVEN_REPOSITORY_URL_DEFAULT)
        ciPluginExtension.mavenRepositoryName.set(MAVEN_REPOSITORY_NAME_DEFAULT)
        ciPluginExtension.mavenRepositoryUsername.set(MAVEN_REPOSITORY_USERNAME_DEFAULT)
        ciPluginExtension.mavenRepositoryPassword.set(MAVEN_REPOSITORY_PASSWORD_DEFAULT)
        ciPluginExtension.dockerRepository.set(DOCKER_REPOSITORY_DEFAULT)
    }

    private fun registerShowCiPluginConfigurationTask(
        project: Project, configuration: Configuration
    ) {
        project.tasks.register(
            SHOW_CI_PLUGIN_CONFIGURATION_TASK_NAME, ShowCiPluginConfigurationTask::class.java
        ) { showCiPluginConfigurationTask ->
            showCiPluginConfigurationTask.configuration = configuration
            showCiPluginConfigurationTask.description = "Shows the configuration values for the CI plugin."
            showCiPluginConfigurationTask.group = CI_PLUGIN_TASK_GROUP
        }
    }

    private fun registerDockerTasks(project: Project, configuration: Configuration) {
        val jarTaskCollection: TaskCollection<Jar> = project.tasks.withType(Jar::class.java)
        val jarTask = jarTaskCollection.findByName(JavaPlugin.JAR_TASK_NAME)
        val assembleTask = project.tasks.findByName(BasePlugin.ASSEMBLE_TASK_NAME)

        val dockerBuildImageWorkingDir = "${project.buildDir}$fileSeparator$DOCKER_SUBDIRECTORY"

        // prepare
        val dockerPrepareContextTask = project.tasks.register(
            DOCKER_PREPARE_CONTEXT_TASK_NAME, Copy::class.java
        ) { dockerPrepareContextTask ->
            dockerPrepareContextTask.group = CI_PLUGIN_TASK_GROUP
            dockerPrepareContextTask.from("${project.projectDir}$fileSeparator$DOCKER_SUBDIRECTORY")
            dockerPrepareContextTask.from(jarTask?.destinationDirectory)
            dockerPrepareContextTask.exclude("*-plain.jar") // for Docker, we need only the full JAR  (can run standalone)
            dockerPrepareContextTask.into(dockerBuildImageWorkingDir)
            dockerPrepareContextTask.description =
                """Copies files from ${project.projectDir}$fileSeparator$DOCKER_SUBDIRECTORY
                    and the build artifact
                    ${jarTask?.destinationDirectory?.orNull}$fileSeparator${jarTask?.archiveBaseName?.orNull}[version].${jarTask?.archiveExtension?.orNull}
                    to working directory $dockerBuildImageWorkingDir
                    where the Docker image is built."""
        }
        if (assembleTask != null) { // without Java plugin the assemble-task is missing (assemble creates the fat JAR)
            dockerPrepareContextTask.get().dependsOn(assembleTask)
        }

        val dockerBuildImageTask = project.tasks.register(
            DOCKER_BUILD_IMAGE_TASK_NAME, DockerBuildImageTask::class.java
        ) { dockerBuildImageTask ->
            dockerBuildImageTask.description =
                "Issues a 'docker build' command for the artifact created by the project."
            dockerBuildImageTask.group = CI_PLUGIN_TASK_GROUP
            dockerBuildImageTask.configuration = configuration
            dockerBuildImageTask.dockerBuildImageWorkingDir = File(dockerBuildImageWorkingDir)
            dockerBuildImageTask.imageIdFile = File(dockerBuildImageWorkingDir, DOCKER_IMAGE_ID_FILENAME)
        }
        dockerBuildImageTask.get().dependsOn(dockerPrepareContextTask.get())

        val dockerPushImageTask = project.tasks.register(
            DOCKER_PUSH_IMAGE_TASK_NAME, DockerPushImageTask::class.java
        ) { dockerPushImageTask ->
            dockerPushImageTask.description =
                "Issues a 'docker push --all-tags' command for the Docker image created with the $DOCKER_BUILD_IMAGE_TASK_NAME task."
            dockerPushImageTask.group = CI_PLUGIN_TASK_GROUP
            dockerPushImageTask.configuration = configuration
        }
        dockerPushImageTask.get().dependsOn(dockerBuildImageTask.get())

        val dockerRemoveImageTask = project.tasks.register(
            DOCKER_REMOVE_IMAGE_TASK_NAME, DockerRemoveImageTask::class.java
        ) { dockerRemoveImageTask ->
            dockerRemoveImageTask.description =
                "Issues a 'docker rmi' command for the Docker image created with the $DOCKER_BUILD_IMAGE_TASK_NAME task."
            dockerRemoveImageTask.group = CI_PLUGIN_TASK_GROUP
            dockerRemoveImageTask.dockerBuildImageWorkingDir = File(dockerBuildImageWorkingDir)
            dockerRemoveImageTask.getImageIdFile().set(File(dockerBuildImageWorkingDir, DOCKER_IMAGE_ID_FILENAME))
        }
        dockerRemoveImageTask.get().dependsOn(dockerBuildImageTask.get())
    }

    /**
     * Declare a corporate Maven repository that should be the secure go-to address for all dependencies.
     *
     * Repositories added by a plugin will come first in sequence (before any repositories added by projects using a
     * plugin). The built-in `mavenCentral()` is not added here by design. The idea is that the corporate Maven
     * repository should provide everything. Builds that do need other repositories can add them, of course, but this
     * one here will be checked first when Gradle attempts to resolve a dependency.
     */
    private fun Project.declareMavenRepository(configuration: Configuration) {
        project.repositories.maven { repo ->
            repo.name = configuration.value(CiPluginExtension::mavenRepositoryName)
            repo.url = uri(configuration.value(CiPluginExtension::mavenRepositoryUrl))
            repo.credentials { credentials ->
                credentials.username = configuration.value(CiPluginExtension::mavenRepositoryUsername)
                credentials.password = configuration.value(CiPluginExtension::mavenRepositoryPassword)
            }
        }
    }

    private fun Project.setupMavenPublishPlugin(configuration: Configuration) {
        project.plugins.apply(MavenPublishPlugin::class.java)
        val publishingExtension = project.extensions.getByType(PublishingExtension::class.java)

        val mavenPublication = publishingExtension.publications.create("maven", MavenPublication::class.java)
        mavenPublication.from(project.components.findByName("java"))
        mavenPublication.artifact(project.sourceJarTask())
        mavenPublication.artifact(project.testJarTask())
        mavenPublication.artifact(project.testSourceJarTask())

        publishingExtension.repositories { repositoryHandler ->
            repositoryHandler.maven { mavenArtifactRepository ->
                mavenArtifactRepository.url = uri(configuration.value(CiPluginExtension::mavenRepositoryUrl))
                mavenArtifactRepository.credentials { credentials ->
                    credentials.username = configuration.value(CiPluginExtension::mavenRepositoryUsername)
                    credentials.password = configuration.value(CiPluginExtension::mavenRepositoryPassword)
                }
            }
        }
    }

    private fun Project.sourceJarTask(): TaskProvider<Jar> {
        return project.tasks.register(
            SOURCE_JAR_TASK_NAME, Jar::class.java
        ) { sourceJarTask ->
            sourceJarTask.description = "Assembles a JAR archive containing the main source files."
            sourceJarTask.group = CI_PLUGIN_TASK_GROUP
            sourceJarTask.archiveClassifier.convention(CLASSIFIER_SOURCES)
            sourceJarTask.archiveClassifier.set(CLASSIFIER_SOURCES)
            val sourceSets = project.extensions.findByName("sourceSets") as SourceSetContainer?
            val allSource = sourceSets?.findByName(MAIN_SOURCE_SET_NAME)?.allSource
            sourceJarTask.from(allSource)
        }
    }

    private fun Project.testJarTask(): TaskProvider<Jar> {
        return project.tasks.register(
            TEST_JAR_TASK_NAME, Jar::class.java
        ) { testJarTask ->
            testJarTask.description = "Assembles a JAR archive containing the test classes."
            testJarTask.group = CI_PLUGIN_TASK_GROUP
            testJarTask.archiveClassifier.convention(CLASSIFIER_TEST)
            testJarTask.archiveClassifier.set(CLASSIFIER_TEST)
            val sourceSets = project.extensions.findByName("sourceSets") as SourceSetContainer?
            val output = sourceSets?.findByName(TEST_SOURCE_SET_NAME)?.output
            testJarTask.from(output)
        }
    }

    private fun Project.testSourceJarTask(): TaskProvider<Jar> {
        return project.tasks.register(
            TEST_SOURCE_JAR_TASK_NAME, Jar::class.java
        ) { testSourceJarTask ->
            testSourceJarTask.description = "Assembles a JAR archive containing the test source files."
            testSourceJarTask.group = CI_PLUGIN_TASK_GROUP
            testSourceJarTask.archiveClassifier.convention(CLASSIFIER_TEST_SOURCES)
            testSourceJarTask.archiveClassifier.set(CLASSIFIER_TEST_SOURCES)
            val sourceSets = project.extensions.findByName("sourceSets") as SourceSetContainer?
            val output = sourceSets?.findByName(TEST_SOURCE_SET_NAME)?.allSource
            testSourceJarTask.from(output)
        }
    }

    private fun Project.setupJacocoPlugin() {
        project.plugins.apply(JacocoPlugin::class.java)
        project.tasks.withType(JacocoReport::class.java).configureEach { jacocoTestReportTask ->
            jacocoTestReportTask.reports.xml.required.set(false) // xml.enabled false
            jacocoTestReportTask.reports.csv.required.set(false) // csv.enabled false
            jacocoTestReportTask.reports.html.outputLocation.set(
                project.layout.buildDirectory.dir(
                    JACOCO_OUTPUT_SUBDIRECTORY
                )
            )
            jacocoTestReportTask.dependsOn(tasks.getByName(JavaPlugin.TEST_TASK_NAME))
        }
    }

    private fun Project.setupSonarQubePlugin() {
        // use SonarQubePlugin only on the root project, not on subprojects of multi-project builds
        if (!project.equals(rootProject)) {
            return
        }
        rootProject.plugins.apply(SonarQubePlugin::class.java)

        val sonarQubeExtension = rootProject.extensions.getByType(SonarQubeExtension::class.java)
        sonarQubeExtension.properties { sonarQubeProperties ->
            sonarQubeProperties.property("sonar.projectName", rootProject.name)
            sonarQubeProperties.property("sonar.description", "${rootProject.description}")
            sonarQubeProperties.property("sonar.rootProjectVersion", "${rootProject.version}")
            sonarQubeProperties.property(
                "sonar.jacoco.reportPaths",
                "${rootProject.layout.buildDirectory.dir(JACOCO_OUTPUT_SUBDIRECTORY).orNull}"
            )
        }
    }
}
