"""Read-only production-code spike; installed runtimes, synthetic fixtures, no downloads.

Run with development Python on Windows. Captures raw pipe bytes (including CR),
timestamped sanitized lines, process identities and temp/output inventory changes.
Long Marker probes are bounded, and explicitly reported as incomplete on timeout.
Child processes belong to a kill-on-close Windows Job, like the production executor.
"""
import argparse
import ctypes as c
from ctypes import wintypes as w
import hashlib
import json
import os
from pathlib import Path
import runpy
import subprocess
import threading
import time

REPO = Path(__file__).resolve().parents[2]
MARKER = "from marker.scripts.convert_single import convert_single_cli; convert_single_cli()"
MD = "from markitdown.__main__ import main; main()"


def read_stream(pipe, destination, stream, sanitize, emit):
    """Sanitize complete UTF-8 lines, preserving CR/LF and an unterminated tail."""
    with (destination / (stream + '.raw')).open('wb') as raw, (destination / (stream + '.log')).open('w', encoding='utf-8', newline='') as clean:
        pending = b''

        def flush_lines(final=False):
            nonlocal pending
            while pending:
                endings = [i for separator in (b'\r', b'\n') if (i := pending.find(separator)) >= 0]
                if not endings:
                    if not final:
                        break
                    end = len(pending)
                else:
                    end = min(endings) + 1
                    if pending[end - 1:end] == b'\r':
                        if end == len(pending) and not final:
                            break  # Wait for a possible LF in the next chunk.
                        if pending[end:end + 1] == b'\n':
                            end += 1
                value = sanitize(pending[:end].decode('utf-8', errors='replace'))
                pending = pending[end:]
                clean.write(value)
                clean.flush()
                emit(stream, value)

        while chunk := os.read(pipe.fileno(), 4096):
            raw.write(chunk)
            raw.flush()
            pending += chunk
            flush_lines()
        flush_lines(final=True)


class IO(c.Structure):
    _fields_ = [(name, c.c_ulonglong) for name in ('ReadOperationCount', 'WriteOperationCount', 'OtherOperationCount', 'ReadTransferCount', 'WriteTransferCount', 'OtherTransferCount')]


class BASIC(c.Structure):
    _fields_ = [('PerProcessUserTimeLimit', c.c_longlong), ('PerJobUserTimeLimit', c.c_longlong), ('LimitFlags', w.DWORD), ('MinimumWorkingSetSize', c.c_size_t), ('MaximumWorkingSetSize', c.c_size_t), ('ActiveProcessLimit', w.DWORD), ('Affinity', c.c_size_t), ('PriorityClass', w.DWORD), ('SchedulingClass', w.DWORD)]


class EXTENDED(c.Structure):
    _fields_ = [('BasicLimitInformation', BASIC), ('IoInfo', IO), ('ProcessMemoryLimit', c.c_size_t), ('JobMemoryLimit', c.c_size_t), ('PeakProcessMemoryUsed', c.c_size_t), ('PeakJobMemoryUsed', c.c_size_t)]


class Job:
    def __init__(self):
        self.k = c.WinDLL('kernel32', use_last_error=True)
        self.k.CreateJobObjectW.restype = w.HANDLE
        self.k.CreateJobObjectW.argtypes = [c.c_void_p, w.LPCWSTR]
        self.k.SetInformationJobObject.argtypes = [w.HANDLE, c.c_int, c.c_void_p, w.DWORD]
        self.k.AssignProcessToJobObject.argtypes = [w.HANDLE, w.HANDLE]
        self.k.CloseHandle.argtypes = [w.HANDLE]
        self.handle = self.k.CreateJobObjectW(None, None)
        info = EXTENDED()
        info.BasicLimitInformation.LimitFlags = 0x2000
        if not self.handle or not self.k.SetInformationJobObject(self.handle, 9, c.byref(info), c.sizeof(info)):
            raise c.WinError(c.get_last_error())

    def assign(self, process):
        if not self.k.AssignProcessToJobObject(self.handle, int(process._handle)):
            process.kill()
            self.close()
            raise c.WinError(c.get_last_error())

    def close(self):
        if self.handle:
            self.k.CloseHandle(self.handle)
            self.handle = None


