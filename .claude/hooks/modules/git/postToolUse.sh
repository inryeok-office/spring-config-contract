#!/bin/bash
# Reports a commit that landed on a protected branch, so it can be moved onto
# an Issue-backed branch before it is pushed.
grep -Eq -- 'git +commit' <<<"$CMD" || exit 0

branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null)
case "$branch" in
    main|master)
        echo "Committed on '$branch'. AGENTS.md requires an Issue-backed branch and PR - move this commit onto a branch before pushing." >&2
        exit 2
        ;;
esac

exit 0
