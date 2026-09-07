#!/bin/bash
# PreToolUse dispatcher: runs every modules/*/preToolUse.sh guard.
# A module that exits non-zero blocks the tool call; its stderr is the reason.
INPUT=$(cat)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULES_DIR="$SCRIPT_DIR/modules"

[[ -d "$MODULES_DIR" ]] || exit 0

# The command under inspection, pulled out of the hook payload.
CMD=$(printf '%s' "$INPUT" | sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"//p' | sed 's/","[a-z_]*":.*$//; s/"}.*$//')
export CMD

[[ -n "$CMD" ]] || exit 0

for hook in "$MODULES_DIR"/*/preToolUse.sh; do
    [[ -f "$hook" ]] || continue
    printf '%s' "$INPUT" | bash "$hook" || exit 2
done

exit 0
