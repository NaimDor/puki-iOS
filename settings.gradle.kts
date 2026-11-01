pluginManagement {
    repositories {
        google()
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

rootProject.name = "Puki"
include(":app")
// The baseline profile generator, only when asked for (see baselineprofile/build.gradle.kts)
if (providers.gradleProperty("puki.profile").isPresent) include(":baselineprofile")
