# AI-assisted development

Codex, Claude Code, and other coding agents may be used, as may ordinary human-led development. Contributors remain responsible for every submitted line. AI-generated code receives exactly the same review, testing, security, and architecture requirements as human-written code. Contributors do not need to disclose AI use.

## Agent instruction ownership

AGENTS.md and the canonical documents linked from it are the shared policy source for Codex, Claude Code, Gemini CLI, GitHub Copilot, and other coding agents. Vendor-specific files are thin adapters only:

- CLAUDE.md imports AGENTS.md for Claude Code.
- GEMINI.md and .gemini/settings.json expose the AGENTS.md hierarchy to Gemini CLI.
- .github/copilot-instructions.md imports AGENTS.md for GitHub Copilot.

These adapters must not become separate sources of truth or duplicate repository policy.

Agents and contributors must read repository instructions, inspect existing code before recreating functionality, keep changes within the Issue scope, preserve module boundaries, and verify claims with commands and tests. AI output is untrusted until reviewed and executed where practical.

Agents must not weaken or bypass tests and CI, expose secrets, invent implementation claims, silently expand scope, or silently change architecture. They must report incomplete verification and uncertainty. Large generated rewrites require strong justification and focused review. Architecture decisions must remain understandable and reviewable by humans.
