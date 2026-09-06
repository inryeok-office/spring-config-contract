# Contributing

Thank you for contributing to Spring Config Contract. All repository-facing communication must be in English.

## Workflow

Issue → branch → implementation → tests → pull request → maintainer review → CI → squash merge.

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

Every new behavior must include tests. Keep pull requests focused, explain architectural effects, and document user-visible changes. CI must pass before maintainer review.
