# Tasks: External OpenAPI Spec Pipeline

- [x] T001 [FR-1] Add spec-kit docs for the Round 2 external spec pipeline.
- [x] T002 [FR-1, FR-2, FR-3] Add external spec DSL and download/normalize task registration.
- [x] T003 [FR-5, FR-6, FR-7, FR-8] Add reusable OpenAPI filter/preprocess task type.
- [x] T004 [FR-4] Add validation and clear Gradle errors for misconfigured spec tasks.
- [x] T005 [SC-1, SC-2] Add TestKit and direct fixture tests for download, normalize, and filter behavior.
- [x] T006 [SC-3] Run existing client generation tests and fix regressions.
- [x] T007 [SC-4] Verify CI still has a `Pipeline Complete` terminal job and coverage gate.
- [ ] T008 Commit, push, open PR, poll CI, and squash-merge when green.

## Dependencies

- T002 precedes T005.
- T003 and T004 precede T005.
- T006 follows T002-T005.
- T008 follows local verification.
