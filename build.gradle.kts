import org.gradle.api.publish.maven.MavenPublication
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    jacoco
}

group = "dev.extratoast"
version = providers.gradleProperty("artifactVersion")
    .orElse(
        providers.environmentVariable("GITHUB_REF_NAME").map { ref ->
            if (ref.startsWith("v")) ref.removePrefix("v") else "0.0.0-SNAPSHOT"
        },
    )
    .orElse("0.0.0-SNAPSHOT")
    .get()

repositories {
    gradlePluginPortal()
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

gradlePlugin {
    isAutomatedPublishing = false
    plugins {
        create("openApiClient") {
            id = "dev.extratoast.openapi-client"
            implementationClass = "dev.extratoast.openapi.client.OpenApiClientPlugin"
            displayName = "ExtraToast OpenAPI Client"
            description = "Generates typed JVM clients from consumer-owned local OpenAPI specs."
        }
    }
}

dependencies {
    implementation("org.openapitools:openapi-generator-gradle-plugin:7.22.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.21.3")

    testImplementation(gradleTestKit())
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "dev.extratoast"
            artifactId = "openapi-client-gradle"
            version = project.version.toString()
            pom {
                name.set("openapi-client-gradle")
                description.set("Gradle plugin for generating typed JVM clients from local OpenAPI specs.")
                url.set("https://github.com/ExtraToast/openapi-client-gradle")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("extratoast")
                        name.set("ExtraToast")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/ExtraToast/openapi-client-gradle.git")
                    developerConnection.set("scm:git:https://github.com/ExtraToast/openapi-client-gradle.git")
                    url.set("https://github.com/ExtraToast/openapi-client-gradle")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ExtraToast/openapi-client-gradle")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .orNull
                password = providers.gradleProperty("gpr.token")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .orNull
            }
        }
    }
}

tasks.register("verifyPublishingCoordinates") {
    group = "verification"
    description = "Verifies the repo publishes only the expected Maven artifact coordinates."

    doLast {
        val publicationNames = publishing.publications.names
        val markerPublications = publicationNames.filter { it.endsWith("PluginMarkerMaven") || it == "pluginMaven" }
        check(markerPublications.isEmpty()) {
            "Plugin marker publications must be suppressed, found: ${markerPublications.joinToString()}"
        }

        val publication = publishing.publications.getByName("maven") as MavenPublication
        check(publication.groupId == "dev.extratoast") { "Unexpected groupId: ${publication.groupId}" }
        check(publication.artifactId == "openapi-client-gradle") { "Unexpected artifactId: ${publication.artifactId}" }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn("verifyPublishingCoordinates", "jacocoTestCoverageVerification")
}
