#!/bin/sh
set -eu

OLD_PACKAGE="${OLD_PACKAGE:-app.kairo.reader}"
NEW_PACKAGE="${NEW_PACKAGE:-com.kairo.reader.debug}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/Library/Application Support/Kairo/android-device-backups}"
BACKUP_FILE="${BACKUP_FILE:-$BACKUP_DIR/${OLD_PACKAGE}-to-${NEW_PACKAGE}-data.tar}"

usage() {
    cat <<USAGE
Usage: $0 export|restore|migrate

Environment overrides:
  OLD_PACKAGE=$OLD_PACKAGE
  NEW_PACKAGE=$NEW_PACKAGE
  BACKUP_DIR=$BACKUP_DIR
  BACKUP_FILE=$BACKUP_FILE

Modes:
  export   Copy private data from OLD_PACKAGE into BACKUP_FILE.
  restore  Restore BACKUP_FILE into NEW_PACKAGE.
  migrate  Run export then restore.

This uses adb run-as and therefore requires debuggable builds installed for
both package ids. It copies Room databases, DataStore preferences, imported
ebook assets, and any other app-owned files under the private files directory.
USAGE
}

die() {
    printf '%s\n' "$*" >&2
    exit 1
}

need_adb() {
    command -v adb >/dev/null 2>&1 || die "adb not found on PATH."
}

ensure_device() {
    devices="$(adb devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
    [ "$devices" -eq 1 ] || die "Expected exactly one connected adb device, found $devices."
}

ensure_installed() {
    package="$1"
    adb shell pm path "$package" >/dev/null 2>&1 || die "$package is not installed on the device."
}

ensure_run_as() {
    package="$1"
    adb shell run-as "$package" sh -c "pwd >/dev/null" >/dev/null 2>&1 ||
        die "run-as failed for $package. Install a debuggable build for that package first."
}

export_data() {
    need_adb
    ensure_device
    ensure_installed "$OLD_PACKAGE"
    ensure_run_as "$OLD_PACKAGE"
    mkdir -p "$BACKUP_DIR"

    printf 'Stopping %s before export...\n' "$OLD_PACKAGE"
    adb shell am force-stop "$OLD_PACKAGE" >/dev/null 2>&1 || die "Could not stop $OLD_PACKAGE before export."

    printf 'Exporting %s private data to %s...\n' "$OLD_PACKAGE" "$BACKUP_FILE"
    python3 "$(dirname "$0")/backup-app-data.py" "$BACKUP_FILE" "$OLD_PACKAGE"
    printf 'Export complete: %s\n' "$BACKUP_FILE"
}

restore_data() {
    need_adb
    ensure_device
    ensure_installed "$NEW_PACKAGE"
    ensure_run_as "$NEW_PACKAGE"
    [ -s "$BACKUP_FILE" ] || die "Backup file not found or empty: $BACKUP_FILE"

    printf 'Stopping %s before restore...\n' "$NEW_PACKAGE"
    adb shell am force-stop "$NEW_PACKAGE" >/dev/null 2>&1 || die "Could not stop $NEW_PACKAGE before restore."

    printf 'Restoring %s into %s private data...\n' "$BACKUP_FILE" "$NEW_PACKAGE"
    python3 "$(dirname "$0")/restore-app-data.py" "$BACKUP_FILE" "$NEW_PACKAGE"

    printf 'Restore complete. Launch %s from Android Studio or the device launcher.\n' "$NEW_PACKAGE"
}

case "${1:-}" in
    export)
        export_data
        ;;
    restore)
        restore_data
        ;;
    migrate)
        export_data
        restore_data
        ;;
    -h|--help|help)
        usage
        ;;
    *)
        usage
        exit 2
        ;;
esac
