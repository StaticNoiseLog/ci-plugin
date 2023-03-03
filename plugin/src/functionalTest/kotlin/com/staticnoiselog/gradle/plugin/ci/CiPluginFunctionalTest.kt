package com.staticnoiselog.gradle.plugin.ci

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val FROM_BUILD_SCRIPT = " from extension in build.gradle.kts"
private const val FROM_GRADLE_PROPERTIES_LEGACY = " from legacy properties in gradle.properties file"
private const val FROM_GRADLE_PROPERTIES = " from standard properties in gradle.properties file"
private const val FROM_COMMAND_LINE_LEGACY = "_from_command_line_LEGACY"
private const val FROM_COMMAND_LINE = "_from_command_line"
private const val DEBUG = "--debug"

/**
 * Functional test for the plugin.
 */
class CiPluginFunctionalTest {

    /** The "plugins" section used in build.gradle.kts for most tests. */
    private val plugins = """
    plugins {
        java
        id("io.github.staticnoiselog.ci")
    }
    """

    /** Configuration with the plugin's extension object, used in build.gradle.kts by some tests. */
    private val configurationExtension = """configure<com.staticnoiselog.gradle.plugin.ci.CiPluginExtension> {
        ${CiPluginExtension::mavenRepositoryUrl.name}.set("${CiPluginExtension::mavenRepositoryUrl.name}$FROM_BUILD_SCRIPT")
        ${CiPluginExtension::mavenRepositoryName.name}.set("${CiPluginExtension::mavenRepositoryName.name}$FROM_BUILD_SCRIPT")
        ${CiPluginExtension::mavenRepositoryUsername.name}.set("${CiPluginExtension::mavenRepositoryUsername.name}$FROM_BUILD_SCRIPT")
        ${CiPluginExtension::mavenRepositoryPassword.name}.set("${CiPluginExtension::mavenRepositoryPassword.name}$FROM_BUILD_SCRIPT")
        ${CiPluginExtension::dockerRepository.name}.set("${CiPluginExtension::dockerRepository.name}$FROM_BUILD_SCRIPT")
        ${CiPluginExtension::dockerArtifactSourceDirectory.name}.set("${CiPluginExtension::dockerArtifactSourceDirectory.name}$FROM_BUILD_SCRIPT")
        ${CiPluginExtension::dockerArtifactFile.name}.set("${CiPluginExtension::dockerArtifactFile.name}$FROM_BUILD_SCRIPT")
    }
    """

    /** Configuration of the two legacy properties, used in gradle.properties by some tests. */
    private val configurationGradlePropertiesLegacy = """
        $MAVEN_USER_LEGACY_PROPERTY=$MAVEN_USER_LEGACY_PROPERTY$FROM_GRADLE_PROPERTIES_LEGACY
        $MAVEN_PASSWORD_LEGACY_PROPERTY=$MAVEN_PASSWORD_LEGACY_PROPERTY$FROM_GRADLE_PROPERTIES_LEGACY
    """

    private val configurationGradleProperties = """
        $PROPERTY_PREFIX${CiPluginExtension::mavenRepositoryUrl.name}=$PROPERTY_PREFIX${CiPluginExtension::mavenRepositoryUrl.name}$FROM_GRADLE_PROPERTIES
        $PROPERTY_PREFIX${CiPluginExtension::mavenRepositoryName.name}=$PROPERTY_PREFIX${CiPluginExtension::mavenRepositoryName.name}$FROM_GRADLE_PROPERTIES
        $PROPERTY_PREFIX${CiPluginExtension::mavenRepositoryUsername.name}=$PROPERTY_PREFIX${CiPluginExtension::mavenRepositoryUsername.name}$FROM_GRADLE_PROPERTIES
        $PROPERTY_PREFIX${CiPluginExtension::mavenRepositoryPassword.name}=$PROPERTY_PREFIX${CiPluginExtension::mavenRepositoryPassword.name}$FROM_GRADLE_PROPERTIES
        $PROPERTY_PREFIX${CiPluginExtension::dockerRepository.name}=$PROPERTY_PREFIX${CiPluginExtension::dockerRepository.name}$FROM_GRADLE_PROPERTIES
        $PROPERTY_PREFIX${CiPluginExtension::dockerArtifactSourceDirectory.name}=$PROPERTY_PREFIX${CiPluginExtension::dockerArtifactSourceDirectory.name}$FROM_GRADLE_PROPERTIES
        $PROPERTY_PREFIX${CiPluginExtension::dockerArtifactFile.name}=$PROPERTY_PREFIX${CiPluginExtension::dockerArtifactFile.name}$FROM_GRADLE_PROPERTIES
    """

