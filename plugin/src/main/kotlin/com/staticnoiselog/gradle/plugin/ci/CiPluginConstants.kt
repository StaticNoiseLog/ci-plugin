package com.staticnoiselog.gradle.plugin.ci

// task group is used in reports and UIs to group related tasks (e.g. gradle tasks)
const val CI_PLUGIN_TASK_GROUP = "CI Plugin"

const val SHOW_CI_PLUGIN_CONFIGURATION_TASK_NAME = "showCiPluginConfiguration"

const val DOCKER_SUBDIRECTORY = "docker"
const val DOCKER_IMAGE_ID_FILENAME = "imageid.txt"
const val DOCKER_PREPARE_CONTEXT_TASK_NAME = "dockerPrepareContext"
const val DOCKER_BUILD_IMAGE_TASK_NAME = "dockerBuildImage"
const val DOCKER_PUSH_IMAGE_TASK_NAME = "dockerPushImage"
const val DOCKER_REMOVE_IMAGE_TASK_NAME = "dockerRemoveImage"

const val SOURCE_JAR_TASK_NAME = "sourceJar"
const val CLASSIFIER_SOURCES = "sources"
const val TEST_JAR_TASK_NAME = "testJar"
const val CLASSIFIER_TEST = "test"
const val TEST_SOURCE_JAR_TASK_NAME = "testSourceJar"
const val CLASSIFIER_TEST_SOURCES = "test-sources"