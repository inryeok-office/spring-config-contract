#!/bin/bash
# Secret hygiene guards. $CMD is exported by the dispatcher.
# AGENTS.md: never commit secrets, credentials, tokens, private keys or
# secret-bearing environment files.
deny() { echo "Blocked (secrets): $1" >&2; exit 2; }
has() { grep -Eq -- "$1" <<<"$CMD"; }

# Secret shapes as written in a command argument.
SECRETS='(\.env([.-][a-zA-Z0-9]+)?|\.(pem|key|p12|pfx|jks|keystore)|id_(rsa|ed25519)|[^ ]*credentials[^ ]*\.json)'
# The same shapes anchored to a whole repository path, for matching a path list.
SECRET_PATH='(^|/)([^/]*\.env([.-][a-zA-Z0-9]+)?|[^/]*\.(pem|key|p12|pfx|jks|keystore)|id_(rsa|ed25519)(\.pub)?|[^/]*credentials[^/]*\.json)$'

has "git +add +.*$SECRETS( |$)" \
    && deny 'stages a secret-bearing file.'

has "git +commit +.*$SECRETS( |$)" \
    && deny 'commits a secret-bearing file directly.'

# A broad selector never names the file it stages, so the command text alone
# cannot show what it picks up. Inspect the working tree instead.
# `git add .`, `-A`, `--all` and a glob also pick up untracked files;
# `git add -u` and `git commit -a` are limited to tracked changes.
untracked_too=0
tracked_only=0
has 'git +add +([^|;&]*[ ])?(\.|\*|-A|--all)( |$)' && untracked_too=1
has 'git +add +([^|;&]*[ ])?(-u|--update)( |$)' && tracked_only=1
has 'git +commit +([^|;&]*[ ])?(-[a-zA-Z]*a[a-zA-Z]*|--all)( |$)' && tracked_only=1

if (( untracked_too || tracked_only )); then
    # Ignored files are absent from `git status --porcelain`, so a gitignored
    # secret never trips this guard.
    status=$(git -C "${CLAUDE_PROJECT_DIR:-.}" status --porcelain 2>/dev/null)
    (( untracked_too )) || status=$(grep -v '^??' <<<"$status")
    hit=$(sed -e 's/^...//' -e 's/^.* -> //' -e 's/^"//' -e 's/"$//' <<<"$status" \
        | grep -E -m1 -- "$SECRET_PATH")
    [[ -n "$hit" ]] \
        && deny "stages without naming a path while a secret-bearing path is present: $hit"
fi

exit 0
