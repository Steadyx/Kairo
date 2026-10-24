#!/usr/bin/env python3
"""Validate and stage a backup before replacing app data; roll back failed moves."""
import re
import shlex
import subprocess
import sys
import tarfile

ROOTS = {"databases", "files", "shared_prefs"}
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
        with tarfile.open(fileobj=source, mode="r:") as archive:
            found = False
            for member in archive:
                parts = member.name.rstrip("/").split("/")
                if parts[0] not in ROOTS or any(part in ("", ".", "..") for part in parts):
                    raise ValueError(f"Unexpected backup path: {member.name}")
                if not (member.isdir() or member.isfile()) or (len(parts) == 1 and not member.isdir()):
                    raise ValueError(f"Unsupported backup entry: {member.name}")
                if member.isdir() and member.mode & 0o700 != 0o700:
                    raise ValueError(f"Backup directory must be owner-readable and writable: {member.name}")
                if member.isfile():
                    with archive.extractfile(member) as content:
                        while content.read(1024 * 1024):
                            pass
                found = True
            if not found:
                raise ValueError("Backup has no app data")
        source.seek(0)
        command = shlex.join(["run-as", package, "sh", "-c", RESTORE])
        subprocess.run(["adb", "exec-in", command], stdin=source, check=True)


if __name__ == "__main__":
    try:
        restore(*sys.argv[1:])
    except (OSError, ValueError, tarfile.TarError, subprocess.CalledProcessError) as error:
        sys.exit(f"Restore failed: {error}")
