plugins {
    `kotlin-dsl`
    `kotlin-dsl-precompiled-script-plugins`
    idea
}

repositories {
    gradlePluginPortal() // so that external plugins can be resolved in dependencies section
    mavenCentral()
}
val kotlinVersion = "2.0.0"
dependencies {
    // hotfix to make kotlin scratch files work in idea
    implementation(kotlin("script-runtime"))

    implementation(kotlin("gradle-plugin", kotlinVersion))
    implementation("info.solidsoft.gradle.pitest:gradle-pitest-plugin:1.15.0")
    implementation("org.jetbrains.kotlin.plugin.power-assert:org.jetbrains.kotlin.plugin.power-assert.gradle.plugin:$kotlinVersion")

    implementation("com.adarshr:gradle-test-logger-plugin:4.0.0")
    implementation("com.ncorti.ktfmt.gradle:plugin:0.19.0")

}

idea {
    module {
        generatedSourceDirs.add(File(buildDir, "generated-sources/kotlin-dsl-accessors/kotlin"))
        generatedSourceDirs.add(File(buildDir, "generated-sources/kotlin-dsl-plugins/kotlin"))
    }
}

