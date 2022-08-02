group = "com.staticnoiselog.gradle"

plugins {
    // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`

    // Apply the Kotlin JVM plugin to add support for Kotlin (targeting the JVM)
    kotlin("jvm") version "1.6.21"

    // Support for publishing to Maven-style repositories (generate the published metadata for your plugin)
    `maven-publish` // also provides "publishToMavenLocal" for testing with local repo

    // Support for publishing on Gradle Plugin Portal
    id("com.gradle.plugin-publish") version "1.0.0"

    // Versioning plugin for this project
    id("ch.fuzzle.gradle.semver") version "1.0.0"
}

repositories {
    mavenCentral()
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // so we can apply the SonarQube plugin programmatically
    implementation("org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:3.3")

    // for JUnit 5 (Jupiter) tests
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
}

// Toolchain for Kotlin
kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(17))
    }
}

testing {
    suites {
        // Configure the built-in test suite
        val test by getting(JvmTestSuite::class) {
            // Use kotlin.test framework
            useKotlinTest()
        }

        // Create a new test suite
        val functionalTest by registering(JvmTestSuite::class) {
            // Use kotlin.test framework
            useKotlinTest()

            dependencies {
                // functionalTest test suite depends on the production code in tests
                implementation(project)
            }

            targets {
                all {
                    // This test suite should run after the built-in test suite has run its tests
                    testTask.configure { shouldRunAfter(test) }
                }
            }
        }
    }
}

gradlePlugin.testSourceSets(sourceSets["functionalTest"])

tasks.named<Task>("check") {
    // Include functionalTest as part of the check lifecycle
    dependsOn(testing.suites.named("functionalTest"))
}

// Configure the Plugin Publishing Plugin (com.gradle.plugin-publish)
pluginBundle {
    website = "https://github.com/StaticNoiseLog/ci-plugin"
    vcsUrl = "https://github.com/StaticNoiseLog/ci-plugin"
    tags = listOf("ci", "maven", "jacoco", "sonarqube", "docker")
}

// plugin information metadata (required for publishing on the Gradle Plugin Portal).
gradlePlugin {
    plugins {
        create("ciPlugin") {
            id = "io.github.staticnoiselog.ci"
            displayName = "CI Plugin"
            description = "Gradle plugin providing support for continuous integration"
            implementationClass = "com.staticnoiselog.gradle.plugin.ci.CiPlugin"
        }
    }
}