# Developer Docs

```text
developer/
|-- README.md
|-- project-tree.md
`-- ci-local.md
```

```text
read-order
|-- 1
|   `-- [project-tree.md](./project-tree.md)
|       `-- understand repository boundaries
`-- 2
    `-- [ci-local.md](./ci-local.md)
        `-- reproduce the checks that gate pull requests and main
```

```text
engineering-state
|-- build-and-ci
|   |-- mature enough to enforce quality gates
|   `-- broad enough to cover lint, tests, release, security, and emulator runs
`-- product-code
    |-- still scaffolded in many places
    |-- safe to treat as early-stage implementation
    `-- should not be documented as feature-complete
```

```text
where-you-will-work
|-- application-code
|   |-- app/
|   |-- domain/
|   |-- data/
|   `-- ai/
`-- automation
    `-- .github/
```

This documentation is written for contributors who need to understand the repository quickly and run the same checks that GitHub Actions runs. The codebase is already strict about build quality, but much of the product logic is still scaffolded, so the docs intentionally describe the current state rather than an ideal future state.
