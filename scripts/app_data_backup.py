"""Shared validation for app-data archives before backup promotion or restore."""
import tarfile

ROOTS = {"databases", "files", "shared_prefs"}


def validate_backup(source):
    try:
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
    except tarfile.TarError as error:
        raise ValueError(f"Invalid backup archive: {error}") from error
