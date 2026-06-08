package dev.extratoast.openapi.client

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.io.File
import javax.inject.Inject

abstract class OpenApiClientExtension @Inject constructor(objects: ObjectFactory) {
    val specPath: Property<String> = objects.property(String::class.java)
    val apiPackage: Property<String> = objects.property(String::class.java)
    val modelPackage: Property<String> = objects.property(String::class.java)
    val packageName: Property<String> = objects.property(String::class.java)
    val apis: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    val schemaMappings: MapProperty<String, String> =
        objects.mapProperty(String::class.java, String::class.java).convention(emptyMap())
    val typeMappings: MapProperty<String, String> =
        objects.mapProperty(String::class.java, String::class.java).convention(emptyMap())
}

class OpenApiClientPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("java-library")
        project.pluginManager.apply("org.openapi.generator")

        val extension = project.extensions.create(
            "openApiClient",
            OpenApiClientExtension::class.java,
        )

        project.repositories.mavenCentral()
        project.addGeneratedClientDependencies()

        val generatedRoot = project.layout.buildDirectory.dir("generated/openapi")
        val generatedJavaSrc = generatedRoot.map { it.dir("src/main/java") }
        val taskInputSpecFile = project.providers.provider {
            val configuredSpec = extension.specPath.orNull
                ?.takeIf { it.isNotBlank() }
                ?.let { resolveSpecFile(project.rootProject.projectDir, it) }

            if (configuredSpec != null && configuredSpec.exists() && configuredSpec.isFile) {
                configuredSpec
            } else {
                project.buildFile
            }
        }
        val inputSpecFile = project.layout.file(taskInputSpecFile)

        val generate = project.tasks.register("generate", GenerateTask::class.java, Action<GenerateTask> {
            group = "openapi"
            description = "Generates the Java client from the configured OpenAPI spec."

            validateSpec.set(false)
            generatorName.set("java")
            library.set("restclient")
            inputSpec.set(inputSpecFile)
            outputDir.set(generatedRoot)

            configOptions.set(
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
            )

            apiPackage.set(extension.apiPackage.orElse(""))
            modelPackage.set(extension.modelPackage.orElse(""))
            packageName.set(extension.packageName.orElse(""))

            generateModelTests.set(false)
            generateApiTests.set(false)
            generateApiDocumentation.set(true)
            generateModelDocumentation.set(true)

            inlineSchemaOptions.set(mapOf("RESOLVE_INLINE_ENUMS" to "true"))
            schemaMappings.set(extension.schemaMappings)
            typeMappings.set(extension.typeMappings)

            globalProperties.set(
                extension.apis.map { apiList ->
                    mapOf(
                        "apis" to apiList.joinToString(","),
                        "models" to "",
                        "supportingFiles" to "",
                    )
                },
            )

            inputs.files(
                project.providers.provider {
                    extension.specPath.orNull
                        ?.takeIf { it.isNotBlank() }
                        ?.let { resolveSpecFile(project.rootProject.projectDir, it) }
                        ?.takeIf { it.exists() && it.isFile }
                        ?.let { listOf(it) }
                        ?: emptyList()
                },
            )
                .withPropertyName("specFile")
                .withPathSensitivity(PathSensitivity.RELATIVE)
            inputs.property("specPath", extension.specPath.orElse(""))
            inputs.property("apiPackage", extension.apiPackage.orElse(""))
            inputs.property("modelPackage", extension.modelPackage.orElse(""))
            inputs.property("packageName", extension.packageName.orElse(""))
            inputs.property("apis", extension.apis)
            inputs.property("schemaMappings", extension.schemaMappings)
            inputs.property("typeMappings", extension.typeMappings)
            outputs.dir(generatedRoot)
            outputs.cacheIf { true }

            doFirst {
                OpenApiClientConfigurationValidator.validate(
                    specPath = extension.specPath.orNull,
                    specFile = extension.specPath.orNull
                        ?.takeIf { it.isNotBlank() }
                        ?.let { resolveSpecFile(project.rootProject.projectDir, it) },
                    apiPackage = extension.apiPackage.orNull,
                    modelPackage = extension.modelPackage.orNull,
                    packageName = extension.packageName.orNull,
                    apis = extension.apis.getOrElse(emptyList()),
                    schemaMappings = extension.schemaMappings.getOrElse(emptyMap()),
                    typeMappings = extension.typeMappings.getOrElse(emptyMap()),
                )
            }
        })

        project.tasks.register("generateOpenApiClient", Action<Task> {
            group = "openapi"
            description = "Alias for generate."
            dependsOn(generate)
        })

        project.extensions.configure(JavaPluginExtension::class.java, Action<JavaPluginExtension> {
            sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).java.srcDir(generatedJavaSrc)
        })

        project.tasks.withType(JavaCompile::class.java).configureEach(Action<JavaCompile> {
            dependsOn(generate)
        })
    }

    private fun Project.addGeneratedClientDependencies() {
        dependencies.add("implementation", "org.springframework:spring-web:7.0.5")
        dependencies.add("implementation", "org.springframework:spring-context:7.0.5")
        dependencies.add("implementation", dependencies.platform("tools.jackson:jackson-bom:3.1.0"))
        dependencies.add("implementation", "com.fasterxml.jackson.core:jackson-annotations:2.21")
        dependencies.add("implementation", "tools.jackson.core:jackson-core")
        dependencies.add("implementation", "tools.jackson.core:jackson-databind")
        dependencies.add("implementation", "org.openapitools:jackson-databind-nullable:0.2.10")
        dependencies.add("compileOnly", "jakarta.validation:jakarta.validation-api:3.1.1")
        dependencies.add("compileOnly", "jakarta.annotation:jakarta.annotation-api:3.0.0")
    }
}

