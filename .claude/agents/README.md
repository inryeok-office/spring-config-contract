# Agents

Task-specialised subagents for this repository. Each one encodes the parts of
`AGENTS.md` and `docs/` that its task needs, and links to the canonical
documents instead of duplicating them.

| Agent | Model | Use it for |
| --- | --- | --- |
| `feature-builder` | inherit | Implementing a feature or bug fix inside one Issue's scope, routed to the owning module. |
| `test-designer` | inherit | Designing and writing deterministic tests, fixtures, and regression cases. |
| `boundary-guard` | sonnet | Read-only check of module boundaries, dependency direction, and public API exposure. |
| `change-reviewer` | inherit | Maintainer-style review of a diff for correctness and policy violations. |
| `build-verifier` | haiku | Running Gradle validation and reporting real pass/fail results. |
| `docs-curator` | sonnet | Keeping docs, module `AGENTS.md`, and ADRs consistent with a change. |

Committing and opening a PR are covered by the existing skills
`.claude/skills/git-commit` and `.claude/skills/create-pr`.

## Typical pipelines

New feature:

```
feature-builder → test-designer → build-verifier → boundary-guard
               → docs-curator → change-reviewer → /git-commit → /create-pr
```

Review of an existing branch:

```
change-reviewer → boundary-guard → build-verifier
```

Failing build:

```
build-verifier (diagnose) → feature-builder or test-designer (fix) → build-verifier
```

## Conventions

- Read-only agents (`boundary-guard`, `build-verifier`) report; they never edit,
  commit, or weaken a test to make a check pass.
- Every agent reports commands it actually ran, and says explicitly what it
  could not verify.
- Repository-facing output is English.

## Automatic delegation

Each `description` is written so Claude Code can route a request without being
told which agent to use: it states what the agent does, the concrete triggers
that should invoke it, and what it must not be used for. Naming an agent
explicitly ("review this with change-reviewer") always wins over automatic
routing, and is the reliable option when the request is ambiguous.
