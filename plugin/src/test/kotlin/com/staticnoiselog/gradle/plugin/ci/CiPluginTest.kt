package com.staticnoiselog.gradle.plugin.ci

import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions
import kotlin.reflect.jvm.javaType
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for CiPlugin.
 */
class CiPluginTest {
    @Test
    fun `plugin registers tasks`() {
        // Create a test project and apply the plugin
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.staticnoiselog.gradle.plugin.ci")

        // Verify the result
        assertNotNull(project.tasks.findByName(DOCKER_PREPARE_CONTEXT_TASK_NAME))
        assertNotNull(project.tasks.findByName(DOCKER_BUILD_IMAGE_TASK_NAME))
        assertNotNull(project.tasks.findByName(DOCKER_PUSH_IMAGE_TASK_NAME))
        assertNotNull(project.tasks.findByName(DOCKER_REMOVE_IMAGE_TASK_NAME))
    }

    @Test
    fun stripPlain() {
        val expectedJarName = "my-artifact.jar"
        Assertions.assertNull(stripPlain(null))
        Assertions.assertEquals(expectedJarName, stripPlain("my-artifact-plain.jar"))
        Assertions.assertEquals(expectedJarName, stripPlain(expectedJarName))
    }

    @Test
    fun findInterfaceMembersReturningSpecificType() {
        val ciPluginExtensionPropertyMembers = CiPluginExtension::class.members
            .filterIsInstance<kotlin.reflect.KProperty1<CiPluginExtension, Property<String>>>()

        ciPluginExtensionPropertyMembers.forEach { member ->
            run {
                assertTrue(member.returnType.javaType.typeName.startsWith(Property::class.qualifiedName!!, false))
            }
        }
    }
}
