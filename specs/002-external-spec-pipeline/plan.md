# Implementation Plan: External OpenAPI Spec Pipeline

## Technical Design

- Add `openApiExternalSpecs` as an opt-in extension on the existing plugin.
- Model configured upstream specs as a Gradle named domain object container.
- Register `downloadExternalOpenApiSpecs` for URL/file acquisition into the configured central directory.
- Register `normalizeExternalOpenApiSpecs` for deterministic JSON/YAML parsing and minified JSON output.
- Add `OpenApiFilterSpecTask` as a public task type so consumers can register vendor-specific filtering tasks with their own allow lists and tags.
- Implement JSON/YAML parsing and output with Jackson, reusing the existing dependencies.

## Functional Requirement Mapping

- FR-1: `OpenApiExternalSpecsExtension` with `specDirectory` and `specs`.
- FR-2: `DownloadExternalOpenApiSpecsTask` reads configured `sourceUrl` and writes raw files.
- FR-3, FR-4: `NormalizeExternalOpenApiSpecsTask` reads raw files and writes `.json` minified output with validation.
- FR-5: `OpenApiFilterSpecTask.allowedOperations` and `injectedTag`.
- FR-6: Reachability traversal starts from retained paths and non-schema component sections, then prunes `components.schemas`.
- FR-7: Recursive rewrite changes `type: "null"` to `type: "boolean"` when enabled.
- FR-8: Recursive rewrite collapses single-ref `allOf` nodes with sibling `enum` when enabled.
- FR-9: Existing `generate` validation remains in `doFirst`, after consumer task dependencies.
- FR-10: Tests use synthetic fixture URLs and allow lists only.

## Verification

- Unit tests cover direct filter and normalization behavior.
- TestKit covers extension task wiring and file-backed downloads.
- Local verification runs `./gradlew build --no-daemon`.
