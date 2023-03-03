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
 *
 * For details see [README.md](../../../../../../../../../README.md).
 */
class CiPlugin : Plugin<Project> {
    private val fileSeparator = FileSystems.getDefault().separator

    override fun apply(project: Project) {
        project.afterEvaluate {
            val ciPluginExtension: CiPluginExtension? =
                project.extensions.findByName(CiPluginExtension::class.simpleName!!) as? CiPluginExtension
            if (!ciPluginExtension?.dockerArtifactFile?.isPresent!!) {
                // The default value for dockerArtifactFile can only be set after the project.version has been set, but
                // if the configuration value was explicitly set, we must not override it.
                val jarTaskCollection: TaskCollection<Jar> = project.tasks.withType(Jar::class.java)
                val jarTask = jarTaskCollection.findByName(JavaPlugin.JAR_TASK_NAME)
                ciPluginExtension.dockerArtifactFile.set(stripPlain(jarTask?.archiveFileName?.orNull))
            }
        }

        project.logger.info("Applying the $CI_PLUGIN_TASK_GROUP (${CiPlugin::class.java.canonicalName})")

        createExtensionObjectWithDefaultValues(project)
        val configuration = Configuration(project)
        registerShowCiPluginConfigurationTask(project, configuration)

        registerDockerTasks(project, configuration)

        project.declareMavenRepository(configuration)
        project.setupMavenPublishPlugin(configuration)
        project.setupJacocoPlugin()
    }

    /**
     * A project extension is the main mechanism to make a Gradle plugin configurable for users.
     */
    private fun createExtensionObjectWithDefaultValues(project: Project) {
        val jarTaskCollection: TaskCollection<Jar> = project.tasks.withType(Jar::class.java)
        val jarTask = jarTaskCollection.findByName(JavaPlugin.JAR_TASK_NAME)

        val ciPluginExtension =
            project.extensions.create(CiPluginExtension::class.simpleName!!, CiPluginExtension::class.java)
        ciPluginExtension.mavenRepositoryUrl.set(MAVEN_REPOSITORY_URL_DEFAULT)
        ciPluginExtension.mavenRepositoryName.set(MAVEN_REPOSITORY_NAME_DEFAULT)
        ciPluginExtension.mavenRepositoryUsername.set(MAVEN_REPOSITORY_USERNAME_DEFAULT)
        ciPluginExtension.mavenRepositoryPassword.set(MAVEN_REPOSITORY_PASSWORD_DEFAULT)
        ciPluginExtension.dockerRepository.set(DOCKER_REPOSITORY_DEFAULT)
        ciPluginExtension.dockerArtifactSourceDirectory.set(jarTask?.destinationDirectory?.get().toString())
        // dockerArtifactFile default must be set in afterEvaluate, because project.version is not yet set here by the semver plugin
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
        val assembleTask = project.tasks.findByName(BasePlugin.ASSEMBLE_TASK_NAME)

        val dockerBuildImageWorkingDir = "${project.buildDir}$fileSeparator$DOCKER_SUBDIRECTORY"

        val dockerPrepareContextTask = project.tasks.register(
            DOCKER_PREPARE_CONTEXT_TASK_NAME, Copy::class.java
        ) { dockerPrepareContextTask ->
            dockerPrepareContextTask.group = CI_PLUGIN_TASK_GROUP
            dockerPrepareContextTask.from("${project.projectDir}$fileSeparator$DOCKER_SUBDIRECTORY")
            dockerPrepareContextTask.from(configuration.value(CiPluginExtension::dockerArtifactSourceDirectory))
            dockerPrepareContextTask.into(dockerBuildImageWorkingDir)
            dockerPrepareContextTask.description =
                """Copies files from ${project.projectDir}$fileSeparator$DOCKER_SUBDIRECTORY
                    and ${configuration.value(CiPluginExtension::dockerArtifactSourceDirectory)}
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
            jacocoTestReportTask.reports.html.required.set(true) // HTML for human inspection
            jacocoTestReportTask.reports.xml.required.set(true) // XML for SonarQube
            jacocoTestReportTask.reports.csv.required.set(false) // CSV not needed
            jacocoTestReportTask.dependsOn(tasks.getByName(JavaPlugin.TEST_TASK_NAME))
        }
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