    @field:TempDir
    lateinit var tempFolder: java.nio.file.Path

    private fun getProjectDir() = tempFolder
    private fun getBuildFile() = getProjectDir().resolve("build.gradle.kts")
    private fun getPropertiesFile() = getProjectDir().resolve("gradle.properties")

    @Test
    fun `when gradle tasks, then expected tasks available`() {
        getBuildFile().toFile().writeText(
            """
                $plugins
            """
        )

        val result = runBuild("tasks")

        // Custom tasks available?
        assertTrue(result.output.contains(CI_PLUGIN_TASK_GROUP))
        assertTrue(result.output.contains(DOCKER_PREPARE_CONTEXT_TASK_NAME))
        assertTrue(result.output.contains(DOCKER_BUILD_IMAGE_TASK_NAME))
        assertTrue(result.output.contains(DOCKER_PUSH_IMAGE_TASK_NAME))
        assertTrue(result.output.contains(DOCKER_REMOVE_IMAGE_TASK_NAME))
        assertTrue(result.output.contains(SOURCE_JAR_TASK_NAME))
        assertTrue(result.output.contains(TEST_JAR_TASK_NAME))
        assertTrue(result.output.contains(TEST_SOURCE_JAR_TASK_NAME))

        // MavenPublishPlugin applied?
        assertTrue(result.output.contains("publishToMavenLocal"))

        // JacocoPlugin applied?
        assertTrue(result.output.contains("jacocoTestReport "))
    }

    @Test
    fun `when gradle showCiPluginConfiguration, then default configuration`() {
        getBuildFile().toFile().writeText(
            """
                $plugins
            """
        )

        val result = runBuild(SHOW_CI_PLUGIN_CONFIGURATION_TASK_NAME)

        assertValueInOutput(MAVEN_REPOSITORY_URL_DEFAULT, result)
        assertValueInOutput(MAVEN_REPOSITORY_NAME_DEFAULT, result)
        assertValueInOutput(MAVEN_REPOSITORY_USERNAME_DEFAULT, result)
        assertPasswordInOutput(MAVEN_REPOSITORY_PASSWORD_DEFAULT, PASSWORD_MASKED_TEXT, result)
        assertValueInOutput(MAVEN_REPOSITORY_URL_DEFAULT, result)
    }

    @Test
    fun `given configuration in build script, when gradle showCiPluginConfiguration, then configuration from build script prevails`() {
        getBuildFile().toFile().writeText(
            """
                $plugins
                $configurationExtension
            """
        )

        val result = runBuild(SHOW_CI_PLUGIN_CONFIGURATION_TASK_NAME)

        assertValueInOutput(CiPluginExtension::mavenRepositoryUrl.name + FROM_BUILD_SCRIPT, result)
        assertValueInOutput(CiPluginExtension::mavenRepositoryName.name + FROM_BUILD_SCRIPT, result)
        assertValueInOutput(CiPluginExtension::mavenRepositoryUsername.name + FROM_BUILD_SCRIPT, result)
        assertPasswordInOutput(
            CiPluginExtension::mavenRepositoryPassword.name + FROM_BUILD_SCRIPT, PASSWORD_MASKED_TEXT, result
        )
        assertValueInOutput(CiPluginExtension::dockerRepository.name + FROM_BUILD_SCRIPT, result)
        assertValueInOutput(CiPluginExtension::dockerArtifactSourceDirectory.name + FROM_BUILD_SCRIPT, result)
        assertValueInOutput(CiPluginExtension::dockerArtifactFile.name + FROM_BUILD_SCRIPT, result)
    }

