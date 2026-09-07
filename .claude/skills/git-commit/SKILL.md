---
name: git-commit
description: Commit staged work in this repository following its Conventional Commits, branch-naming, validation, and secret-hygiene rules. Use when the user asks to commit changes or types /git-commit.
---

# git-commit

Create a commit that satisfies `AGENTS.md`, `docs/development.md`, and the
`Validate PR policy` workflow that will later read these commits.

## 1. Inspect before writing anything

```bash
git status --short
git branch --show-current
git diff            # unstaged
git diff --staged   # staged
```

Read the actual diff. The commit message describes what the diff does, never
what was intended.

## 2. Branch guard

Never commit on `main` or `master` — the branch is protected and the
`PostToolUse` hook reports such commits. When the current branch is `main`,
create the correct branch first:

| Change | Branch |
| --- | --- |
| Feature | `feat/<issue>-description` |
| Bug fix | `fix/<issue>-description` |
| Documentation | `docs/<issue>-description` |
| Refactor | `refactor/<issue>-description` |
| Tests | `test/<issue>-description` |

`<issue>` is the GitHub Issue number. A small documentation or typo fix may
proceed without an Issue, but still uses a branch.

## 3. Validate

Run before committing, and report the real results:

```bash
./gradlew check      # gradlew.bat on Windows
git diff --check     # whitespace and conflict markers
```

Never commit generated `build/` output or local Gradle state. Never stage
`.env*`, `*.pem`, `*.key`, keystores, or credential files — the `PreToolUse`
hook blocks this, and `AGENTS.md` forbids it outright.

## 4. Message format

Conventional Commits, matching the title pattern that `pr-policy.yml`
enforces:

```
<type>(<scope>)?<!>?: <subject>

<body>
```

- **type**: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `perf`,
  `build`, `ci`, `revert`
- **scope** (optional): `core`, `spring`, `deployment`, `gradle`, `test`,
  `deps`, or a specific area such as `hooks`
- **subject**: English, imperative mood, lowercase, no trailing period,
  roughly 72 characters or less
- **`!`**: only for a breaking change, which also needs a PR entry under
  `## Breaking Changes`
- **body**: English, explains why the change is needed and any compatibility
  or public API impact

Do **not** append `(#123)` to the subject. The repository squash-merges, and
GitHub adds the PR number at merge time.

Examples from this repository's history:

```
docs: document protected main merge workflow
chore: enforce contribution governance
chore(deps): bump gradle/actions from 4 to 6
```

Append the attribution trailers the harness requires for this session.

## 5. Commit

Stage deliberately — name the paths rather than using `git add -A` — then
commit. Never pass `--no-verify` or `--no-gpg-sign`; if a hook fails, fix the
cause. Prefer a new commit over `--amend` on work that was already pushed.

## 6. Report

State the branch, the commit subject, and the validation results, including
anything that failed or was skipped. Never claim a command passed without
running it.
