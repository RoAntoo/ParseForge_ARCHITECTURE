"""Optional read-only comparison against Stage 3A's existing OLD baseline."""
import hashlib
import json
import sys
from pathlib import Path

old = Path(sys.argv[1]).resolve()
baseline = Path(sys.argv[2]).resolve()
destination = Path(sys.argv[3])
files = json.loads((baseline / 'old-files-before.json').read_text(encoding='utf-8-sig'))
hashes = json.loads((baseline / 'old-hashes-before.json').read_text(encoding='utf-8-sig'))
diffs = []
expected_paths = {record['path'].replace('\\', '/') for record in files}
observed_paths = {file.relative_to(old).as_posix() for file in old.rglob('*') if file.is_file()}
for name in sorted(expected_paths ^ observed_paths):
    diffs.append({'path': name, 'reason': 'added or missing'})
for record in files:
    file = old / record['path']
    if not file.is_file():
        continue
    stat = file.stat()
    if stat.st_size != record['size'] or stat.st_mtime_ns != record['mtime_ns']:
        diffs.append({'path': record['path'], 'reason': 'size or modification time'})
for name, expected in hashes.items():
    file = old / name
    if not file.is_file():
        diffs.append({'path': name, 'reason': 'missing critical file'})
        continue
    with file.open('rb') as stream:
        actual = hashlib.file_digest(stream, 'sha256').hexdigest()
    if expected != actual:
        diffs.append({'path': name, 'reason': 'sha256'})
report = {'old': str(old), 'baselineFiles': len(files), 'criticalHashes': len(hashes), 'differences': diffs}
destination.write_text(json.dumps(report, indent=2), encoding='utf-8')
print(json.dumps(report, indent=2))
if diffs:
    raise SystemExit('OLD differs from Stage 3A baseline')
