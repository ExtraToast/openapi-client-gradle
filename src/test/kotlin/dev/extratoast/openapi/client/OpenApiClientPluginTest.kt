package dev.extratoast.openapi.client

import com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.api.GradleException
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class OpenApiClientPluginTest {
    private val jsonMapper = ObjectMapper()

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `generates and compiles yaml client with selected api and mappings`() {
        writeSettings()
        writeResource("specs/sample.yml", tempDir.resolve("specs/sample.yml"))
        writeBuildFile(
            """
            plugins {
                id("dev.extratoast.openapi-client")
            }

            openApiClient {
                specPath.set("specs/sample.yml")
                apiPackage.set("com.example.pet.api")
                modelPackage.set("com.example.pet.model")
                packageName.set("com.example.pet.invoker")
                apis.set(listOf("Pets"))
                schemaMappings.set(mapOf("UnusedInlineSchema" to "java.lang.Object"))
                typeMappings.set(mapOf("unused-format" to "java.lang.String"))
            }
            """.trimIndent(),
        )

        val first = runGradle("build")

        assertEquals(TaskOutcome.SUCCESS, first.task(":generate")?.outcome)
        assertTrue(
            Files.exists(tempDir.resolve("build/generated/openapi/src/main/java/com/example/pet/api/PetsApi.java")),
            "generated API source should be part of the main source tree",
        )

        val second = runGradle("generate")

        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generate")?.outcome)
    }

    @Test
    fun `runs consumer owned preparation before validating and generating`() {
        writeSettings()
        writeBuildFile(
            """
            plugins {
                id("dev.extratoast.openapi-client")
            }

            val preparedSpec = layout.buildDirectory.file("prepared/spec.yml")

            tasks.register("prepareSpec") {
                outputs.file(preparedSpec)
                doLast {
                    val target = preparedSpec.get().asFile
                    target.parentFile.mkdirs()
                    target.writeText(
                        ${preparedSpecText().toKotlinString()}
                    )
                }
            }

            tasks.named("generate") {
                dependsOn("prepareSpec")
            }

            openApiClient {
                specPath.set("build/prepared/spec.yml")
                apiPackage.set("com.example.prepared.api")
                modelPackage.set("com.example.prepared.model")
                packageName.set("com.example.prepared.invoker")
                apis.set(listOf("Prepared"))
            }
            """.trimIndent(),
        )

        val result = runGradle("build")

        assertEquals(TaskOutcome.SUCCESS, result.task(":prepareSpec")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generate")?.outcome)
        assertTrue(
            Files.exists(tempDir.resolve("build/generated/openapi/src/main/java/com/example/prepared/api/PreparedApi.java")),
            "prepared spec should generate the selected API",
        )
    }

    @Test
    fun `fails clearly when required configuration is missing`() {
        writeSettings()
        writeBuildFile(
            """
            plugins {
                id("dev.extratoast.openapi-client")
            }
            """.trimIndent(),
        )

        val result = runGradleAndFail("generate")

        assertTrue(result.output.contains("openApiClient.specPath is required"))
    }

    @Test
    fun `fails clearly when spec path cannot be read`() {
        writeSettings()
        writeBuildFile(buildFileFor(specPath = "specs/missing.yml"))

        val result = runGradleAndFail("generate")

        assertTrue(result.output.contains("OpenAPI spec file does not exist"))
    }

    @Test
    fun `fails clearly when spec is not an openapi document`() {
        writeSettings()
        writeResource("specs/invalid-shape.yml", tempDir.resolve("specs/invalid-shape.yml"))
        writeBuildFile(buildFileFor(specPath = "specs/invalid-shape.yml"))

        val result = runGradleAndFail("generate")

        assertTrue(result.output.contains("OpenAPI spec must be an object with an 'openapi' field"))
    }

    @Test
    fun `fails clearly when selected api tag is absent`() {
        writeSettings()
        writeResource("specs/sample.yml", tempDir.resolve("specs/sample.yml"))
        writeBuildFile(buildFileFor(specPath = "specs/sample.yml", apis = listOf("Missing")))

        val result = runGradleAndFail("generate")

        assertTrue(result.output.contains("Selected OpenAPI API/tag(s) are not present"))
        assertTrue(result.output.contains("Missing"))
    }

    @Test
    fun `fails clearly when mappings contain blanks`() {
        writeSettings()
        writeResource("specs/sample.yml", tempDir.resolve("specs/sample.yml"))
        writeBuildFile(
            """
            plugins {
                id("dev.extratoast.openapi-client")
            }

            openApiClient {
                specPath.set("specs/sample.yml")
                apiPackage.set("com.example.pet.api")
                modelPackage.set("com.example.pet.model")
                packageName.set("com.example.pet.invoker")
                schemaMappings.set(mapOf("" to "java.lang.String"))
            }
            """.trimIndent(),
        )

        val result = runGradleAndFail("generate")

        assertTrue(result.output.contains("openApiClient.schemaMappings must not contain blank keys or values"))
    }

    @Test
    fun `downloads and normalizes configured external specs with deterministic json output`() {
        writeSettings()
        writeSpec(
            "fixtures/upstream.json",
            """
            {
              "paths": {},
              "openapi": "3.0.3",
              "info": {
                "version": "1.0.0",
                "title": "JSON Fixture"
              }
            }
            """.trimIndent(),
        )
        writeSpec(
            "fixtures/upstream.yml",
            """
            paths: {}
            openapi: 3.0.3
            info:
              version: 1.0.0
              title: YAML Fixture
            """.trimIndent(),
        )
        writeBuildFile(
            """
            plugins {
                id("dev.extratoast.openapi-client")
            }

            openApiExternalSpecs {
                specDirectory.set(layout.buildDirectory.dir("central-openapi-specs"))
                specs {
                    create("jsonFixture") {
                        sourceUrl.set(file("fixtures/upstream.json").toURI().toString())
                        rawFileName.set("json-fixture.raw")
                        normalizedFileName.set("json-fixture.json")
                    }
                    create("yamlFixture") {
                        sourceUrl.set(file("fixtures/upstream.yml").toURI().toString())
                        rawFileName.set("yaml-fixture.yml")
                        normalizedFileName.set("yaml-fixture.json")
                    }
                }
            }
            """.trimIndent(),
        )

        val result = runGradle("downloadExternalOpenApiSpecs", "normalizeExternalOpenApiSpecs")

        assertEquals(TaskOutcome.SUCCESS, result.task(":downloadExternalOpenApiSpecs")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":normalizeExternalOpenApiSpecs")?.outcome)
        assertTrue(Files.exists(tempDir.resolve("build/central-openapi-specs/json-fixture.raw")))
        assertEquals(
            """{"info":{"title":"JSON Fixture","version":"1.0.0"},"openapi":"3.0.3","paths":{}}""",
            tempDir.resolve("build/central-openapi-specs/json-fixture.json").readText(),
        )
        assertEquals(
            """{"info":{"title":"YAML Fixture","version":"1.0.0"},"openapi":"3.0.3","paths":{}}""",
            tempDir.resolve("build/central-openapi-specs/yaml-fixture.json").readText(),
        )
    }

    @Test
    fun `fails clearly for misconfigured external specs`() {
        writeSettings()
        writeBuildFile(
            """
            plugins {
                id("dev.extratoast.openapi-client")
            }

            openApiExternalSpecs {
                specs {
                    create("missingUrl")
                }
            }
            """.trimIndent(),
        )

        var result = runGradleAndFail("downloadExternalOpenApiSpecs")

        assertTrue(result.output.contains("openApiExternalSpecs.specs.missingUrl.sourceUrl is required"))

        writeSpec("fixtures/broken.yml", "openapi: [")
        writeBuildFile(
            """
            plugins {
                id("dev.extratoast.openapi-client")
            }

            openApiExternalSpecs {
                specs {
                    create("broken") {
                        sourceUrl.set(file("fixtures/broken.yml").toURI().toString())
                        rawFileName.set("broken.yml")
                        normalizedFileName.set("broken.json")
                    }
                }
            }
            """.trimIndent(),
        )

        result = runGradleAndFail("normalizeExternalOpenApiSpecs")

        assertTrue(result.output.contains("OpenAPI spec must be valid JSON or YAML"))

        writeSpec("fixtures/valid.json", """{"openapi":"3.0.3","info":{"title":"Valid","version":"1.0"},"paths":{}}""")
        writeBuildFile(
            """
            plugins {
                id("dev.extratoast.openapi-client")
            }

            openApiExternalSpecs {
                specs {
                    create("badOutput") {
                        sourceUrl.set(file("fixtures/valid.json").toURI().toString())
                        rawFileName.set("valid.json")
                        normalizedFileName.set("valid.txt")
                    }
                }
            }
            """.trimIndent(),
        )

        result = runGradleAndFail("normalizeExternalOpenApiSpecs")

        assertTrue(result.output.contains("openApiExternalSpecs.specs.badOutput.normalizedFileName must end with .json"))
    }

    @Test
    fun `filters allowed operations injects tags and rewrites reachable schemas`() {
        writeSpec("specs/discord-like.json", discordLikeFixture())
        val output = tempDir.resolve("build/filtered/discord-like.json")
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()

        val task = project.tasks.register("filterDiscordLikeSpec", OpenApiFilterSpecTask::class.java) {
            inputSpec.set(project.layout.projectDirectory.file("specs/discord-like.json"))
            outputSpec.set(project.layout.buildDirectory.file("filtered/discord-like.json"))
            allowedOperations.set(
                mapOf(
                    "/users/@me" to listOf("get"),
                    "/guilds/{guild_id}" to listOf("get"),
                    "/guilds/{guild_id}/members/{user_id}" to listOf("get", "patch"),
                ),
            )
            injectedTag.set("Fixture")
        }.get()

        task.filter()

        val root = jsonMapper.readTree(output.toFile())
        val paths = root.path("paths")
        assertTrue(paths.has("/users/@me"))
        assertTrue(paths.has("/guilds/{guild_id}"))
        assertTrue(paths.has("/guilds/{guild_id}/members/{user_id}"))
        assertFalse(paths.has("/unused"))
        assertEquals("Fixture", paths.path("/users/@me").path("get").path("tags")[0].asText())
        assertFalse(paths.path("/guilds/{guild_id}/members/{user_id}").has("delete"))

        val schemas = root.path("components").path("schemas")
        assertTrue(schemas.has("CurrentUserResponse"))
        assertTrue(schemas.has("GuildResponse"))
        assertTrue(schemas.has("GuildMemberResponse"))
        assertTrue(schemas.has("SnowflakeType"))
        assertTrue(schemas.has("NullableRoleMarker"))
        assertTrue(schemas.has("Sticker"))
        assertTrue(schemas.has("StickerType"))
        assertTrue(schemas.has("ErrorResponse"))
        assertFalse(schemas.has("UnusedSchema"))
        assertFalse(schemas.has("UnusedComponentSchema"))
        assertEquals("boolean", schemas.path("NullableRoleMarker").path("type").asText())
        assertEquals(
            "#/components/schemas/StickerType",
            schemas.path("Sticker").path("properties").path("type").path("\$ref").asText(),
        )
        assertFalse(schemas.path("Sticker").path("properties").path("type").has("allOf"))
        assertFalse(schemas.path("Sticker").path("properties").path("type").has("enum"))
    }

    @Test
    fun `applies plugin conventions to a Gradle project`() {
        writeBuildFile("")
        writeResource("specs/sample.yml", tempDir.resolve("specs/sample.yml"))
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()

        OpenApiClientPlugin().apply(project)

        val extension = project.extensions.getByType(OpenApiClientExtension::class.java)
        extension.specPath.set("specs/sample.yml")
        extension.apiPackage.set("com.example.pet.api")
        extension.modelPackage.set("com.example.pet.model")
        extension.packageName.set("com.example.pet.invoker")
        extension.apis.set(listOf("Pets"))
        extension.schemaMappings.set(mapOf("UnusedInlineSchema" to "java.lang.Object"))
        extension.typeMappings.set(mapOf("unused-format" to "java.lang.String"))

        val generate = project.tasks.named("generate", GenerateTask::class.java).get()
        assertEquals("openapi", generate.group)
        assertFalse(generate.validateSpec.get())
        assertEquals("java", generate.generatorName.get())
        assertEquals("restclient", generate.library.get())
        assertEquals(tempDir.resolve("specs/sample.yml").toFile(), generate.inputSpec.get().asFile)
        assertEquals(tempDir.resolve("build/generated/openapi").toFile(), generate.outputDir.get().asFile)
        assertEquals("com.example.pet.api", generate.apiPackage.get())
        assertEquals("com.example.pet.model", generate.modelPackage.get())
        assertEquals("com.example.pet.invoker", generate.packageName.get())
        assertEquals(
            mapOf("apis" to "Pets", "models" to "", "supportingFiles" to ""),
            generate.globalProperties.get(),
        )
        assertEquals(mapOf("UnusedInlineSchema" to "java.lang.Object"), generate.schemaMappings.get())
        assertEquals(mapOf("unused-format" to "java.lang.String"), generate.typeMappings.get())
        assertEquals(
            mapOf(
                "sourceFolder" to "src/main/java",
                "serializationLibrary" to "jackson",
                "dateLibrary" to "java8",
                "useJakartaEe" to "true",
                "useBeanValidation" to "true",
                "useJackson3" to "true",
                "useSpringBoot4" to "true",
                "enumPropertyNaming" to "MACRO_CASE",
            ),
            generate.configOptions.get(),
        )

        val alias = project.tasks.named("generateOpenApiClient").get()
        assertTrue(alias.taskDependencies.getDependencies(alias).contains(generate))
        assertEquals(
            tempDir.resolve("openapi-specs").toFile(),
            project.extensions.getByType(OpenApiExternalSpecsExtension::class.java).specDirectory.get().asFile,
        )
        assertTrue(project.tasks.names.contains("downloadExternalOpenApiSpecs"))
        assertTrue(project.tasks.names.contains("normalizeExternalOpenApiSpecs"))

        val compileJava = project.tasks.named("compileJava", JavaCompile::class.java).get()
        assertTrue(compileJava.taskDependencies.getDependencies(compileJava).contains(generate))

        val java = project.extensions.getByType(JavaPluginExtension::class.java)
        assertTrue(
            java.sourceSets.getByName("main").java.srcDirs.any {
                it.toPath().endsWith("build/generated/openapi/src/main/java")
            },
        )

        val implementation = project.configurations.getByName("implementation").dependencies
        assertTrue(implementation.any { it.group == "org.springframework" && it.name == "spring-web" })
        assertTrue(implementation.any { it.group == "tools.jackson" && it.name == "jackson-bom" })
    }

    @Test
    fun `uses build file as inert generate input until a spec is configured`() {
        writeBuildFile("")
        val project = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()

        OpenApiClientPlugin().apply(project)

        val generate = project.tasks.named("generate", GenerateTask::class.java).get()
        assertEquals(project.buildFile, generate.inputSpec.get().asFile)
        assertTrue(generate.inputs.files.files.contains(project.buildFile))
    }

    @Test
    fun `validates yaml and json specs directly`() {
        val yaml = writeSpec(
            "specs/direct.yml",
            """
            openapi: 3.0.3
            info:
              title: Direct API
              version: 1.0.0
            paths:
              /pets:
                get:
                  tags:
                    - Pets
                  responses:
                    '204':
                      description: No content.
              /metadata:
                parameters: []
            """.trimIndent(),
        )
        validateConfiguration(
            specFile = yaml,
            apis = listOf("Pets"),
            schemaMappings = mapOf("PetEnvelope" to "com.example.PetEnvelope"),
            typeMappings = mapOf("uuid" to "java.util.UUID"),
        )

        val json = writeSpec(
            "specs/direct.json",
            """
            {
              "openapi": "3.0.3",
              "info": {"title": "Direct JSON API", "version": "1.0.0"},
              "paths": {}
            }
            """.trimIndent(),
        )
        validateConfiguration(specFile = json, apis = emptyList())
    }

    @Test
    fun `rejects invalid direct configuration`() {
        writeResource("specs/sample.yml", tempDir.resolve("specs/sample.yml"))
        val validSpec = tempDir.resolve("specs/sample.yml")

        assertValidationFails(
            "openApiClient.apiPackage is required",
            specFile = validSpec,
            apiPackage = "",
        )
        assertValidationFails(
            "openApiClient.apis must not contain blank values",
            specFile = validSpec,
            apis = listOf(" "),
        )
        assertValidationFails(
            "openApiClient.typeMappings must not contain blank keys or values",
            specFile = validSpec,
            typeMappings = mapOf("uuid" to ""),
        )
        assertValidationFails(
            "openApiClient.specPath is required",
            specPath = "specs/missing.yml",
            specFile = null,
        )
        assertValidationFails(
            "OpenAPI spec file does not exist",
            specFile = tempDir.resolve("specs/missing.yml"),
        )
        assertValidationFails(
            "OpenAPI spec file is not readable",
            specFile = tempDir,
        )

        val emptySpec = writeSpec("specs/empty.yml", "")
        assertValidationFails("OpenAPI spec file is empty", specFile = emptySpec)

        val invalidJson = writeSpec("specs/broken.json", "{")
        assertValidationFails("OpenAPI spec must be valid JSON or YAML", specFile = invalidJson)

        val missingPaths = writeSpec(
            "specs/missing-paths.yml",
            """
            openapi: 3.0.3
            info:
              title: Missing paths
              version: 1.0.0
            """.trimIndent(),
        )
        assertValidationFails("OpenAPI spec must contain a 'paths' object", specFile = missingPaths)

        writeResource("specs/untagged.yml", tempDir.resolve("specs/untagged.yml"))
        assertValidationFails(
            "Available tags: (none)",
            specFile = tempDir.resolve("specs/untagged.yml"),
            apis = listOf("Missing"),
        )
    }

    private fun writeSettings() {
        tempDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"consumer\"")
    }

    private fun writeBuildFile(contents: String) {
        tempDir.resolve("build.gradle.kts").writeText(contents)
    }

    private fun writeResource(resourcePath: String, target: Path) {
        target.parent.createDirectories()
        val resource = checkNotNull(javaClass.classLoader.getResource(resourcePath)) {
            "Missing test resource: $resourcePath"
        }
        target.writeText(resource.readText())
    }

    private fun runGradle(vararg arguments: String): BuildResult =
        GradleRunner.create()
            .withProjectDir(tempDir.toFile())
            .withArguments(arguments.toList() + "--stacktrace")
            .withPluginClasspath()
            .build()

    private fun runGradleAndFail(vararg arguments: String): BuildResult =
        GradleRunner.create()
            .withProjectDir(tempDir.toFile())
            .withArguments(arguments.toList() + "--stacktrace")
            .withPluginClasspath()
            .buildAndFail()

    private fun buildFileFor(
        specPath: String,
        apis: List<String> = listOf("Pets"),
    ): String =
        """
        plugins {
            id("dev.extratoast.openapi-client")
        }

        openApiClient {
            specPath.set("$specPath")
            apiPackage.set("com.example.pet.api")
            modelPackage.set("com.example.pet.model")
            packageName.set("com.example.pet.invoker")
            apis.set(listOf(${apis.joinToString { it.toKotlinString() }}))
        }
        """.trimIndent()

    private fun writeSpec(relativePath: String, contents: String): Path {
        val target = tempDir.resolve(relativePath)
        target.parent.createDirectories()
        target.writeText(contents)
        return target
    }

    private fun validateConfiguration(
        specFile: Path?,
        specPath: String? = specFile?.toString() ?: "spec.yml",
        apiPackage: String? = "com.example.pet.api",
        modelPackage: String? = "com.example.pet.model",
        packageName: String? = "com.example.pet.invoker",
        apis: List<String> = listOf("Pets"),
        schemaMappings: Map<String, String> = emptyMap(),
        typeMappings: Map<String, String> = emptyMap(),
    ) {
        OpenApiClientConfigurationValidator.validate(
            specPath = specPath,
            specFile = specFile?.toFile(),
            apiPackage = apiPackage,
            modelPackage = modelPackage,
            packageName = packageName,
            apis = apis,
            schemaMappings = schemaMappings,
            typeMappings = typeMappings,
        )
    }

    private fun assertValidationFails(
        expectedMessage: String,
        specFile: Path?,
        specPath: String? = specFile?.toString() ?: "spec.yml",
        apiPackage: String? = "com.example.pet.api",
        modelPackage: String? = "com.example.pet.model",
        packageName: String? = "com.example.pet.invoker",
        apis: List<String> = listOf("Pets"),
        schemaMappings: Map<String, String> = emptyMap(),
        typeMappings: Map<String, String> = emptyMap(),
    ) {
        val error = assertThrows(GradleException::class.java) {
            validateConfiguration(
                specPath = specPath,
                specFile = specFile,
                apiPackage = apiPackage,
                modelPackage = modelPackage,
                packageName = packageName,
                apis = apis,
                schemaMappings = schemaMappings,
                typeMappings = typeMappings,
            )
        }
        assertTrue(
            error.message?.contains(expectedMessage) == true,
            "Expected '${error.message}' to contain '$expectedMessage'",
        )
    }

    private fun preparedSpecText(): String =
        """
        openapi: 3.0.3
        info:
          title: Prepared API
          version: 1.0.0
        paths:
          /status:
            get:
              tags:
                - Prepared
              operationId: getStatus
              responses:
                '204':
                  description: No content.
        """.trimIndent()

    private fun discordLikeFixture(): String =
        """
        {
          "openapi": "3.1.0",
          "info": {"title": "Discord-like Fixture", "version": "1.0.0"},
          "paths": {
            "/users/@me": {
              "get": {
                "operationId": "getCurrentUser",
                "responses": {
                  "200": {
                    "description": "ok",
                    "content": {
                      "application/json": {
                        "schema": {"${'$'}ref": "#/components/schemas/CurrentUserResponse"}
                      }
                    }
                  },
                  "4XX": {"${'$'}ref": "#/components/responses/ClientErrorResponse"}
                }
              }
            },
            "/guilds/{guild_id}": {
              "parameters": [{"${'$'}ref": "#/components/parameters/GuildId"}],
              "get": {
                "operationId": "getGuild",
                "responses": {
                  "200": {"${'$'}ref": "#/components/responses/GuildResponse"}
                }
              }
            },
            "/guilds/{guild_id}/members/{user_id}": {
              "get": {
                "operationId": "getGuildMember",
                "responses": {
                  "200": {
                    "description": "ok",
                    "content": {
                      "application/json": {
                        "schema": {"${'$'}ref": "#/components/schemas/GuildMemberResponse"}
                      }
                    }
                  }
                }
              },
              "patch": {
                "operationId": "updateGuildMember",
                "requestBody": {
                  "content": {
                    "application/json": {
                      "schema": {"${'$'}ref": "#/components/schemas/GuildMemberResponse"}
                    }
                  }
                },
                "responses": {"204": {"description": "updated"}}
              },
              "delete": {
                "operationId": "deleteGuildMember",
                "responses": {"204": {"description": "deleted"}}
              }
            },
            "/unused": {
              "get": {
                "operationId": "unused",
                "responses": {
                  "200": {
                    "description": "unused",
                    "content": {
                      "application/json": {
                        "schema": {"${'$'}ref": "#/components/schemas/UnusedSchema"}
                      }
                    }
                  }
                }
              }
            }
          },
          "components": {
            "parameters": {
              "GuildId": {
                "name": "guild_id",
                "in": "path",
                "required": true,
                "schema": {"${'$'}ref": "#/components/schemas/SnowflakeType"}
              }
            },
            "responses": {
              "GuildResponse": {
                "description": "ok",
                "content": {
                  "application/json": {
                    "schema": {"${'$'}ref": "#/components/schemas/GuildResponse"}
                  }
                }
              },
              "ClientErrorResponse": {
                "description": "error",
                "content": {
                  "application/json": {
                    "schema": {"${'$'}ref": "#/components/schemas/ErrorResponse"}
                  }
                }
              },
              "UnusedResponse": {
                "description": "unused",
                "content": {
                  "application/json": {
                    "schema": {"${'$'}ref": "#/components/schemas/UnusedComponentSchema"}
                  }
                }
              }
            },
            "schemas": {
              "CurrentUserResponse": {
                "type": "object",
                "properties": {
                  "id": {"${'$'}ref": "#/components/schemas/SnowflakeType"},
                  "role_marker": {"${'$'}ref": "#/components/schemas/NullableRoleMarker"}
                }
              },
              "GuildResponse": {
                "type": "object",
                "properties": {
                  "id": {"${'$'}ref": "#/components/schemas/SnowflakeType"},
                  "sticker": {"${'$'}ref": "#/components/schemas/Sticker"}
                }
              },
              "GuildMemberResponse": {
                "type": "object",
                "properties": {
                  "user": {"${'$'}ref": "#/components/schemas/CurrentUserResponse"}
                }
              },
              "SnowflakeType": {"type": "string"},
              "NullableRoleMarker": {"type": "null"},
              "Sticker": {
                "type": "object",
                "properties": {
                  "type": {
                    "allOf": [{"${'$'}ref": "#/components/schemas/StickerType"}],
                    "enum": [1, 2]
                  }
                }
              },
              "StickerType": {"type": "integer", "enum": [1, 2, 3]},
              "ErrorResponse": {"type": "object", "properties": {"message": {"type": "string"}}},
              "UnusedSchema": {"type": "object"},
              "UnusedComponentSchema": {"type": "object"}
            }
          }
        }
        """.trimIndent()

    private fun String.toKotlinString(): String =
        "\"${replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
}
