#!/usr/bin/env python3
"""Create and validate an app-data backup before replacing the destination."""
import os
import re
import shlex
import subprocess
import sys
import tempfile

from app_data_backup import validate_backup

BACKUP = r'''
cd "$HOME" || exit 1
set --
for path in databases files shared_prefs; do
    [ -e "$path" ] && set -- "$@" "$path"
done
[ "$#" -gt 0 ] || exit 3
tar -cf - "$@"
'''


def backup(destination, package):
    if not re.fullmatch(r"[a-zA-Z0-9_.]+", package):
        raise ValueError("Invalid package name")
    directory = os.path.dirname(os.path.abspath(destination))
    os.makedirs(directory, exist_ok=True)
    temp_path = None
    try:
        with tempfile.NamedTemporaryFile(mode="w+b", prefix=".kairo-backup-", dir=directory, delete=False) as target:
            temp_path = target.name
            command = shlex.join(["run-as", package, "sh", "-c", BACKUP])
            subprocess.run(["adb", "shell", "-T", command], stdout=target, check=True)
            target.flush()
            target.seek(0)
            validate_backup(target)
        os.replace(temp_path, destination)
        temp_path = None
    finally:
        if temp_path is not None:
            os.unlink(temp_path)


if __name__ == "__main__":
    try:
        backup(*sys.argv[1:])
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        sys.exit(f"Backup failed: {error}")
