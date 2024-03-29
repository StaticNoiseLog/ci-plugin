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
        project.plugins.apply("io.github.staticnoiselog.ci")
        // Verify the result
        val ciPlugin = project.plugins.findPlugin("io.github.staticnoiselog.ci")
        assertNotNull(ciPlugin)
    }

    @Test
    fun stripPlain() {
        val expectedJarName = "my-artifact.jar"
        Assertions.assertNull(stripPlain(null))
        Assertions.assertEquals(expectedJarName, stripPlain("my-artifact-plain.jar"))
        Assertions.assertEquals(expectedJarName, stripPlain(expectedJarName))
    }

    @Test
    fun replaceInvalidDockerTagCharacters() {
        Assertions.assertNull(replaceInvalidDockerTagCharacters(null))
        Assertions.assertEquals("", replaceInvalidDockerTagCharacters(""))
        Assertions.assertEquals("1.2.3", replaceInvalidDockerTagCharacters("1.2.3"))
        Assertions.assertEquals("_.23k21", replaceInvalidDockerTagCharacters("%.23k21"))
        Assertions.assertEquals("2.0.56-branch_e17a9ffa", replaceInvalidDockerTagCharacters("2.0.56-branch+e17a9ffa"))
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
