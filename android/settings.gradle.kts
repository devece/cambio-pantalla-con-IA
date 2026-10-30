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

rootProject.name = "tablero-launcher"

include(":core") // reglas de las ediciones, sin Android: se prueba con `./gradlew :core:test`
include(":app")  // la app Android
