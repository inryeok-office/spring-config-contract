#!/bin/bash
# Reports a write to a secret-bearing path, so the file is not left in the tree
# or staged by a later `git add .`.
[[ -n "$FILE" ]] || exit 0

if grep -Eq -- '(\.env([.-][a-zA-Z0-9]+)?|\.(pem|key|p12|pfx|jks|keystore)|id_(rsa|ed25519)|credentials[^/]*\.json)$' <<<"$FILE"; then
    echo "Wrote a secret-bearing path: $FILE. Keep it out of the commit and make sure it is gitignored." >&2
    exit 2
fi

exit 0
