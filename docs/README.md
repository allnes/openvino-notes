# Docs Tree

```text
docs/
|-- README.md
|   `-- role
|       `-- documentation index
`-- developer/
    |-- README.md
    |   `-- role
    |       `-- contributor entry point
    |-- project-tree.md
    |   `-- role
    |       `-- repository, module, build, and CI structure
    `-- ci-local.md
        `-- role
            `-- local CI reproduction on Linux, macOS, and Windows
```

```text
start-here
`-- developer/
    |-- [README.md](./developer/README.md)
    |-- [project-tree.md](./developer/project-tree.md)
    `-- [ci-local.md](./developer/ci-local.md)
```

```text
scope
|-- audience
|   `-- contributors
|-- focus
|   |-- repository shape
|   |-- implementation maturity
|   |-- CI structure
|   `-- local developer workflows
`-- excludes
    `-- end-user product docs
```

This directory is written for developers. Start with the contributor entry point, then use the project tree to understand the repository, and finally use the CI guide to reproduce checks locally on any supported OS.
