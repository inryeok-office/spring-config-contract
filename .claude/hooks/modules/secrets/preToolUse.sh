#!/bin/bash
# Secret hygiene guards. $CMD is exported by the dispatcher.
# AGENTS.md: never commit secrets, credentials, tokens, private keys or
# secret-bearing environment files.
deny() { echo "Blocked (secrets): $1" >&2; exit 2; }
has() { grep -Eq -- "$1" <<<"$CMD"; }

SECRETS='(\.env([.-][a-zA-Z0-9]+)?|\.(pem|key|p12|pfx|jks|keystore)|id_(rsa|ed25519)|[^ ]*credentials[^ ]*\.json)'

has "git +add +.*$SECRETS( |$)" \
    && deny 'stages a secret-bearing file.'

has "git +commit +.*$SECRETS( |$)" \
    && deny 'commits a secret-bearing file directly.'

exit 0
