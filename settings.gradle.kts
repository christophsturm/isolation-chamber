pluginManagement {
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
    maven("https://packages.jetbrains.team/maven/p/amper/amper")
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
  }
}

plugins {
  id("org.jetbrains.amper.settings.plugin").version("0.5.0")
}

dependencyResolutionManagement {
  @Suppress("UnstableApiUsage")
  repositories {
    mavenCentral()
  }
  @Suppress("UnstableApiUsage")
  repositoriesMode = RepositoriesMode.PREFER_SETTINGS
}
include("core")
include("integresql")
include("integresql-client")
include("postgresql-docker")
