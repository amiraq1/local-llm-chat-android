pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LocalLLM"
include(":app")

// TODO(MLC Integration): 
// Once the `mlc4j` module is built externally on a PC/Mac (using prepare_libs.sh from the official MLC LLM repo),
// place the `mlc4j` directory in the root of this project and uncomment the lines below to include it in the build.
// 
// include(":mlc4j")
// project(":mlc4j").projectDir = file("mlc4j")