def environment(root, marker):
    env = {k: os.environ[k] for k in ('SystemRoot', 'WINDIR', 'COMSPEC', 'NUMBER_OF_PROCESSORS') if k in os.environ}
    folders = dict(HOME='cache/home', USERPROFILE='cache/home', LOCALAPPDATA='cache/home/AppData/Local', APPDATA='cache/home/AppData/Roaming', PIP_CACHE_DIR='cache/pip', TEMP='temp', TMP='temp')
    if marker:
        folders.update(HF_HOME='models/huggingface', HF_HUB_CACHE='models/huggingface/hub', MODEL_CACHE_DIR='models/datalab', TORCH_HOME='cache/torch', XDG_CACHE_HOME='cache')
    for key, value in folders.items():
        (root / value).mkdir(parents=True, exist_ok=True)
        env[key] = str(root / value)
    env.update(PATH=str(root / 'runtime/python'), PYTHONNOUSERSITE='1', PYTHONDONTWRITEBYTECODE='1', PYTHONUNBUFFERED='1', PYTHONIOENCODING='utf-8')
    if marker:
        manifest = json.loads((REPO / 'src/main/resources/engines/marker-windows-x64.json').read_text(encoding='utf-8-sig'))
        env.update(PATH=str(root / 'runtime/python') + ';' + str(root / 'runtime/llamacpp') + ';' + str(Path(os.environ['SystemRoot']) / 'System32'), LLAMA_CPP_BINARY=str(root / manifest['llamaCpp']['executable']), TORCH_DEVICE='cpu', SURYA_INFERENCE_BACKEND='llamacpp', SURYA_INFERENCE_KEEP_ALIVE='true', HF_HUB_DISABLE_SYMLINKS_WARNING='1', HF_HUB_OFFLINE='1')
    return env


