# Project Tree

```text
openvino-notes/
|-- .github/
|   |-- actions/
|   |   `-- setup-android-gradle/
|   |-- scripts/
|   |   |-- android/
|   |   |-- codeql/
|   |   |-- preflight/
|   |   |-- pr/
|   |   |-- quality/
|   |   |-- release/
|   |   |-- security/
|   |   `-- setup/
|   `-- workflows/
|-- ai/
|-- app/
|-- data/
|-- docs/
|   `-- developer/
|-- domain/
|-- gradle/
|-- build.gradle.kts
|-- detekt.yml
|-- gradle.properties
|-- lint.xml
|-- settings.gradle.kts
`-- README.md
```

This repository is an Android multi-module project. The application idea is an AI-assisted notes app powered by OpenVINO, but the current codebase is still at an early implementation stage.

```text
modules
|-- :app
|   |-- role
|   |   |-- Android application module
|   |   |-- Compose UI
|   |   `-- app-level wiring
|   `-- current-state
|       |-- MainActivity still renders starter content
|       |-- ui/notes is placeholder UI
|       |-- ui/editor is placeholder UI
|       `-- CI debug and androidTest APKs come from here
|-- :domain
|   |-- role
|   |   |-- business contracts
|   |   |-- models
|   |   `-- use cases
|   `-- current-state
|       |-- Note exists
|       |-- NotesRepository is placeholder
|       `-- AnalyzeNoteUseCase is placeholder
|-- :data
|   |-- role
|   |   |-- repository implementations
|   |   `-- storage and mapping
|   `-- current-state
|       |-- structural classes exist
|       `-- implementation depth is minimal
`-- :ai
    |-- role
    |   |-- OpenVINO-facing inference layer
    |   `-- result processing
    `-- current-state
        |-- named integration points exist
        `-- production inference behavior is not implemented yet
```

```text
build-and-quality
|-- root-config
|   `-- [build.gradle.kts](/Users/anesterov/repos/openvino-notes/build.gradle.kts)
|-- enforced-tools
|   |-- ktlint
|   |-- detekt
|   |-- Android Lint
|   |-- kover
|   `-- dependency locking
`-- baseline
    |-- JDK 17
    |-- compileSdk 36
    |-- targetSdk 36
    |-- minSdk 33
    |-- Kotlin 2.0.21
    `-- AGP 8.13.2
```

```text
ci
|-- workflows
|   |-- ci.yml
|   |   `-- top-level orchestrator
|   |-- preflight.yml
|   |   `-- decides whether release, CodeQL, and emulator jobs should run
|   |-- quality.yml
|   |   `-- main developer gate
|   |       |-- formatting
|   |       |-- linting
|   |       |-- debug build
|   |       |-- host unit tests
|   |       `-- coverage
|   |-- security.yml
|   |   `-- gitleaks
|   |-- release.yml
|   |   `-- release assemble and release lint
|   |-- android-tests.yml
|   |   `-- emulator validation and instrumentation
|   |-- codeql.yml
|   |   `-- CodeQL-oriented build and analysis
|   `-- supply-chain.yml
|       `-- dependency review on pull requests
`-- scripts
    |-- android/
    |-- codeql/
    |-- preflight/
    |-- pr/
    |-- quality/
    |-- release/
    |-- security/
    `-- setup/
```

```text
contributor-notes
|-- build-logic-change
|   `-- check .github/scripts/ before editing workflow YAML
|-- module-boundary-change
|   `-- update settings.gradle.kts, module build files, and docs together
`-- test-failure-interpretation
    `-- many current tests are scaffolding tests
        `-- build or packaging regressions are often the first suspect
```

In practice, this means contributors usually work in one of two directions:

- application code under `app`, `domain`, `data`, and `ai`
- build and automation code under `.github/`
