"""Record wheel pins and critical artifact hashes, using private Python only."""
import hashlib
import importlib.metadata as metadata
import json
import sys
import zipfile
from email.parser import BytesParser
from pathlib import Path


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


root = Path(sys.argv[2]).resolve()
if sys.argv[1] == 'wheels':
    rows = []
    inventory = []
    for wheel in sorted((root / 'downloads/wheels').glob('*.whl')):
        with zipfile.ZipFile(wheel) as archive:
            name = next(n for n in archive.namelist() if n.endswith('.dist-info/METADATA'))
            info = BytesParser().parsebytes(archive.read(name))
        digest = sha(wheel)
        rows.append(f"{info['Name']}=={info['Version']} --hash=sha256:{digest}")
        inventory.append({'file': wheel.name, 'bytes': wheel.stat().st_size, 'sha256': digest})
    (root / 'downloads/requirements.lock').write_text('\n'.join(rows) + '\n', encoding='utf-8')
    (root / 'downloads/wheels.json').write_text(json.dumps(inventory, indent=2), encoding='utf-8')
else:
    sources = json.loads(Path(sys.argv[3]).read_text(encoding='utf-8-sig'))
    critical = []
    for folder in [root / 'runtime/python', root / 'runtime/llamacpp']:
        for item in sorted(folder.rglob('*')):
            # Extracted stdlib .pyc files are runtime artifacts; only generated
            # cache directories are mutable and excluded from the seal.
            if item.is_file() and '__pycache__' not in item.relative_to(folder).parts:
                critical.append({'path': item.relative_to(root).as_posix(), 'sha256': sha(item)})
    for item in [root / 'downloads/requirements.lock', root / 'downloads/wheels.json']:
        critical.append({'path': item.relative_to(root).as_posix(), 'sha256': sha(item)})
    server = next((root / 'runtime/llamacpp').rglob('llama-server.exe'))
    manifest = {
        'id': 'marker', 'displayName': 'Marker (Stage 3B spike)', 'platform': 'windows-x64',
        'markerVersion': metadata.version('marker-pdf'), 'suryaVersion': metadata.version('surya-ocr'),
        'torchVersion': metadata.version('torch'), 'torchvisionVersion': metadata.version('torchvision'),
        'python': {**sources['python'], 'executable': 'runtime/python/python.exe', 'executableSha256': sha(root / 'runtime/python/python.exe')},
        'llamaCpp': {**sources['llamaCpp'], 'executable': server.relative_to(root).as_posix(), 'executableSha256': sha(server)},
        'pip': sources['pip'], 'criticalFiles': critical,
    }
    (root / 'engine.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
