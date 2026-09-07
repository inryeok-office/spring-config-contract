#!/bin/bash
# Filesystem and remote-code guards. $CMD is exported by the dispatcher.
deny() { echo "Blocked (filesystem): $1" >&2; exit 2; }
has() { grep -Eq -- "$1" <<<"$CMD"; }

has 'rm +(-[a-zA-Z]+ +)*-[a-zA-Z]*r[a-zA-Z]* *(-[a-zA-Z]+ +)*(/|~|\*|\$HOME|/etc|/usr|/var|/home)( |/?\*?$)' \
    && deny 'recursive rm targeting a root, home or wildcard path.'

has 'Remove-Item.*-Recurse.*-Force.*([A-Za-z]:\\?|~)( |"|$)|rd +/s +/q +[A-Za-z]:' \
    && deny 'recursive force delete of a drive or filesystem root.'

has '(curl|wget|iwr)[^|]*\| *(sudo +)?(bash|sh|zsh|iex)( |$)' \
    && deny 'pipes a downloaded script straight into a shell.'

has 'mkfs|Format-Volume|Clear-Disk|dd +if=.*of=/dev/' \
    && deny 'formats or overwrites a block device.'

has 'chmod +(-R +)?777' \
    && deny 'grants world-writable permissions.'

exit 0
