#!/bin/bash
# PostToolUse dispatcher: runs every modules/*/postToolUse.sh check after a tool
# call succeeds. A module that exits non-zero reports its stderr back to Claude.
INPUT=$(cat)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULES_DIR="$SCRIPT_DIR/modules"

[[ -d "$MODULES_DIR" ]] || exit 0

CMD=$(printf '%s' "$INPUT" | sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"//p' | sed 's/","[a-z_]*":.*$//; s/"}.*$//')
FILE=$(printf '%s' "$INPUT" | sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"//p' | sed 's/","[a-z_]*":.*$//; s/".*$//')
export CMD FILE

for hook in "$MODULES_DIR"/*/postToolUse.sh; do
    [[ -f "$hook" ]] || continue
    printf '%s' "$INPUT" | bash "$hook" || exit 2
done

exit 0
