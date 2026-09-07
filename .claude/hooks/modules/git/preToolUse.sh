#!/bin/bash
# Git and GitHub guards. $CMD is exported by the dispatcher.
deny() { echo "Blocked (git): $1" >&2; exit 2; }
has() { grep -Eq -- "$1" <<<"$CMD"; }

has 'git +push .*(--force|-f)( |$)' && ! has '--force-with-lease' \
    && deny 'force push. Use --force-with-lease, and never on a shared branch.'

has 'git +push .*(main|master)( |$)' \
    && deny 'push to a protected branch. AGENTS.md requires an Issue-backed branch and PR.'

has 'git +(commit|push|merge) .*(--no-verify|--no-gpg-sign)' \
    && deny 'skips hooks or commit signing. Fix the failing check instead.'

has 'git +(filter-branch|filter-repo)|git +reflog +delete|git +update-ref +-d' \
    && deny 'rewrites or destroys history and removes the recovery path.'

has 'git +reset +--hard' \
    && deny 'git reset --hard discards uncommitted work. Commit or stash first.'

has 'git +clean +-[a-zA-Z]*(fd|fx|df|xf)' \
    && deny 'git clean removes untracked files permanently.'

has 'gh +pr +merge .*(--admin|--auto)' \
    && deny 'merges around required checks. AGENTS.md forbids merging past a failed check.'

has 'gh +(repo|release) +delete|gh +api .*-X *DELETE' \
    && deny 'deletes a remote repository, release or resource.'

exit 0
