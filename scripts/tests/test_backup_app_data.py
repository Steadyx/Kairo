"""Exercise safe backups without modifying a real device."""
import io
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
FAKE_ADB = r'''#!/usr/bin/env python3
import os, shlex, sys
from pathlib import Path
args = sys.argv[1:]
if args == ['devices']:
    print('List of devices attached\nfake\tdevice')
elif args[:2] == ['shell', '-T']:
    command = shlex.split(args[2])
    assert command[0] == 'run-as'
    assert 'tar -cf' in command[-1]
    sys.stdout.buffer.write(Path(os.environ['FAKE_ARCHIVE']).read_bytes())
    sys.exit(int(os.environ.get('FAKE_REMOTE_STATUS', '0')))
elif args[:3] == ['shell', 'am', 'force-stop']:
    sys.exit(int(os.environ.get('FAKE_STOP_STATUS', '0')))
elif args[0] == 'uninstall':
    Path(os.environ['FAKE_UNINSTALL']).write_text(args[1])
elif args[0] in ('exec-in', 'exec-out'):
    # exec transports return success even if the remote command fails.
    sys.exit(0)
'''


class BackupTest(unittest.TestCase):
    def run_backup(self, entry, scenario):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            binary = root / 'bin'
            binary.mkdir()
            (binary / 'adb').write_text(FAKE_ADB)
            (binary / 'adb').chmod(0o755)
            archive_file = root / 'source.tar'
            with tarfile.open(archive_file, 'w') as archive:
                info = tarfile.TarInfo('files')
                info.type = tarfile.DIRTYPE
                info.mode = 0o700
                archive.addfile(info)
                payload = b'backup\x00\xff\r\n' * 200
                info = tarfile.TarInfo('files/book')
                info.size = len(payload)
                archive.addfile(info, io.BytesIO(payload))
            if scenario == 'partial':
                archive_file.write_bytes(b'partial archive')
            elif scenario == 'truncated':
                archive_file.write_bytes(archive_file.read_bytes()[:1200])
            elif scenario == 'empty':
                archive_file.write_bytes(b'')
            backup = root / 'backup.tar'
            old = b'previous backup must survive'
            backup.write_bytes(old)
            marker = root / 'latest-com.kairo.reader.debug.txt'
            marker.write_text('previous marker')
            uninstall = root / 'uninstall'
            environment = dict(
                os.environ,
                PATH=str(binary) + os.pathsep + os.environ['PATH'],
                FAKE_ARCHIVE=str(archive_file),
                FAKE_UNINSTALL=str(uninstall),
                BACKUP_FILE=str(backup),
                BACKUP_DIR=str(root),
                KAIRO_BACKUP_ROOT=str(root),
                FAKE_REMOTE_STATUS='7' if scenario == 'remote_failure' else '0',
                FAKE_STOP_STATUS='1' if scenario == 'stop_failure' else '0',
            )
            args = ['--yes'] if entry == 'clean-demo-install.sh' else ['export']
            result = subprocess.run(['sh', str(SCRIPTS / entry), *args], env=environment,
                                    capture_output=True, text=True)
            if scenario != 'success':
                self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
                self.assertEqual(old, backup.read_bytes())
                self.assertEqual('previous marker', marker.read_text())
                self.assertFalse(uninstall.exists())
            else:
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                with tarfile.open(backup) as restored:
                    self.assertEqual(payload, restored.extractfile('files/book').read())
                if entry == 'clean-demo-install.sh':
                    self.assertEqual('com.kairo.reader.debug', uninstall.read_text())
                    self.assertEqual(str(backup) + '\n', marker.read_text())
                else:
                    self.assertFalse(uninstall.exists())
            self.assertEqual([], list(root.glob('.kairo-backup-*')))

    def test_backup_failures_preserve_existing_archive_and_do_not_uninstall(self):
        for entry in ('clean-demo-install.sh', 'migrate-app-id-data.sh'):
            for scenario in ('remote_failure', 'partial', 'truncated', 'empty', 'stop_failure', 'success'):
                with self.subTest(entry=entry, scenario=scenario):
                    self.run_backup(entry, scenario)


if __name__ == '__main__':
    unittest.main()