def run(engine, case, source, base, limit, cancel=False):
    root = Path(os.environ['LOCALAPPDATA']) / 'ParseForge/engines' / engine
    destination = base / engine / case
    destination.mkdir(parents=True)
    output = destination / 'output'
    output.mkdir()
    marker = engine == 'marker'
    args = ['-c', MARKER if marker else MD, str(source)]
    args += ['--output_dir', str(output), '--output_format', 'markdown', '--disable_multiprocessing'] if marker else ['-o', str(output / (source.stem + '.md'))]
    command = [str(root / 'runtime/python/python.exe'), '-I', '-X', 'utf8', '-u', '-B'] + args
    substitutions = [(str(REPO), '<REPO>'), (str(root), '<ENGINE>'), (str(Path.home()), '<USER>')]

    def sanitize(value):
        for path, token in substitutions:
            value = value.replace(path, token).replace(path.replace('\\', '/'), token)
        return value

    lock = threading.Lock()
    events = destination / 'events.jsonl'
    start = time.monotonic()

    def emit(kind, data):
        with lock, events.open('a', encoding='utf-8') as f:
            f.write(json.dumps(dict(seconds=round(time.monotonic()-start, 4), kind=kind, data=data), ensure_ascii=False) + '\n')

    job = Job()
    process = subprocess.Popen(command, cwd=root, env=environment(root, marker), stdout=subprocess.PIPE, stderr=subprocess.PIPE, creationflags=subprocess.CREATE_NO_WINDOW)
    job.assign(process)
    emit('start', dict(pid=process.pid, command=[sanitize(x) for x in command]))

    readers = [threading.Thread(target=read_stream, args=(pipe, destination, name, sanitize, emit)) for pipe, name in ((process.stdout, 'stdout'), (process.stderr, 'stderr'))]
    for thread in readers:
        thread.start()
    stop = threading.Event()

    def monitor():
        previous = None
        prefix = str(root).replace("'", "''") + '\\'
        query = "$ErrorActionPreference='Stop'; @(Get-CimInstance Win32_Process | Where-Object { $_.ExecutablePath -and $_.ExecutablePath.StartsWith('" + prefix + "', [StringComparison]::OrdinalIgnoreCase) } | Select-Object ProcessId,ParentProcessId,ExecutablePath,CommandLine,CreationDate) | ConvertTo-Json -Compress"
        while not stop.is_set():
            try:
                result = subprocess.run(['powershell.exe', '-NoProfile', '-NonInteractive', '-Command', query], capture_output=True, timeout=20, creationflags=subprocess.CREATE_NO_WINDOW)
            except subprocess.TimeoutExpired:
                emit('monitor_error', 'CIM snapshot timeout; conversion continues')
                stop.wait(2)
                continue
            snapshot = result.stdout.decode('utf-8', errors='replace')
            if result.returncode == 0 and snapshot.strip():
                snapshot = json.loads(snapshot)
                if isinstance(snapshot, dict):
                    snapshot = [snapshot]
                snapshot = [{key: sanitize(value) if isinstance(value, str) else value for key, value in item.items()} for item in snapshot]
            emit('process_snapshot', dict(exit=result.returncode, processes=snapshot, error=sanitize(result.stderr.decode('utf-8', errors='replace'))))
            inventory = {}
            for label, folder in (('temp', root / 'temp'), ('server-cache', root / 'cache/home/.cache/datalab/surya'), ('output', output)):
                for item in folder.rglob('*'):
                    if item.is_file():
                        try:
                            stat = item.stat()
                            inventory[label + '/' + item.relative_to(folder).as_posix()] = [stat.st_size, stat.st_mtime_ns]
                        except OSError:
                            pass
            if inventory != previous:
                emit('inventory', inventory)
                previous = inventory
            stop.wait(2)

    observer = threading.Thread(target=monitor)
    observer.start()
    status = 'COMPLETED'
    try:
        process.wait(timeout=limit)
        if process.returncode != 0:
            status = 'FAILED'
    except subprocess.TimeoutExpired:
        status = 'CANCELLED' if cancel else 'TIMEOUT_INCOMPLETE'
        emit('cancel' if cancel else 'timeout', {'limitSeconds': limit})
    finally:
        job.close()
        process.wait(timeout=20)
        for thread in readers:
            thread.join(timeout=20)
        stop.set()
        observer.join(timeout=25)
    files = {p.relative_to(output).as_posix(): dict(bytes=p.stat().st_size, sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in output.rglob('*') if p.is_file()}
    summary = dict(engine=engine, case=case, status=status, exitCode=process.returncode, seconds=round(time.monotonic()-start, 3), source=sanitize(str(source)), sourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(), limitSeconds=limit, outputs=files, logDirectory=str(destination.relative_to(REPO)))
    (destination / 'summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    print(json.dumps(summary), flush=True)
    return summary


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--marker-limit', type=int, default=240)
    parser.add_argument('--complete-cancellation', type=Path, help='Extend an existing run whose 240-page cancellation probe finished too quickly')
    args = parser.parse_args()
    if args.complete_cancellation:
        base = args.complete_cancellation.resolve()
        summaries = json.loads((base / 'summary.json').read_text(encoding='utf-8'))
        source = base / 'cancel-15000.pdf'
        runpy.run_path(str(REPO / 'scripts/spikes/markitdown-spike.py'))['fixture'](source, 15000)
        summaries.append(run('markitdown', 'cancel-many-pages', source, base, 2, True))
        (base / 'summary.json').write_text(json.dumps(summaries, indent=2), encoding='utf-8')
        return
    base = REPO / 'build/stage9-progress' / str(time.time_ns())
    base.mkdir(parents=True)
    fixtures = REPO / 'build/stage7-host/fixtures'
    summaries = []
    matrix = [('marker', case, pdf, args.marker_limit, False) for case, pdf in [('digital-short', 'digital-text'), ('digital-long', 'digital-long'), ('scanned-short', 'scanned-image-only'), ('scanned-long', 'scanned-long'), ('mixed', 'mixed'), ('digital-repeat', 'digital-text')]]
    matrix += [('markitdown', case, pdf, limit, cancel) for case, pdf, limit, cancel in [('digital-short', 'digital-text', 120, False), ('digital-long', 'digital-long', 120, False), ('many-pages', 'digital-long', 120, False), ('corrupt', 'corrupt', 120, False), ('cancel', 'digital-long', 2, True)]]
    for engine, case, pdf, limit, cancel in matrix:
        summaries.append(run(engine, case, fixtures / (pdf + '.pdf'), base, limit, cancel))
        (base / 'summary.json').write_text(json.dumps(summaries, indent=2), encoding='utf-8')
    if summaries[-1]['status'] != 'CANCELLED':
        source = base / 'cancel-15000.pdf'
        runpy.run_path(str(REPO / 'scripts/spikes/markitdown-spike.py'))['fixture'](source, 15000)
        summaries.append(run('markitdown', 'cancel-many-pages', source, base, 2, True))
        (base / 'summary.json').write_text(json.dumps(summaries, indent=2), encoding='utf-8')
    print('EVIDENCE_DIRECTORY=' + str(base), flush=True)


if __name__ == '__main__':
    main()
