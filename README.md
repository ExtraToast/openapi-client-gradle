# openapi-client-gradle

Gradle plugin for generating typed JVM clients from local OpenAPI specs owned by the consuming repo.

## Coordinates

Published Maven artifact:

```text
dev.extratoast:openapi-client-gradle:<version>
```

Plugin id:

```text
dev.extratoast.openapi-client
```

The artifact intentionally does not publish Gradle plugin marker modules. Consumers map the plugin id to the Maven artifact in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "ExtraToastOpenApiClientGradle"
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
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "dev.extratoast.openapi-client") {
                useModule("dev.extratoast:openapi-client-gradle:${requested.version}")
            }
        }
    }
}
```

Then apply the pinned plugin version in a client module:

```kotlin
plugins {
    id("dev.extratoast.openapi-client") version "0.1.0"
}
```

## Configuration

```kotlin
openApiClient {
    specPath.set("libs/openapi-specs/brevo.yml")
    apiPackage.set("net.blueshell.clients.brevo.api")
    modelPackage.set("net.blueshell.clients.brevo.model")
    packageName.set("net.blueshell.clients.brevo.invoker")
    apis.set(listOf("TransactionalEmails", "Contacts"))
    schemaMappings.set(
        mapOf(
            "getContactInfo_identifier_parameter" to "java.lang.String",
        ),
    )
    typeMappings.set(emptyMap())
}
```

Required fields:

- `specPath`: local JSON/YAML OpenAPI document, absolute or relative to the consuming root project.
- `apiPackage`: package for generated API classes.
- `modelPackage`: package for generated model classes.
- `packageName`: invoker/support package.

Optional fields:

- `apis`: selected OpenAPI tags/API groups. Empty means generate all APIs.
- `schemaMappings`: OpenAPI Generator schema mappings.
- `typeMappings`: OpenAPI Generator type mappings.

The plugin registers `build/generated/openapi/src/main/java` as main Java source and makes Java compilation depend on `generate`. Generated clients use OpenAPI Generator `java` + `restclient`, Jackson 3, Jakarta annotations/validation, and Spring 7 client dependencies.

## Prepared Specs

Spec acquisition, refresh, filtering, and vendor-specific normalization stay in the consuming repo. Wire prep tasks to `generate`:

```kotlin
val filteredSpec = layout.buildDirectory.file("discord-filtered.json")

val filterDiscordSpec = tasks.register("filterDiscordSpec") {
    inputs.file(rootProject.layout.projectDirectory.file("libs/openapi-specs/discord.json"))
    outputs.file(filteredSpec)

    doLast {
        // Write the reduced local spec to filteredSpec.
    }
}

tasks.named("generate") {
    dependsOn(filterDiscordSpec)
}

openApiClient {
    specPath.set("services/api/clients/discord/build/discord-filtered.json")
    apiPackage.set("net.blueshell.clients.discord.api")
    modelPackage.set("net.blueshell.clients.discord.model")
    packageName.set("net.blueshell.clients.discord.invoker")
    apis.set(listOf("Discord"))
}
```

Validation runs after `generate` dependencies, so prepared specs are checked after the prep task writes them.

## Boundary

This plugin only reads local OpenAPI documents and generates typed JVM clients. It does not download upstream specs, detect upstream drift, publish generated client libraries, or replace `api-contract-checks`.

## Publishing

Releases are created by release-please. Tag publishing runs:

```bash
./gradlew publish --no-daemon --no-parallel --max-workers=1
```

GitHub Packages may mark a brand-new package private by default on this account; the package owner must set it public once after the first publish.
