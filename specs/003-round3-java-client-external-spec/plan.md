# Implementation Plan: Round 3 Java Client Convention and External Spec Filtering

## Technical Design

- Extend `OpenApiClientExtension` with configurable generator knobs and dependency version properties while retaining website-compatible defaults.
- Keep the existing `generate` task name for compatibility and map extension properties into OpenAPI Generator `GenerateTask`.
- Add named filter definitions under `openApiExternalSpecs.filters`; register one `OpenApiFilterSpecTask` per definition.
- Keep `OpenApiFilterSpecTask` public so consumers can still wire custom task instances directly.
- Add `OpenApiProvenanceBannerTask` and `OpenApiDriftCheckTask` as generic text-file helpers for generated artifacts.
- Update README examples to show the shared DSL instead of website-local Groovy filtering.
- Prefer direct task and validator tests for coverage; use TestKit only where Gradle plugin wiring is the behavior under test.

## Requirement Mapping

- FR-1, FR-2: New extension properties plus generate-task assertions.
- FR-3: Existing Java source-set and compile dependencies remain in plugin wiring tests.
- FR-4: Existing validator tests remain and are extended for new configurable fields.
- FR-5, FR-6: Named filter container and TestKit/direct filter tests.
- FR-7: Existing download/normalize tests remain unchanged.
- FR-8: Direct provenance and drift task tests.
- FR-9: Existing task type behavior is preserved.
- FR-10: README and spec-kit docs are updated.

## Verification

- Local non-network checks: `./gradlew test jacocoTestCoverageVerification --no-daemon`.
- The user-provided sandbox blocks networked Gradle dependency resolution. If caches are incomplete, reason about compilation and leave CI verification to the orchestrator.
