"""Exercise both restore entry points against a temporary fake adb installation."""
import io
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
FAKE_ADB = r'''#!/usr/bin/env python3
import os, shlex, subprocess, sys
args = sys.argv[1:]
if args == ['devices']:
    print('List of devices attached\nfake\tdevice')
elif args[0] == 'exec-in':
    command = args[1:] if len(args) > 2 else shlex.split(args[1])
    assert command[:2] == ['run-as', 'com.kairo.reader.debug']
    command[-1] = command[-1].replace('cd "$HOME"', 'cd "$FAKE_APP"')
    sys.exit(subprocess.call(command[2:], cwd=os.environ['FAKE_APP']))
'''


class RestoreTest(unittest.TestCase):
    def run_restore(self, entry, failure=None, invalid=None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            app = root / 'app'
            app.mkdir()
            for name in ('databases', 'files', 'shared_prefs'):
                (app / name).mkdir()
                (app / name / 'original').write_text(name)
            binary = root / 'bin'
            binary.mkdir()
            (binary / 'adb').write_text(FAKE_ADB)
            (binary / 'adb').chmod(0o755)
            backup = root / 'backup.tar'
            with tarfile.open(backup, 'w') as archive:
                data = b'restored content' * 1000
                for name in ('databases', 'files', 'shared_prefs'):
                    directory_entry = tarfile.TarInfo(name)
                    directory_entry.type = tarfile.DIRTYPE
                    directory_entry.mode = 0o700
                    archive.addfile(directory_entry)
                    info = tarfile.TarInfo(name + '/restored')
                    info.size = len(data)
                    archive.addfile(info, io.BytesIO(data))
            if invalid == 'invalid':
                backup.write_bytes(b'not a tar archive')
            elif invalid == 'truncated':
                backup.write_bytes(backup.read_bytes()[:2000])
            elif invalid in ('traversal', 'symlink', 'permissions'):
                with tarfile.open(backup, 'a') as archive:
                    info = tarfile.TarInfo('../outside' if invalid == 'traversal' else 'files/invalid')
                    if invalid == 'symlink':
                        info.type = tarfile.SYMTYPE
                        info.linkname = '../../outside'
                    elif invalid == 'permissions':
                        info.type = tarfile.DIRTYPE
                        info.mode = 0o000
                    archive.addfile(info, io.BytesIO())
            if failure:
                tool = 'tar' if failure == 'extract' else 'mv'
                real_tool = '/usr/bin/' + tool if tool == 'tar' else '/bin/mv'
                if failure == 'extract':
                    condition = 'true'
                elif failure == 'move_original':
                    condition = '[ "$1" = shared_prefs ]'
                else:
                    condition = 'case "$1" in */incoming/shared_prefs) true;; *) false;; esac'
                wrapper = '#!/bin/sh\nif ' + condition + '; then exit 1; fi\nexec ' + real_tool + ' "$@"\n'
                (binary / tool).write_text(wrapper)
                (binary / tool).chmod(0o755)
            environment = dict(os.environ, PATH=str(binary) + os.pathsep + os.environ['PATH'],
                               FAKE_APP=str(app), BACKUP_FILE=str(backup))
            args = ['--yes', '--backup-file', str(backup)] if entry == 'reimport-demo-data.sh' else ['restore']
            result = subprocess.run(['sh', str(SCRIPTS / entry), *args], env=environment,
                                    capture_output=True, text=True)
            if failure or invalid:
                self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
                for name in ('databases', 'files', 'shared_prefs'):
                    self.assertEqual(name, (app / name / 'original').read_text())
            else:
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                self.assertEqual(data, (app / 'files/restored').read_bytes())
                self.assertEqual(data, (app / 'databases/restored').read_bytes())
                self.assertEqual(data, (app / 'shared_prefs/restored').read_bytes())
                self.assertFalse(list(app.glob('*/original')))
            self.assertEqual([], list(app.glob('.kairo-restore.*')))

    def test_both_restore_paths(self):
        for entry in ('reimport-demo-data.sh', 'migrate-app-id-data.sh'):
            for invalid in ('invalid', 'truncated', 'traversal', 'symlink', 'permissions'):
                with self.subTest(entry=entry, invalid=invalid):
                    self.run_restore(entry, invalid=invalid)
            for failure in ('extract', 'replace', 'move_original', None):
                with self.subTest(entry=entry, failure=failure):
                    self.run_restore(entry, failure=failure)


if __name__ == '__main__':
    unittest.main()
