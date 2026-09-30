"""Stdlib regression tests; run with the private Python using -I -B."""
import hashlib
import json
import runpy
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parent


class RuntimeScriptTests(unittest.TestCase):
    def test_seal_covers_stdlib_and_packages_but_not_generated_caches(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            paths = ['runtime/python/python.exe', 'runtime/python/Lib/os.pyc',
                     'runtime/python/Lib/site-packages/marker/module.py',
                     'runtime/python/Lib/site-packages/torch/lib/torch_cpu.dll',
                     'runtime/python/Lib/site-packages/marker/__pycache__/module.cpython-312.pyc',
                     'runtime/llamacpp/llama-server.exe',
                     'downloads/requirements.lock', 'downloads/wheels.json']
            for name in paths:
                file = root / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_bytes(b'fixture')
            args = ['seal-marker-runtime.py', 'manifest', str(root), str(SCRIPTS / 'runtime-artifacts.json')]
            with patch.object(sys, 'argv', args), patch('importlib.metadata.version', return_value='fixture'):
                runpy.run_path(str(SCRIPTS / 'seal-marker-runtime.py'), run_name='__main__')
            sealed = {entry['path'] for entry in json.loads((root / 'engine.json').read_text())['criticalFiles']}
            self.assertIn('runtime/python/Lib/os.pyc', sealed)
            self.assertIn('runtime/python/Lib/site-packages/marker/module.py', sealed)
            self.assertIn('runtime/python/Lib/site-packages/torch/lib/torch_cpu.dll', sealed)
            self.assertFalse(any('__pycache__' in name for name in sealed))

    def test_missing_critical_file_still_writes_report_and_checks_remaining_hashes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            old, baseline = root / 'old', root / 'baseline'
            old.mkdir()
            baseline.mkdir()
            (old / 'present.exe').write_bytes(b'tampered')
            (baseline / 'old-files-before.json').write_text('[]')
            hashes = {'missing.exe': hashlib.sha256(b'fixture').hexdigest(),
                      'present.exe': hashlib.sha256(b'fixture').hexdigest()}
            (baseline / 'old-hashes-before.json').write_text(json.dumps(hashes))
            destination = root / 'report.json'
            args = ['verify-stage-3a-integrity.py', str(old), str(baseline), str(destination)]
            with patch.object(sys, 'argv', args), self.assertRaises(SystemExit) as exit_status:
                runpy.run_path(str(SCRIPTS / 'verify-stage-3a-integrity.py'), run_name='__main__')
            self.assertNotEqual(exit_status.exception.code, 0)
            diffs = json.loads(destination.read_text())['differences']
            self.assertIn({'path': 'missing.exe', 'reason': 'missing critical file'}, diffs)
            self.assertIn({'path': 'present.exe', 'reason': 'sha256'}, diffs)


if __name__ == '__main__':
    unittest.main()
