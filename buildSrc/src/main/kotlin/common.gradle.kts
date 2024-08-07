import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.`maven-publish`
import org.gradle.kotlin.dsl.signing
import com.adarshr.gradle.testlogger.TestLoggerExtension
import com.adarshr.gradle.testlogger.theme.ThemeType.MOCHA_PARALLEL

plugins {
    `maven-publish`
    signing
    id("com.adarshr.test-logger")
    id("com.ncorti.ktfmt.gradle")
    kotlin("plugin.power-assert")
}

group = "com.christophsturm.isolationchamber"
version = "0.0.1"

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}
publishing {
    publications.withType<MavenPublication> {
        groupId = project.group as String
        version = project.version as String

        // stub javadoc
        artifact(javadocJar.get())

        pom {
            description.set("isolation for your tests")
            name.set("isolation-chamber")
            url.set("https://github.com/christophsturm/isolation-chamber")
            developers {
                developer {
                    id.set("christophsturm")
                    name.set("Christoph Sturm")
                    email.set("me@christophsturm.com")
                }
            }
            licenses {
                license {
                    name.set("The MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                    distribution.set("repo")
                }
            }
            scm {
                url.set("https://github.com/christophsturm/isolation-chamber.git")
            }
        }
    }
}
signing {
    sign(publishing.publications)
}

// needed because we stub the javadoc
tasks.getByName("publishJvmPublicationToSonatypeRepository").dependsOn(tasks.getByName("signKotlinMultiplatformPublication")).dependsOn(tasks.getByName("signJvmPublication"))
tasks.getByName("publishKotlinMultiplatformPublicationToSonatypeRepository").dependsOn(tasks.getByName("signKotlinMultiplatformPublication")).dependsOn(tasks.getByName("signJvmPublication"))

configure<TestLoggerExtension> {
    theme = MOCHA_PARALLEL
    showSimpleNames = true
    showFullStackTraces = true
}
@Suppress("OPT_IN_USAGE")
powerAssert {
    functions = listOf(
        "kotlin.assert",
        "kotlin.test.assertTrue",
        "kotlin.test.assertNotNull",
        "failgood.softly.AssertDSL.assert"
    )
}
