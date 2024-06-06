pluginManagement {
  repositories {
    mavenCentral()
    google {
      @Suppress("UnstableApiUsage")
      content {
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google.testing.platform")
        includeGroupAndSubgroups("androidx.databinding")
      }
    }
    gradlePluginPortal()
    maven("https://maven.pkg.jetbrains.space/public/p/amper/amper")
    maven("https://www.jetbrains.com/intellij-repository/releases") {
      content {
        includeGroup("com.jetbrains.intellij.platform")
      }
    }
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
  }
}

plugins {
  id("org.jetbrains.amper.settings.plugin").version("0.3.1")
}

dependencyResolutionManagement {
  @Suppress("UnstableApiUsage")
  repositories {
    mavenCentral()
  }
  @Suppress("UnstableApiUsage")
  repositoriesMode = RepositoriesMode.PREFER_SETTINGS
}
