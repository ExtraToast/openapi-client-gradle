# Round 3 Java Client Convention and External Spec Filtering Specification

## Overview

Round 3 promotes the remaining website-local Java OpenAPI client convention and reusable external-spec preparation behavior into this shared Gradle plugin. The implementation builds on the existing round-2 external-spec pipeline and keeps all consumer-specific values in consumer configuration.

Reference inputs include the website Java client convention, website external spec scripts, website Discord filtering, and the personal-stack generated-contract banner. The shared plugin must not hardcode vendor URLs, domains, package names, paths, namespaces, hostnames, image prefixes, queue names, IP addresses, or a frontend TypeScript generator.

## Functional Requirements

- FR-1: The Java client convention MUST expose generator options currently hardcoded by the website convention as configurable extension properties with website-compatible defaults.
- FR-2: Consumers MUST be able to override generator name, library, serialization library, date library, enum naming, Jakarta/Bean Validation/Jackson/Spring Boot 4 flags, docs/tests flags, supporting files, inline schema options, schema mappings, and type mappings.
- FR-3: Generated Java source MUST remain wired into the main Java source set and `JavaCompile` tasks.
- FR-4: Required client configuration and selected API/tag validation MUST continue to fail with clear Gradle errors.
- FR-5: The external-spec DSL MUST expose reusable named filter definitions so consumers can configure filtering without copying the website Groovy task body.
- FR-6: Named filters MUST support operation allow lists, injected tags, reachable schema pruning, OpenAPI 3.1 `type: "null"` rewrites, and redundant single-ref `allOf` plus `enum` rewrites.
- FR-7: Download and normalize tasks MUST remain generic and use consumer-supplied URLs/file names only.
- FR-8: The plugin MUST provide generic provenance banner and drift-check task types that work for any generated text file and do not assume a TypeScript generator.
- FR-9: The implementation MUST preserve the round-2 pipeline behavior and existing public task types.
- FR-10: The repo MUST document the new Java convention, named filter DSL, provenance banner, and drift-check helpers.

## Success Criteria

- SC-1: A Brevo-like client can use default Java generator settings while overriding packages, selected APIs, and mappings.
- SC-2: A Discord-like spec can be filtered through the shared named filter DSL with only consumer-owned allow lists and tag names.
- SC-3: A consumer can override Java generator options without editing the plugin source.
- SC-4: Provenance banner and drift-check helpers can be exercised in direct in-process tests.
- SC-5: CI keeps the existing terminal `Pipeline Complete` job and the 80% JaCoCo line coverage gate.

## Out of Scope

- Hardcoded Discord, Brevo, Hornet, website, or personal-stack URLs.
- Running networked spec refreshes in this repo.
- Forcing `@hey-api/openapi-ts`, `openapi-typescript`, or any other frontend TypeScript generator.
- Migrating reference repos during this task.
- Replacing internal contract validation tools.
