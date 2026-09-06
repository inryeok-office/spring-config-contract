# Contribution workflow

## Small contribution

Branch → focused change → relevant validation → PR. A small documentation or typo fix does not require an Issue unless the maintainer asks for one, but it still follows repository language and review rules.

## Normal feature or bug

Issue → branch → implementation and tests → PR → maintainer review → CI → squash merge. Use `feat/<issue>-description`, `fix/<issue>-description`, `docs/<issue>-description`, `refactor/<issue>-description`, or `test/<issue>-description`.

## Architecture or public API change

Issue/design discussion → ADR when the decision is durable → implementation → enhanced review and compatibility assessment → CI. Do not introduce a public type or dependency edge casually.

All commits use Conventional Commits. PR and Issue communication is English. No contributor may push directly to `main` or merge their own PR.

See [the governance policy](governance.md) for metadata requirements, Dependabot and external-contributor exceptions, automatic labels, and the PR policy check.