    @Test
    fun `given legacy properties in Gradle properties file, when gradle showCiPluginConfiguration, then legacy properties prevail`() {
        getPropertiesFile().toFile().writeText(
            """
                $configurationGradlePropertiesLegacy
            """
        )
        getBuildFile().toFile().writeText(
            """
                $plugins
                $configurationExtension
            """
        )

        val result = runBuild(SHOW_CI_PLUGIN_CONFIGURATION_TASK_NAME)

        // legacy properties must override the credentials for Maven
        assertValueInOutput(MAVEN_USER_LEGACY_PROPERTY + FROM_GRADLE_PROPERTIES_LEGACY, result)
        assertPasswordInOutput(
            MAVEN_PASSWORD_LEGACY_PROPERTY + FROM_GRADLE_PROPERTIES_LEGACY, PASSWORD_MASKED_TEXT, result
        )

        // the other values must still come from build.gradle.kts
        assertValueInOutput(CiPluginExtension::mavenRepositoryUrl.name + FROM_BUILD_SCRIPT, result)
        assertValueInOutput(CiPluginExtension::mavenRepositoryName.name + FROM_BUILD_SCRIPT, result)
        assertValueInOutput(CiPluginExtension::dockerRepository.name + FROM_BUILD_SCRIPT, result)
        assertValueInOutput(CiPluginExtension::dockerArtifactSourceDirectory.name + FROM_BUILD_SCRIPT, result)
        assertValueInOutput(CiPluginExtension::dockerArtifactFile.name + FROM_BUILD_SCRIPT, result)
    }

    @Test
    fun `given standard and legacy properties in Gradle properties file, when gradle showCiPluginConfiguration, then standard properties prevail`() {
        getPropertiesFile().toFile().writeText(
            """
                $configurationGradlePropertiesLegacy
                $configurationGradleProperties
            """
        )
        getBuildFile().toFile().writeText(
            """
                $plugins
                $configurationExtension
            """
        )

        val result = runBuild(SHOW_CI_PLUGIN_CONFIGURATION_TASK_NAME)

        // all configuration must come from the standard properties defined in gradle.properties
        assertValueInOutput(CiPluginExtension::mavenRepositoryUrl.name + FROM_GRADLE_PROPERTIES, result)
        assertValueInOutput(CiPluginExtension::mavenRepositoryName.name + FROM_GRADLE_PROPERTIES, result)
        assertValueInOutput(CiPluginExtension::mavenRepositoryUsername.name + FROM_GRADLE_PROPERTIES, result)
        assertPasswordInOutput(
            CiPluginExtension::mavenRepositoryPassword.name + FROM_GRADLE_PROPERTIES, PASSWORD_MASKED_TEXT, result
        )
        assertValueInOutput(CiPluginExtension::dockerRepository.name + FROM_GRADLE_PROPERTIES, result)
        assertValueInOutput(CiPluginExtension::dockerArtifactSourceDirectory.name + FROM_GRADLE_PROPERTIES, result)
        assertValueInOutput(CiPluginExtension::dockerArtifactFile.name + FROM_GRADLE_PROPERTIES, result)

        // nothing from extension object in build.gradle.kts
        assertFalse(result.output.contains(FROM_BUILD_SCRIPT))
        // nothing from legacy properties in gradle.properties
        assertFalse(result.output.contains(FROM_GRADLE_PROPERTIES_LEGACY))
    }

    /**
     * Note that legacy properties (mavenUser and mavenPassword) are always overridden by the standard properties if
     * the standard properties are present in any form. This is why the standard properties are *not* defined in
     * gradle.properties for this test.
     */
    @Test
    fun `given legacy properties on command line, when gradle showCiPluginConfiguration, then legacy properties from command line prevail`() {
        getPropertiesFile().toFile().writeText(
            """
                $configurationGradlePropertiesLegacy
            """
        )
        getBuildFile().toFile().writeText(
            """
                $plugins
                $configurationExtension
            """
        )

        val result = runBuild(
            "-P$MAVEN_USER_LEGACY_PROPERTY=$MAVEN_USER_LEGACY_PROPERTY$FROM_COMMAND_LINE_LEGACY",
            "-P$MAVEN_PASSWORD_LEGACY_PROPERTY=$MAVEN_PASSWORD_LEGACY_PROPERTY$FROM_COMMAND_LINE_LEGACY",
            SHOW_CI_PLUGIN_CONFIGURATION_TASK_NAME
        )

        // legacy properties from the command line must prevail
        assertValueInOutput(MAVEN_USER_LEGACY_PROPERTY + FROM_COMMAND_LINE_LEGACY, result)
        assertPasswordInOutput(
            MAVEN_PASSWORD_LEGACY_PROPERTY + FROM_COMMAND_LINE_LEGACY, PASSWORD_MASKED_TEXT, result
        )

        // nothing from legacy properties in gradle.properties
        assertFalse(result.output.contains(FROM_GRADLE_PROPERTIES_LEGACY))

        // the other values must still come from build.gradle.kts
        assertValueInOutput(CiPluginExtension::mavenRepositoryUrl.name + FROM_BUILD_SCRIPT, result)
        assertValueInOutput(CiPluginExtension::mavenRepositoryName.name + FROM_BUILD_SCRIPT, result)
        assertValueInOutput(CiPluginExtension::dockerRepository.name + FROM_BUILD_SCRIPT, result)
        assertValueInOutput(CiPluginExtension::dockerArtifactSourceDirectory.name + FROM_BUILD_SCRIPT, result)
        assertValueInOutput(CiPluginExtension::dockerArtifactFile.name + FROM_BUILD_SCRIPT, result)
    }