internal object OpenApiClientConfigurationValidator {
    private val jsonMapper = ObjectMapper()
    private val yamlMapper = ObjectMapper(YAMLFactory())
    private val httpMethods = setOf("get", "post", "put", "patch", "delete", "head", "options", "trace")

    fun validate(
        specPath: String?,
        specFile: File?,
        apiPackage: String?,
        modelPackage: String?,
        packageName: String?,
        apis: List<String>,
        schemaMappings: Map<String, String>,
        typeMappings: Map<String, String>,
    ) {
        requireNonBlank("specPath", specPath)
        requireNonBlank("apiPackage", apiPackage)
        requireNonBlank("modelPackage", modelPackage)
        requireNonBlank("packageName", packageName)

        apis.forEach { api ->
            if (api.isBlank()) {
                throw GradleException("openApiClient.apis must not contain blank values.")
            }
        }
        validateMappings("schemaMappings", schemaMappings)
        validateMappings("typeMappings", typeMappings)

        val file = specFile ?: throw GradleException("openApiClient.specPath is required.")
        if (!file.exists()) {
            throw GradleException("OpenAPI spec file does not exist: ${file.absolutePath}")
        }
        if (!file.isFile || !file.canRead()) {
            throw GradleException("OpenAPI spec file is not readable: ${file.absolutePath}")
        }
        if (file.length() == 0L) {
            throw GradleException("OpenAPI spec file is empty: ${file.absolutePath}")
        }

        val root = parseSpec(file)
        if (!root.isObject || root.path("openapi").isMissingNode) {
            throw GradleException("OpenAPI spec must be an object with an 'openapi' field: ${file.absolutePath}")
        }
        val paths = root.path("paths")
        if (!paths.isObject) {
            throw GradleException("OpenAPI spec must contain a 'paths' object: ${file.absolutePath}")
        }

        if (apis.isNotEmpty()) {
            val operationTags = collectOperationTags(paths)
            val missingApis = apis.filterNot(operationTags::contains)
            if (missingApis.isNotEmpty()) {
                val available = operationTags.sorted().joinToString(", ").ifBlank { "(none)" }
                throw GradleException(
                    "Selected OpenAPI API/tag(s) are not present in ${file.absolutePath}: " +
                        "${missingApis.joinToString(", ")}. Available tags: $available",
                )
            }
        }
    }

    private fun requireNonBlank(name: String, value: String?) {
        if (value.isNullOrBlank()) {
            throw GradleException("openApiClient.$name is required and must not be blank.")
        }
    }

    private fun validateMappings(name: String, mappings: Map<String, String>) {
        mappings.forEach { (key, value) ->
            if (key.isBlank() || value.isBlank()) {
                throw GradleException("openApiClient.$name must not contain blank keys or values.")
            }
        }
    }

    private fun parseSpec(file: File): JsonNode {
        val mapper = if (file.extension.equals("yaml", ignoreCase = true) ||
            file.extension.equals("yml", ignoreCase = true)
        ) {
            yamlMapper
        } else {
            jsonMapper
        }

        return try {
            mapper.readTree(file)
        } catch (exc: JsonProcessingException) {
            throw GradleException("OpenAPI spec must be valid JSON or YAML: ${file.absolutePath}", exc)
        }
    }

    private fun collectOperationTags(paths: JsonNode): Set<String> {
        val tags = linkedSetOf<String>()
        val pathItems = paths.fields()
        while (pathItems.hasNext()) {
            val pathItem = pathItems.next().value
            if (!pathItem.isObject) continue
            val operations = pathItem.fields()
            while (operations.hasNext()) {
                val operation = operations.next()
                if (operation.key.lowercase() !in httpMethods || !operation.value.isObject) continue
                val operationTags = operation.value.path("tags")
                if (!operationTags.isArray) continue
                operationTags.forEach { tag ->
                    if (tag.isTextual) tags.add(tag.asText())
                }
            }
        }
        return tags
    }
}

private fun resolveSpecFile(rootProjectDir: File, specPath: String): File {
    val configured = File(specPath)
    return if (configured.isAbsolute) configured else rootProjectDir.resolve(specPath)
}
