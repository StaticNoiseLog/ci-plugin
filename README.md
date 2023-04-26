CI Plugin
=========

This Gradle plugin supports continuous integration for projects in a corporate environment. It does the following:

- Declares a custom Maven repository
- Applies and configures these Gradle plugins:
  * [Maven Publish Plugin](https://docs.gradle.org/current/userguide/publishing_maven.html)
  * [JaCoCo Plugin](https://docs.gradle.org/current/userguide/jacoco_plugin.html)
- Adds a set of custom tasks for creating a Docker image from the JAR artifact produced by the Gradle build

**The purpose of the CI plugin is to provide what is needed for continuous integration and otherwise stay out of the way
of the software developer.**  
In other words, this plugin does not make any choices regarding programming language, IDE, dependencies or versions for
the software you are writing. But it tries to free you from the burden of remembering what is needed for the CI
pipeline. 

Versions
--------
- JDK: 11
- Gradle: 8.1.1
- Kotlin 1.8.10

Usage
-----
To use this plugin, add it to the "plugins" section of `build.gradle.kts`:
```
plugins {
    id("io.github.staticnoiselog.ci") version "1.1.0"
}
```

Your project should be based on JDK 11 or higher, which can be achieved by specifying the [toolchain](https://docs.gradle.org/current/userguide/toolchains.html):
```
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
} 
 ```

### Kotlin Compatibility Issue
The following exception was observed in a Java project using this plugin:

    Execution failed for task ':compileTestKotlin'.
    > 'void org.jetbrains.kotlin.incremental.FileUtilsKt.cleanDirectoryContents(java.io.File)'

The Java project itself specified a lower Kotlin version:

    plugins {
        java
        kotlin("jvm") version "1.7.0"
        ...

The solution was to use the same Kotlin version as this plugin, or even better, to remove the Kotlin version
specification from the Java project:

    plugins {
        java
    //    kotlin("jvm") version "1.8.10"

Custom Maven Repository
-----------------------
In a corporate environment you may not be allowed to download dependencies from public repositories like Maven Central.
Instead, your company can enforce an extra level of security by providing a custom Maven repository and blocking access
to external repositories.

This plugin supports this concept by declaring a custom Maven repository. You could achieve the same effect by adding
something like the following to your `gradle.build.kts`:
```
repositories {
    // You do NOT have to add this to your build script, the CI plugin does it for you.
    maven {
        url = uri("https://nwb-maven-local.bin.acme.com/artifactory/nwb-maven-virtual/")
        name = "Secure Maven Repository"
        credentials {
            username = rootProject.properties["plugin.ci.mavenUser"].toString()
            password = rootProject.properties["plugin.ci.mavenPassword"].toString()
        }
    }
}
```
But the idea is to free developers from maintaining the details of the corporate Maven repository in each project.
Instead, they can add this plugin to the Gradle build script and store the required configuration details for the custom
Maven repository in their `$HOME/.gradle/gradle.properties` file.

In an ideal scenario, developers would just use this plugin and never add additional repositories for resolving project
dependencies because their corporation's custom Maven repository provides everything they need.

Four values can be configured for the custom Maven repository:
1. name (default is "CI Plugin Maven Repository")
2. url (default is an example custom repository)
3. username (no default, must be provided)
4. password (no default, must be provided)

### Configuring the Custom Maven Repository With Gradle Properties ###
The four values for customizing the Maven repository can be provided as Gradle properties (note that the properties
start with the prefix `plugin.ci.` to reduce the chances of naming conflicts).  
Gradle properties are usually set in a `gradle.properties` file:

    plugin.ci.mavenRepositoryUrl=https://nwb-maven-local.bin.acme.com/artifactory/nwb-maven-virtual/
    plugin.ci.mavenRepositoryName=ACME's Secure Maven Repo
    plugin.ci.mavenRepositoryUsername=user
    plugin.ci.mavenRepositoryPassword=password

Or you can define Gradle properties on the command line. These will override values from `gradle.propeties` files. For
example:

    ./gradlew -Pplugin.ci.mavenRepositoryPassword=password ...

For historic reasons two properties `mavenUser` and `mavenPassword` (without a prefix) are also supported. They serve the
same purpose as `plugin.ci.mavenRepositoryUsername` and `plugin.ci.mavenRepositoryPassword` but are ignored if the
standard properties are present.

### Configuring the Custom Maven Repository Through the Plugin’s Extension Object ###
If you prefer, you can also configure the four values for the custom Maven repository using Gradle's extension object
mechanism.  
The effect is the same as using Gradle properties, but it is done in the project's build script (`build.gradle.kts`):
```
configure<com.staticnoiselog.gradle.plugin.ci.CiPluginExtension> {
    mavenRepositoryUrl.set("https://nwb-maven-local.bin.acme.com/artifactory/nwb-maven-virtual/")
    mavenRepositoryName.set("ACME's Secure Maven Repo")
    mavenRepositoryUsername.set("user")
    mavenRepositoryPassword.set("password")
}
```
Note that Gradle properties take precedence over values defined using the extension object.

As build scripts are usually stored in a shared source code repository you have to be careful not to publish your
password by mistake. But as you can mix the two ways of specifying configuration values, you could provide name, URL and
username in `build.gradle.kts` and the password as a Gradle property.

Maven Publish Plugin
--------------------
The core Gradle plugin [Maven Publish Plugin](https://docs.gradle.org/current/userguide/publishing_maven.html) is added
so that the CI pipeline can publish artifacts to the custom Maven repository.

As described above, the repository for the artifacts is found through the configuration value `mavenRepositoryUrl` and
accessed with the credentials `mavenRepositoryUsername`/`mavenRepositoryPassword`.

JaCoCo Plugin
-------------
The core Gradle plugin [JaCoCo Plugin](https://docs.gradle.org/current/userguide/jacoco_plugin.html) is added so that
the CI pipeline can provide coverage metrics to a SonarQube server. It is configured as follows:

- HTML reports required (for humans)
- XML reports required (for SonarQube)
- CSV reports not required
- The `jacocoTestReport` task depends on the `JavaPlugin.TEST_TASK_NAME` to ensure that the "test" task has been
 executed before `jacocoTestReport` is run.

Docker Tasks
------------
The following custom tasks are added by the CI plugin so that the CI pipeline can create and publish Docker images. This
is useful in an environment where microservices are deployed as Docker images.

- **dockerPrepareContext** - Copies files from `../docker` and the build artifact `../build/libs/artifact[version].jar`
  to working directory `../build/docker` where the Docker image is built.  
  The description of this task shown with `./gradlew tasks` displays the actual source and destination directories for
  the copy operation.
- **dockerBuildImage** - Issues a `docker build` command for the artifact created by the project.
- **dockerPushImage** - Issues a `docker push --all-tags` command for the Docker image created with `dockerBuildImage`.
- **dockerRemoveImage** - Issues a `docker rmi` command for the Docker image created with `dockerBuildImage`.

The setup for a project that produces Docker images would require a subdirectory `../docker` at the project root level
containing a `Dockerfile`. By design, the name of the subdirectory `docker` is *not* configurable (order over chaos).

### Configuring the Docker Repository ###
The Docker repository where Docker images are pushed to is defined by this configuration value:

- `dockerRepository` (default is an example custom repository)

It can be set as a Gradle property (with prefix `plugin.ci.`) in a `gradle.properties` file:

    plugin.ci.dockerRepository=nwb-docker-local.bin.acme.com

Or as a Gradle property on the command line (overrides values from `gradle.propeties`):

    ./gradlew -Pplugin.ci.dockerRepository=nwb-docker-local.bin.acme.com ...

Or in the plugin's extension object in `build.gradle.kts` (note that the Gradle property `plugin.ci.dockerRepository`
takes precedence if it exists):
```
configure<com.staticnoiselog.gradle.plugin.ci.CiPluginExtension> {
    dockerRepository.set("nwb-docker-local.bin.acme.com")
}
```

### Configuring Source Directory and Name of the Artifact for the Docker Image ###
Creation of the Docker image can be customized with two configuration value:

- `dockerArtifactSourceDirectory` is the directory where the `dockerPrepareContext` tasks looks for the artifact to be 
  deployed as a Docker image. The default is the standard build directory (something like `..\build\libs`). All files
  from this directory are made available for building the Docker image. It is up to the application's `Docker` file
  which ones it will use.
- `dockerArtifactFile` is a string used to set the environment variable ARTIFACT_FILE in the Docker image created by
  the task `dockerBuildImage`. The default is the standard Spring Boot fat JAR filename (something like "my-service.jar").

If you want to build Docker images for something else than regular Spring Boot artifacts, it may be necessary to set
these two configuration values. Like with the other configuration values, there are three ways to do this:

As Gradle properties (with prefix `plugin.ci.`) in a `gradle.properties` file:

    plugin.ci.dockerArtifactSourceDirectory=./build/libs/dist
    plugin.ci.dockerArtifactFile=run-tests.sh

Or as a Gradle property on the command line (overrides values from `gradle.propeties`):

    ./gradlew -Pplugin.ci.dockerArtifactSourceDirectory=./build/libs/dist ...
    ./gradlew -Pplugin.ci.dockerArtifactFile=run-tests.sh ...

Or in the plugin's extension object in `build.gradle.kts` (note that the Gradle properties
`plugin.ci.dockerArtifactSourceDirectory` and `plugin.ci.dockerArtifactFile` take precedence if any of them exists).
Kotlin example:
```
configure<com.staticnoiselog.gradle.plugin.ci.CiPluginExtension> {
    dockerArtifactSourceDirectory.set(project.buildDir.name + File.separator + "dist")
    dockerArtifactFile.set("run-tests.sh")
}
```
Groovy example:
```
def ciPluginExtension = project.extensions.getByName("CiPluginExtension")
ciPluginExtension.dockerArtifactSourceDirectory = buildDir.path + File.separator + 'dist'
ciPluginExtension.dockerArtifactFile = war.archiveFileName
```

Displaying the Current Configuration
------------------------------------
The `showCiPluginConfiguration` task will display the current configuration of the CI plugin:

    ./gradlew showCiPluginConfiguration


Developing and Changing this Gradle Plugin
==========================================

Working With a Local Maven Repository
-------------------------------------
While developing you may want to publish the CI plugin to a local maven repository (usually that is just the directory
`$HOME/.m2/repository` on your computer).  
To do so, publish with the `publishToMavenLocal` task (provided by the `maven-publish` plugin):

    ./gradlew publishToMavenLocal

In the project where you want to try out your new version of the CI plugin, you have to (temporarily) add
`mavenLocal()` to the `pluginManagement` block in `settings.gradle`. For example:
```
rootProject.name = "otherproject"
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenLocal() // added for accessing locally published CI plugin
    }
}
```

Set a new and specific project version to make sure that you are actually testing your changes of the CI plugin. The
easiest way to do this is to temporarily set an explicit version in [build.gradle.kts](plugin/build.gradle.kts) of this
project:

    project.version = "1.2.3"

And then refer to that version in the test project that uses the CI plugin:

    plugins {
        id("io.github.staticnoiselog.ci") version "1.2.3"
    }

Adding New Configuration Values
-------------------------------
If you want to add a new configuration value, and it is a string, you can add it to the interface `CiPluginExtension` in
[Configuration.kt](plugin/src/main/kotlin/com/staticnoiselog/gradle/plugin/ci/Configuration.kt). Like this it will
benefit from some reflection-based mechanisms. Specifically, you can set it both in the Gradle extension object or as a
Gradle property, and it will be resolved according to the same precedence rules that apply for the existing
configuration values. Furthermore, the currently configured value can be displayed with the `showCiPluginConfiguration`
task.

Versioning
----------
The [f-u-z-z-l-e / semver-plugin](https://github.com/f-u-z-z-l-e/semver-plugin) is used for versioning. It works with
[Git tags](https://git-scm.com/book/en/v2/Git-Basics-Tagging).

These are the steps for creating an official new version of the CI plugin:

- `./gradlew displayVersion` shows the (new) semver version you are working on
- make the code changes
- commit and push to Git
- publish the plugin (normally to the [Gradle Plugin Portal](https://docs.gradle.org/current/userguide/publishing_gradle_plugins.html#publish_your_plugin_to_the_plugin_portal))
- `./gradlew tagHeadCommit pushTagOrigin` (add the semver version tag and push it to the remote Git repository)

When working with feature branches, the command `git tag --sort=taggerdate` is useful in conjunction with the semver-plugin.