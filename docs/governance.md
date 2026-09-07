# Repository governance

This document is the canonical policy for Issue and Pull Request metadata. It complements, but does not replace, maintainer judgment about architecture, ownership, security, or review quality.

## Metadata taxonomy

Use the smallest applicable set of existing labels:

- Type: `bug`, `enhancement`, `documentation`, `question`, `dependencies`
- Area: `core`, `spring`, `deployment`, `gradle`, `testing`, `ci`
- Impact: `breaking-change`
- Community: `good first issue`, `help wanted`
- Automation: `automated`

The legacy `github_actions` label is retained for compatibility with existing repository metadata; `ci` is the area label for CI and workflow changes. Do not create duplicate `type:*` labels or invent labels without a clear workflow purpose.

## Issue metadata

Normal Issues need a clear problem or goal. Implementation Issues should state acceptance criteria, have at least one type label, and have an area label when technically applicable. Issue templates provide the initial type label.

Assign an actively maintained Issue to the person responsible for the work. Leave backlog, community, and unclaimed Issues unassigned. Do not use a placeholder or random assignee.

## Pull Request metadata

Human-created PRs should have a meaningful Conventional Commit-style title, use the PR template, state what was tested, declare compatibility and breaking-change impact, and reference an Issue for non-trivial product changes. Tiny typo/documentation fixes may state `None` for the Issue. Product and architecture changes require maintainer review and successful CI.

The PR policy workflow checks title and body structure and requests an Issue reference when the change is not an obvious small documentation or repository-maintenance change. It does not block contributors on labels or assignees they cannot change. Path-based automation applies area labels; maintainers correct ambiguous classifications.

Issue ownership belongs on the Issue. Reviewer responsibility belongs in requested reviewers or CODEOWNERS. A PR assignee is not a substitute for a reviewer.

## Dependabot and external contributors

Dependabot PRs do not need a manually created Issue, human assignee, or human-written body. They receive `dependencies` and `automated`, plus applicable area labels such as `ci`, `gradle`, or `testing`. Compatibility review remains human-controlled.

External contributors may fork, create a branch, and open a PR without repository permissions. They are not required to assign themselves labels, assignees, or reviewers. Automation validates metadata they control, while maintainers apply repository-controlled metadata and request review.

## Workflow

Small contribution: branch → focused change → relevant validation → PR.

Normal feature or bug: Issue → owner assignment when work begins → correctly named branch → implementation and tests → PR → maintainer review → CI → squash merge.

Architecture or public API change: Issue/design discussion → ADR when durable → implementation → compatibility and enhanced review → CI.

Agents must inspect the Issue, labels, assignment, branch, PR metadata, requested reviewers, and CI. They must preserve Issue scope and report any metadata update they cannot perform. No one may push directly to `main` or merge around failed checks.

## Enforcement model

### ENFORCED / AUTOMATED

- Gradle build, tests, formatting, and repository rules through `./gradlew check` and CI.
- PR title/body structure and applicable Issue-reference checks through `pr-policy.yml`.
- Area and automation labels through `pr-labels.yml`.
- Maintainer review requests for repository paths through CODEOWNERS where GitHub branch settings honor it.

### DOCUMENTED / MANUAL

- Label accuracy for ambiguous changes, active ownership, compatibility judgment, and whether a change is truly small remain maintainer decisions.
- Architectural judgment, public API approval, reviewer quality, Issue ownership, ADR necessity, and security decisions remain human responsibilities.

## Protected main workflow

The `main` branch is protected by the active `Protect main` repository ruleset.

Normal merges require:

- a Pull Request;
- one approval from an authorized human reviewer;
- successful `Check` and `Validate PR policy` checks;
- resolved review conversations;
- squash merge only.

Approvals are dismissed when new commits are pushed. Force pushes and branch deletion are blocked. The `Apply repository labels` workflow remains informational and is not a merge blocker.

For exceptional emergencies, `exijn` has a narrowly scoped bypass that can be used only through a Pull Request. This does not permit direct pushes to `main`; emergency use must remain auditable in the PR history.

Branch protection and ruleset administration remain subject to GitHub permissions and organization-level policy. The repository-specific ruleset is the active source for the requirements above.
