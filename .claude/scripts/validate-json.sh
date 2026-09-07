#!/bin/bash
# Checks that each argument parses as JSON. Used after editing .claude
# configuration, since a malformed settings file silently disables every
# setting in it.
#
#   bash .claude/scripts/validate-json.sh .claude/settings.json
#
# Exists so the permission allow list can name this exact script instead of a
# general-purpose interpreter: `node -e` and `python -c` would allow arbitrary
# code, which also bypasses the command guards in .claude/hooks.
set -u

(($#)) || { echo "usage: validate-json.sh <file>..." >&2; exit 64; }

if command -v node >/dev/null 2>&1; then
    parse() { node -e 'JSON.parse(require("fs").readFileSync(process.argv[1],"utf8"))' "$1"; }
elif command -v python3 >/dev/null 2>&1; then
    parse() { python3 -m json.tool "$1" >/dev/null; }
elif command -v python >/dev/null 2>&1; then
    parse() { python -m json.tool "$1" >/dev/null; }
else
    echo "no JSON parser available (node or python required)" >&2
    exit 69
fi

status=0
for file in "$@"; do
    if [[ ! -f "$file" ]]; then
        echo "missing: $file" >&2
        status=66
    elif parse "$file" 2>/dev/null; then
        echo "ok: $file"
    else
        echo "invalid JSON: $file" >&2
        parse "$file" 2>&1 >/dev/null | head -3 >&2
        status=65
    fi
done

exit $status
