#!/usr/bin/env python3
"""Validate and stage a backup before replacing app data; roll back failed moves."""
import re
import shlex
import subprocess
import sys

from app_data_backup import validate_backup

RESTORE = r'''
set -eu
stage=$(mktemp -d .kairo-restore.XXXXXX)
moved=""
installed=""
rollback() {
    status=$?
    trap - EXIT HUP INT TERM
    if [ "$status" -ne 0 ]; then
        for path in $installed; do rm -rf "$path"; done
        for path in $moved; do
            if [ -e "$stage/previous/$path" ] || [ -L "$stage/previous/$path" ]; then
                mv "$stage/previous/$path" "$path" || {
                    echo "Rollback incomplete; original data retained in $stage/previous" >&2
                    exit 1
                }
            fi
        done
    fi
    rm -rf "$stage"
    exit "$status"
}
trap rollback EXIT
trap 'exit 1' HUP INT TERM
mkdir "$stage/incoming" "$stage/previous"
tar -xf - -C "$stage/incoming"
for path in databases files shared_prefs; do
    if [ -e "$path" ] || [ -L "$path" ]; then
        moved="$path $moved"
        mv "$path" "$stage/previous/$path"
    fi
    if [ -e "$stage/incoming/$path" ]; then
        installed="$path $installed"
        mv "$stage/incoming/$path" "$path"
    fi
done
'''


def restore(backup, package):
    if not re.fullmatch(r"[a-zA-Z0-9_.]+", package):
        raise ValueError("Invalid package name")
    # Keep the same open file for validation and transfer.
    with open(backup, "rb", buffering=0) as source:
        validate_backup(source)
        source.seek(0)
        command = shlex.join(["run-as", package, "sh", "-c", RESTORE])
        subprocess.run(["adb", "shell", "-T", command], stdin=source, check=True)


if __name__ == "__main__":
    try:
        restore(*sys.argv[1:])
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        sys.exit(f"Restore failed: {error}")
