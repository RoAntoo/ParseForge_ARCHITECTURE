"""Read-only inventory of installed sizes, model revisions/hashes and private processes."""
import hashlib
import json
import sys
from pathlib import Path
import psutil

root = Path(sys.argv[1]).resolve()


def size(folder):
    return sum(p.stat().st_size for p in folder.rglob('*') if p.is_file())


site = root / 'runtime/python/Lib/site-packages'
parts = {
    'pythonWithoutSitePackages': size(root / 'runtime/python') - size(site),
    'sitePackages': size(site),
    'llamaCpp': size(root / 'runtime/llamacpp'),
    'models': size(root / 'models'),
    'cache': size(root / 'cache'),
    'temp': size(root / 'temp'),
    'logs': size(root / 'logs'),
    'downloadArtifacts': size(root / 'downloads'),
    'rootFiles': sum(p.stat().st_size for p in root.iterdir() if p.is_file()),
}
parts['installedTotal'] = sum(v for k, v in parts.items() if k != 'downloadArtifacts')
parts['totalWithDownloadArtifacts'] = parts['installedTotal'] + parts['downloadArtifacts']
model_files = []
for path in sorted((root / 'models').rglob('*')):
    if path.is_file() and '/xet/' not in path.as_posix() and '/.locks/' not in path.as_posix():
        with path.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        model_files.append({'path': path.relative_to(root).as_posix(), 'bytes': path.stat().st_size, 'sha256': digest})
processes = []
for process in psutil.process_iter(['pid', 'exe', 'cmdline']):
    try:
        if process.pid != psutil.Process().pid and process.info['exe'] and Path(process.info['exe']).resolve().is_relative_to(root):
            processes.append(process.info)
    except (psutil.AccessDenied, psutil.NoSuchProcess, OSError):
        pass
artifacts = []
for path in sorted((root / 'downloads').iterdir()):
    if path.is_file():
        with path.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        artifacts.append({'path': path.relative_to(root).as_posix(), 'bytes': path.stat().st_size, 'sha256': digest})
report = {'root': str(root), 'sizesBytes': parts, 'models': model_files,
          'downloadedArtifacts': artifacts, 'remainingPrivateProcesses': processes}
(root / 'logs/runtime-audit.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
print(json.dumps({'sizesBytes': parts, 'modelFiles': len(model_files), 'remainingPrivateProcesses': processes}, indent=2))
if processes:
    raise SystemExit('Private runtime still has live processes')
