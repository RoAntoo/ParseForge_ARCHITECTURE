"""Maintenance tool: resolve reviewed 3B hashes to immutable artifact URLs.

Not used by the application or installation. Fails if upstream differs.
"""
import concurrent.futures
import json
from pathlib import Path
import shutil
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/engines'
OUT.mkdir(parents=True, exist_ok=True)
old = json.loads((ROOT / 'docs/spikes/marker-autonomous-engine.json').read_text())
evidence = json.loads((ROOT / 'docs/spikes/marker-autonomous-evidence.json').read_text())
wheels = json.loads((ROOT / 'scripts/spikes/marker-autonomous-wheels.json').read_text())

def resolve(wheel):
    name, version = wheel['file'].split('-')[:2]
    if '+cpu' in version:
        url = 'https://download.pytorch.org/whl/cpu/' + wheel['file'].replace('+', '%2B')
    else:
        with urllib.request.urlopen(f'https://pypi.org/pypi/{name}/{version}/json', timeout=60) as response:
            release = json.load(response)
        matching = [x for x in release['urls'] if x['filename'] == wheel['file']
                    and x['digests']['sha256'] == wheel['sha256']]
        if len(matching) != 1:
            raise RuntimeError(f'No reviewed artifact: {wheel["file"]}')
        url = matching[0]['url']
    return dict(wheel, url=url)

with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    packages = list(pool.map(resolve, wheels))
models = []
refs = []
for item in evidence['runtimeAudit']['models']:
    path = item['path']
    if '/snapshots/' in path:
        before, tail = path.split('/snapshots/')
        repo = before.split('models--')[1].replace('--', '/')
        revision, file = tail.split('/', 1)
        models.append(dict(item, url=f'https://huggingface.co/{repo}/resolve/{revision}/{file}'))
    elif path.startswith('models/datalab/'):
        models.append(dict(item, url='https://models.datalab.to/' + path.removeprefix('models/datalab/')))
    elif '/refs/main' in path:
        repo = path.split('models--')[1].split('/')[0].replace('--', '/')
        revision = next(x['path'].split('/snapshots/')[1].split('/')[0]
                        for x in evidence['runtimeAudit']['models']
                        if f'models--{repo.replace("/", "--")}/snapshots/' in x['path'])
        refs.append(dict(item, content=revision))

critical = [x for x in old['criticalFiles'] if x['path'].startswith('runtime/')
            and '/Scripts/' not in x['path'] and not x['path'].endswith('.dist-info/RECORD')]
manifest = dict(schemaVersion=1, id='marker', displayName='Marker', platform='windows-x64',
                engineVersion=old['markerVersion'], minimumFreeBytes=7_000_000_000,
                installedBytes=3_230_000_000, python=old['python'], llamaCpp=old['llamaCpp'],
                pip=old['pip'], packages=dict(lockFile='marker-requirements.lock', wheels=packages),
                models=models, refs=refs, criticalFiles=critical,
                assets=[dict(x, url='https://models.datalab.to/artifacts/' + x['path'].rsplit('/', 1)[1])
                        for x in critical if '/static/fonts/' in x['path']])
(OUT / 'marker-windows-x64.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
shutil.copyfile(ROOT / 'scripts/spikes/marker-autonomous-requirements.lock', OUT / 'marker-requirements.lock')
print(f'Manifest v1: {len(packages)} wheels, {len(models)} model files, {len(critical)} critical files')
