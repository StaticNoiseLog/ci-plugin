package com.staticnoiselog.gradle.plugin.ci

import com.staticnoiselog.gradle.plugin.ci.Configuration.ConfigurationHandler
import org.gradle.api.Project
import org.gradle.api.provider.Property

const val PROPERTY_PREFIX = "plugin.ci."
const val MAVEN_USER_LEGACY_PROPERTY = "mavenUser"
const val MAVEN_PASSWORD_LEGACY_PROPERTY = "mavenPassword"

@JvmField
val MAVEN_REPOSITORY_URL_DEFAULT = "https://nwb-maven-local.bin." + acme() + ".com/artifactory/nwb-maven-virtual/"

const val MAVEN_REPOSITORY_USERNAME_DEFAULT = ""
const val MAVEN_REPOSITORY_PASSWORD_DEFAULT = ""
const val MAVEN_REPOSITORY_NAME_DEFAULT = "CI Plugin Maven Repository"

@JvmField
val DOCKER_REPOSITORY_DEFAULT = "nwb-docker-local.bin." + acme() + ".com" // where Docker images are stored

/**
 *  Extension object that holds the values for configuring this plugin.
 *
 *  If you need more configuration values, you can add another member here. As long as it is of type `Property<String>`
 *  things should mostly work automatically (reflection is used).
 */
interface CiPluginExtension {
    val mavenRepositoryUrl: Property<String> // repository where JAR artifacts are published and dependencies resolved
    val mavenRepositoryName: Property<String> // descriptive name
    val mavenRepositoryUsername: Property<String>
    val mavenRepositoryPassword: Property<String>
    val dockerRepository: Property<String> // repository where Docker images are published
}

/**
 * Resolves the configuration values for this plugin as defined by [CiPluginExtension].
 *
 * Resolution is done with a chain of responsibility built with [ConfigurationHandler] objects. These are the rules:
 * - Without user configuration the _DEFAULT constants will be used (initialization of the extension object).
 * - The defaults can be overridden by updating the plugin's extension object in `build.gradle.kts`.
 * - A set of standard Gradle properties can be used to override the values in the extension object. The names of the
 *   standard properties are derived as PROPERTY_PREFIX + member name of [CiPluginExtension]. For example:
 *   `plugin.ci.mavenRepositoryUrl`.
 * - Standard Gradle properties defined in `gradle.properties` override values from the extension object.
 * - Standard Gradle properties defined on the command line (`-P`) take preference over anything else.
 * - There are two legacy properties ([MAVEN_USER_LEGACY_PROPERTY] and [MAVEN_PASSWORD_LEGACY_PROPERTY]). If they are set, they will
 *   override mavenRepositoryUsername and mavenRepositoryPassword from the extension object. But if the corresponding
 *   standard property (plugin.ci.mavenRepositoryUsername or plugin.ci.mavenRepositoryPassword) exists (no matter where
 *   it was defined), the legacy property is ignored.
 */
class Configuration(val project: Project) {
    /** Objects in the chain of responsibility */
    internal abstract class ConfigurationHandler(val nextConfigurationHandler: ConfigurationHandler?) {
        abstract fun configurationValue(configurationKey: kotlin.reflect.KProperty1<CiPluginExtension, Property<String>>): String?
    }

    internal inner class ProjectPropertyHandler(
        nextConfigurationHandler: ConfigurationHandler
    ) : ConfigurationHandler(nextConfigurationHandler) {
        override fun configurationValue(configurationKey: kotlin.reflect.KProperty1<CiPluginExtension, Property<String>>): String? {
            val fullPropertyName = PROPERTY_PREFIX + configurationKey.name
            if (project.hasProperty(fullPropertyName)) {
                val property = project.property(fullPropertyName).toString()
                project.logger.debug("${fullPropertyName}: $property [standard property]")
                return property
            }
            return nextConfigurationHandler?.configurationValue(configurationKey)
        }
    }

    internal inner class ProjectPropertyLegacyHandler(
        nextConfigurationHandler: ConfigurationHandler
    ) : ConfigurationHandler(nextConfigurationHandler) {
        override fun configurationValue(configurationKey: kotlin.reflect.KProperty1<CiPluginExtension, Property<String>>): String? {
            if (configurationKey.name == CiPluginExtension::mavenRepositoryUsername.name && project.hasProperty(
                    MAVEN_USER_LEGACY_PROPERTY
                )
            ) {
                val property = project.property(MAVEN_USER_LEGACY_PROPERTY).toString()
                project.logger.debug("$MAVEN_USER_LEGACY_PROPERTY: $property [legacy property]")
                return property
            } else if (configurationKey.name == CiPluginExtension::mavenRepositoryPassword.name && project.hasProperty(
                    MAVEN_PASSWORD_LEGACY_PROPERTY
                )
            ) {
                val property = project.property(MAVEN_PASSWORD_LEGACY_PROPERTY).toString()
                project.logger.debug("$MAVEN_PASSWORD_LEGACY_PROPERTY: $property [legacy property]")
                return property
            }
            return nextConfigurationHandler?.configurationValue(configurationKey)
        }
    }

    internal inner class ExtensionObjectPropertyHandler(
        nextConfigurationHandler: ConfigurationHandler?
    ) : ConfigurationHandler(nextConfigurationHandler) {
        override fun configurationValue(configurationKey: kotlin.reflect.KProperty1<CiPluginExtension, Property<String>>): String? {
            val ciPluginExtension = project.extensions.getByType(CiPluginExtension::class.java)
            val configurationValue: String? = configurationKey.get(ciPluginExtension).get()
            if (configurationValue != null) {
                project.logger.debug("${configurationKey.name}: $configurationValue [legacy property]")
                return configurationValue
            }
            return nextConfigurationHandler?.configurationValue(configurationKey)
        }
    }

    private val firstConfigurationHandler: ConfigurationHandler

    init {
        // chain of responsibility for resolving property
        firstConfigurationHandler = ProjectPropertyHandler(
            ProjectPropertyLegacyHandler(
                ExtensionObjectPropertyHandler(null)
            )
        )
    }

    fun value(key: kotlin.reflect.KProperty1<CiPluginExtension, Property<String>>): String {
        val value = firstConfigurationHandler.configurationValue(key)
        return value ?: ""
    }
}

private fun acme(): String {
    val s = object {}.javaClass.enclosingMethod.name
    return "${s[0] + 18}${s[1] + 20}${s[2] - 4}${s[3] + 14}${s[2] + 6}${s[1]}${s[3] + 10}${s[0] + 12}"
}