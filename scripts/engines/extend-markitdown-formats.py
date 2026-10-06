"""Maintenance only: add offline Office dependencies to the reviewed PDF profile.

Consumes a pip --dry-run --ignore-installed Windows CPython 3.12 report for
markitdown[docx,pptx,xlsx,xls,outlook]==0.1.8. Existing pins are preserved.
Downloads are verified against PyPI hashes before entering the manifest.
Creates an isolated test runtime under build; never modifies installed engines.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'src/main/resources/engines'
BUILD = ROOT / 'build/multiformat'
manifest = json.loads((RES / 'markitdown-windows-x64.json').read_text(encoding='utf-8'))
report = json.loads((ROOT / 'build/multiformat-resolution.json').read_text(encoding='utf-8'))
existing = {w['name'].lower().replace('_', '-') for w in manifest['packages']['wheels']}
runtime = BUILD / 'runtime/python'
if not runtime.exists():
    shutil.copytree(Path(os.environ['LOCALAPPDATA']) / 'ParseForge/engines/markitdown/runtime/python', runtime)
lines = (RES / 'markitdown-requirements.lock').read_text(encoding='utf-8').splitlines()
for item in report['install']:
    name, version = item['metadata']['name'], item['metadata']['version']
    if name.lower().replace('_', '-') in existing:
        continue
    info = item['download_info']
    filename = info['url'].rsplit('/', 1)[-1]
    assert filename.endswith('.whl')
    expected = info['archive_info']['hashes']['sha256']
    target = BUILD / 'wheels' / filename
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists():
        urllib.request.urlretrieve(info['url'], target)
    data = target.read_bytes()
    assert hashlib.sha256(data).hexdigest() == expected
    manifest['packages']['wheels'].append(dict(name=name, version=version, file=filename,
        url=info['url'], sha256=expected, bytes=len(data)))
    lines.append(f'{name}=={version} --hash=sha256:{expected}')
    with zipfile.ZipFile(target) as wheel:
        for entry in wheel.infolist():
            if entry.is_dir() or entry.filename.endswith('.dist-info/RECORD') or '.data/scripts/' in entry.filename:
                continue
            assert '.data/' not in entry.filename, entry.filename
            relative = 'runtime/python/Lib/site-packages/' + entry.filename
            content = wheel.read(entry)
            manifest['criticalFiles'].append(dict(path=relative, bytes=len(content),
                sha256=hashlib.sha256(content).hexdigest()))
            installed = runtime / 'Lib/site-packages' / entry.filename
            installed.parent.mkdir(parents=True, exist_ok=True)
            installed.write_bytes(content)
    print('Added', name, version)
lines = [line.replace('markitdown[pdf]', 'markitdown[pdf,docx,pptx,xlsx,xls,outlook]') for line in lines]
manifest['installedBytes'] = sum(p.stat().st_size for p in runtime.rglob('*') if p.is_file())
(RES / 'markitdown-requirements.lock').write_text('\n'.join(sorted(lines)) + '\n', encoding='utf-8')
(RES / 'markitdown-windows-x64.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
print('Private runtime:', runtime)