    @Test
    fun `given all properties on command line, when gradle showCiPluginConfiguration, then standard command line properties prevail`() {
        getPropertiesFile().toFile().writeText(
            """
                $configurationGradlePropertiesLegacy
                $configurationGradleProperties
            """
        )
        getBuildFile().toFile().writeText(
            """
                $plugins
                $configurationExtension
            """
        )

        val result = runBuild(
            // the two legacy properties will be ignored because all standard properties are also present on the command line
            "-P$MAVEN_USER_LEGACY_PROPERTY=$MAVEN_USER_LEGACY_PROPERTY$FROM_COMMAND_LINE_LEGACY",
            "-P$$MAVEN_PASSWORD_LEGACY_PROPERTY=$$MAVEN_PASSWORD_LEGACY_PROPERTY$FROM_COMMAND_LINE_LEGACY",
            // all standard properties specified on the command line, this is the highest priority possible
            "-P$PROPERTY_PREFIX${CiPluginExtension::mavenRepositoryUrl.name}=${CiPluginExtension::mavenRepositoryUrl.name}$FROM_COMMAND_LINE",
            "-P$PROPERTY_PREFIX${CiPluginExtension::mavenRepositoryName.name}=${CiPluginExtension::mavenRepositoryName.name}$FROM_COMMAND_LINE",
            "-P$PROPERTY_PREFIX${CiPluginExtension::mavenRepositoryUsername.name}=${CiPluginExtension::mavenRepositoryUsername.name}$FROM_COMMAND_LINE",
            "-P$PROPERTY_PREFIX${CiPluginExtension::mavenRepositoryPassword.name}=${CiPluginExtension::mavenRepositoryPassword.name}$FROM_COMMAND_LINE",
            "-P$PROPERTY_PREFIX${CiPluginExtension::dockerRepository.name}=${CiPluginExtension::dockerRepository.name}$FROM_COMMAND_LINE",
            "-P$PROPERTY_PREFIX${CiPluginExtension::dockerArtifactSourceDirectory.name}=${CiPluginExtension::dockerArtifactSourceDirectory.name}$FROM_COMMAND_LINE",
            "-P$PROPERTY_PREFIX${CiPluginExtension::dockerArtifactFile.name}=${CiPluginExtension::dockerArtifactFile.name}$FROM_COMMAND_LINE",
            SHOW_CI_PLUGIN_CONFIGURATION_TASK_NAME
        )

        // all configuration must come from the standard command-line properties
        assertValueInOutput(CiPluginExtension::mavenRepositoryUrl.name + FROM_COMMAND_LINE, result)
        assertValueInOutput(CiPluginExtension::mavenRepositoryName.name + FROM_COMMAND_LINE, result)
        assertValueInOutput(CiPluginExtension::mavenRepositoryUsername.name + FROM_COMMAND_LINE, result)
        assertPasswordInOutput(
            CiPluginExtension::mavenRepositoryPassword.name + FROM_COMMAND_LINE, PASSWORD_MASKED_TEXT, result
        )
        assertValueInOutput(CiPluginExtension::dockerRepository.name + FROM_COMMAND_LINE, result)
        assertValueInOutput(CiPluginExtension::dockerArtifactSourceDirectory.name + FROM_COMMAND_LINE, result)
        assertValueInOutput(CiPluginExtension::dockerArtifactFile.name + FROM_COMMAND_LINE, result)

        // nothing from extension object in build.gradle.kts
        assertFalse(result.output.contains(FROM_BUILD_SCRIPT))
        // nothing from gradle.properties file
        assertFalse(result.output.contains(FROM_GRADLE_PROPERTIES_LEGACY))
        assertFalse(result.output.contains(FROM_GRADLE_PROPERTIES))
    }

