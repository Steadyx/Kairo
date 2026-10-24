#!/bin/sh
set -eu

PACKAGE="${PACKAGE:-com.kairo.reader.debug}"
BACKUP_ROOT="${KAIRO_BACKUP_ROOT:-$HOME/Library/Application Support/Kairo/android-device-backups}"
BACKUP_FILE="${BACKUP_FILE:-}"
CONFIRMED=0

usage() {
    cat <<USAGE
Usage: $0 --yes [--backup-file PATH]
       $0 --list

Restores a saved off-repo backup into PACKAGE private data. By default it uses
the latest backup created by scripts/clean-demo-install.sh.

Options:
  --yes               Required for restore. Existing PACKAGE data is replaced.
  --backup-file PATH  Restore a specific backup tar.
  --list              Show available backups and exit.

Environment overrides:
  PACKAGE=$PACKAGE
  KAIRO_BACKUP_ROOT=$BACKUP_ROOT
  BACKUP_FILE=$BACKUP_FILE
USAGE
}

die() {
    printf '%s\n' "$*" >&2
    exit 1
}

list_backups() {
    if [ ! -d "$BACKUP_ROOT" ]; then
        printf 'No backup directory found: %s\n' "$BACKUP_ROOT"
        return
    fi

    find "$BACKUP_ROOT" -maxdepth 1 -type f -name "$PACKAGE-*.tar" -print | sort
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --yes)
            CONFIRMED=1
            ;;
        --backup-file)
            [ "$#" -gt 1 ] || die "--backup-file requires a path."
            shift
            BACKUP_FILE="$1"
            ;;
        --list)
            list_backups
            exit 0
            ;;
        -h|--help|help)
            usage
            exit 0
            ;;
        *)
            usage
            die "Unknown option: $1"
            ;;
    esac
    shift
done

[ "$CONFIRMED" -eq 1 ] || {
    usage
    die "Refusing to replace app data without --yes."
}

if [ -z "$BACKUP_FILE" ]; then
    LATEST_FILE="$BACKUP_ROOT/latest-$PACKAGE.txt"
    [ -s "$LATEST_FILE" ] || die "No latest backup marker found: $LATEST_FILE"
    BACKUP_FILE="$(sed -n '1p' "$LATEST_FILE")"
fi

[ -s "$BACKUP_FILE" ] || die "Backup file not found or empty: $BACKUP_FILE"

command -v adb >/dev/null 2>&1 || die "adb not found on PATH."
devices="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
[ "$devices" -eq 1 ] || die "Expected exactly one connected adb device, found $devices."

adb shell pm path "$PACKAGE" >/dev/null 2>&1 || die "$PACKAGE is not installed. Install the app first."
adb shell run-as "$PACKAGE" sh -c "pwd >/dev/null" >/dev/null 2>&1 ||
    die "run-as failed for $PACKAGE. Install a debuggable build first."

printf 'Stopping %s before restore...\n' "$PACKAGE"
adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || die "Could not stop $PACKAGE before restore."

printf 'Restoring %s into %s private data...\n' "$BACKUP_FILE" "$PACKAGE"
python3 "$(dirname "$0")/restore-app-data.py" "$BACKUP_FILE" "$PACKAGE"

printf 'Restore complete. Launch %s from Android Studio or the device launcher.\n' "$PACKAGE"
