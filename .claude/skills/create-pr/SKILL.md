---
name: create-pr
description: Open a pull request that passes this repository's Validate PR policy check - Conventional Commit title, full PR template body, Issue reference, and maintainer review. Use when the user asks to open a PR or types /create-pr.
---

# create-pr

Open a PR that satisfies `.github/pull_request_template.md`,
`.github/workflows/pr-policy.yml`, and `docs/governance.md`.

## 1. Preconditions

```bash
git branch --show-current      # must not be main
git status --short             # must be clean
git log origin/main..HEAD      # commits that will land
./gradlew check                # must pass, and you must report the result
git push -u origin <branch>
```

The branch follows `<type>/<issue>-description`. Pushing to `main` is blocked
by the ruleset and by the `PreToolUse` hook.

## 2. Title

`pr-policy.yml` rejects anything that does not match:

```
^(feat|fix|docs|refactor|test|chore|perf|build|ci|revert)(\(scope\))?!?: subject
```

Write it in English, imperative mood, describing the whole PR — usually the
same shape as the primary commit subject.

## 3. Body

Use every section of the template. The policy check **fails** unless these four
appear as exact H2 headings: `## Summary`, `## Related Issue`, `## Testing`,
`## Breaking Changes`. Keep the remaining template sections as well.

```markdown
## Summary
What this change does and why.

## Related Issue
Closes #123

## Changes
- Important implementation or documentation changes.

## Behavior and Compatibility
Behavior, compatibility, or public API impact, or "None".

## Public API Impact
Added or changed public API, or "None".

## Testing
`./gradlew check` — passed. Plus any targeted tests and their results.

## Breaking Changes
None

## Documentation
What documentation changed, or why none was needed.

## Checklist
- [x] The change is scoped and documented where needed.
- [x] Tests cover new functionality or the reason no tests are needed is explained.
- [x] `./gradlew check` passes locally.
- [x] No credentials or sensitive local files are included.
```

**Related Issue** must contain `Closes #<n>` (or `Fixes`/`Resolves`/`Refs`).
The only exemption is a change touching solely `docs/`, `.github/`, or a
root-level `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`
or `SECURITY.md` — then write `None` plus the reason for the exemption.

All PR communication is English. Tick a checklist box only when it is true.

## 4. Open it

```bash
gh pr create --base main --title "<title>" --body-file <file>
```

Write the body to a scratch file rather than inlining it, so Markdown survives
shell quoting. Add the attribution footer the harness requires for this
session.

## 5. After opening

```bash
gh pr view --json number,title,labels,url
gh pr checks
```

- `pr-labels.yml` applies area labels (`core`, `spring`, `deployment`,
  `gradle`, `testing`, `ci`, `documentation`) from changed paths. Do not add
  them by hand; report a misclassification instead of forcing a label.
- Request review from the maintainers in `.github/CODEOWNERS`
  (`@inryeok-office/maintainers`).
- `Check` and `Validate PR policy` must pass. If the policy check fails, fix
  the title or body and push — never work around it.

## 6. Never

Never merge your own PR, never use `gh pr merge --admin` or `--auto`, and never
merge around a failed required check. `main` accepts squash merges only, after
one human approval. These are blocked by the `PreToolUse` hook and by the
`Protect main` ruleset.