    @Test
    fun `given password in build script, when gradle --debug showCiPluginConfiguration, then password from build script prevails`() {
        getBuildFile().toFile().writeText(
            """
                $plugins
                $configurationExtension
            """
        )

        val result = runBuild(
            DEBUG, SHOW_CI_PLUGIN_CONFIGURATION_TASK_NAME
        )

        // password configured in the extension object in build.gradle.kts must appear in output
        assertPasswordInOutput(
            CiPluginExtension::mavenRepositoryPassword.name + FROM_BUILD_SCRIPT,
            CiPluginExtension::mavenRepositoryPassword.name + FROM_BUILD_SCRIPT,
            result
        )
    }

    @Test
    fun `given legacy password in Gradle properties file, when gradle --debug showCiPluginConfiguration, then legacy password prevails`() {
        getPropertiesFile().toFile().writeText(
            """
                $configurationGradlePropertiesLegacy
            """
        )
        getBuildFile().toFile().writeText(
            """
                $plugins
                $configurationExtension
            """
        )

        val result = runBuild(
            DEBUG, SHOW_CI_PLUGIN_CONFIGURATION_TASK_NAME
        )

        // unmasked password from legacy property in gradle.properties expected in output
        assertPasswordInOutput(
            MAVEN_PASSWORD_LEGACY_PROPERTY + FROM_GRADLE_PROPERTIES_LEGACY,
            MAVEN_PASSWORD_LEGACY_PROPERTY + FROM_GRADLE_PROPERTIES_LEGACY,
            result
        )

        assertFalse(
            result.output.contains(CiPluginExtension::mavenRepositoryPassword.name + FROM_BUILD_SCRIPT),
            "password configured in the extension object in build.gradle.kts must not appear in output"
        )
    }

    @Test
    fun `given legacy and standard password in Gradle properties file, when gradle --debug showCiPluginConfiguration, then standard password prevails`() {
        getPropertiesFile().toFile().writeText(
            """
                $configurationGradlePropertiesLegacy
                $configurationGradleProperties
            """
        )
        getBuildFile().toFile().writeText(
            """
                $plugins
                $configurationExtension
            """
        )

        val result = runBuild(
            DEBUG, SHOW_CI_PLUGIN_CONFIGURATION_TASK_NAME
        )

        // unmasked password from standard property in gradle.properties expected in output
        assertPasswordInOutput(
            CiPluginExtension::mavenRepositoryPassword.name + FROM_GRADLE_PROPERTIES,
            CiPluginExtension::mavenRepositoryPassword.name + FROM_GRADLE_PROPERTIES,
            result
        )

        assertFalse(
            result.output.contains(CiPluginExtension::mavenRepositoryPassword.name + FROM_BUILD_SCRIPT),
            "password configured in the extension object in build.gradle.kts must not appear in output"
        )
        assertFalse(
            result.output.contains(MAVEN_PASSWORD_LEGACY_PROPERTY + FROM_GRADLE_PROPERTIES_LEGACY),
            "legacy password must not appear in output (standard password property always prevails"
        )
    }

    @Test
    fun `given setup without JAR task, when gradle dockerPrepareContext, then no exception`() {
        getBuildFile().toFile().writeText(
            """
            plugins {
                id("io.github.staticnoiselog.ci")
            }
            """
        )

        val result = runBuild(
            DOCKER_PREPARE_CONTEXT_TASK_NAME
        )

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    }

    private fun runBuild(vararg arguments: String): BuildResult {
        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments(arguments.asList())
        runner.withProjectDir(getProjectDir().toFile())
        return runner.build()
    }

    private fun assertValueInOutput(value: String, result: BuildResult) {
        if (value.isEmpty()) {
            return
        }
        assertTrue(result.output.contains(value))
    }

    private fun assertPasswordInOutput(realPassword: String, expectedValue: String, result: BuildResult) {
        if (realPassword.isEmpty()) {
            return
        }
        assertTrue(result.output.contains(expectedValue))
    }
}