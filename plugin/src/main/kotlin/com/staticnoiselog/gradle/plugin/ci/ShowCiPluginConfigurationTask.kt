package com.staticnoiselog.gradle.plugin.ci

import org.gradle.api.DefaultTask
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

const val PASSWORD_MASKED_TEXT = "A password is set (only shown with --debug)"

/**
 * A task that will show users the actual configuration values used by this plugin during execution.
 *
 * The configuration consists of the members of [CiPluginExtension].
 */
abstract class ShowCiPluginConfigurationTask : DefaultTask() {

    @Internal
    var configuration: Configuration? = null

    @TaskAction
    fun showCiPluginConfiguration() {
        // only look at the interface members that are our configuration properties (not `toString`, `equals` etc.)
        val ciPluginExtensionPropertyMembers =
            CiPluginExtension::class.members.filterIsInstance<kotlin.reflect.KProperty1<CiPluginExtension, Property<String>>>()

        ciPluginExtensionPropertyMembers.stream().forEach { property ->
            run {
                val configurationName = property.name
                val isPassword = (configurationName.lowercase().contains("password"))
                val configurationValue =
                    configuration?.value(property as kotlin.reflect.KProperty1<CiPluginExtension, Property<String>>)

                if (!isPassword) {
                    // harmless values can always be printed
                    project.logger.lifecycle("$configurationName: $configurationValue")
                } else if (project.logger.isEnabled(LogLevel.DEBUG)) {
                    // printing password is OK in debug output
                    project.logger.debug("$configurationName: $configurationValue")
                } else {
                    //  non-empty password for lifecycle output must be sanitized
                    val passwordSanitized = if (!configurationValue.isNullOrEmpty()) PASSWORD_MASKED_TEXT
                    else configurationValue
                    project.logger.lifecycle("$configurationName: $passwordSanitized")
                }
            }
        }
    }
}