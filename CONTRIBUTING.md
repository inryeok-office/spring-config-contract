# Contributing

Thank you for contributing to Spring Config Contract. All repository-facing communication must be in English.

## Quick path

For a small change: branch → focused change → relevant validation → PR.

For a normal feature or bug: Issue → branch → implementation and tests → PR → maintainer review → CI → squash merge.

See the [detailed contribution workflow](docs/contribution-workflow.md) for architecture and public API changes.

Use one of these branch names:

- `feat/<issue>-description`
- `fix/<issue>-description`
- `docs/<issue>-description`
- `refactor/<issue>-description`
- `test/<issue>-description`

Use Conventional Commits, for example:

```text
feat: add configuration requirement model
fix: resolve profile lookup correctly
test: add configuration fixture
docs: improve quick start
```

Every new behavior must include tests. Keep pull requests focused, explain architectural effects, and document user-visible changes. CI must pass before maintainer review. Read [AGENTS.md](AGENTS.md) and the relevant module instructions before implementation.

Issue and PR metadata, Dependabot exceptions, reviewer ownership, and the distinction between automated checks and maintainer judgment are documented in the [governance policy](docs/governance.md).
