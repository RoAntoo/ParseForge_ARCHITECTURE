"""Focused regressions for Stage 9 capture and report generation (stdlib only)."""
import contextlib
import io
import json
import os
from pathlib import Path
import runpy
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

SCRIPTS = Path(__file__).resolve().parent
PROGRESS = runpy.run_path(str(SCRIPTS / 'stage9-progress.py'))


class Stage9ScriptTests(unittest.TestCase):
    def test_split_paths_utf8_crlf_and_unterminated_tail_are_sanitized(self):
        private_path = 'C:\\Users\\private\\carpeta ñ'
        lines = [f'error {private_path}\\a.pdf\r\n', f'phase {private_path}\r', f'tail {private_path}']
        data = ''.join(lines).encode('utf-8')
        events = []
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory)
            # Every-byte reads split every path and the multi-byte ñ, plus CRLF.
            with patch('os.read', side_effect=[bytes([b]) for b in data] + [b'']):
                PROGRESS['read_stream'](Mock(), destination, 'stderr',
                                        lambda text: text.replace(private_path, '<USER>'),
                                        lambda stream, text: events.append((stream, text)))
            self.assertEqual((destination / 'stderr.raw').read_bytes(), data)
            expected = [line.replace(private_path, '<USER>') for line in lines]
            self.assertEqual((destination / 'stderr.log').read_bytes().decode('utf-8'), ''.join(expected))
            self.assertEqual(events, [('stderr', line) for line in expected])

    def test_empty_stream_and_final_cr_preserve_exact_diagnostics(self):
        for data, expected in [(b'', []), (b'diagnostic\r', ['diagnostic\r'])]:
            with self.subTest(data=data), tempfile.TemporaryDirectory() as directory:
                events = []
                reads = [data, b''] if data else [b'']
                with patch('os.read', side_effect=reads):
                    PROGRESS['read_stream'](Mock(), Path(directory), 'stdout',
                                            lambda text: text,
                                            lambda stream, text: events.append(text))
                self.assertEqual(events, expected)
                self.assertEqual((Path(directory) / 'stdout.log').read_bytes(), data)

    def test_reports_use_queried_versions_and_hash_only_current_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            local = repo / 'local'
            engine_root = local / 'ParseForge/engines'
            installed_sources = {
                'marker': ['marker/scripts/convert_single.py', 'surya/common/batch_service/client.py',
                           'surya/common/batch_service/server.py', 'surya/ocr_error/server.py',
                           'surya/ocr_error/__init__.py', 'surya/fast_layout/__init__.py',
                           'surya/layout/__init__.py', 'surya/recognition/__init__.py',
                           'surya/inference/backends/openai_client.py', 'surya/inference/backends/llamacpp.py'],
                'markitdown': ['markitdown/converters/_pdf_converter.py', 'markitdown/__main__.py'],
            }
            for engine, names in installed_sources.items():
                for name in names:
                    path = engine_root / engine / 'runtime/python/Lib/site-packages' / name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text('synthetic source', encoding='utf-8')
            versions = {
                'marker': {'python': '3.13.1 test-marker', 'packages': {'marker-pdf': '9.1', 'surya-ocr': '8.2', 'tqdm': '6.3'}},
                'markitdown': {'python': '3.14.2 test-markitdown', 'packages': {'markitdown': '7.4', 'pdfminer-six': '20300101', 'pdfplumber': '5.6'}},
            }
            base = repo / 'build/matrix'
            base.mkdir(parents=True)
            entries = []
            for index in range(11):
                folder = repo / f'build/logs/case-{index}'
                folder.mkdir(parents=True)
                (folder / 'events.jsonl').write_text('{"kind":"start","data":{}}\n')
                for stream in ['stdout', 'stderr']:
                    (folder / (stream + '.raw')).write_bytes(b'')
                entries.append(dict(engine='markitdown', case=f'case-{index}',
                                    status='CANCELLED' if index == 10 else 'COMPLETED',
                                    logDirectory=folder.relative_to(repo).as_posix(), seconds=1, outputs={}))
            (base / 'summary.json').write_text(json.dumps(entries))
            for name in ['stage9-tests.log', 'stage9-ui.log']:
                (repo / 'build' / name).write_text('synthetic test log: BUILD SUCCESS')
            stale = repo / 'docs/spikes/fixtures/stage9/cancel-many-pages/stale.log'
            stale.parent.mkdir(parents=True)
            stale.write_text('artifact from another matrix')
            results = [subprocess.CompletedProcess([], 0, json.dumps(versions[engine]))
                       for engine in ['marker', 'markitdown']]
            results.append(subprocess.CompletedProcess([], 0, '[]'))
            script = SCRIPTS / 'stage9-report.py'
            # Run unchanged report logic against a temporary repository and mocked
            # version/CIM queries, so no real reports or runtimes are overwritten.
            with patch.object(sys, 'argv', [str(script), str(base)]), patch.dict(os.environ, LOCALAPPDATA=str(local)), patch('subprocess.run', side_effect=results), contextlib.redirect_stdout(io.StringIO()):
                exec(compile(script.read_text(encoding='utf-8'), str(script), 'exec'),
                     {'__file__': str(repo / 'scripts/spikes/stage9-report.py'), '__name__': '__main__'})
            investigation = (repo / 'docs/spikes/STAGE_9_PROGRESS_INVESTIGATION.md').read_text(encoding='utf-8')
            result = (repo / 'docs/release/STAGE_9_RESULT.md').read_text(encoding='utf-8')
            for report in [investigation, result]:
                for text in ['Marker 9.1', 'Surya 8.2', 'MarkItDown 7.4']:
                    self.assertIn(text, report)
                self.assertNotIn('Marker 2.0.0', report)
            for text in ['3.13.1 test-marker', '3.14.2 test-markitdown', 'tqdm 6.3', 'pdfminer.six 20300101', 'pdfplumber 5.6']:
                self.assertIn(text, investigation)
            evidence = json.loads((repo / 'docs/release/STAGE_9_EVIDENCE.json').read_text())
            self.assertEqual(evidence['versions'], versions)
            self.assertNotIn(stale.relative_to(repo).as_posix(), evidence['fixtureHashes'])
            self.assertEqual(len(evidence['fixtureHashes']), 11 * 4 + 2)
            self.assertTrue(stale.exists())


if __name__ == '__main__':
    unittest.main()
