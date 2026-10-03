"""Isolated Windows spike and reviewed manifest generator; development only.

Run with development Python. All probes run embedded CPython with a scrubbed
environment. Never uses Marker, Java, user pip configuration or a global CLI.
Artifacts stay under build; only the lock, manifest and evidence enter Git.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.request
import zipfile

REPO = Path(__file__).resolve().parents[2]
BASE = REPO / "build/stage6-spike" / ("privado con espacios ñ " + str(time.time_ns()))
RUNTIME = BASE / "runtime/python"
DOWNLOADS = BASE / "downloads"
VERSION = "0.1.8"
ENTRYPOINT = "import sys; from pdfminer.pdfparser import PDFParser; from pdfminer.pdfdocument import PDFDocument; f=open(sys.argv[1],'rb'); PDFDocument(PDFParser(f)); f.close(); from markitdown.__main__ import main; main()"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def size(path):
    return sum(p.stat().st_size for p in path.rglob("*") if p.is_file())


def fetch(item, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists():
        urllib.request.urlretrieve(item["url"], target)
    assert digest(target) == item["sha256"], target
    return dict(item, bytes=target.stat().st_size)


def fixture(path, pages=1):
    objects = [b"<< /Type /Catalog /Pages 2 0 R >>", b"", b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"]
    kids = []
    for index in range(pages):
        page = len(objects) + 1
        kids.append(f"{page} 0 R")
        text = f"BT /F1 18 Tf 72 720 Td (ParseForge Stage 6 page {index + 1}) Tj 0 -36 Td (Readable Markdown from a digital PDF.) Tj ET".encode()
        objects.extend([f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> /Contents {page + 1} 0 R >>".encode(),
                        b"<< /Length " + str(len(text)).encode() + b" >>\nstream\n" + text + b"\nendstream"])
    objects[1] = f"<< /Type /Pages /Kids [{' '.join(kids)}] /Count {pages} >>".encode()
    pdf = bytearray(b"%PDF-1.4\n")
    offsets = [0]
    for number, obj in enumerate(objects, 1):
        offsets.append(len(pdf))
        pdf.extend(f"{number} 0 obj\n".encode() + obj + b"\nendobj\n")
    xref = len(pdf)
    pdf.extend(f"xref\n0 {len(offsets)}\n0000000000 65535 f \n".encode())
    for offset in offsets[1:]:
        pdf.extend(f"{offset:010d} 00000 n \n".encode())
    pdf.extend(f"trailer\n<< /Size {len(offsets)} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode())
    path.write_bytes(pdf)


def main():
    assert os.name == "nt", "Windows x64 spike required"
    assert not RUNTIME.exists(), "Use a new build/stage6-spike directory for clean measurement"
    started = time.monotonic()
    artifacts = json.loads((REPO / "scripts/spikes/runtime-artifacts.json").read_text())
    python = fetch(artifacts["python"], DOWNLOADS / "python.zip")
    pip = fetch(artifacts["pip"], DOWNLOADS / "pip.whl")
    python["executable"] = "runtime/python/python.exe"
    RUNTIME.mkdir(parents=True)
    with zipfile.ZipFile(DOWNLOADS / "python.zip") as z:
        z.extractall(RUNTIME)
    with zipfile.ZipFile(RUNTIME / "python312.zip") as z:
        z.extractall(RUNTIME / "Lib")
    (RUNTIME / "python312._pth").write_text("Lib\n.\nLib/site-packages\nimport site\n", newline="\n")
    private_python_bytes = size(RUNTIME)
    with zipfile.ZipFile(DOWNLOADS / "pip.whl") as z:
        z.extractall(RUNTIME / "Lib/site-packages")
    env = {k: os.environ[k] for k in ("SystemRoot", "WINDIR", "COMSPEC") if k in os.environ}
    env.update(PATH=str(RUNTIME), TEMP=str(BASE / "temp"), TMP=str(BASE / "temp"),
               HOME=str(BASE / "cache/home"), USERPROFILE=str(BASE / "cache/home"), PYTHONNOUSERSITE="1")
    (BASE / "temp").mkdir()
    (BASE / "cache/home").mkdir(parents=True)
    executable = RUNTIME / "python.exe"
    prefix = [str(executable), "-I", "-X", "utf8", "-u", "-B"]

    def run(args, success=True):
        result = subprocess.run(prefix + args, cwd=BASE, env=env, capture_output=True, encoding="utf-8", timeout=180)
        (BASE / "spike.log").open("a", encoding="utf-8").write(result.stdout + result.stderr)
        if success and result.returncode:
            raise RuntimeError(result.stderr)
        return result

    run(["-m", "pip", "--isolated", "install", "--dry-run", "--only-binary=:all:", "--report", str(DOWNLOADS / "resolution.json"), f"markitdown[pdf]=={VERSION}"])
    resolved = json.loads((DOWNLOADS / "resolution.json").read_text(encoding="utf-8"))["install"]
    wheels = []
    lock = []
    for package in resolved:
        info = package["download_info"]
        name, version = package["metadata"]["name"], package["metadata"]["version"]
        filename = info["url"].rsplit("/", 1)[1]
        assert filename.endswith(".whl")
        wheel = fetch(dict(url=info["url"], sha256=info["archive_info"]["hashes"]["sha256"]), DOWNLOADS / "wheels" / filename)
        wheels.append(dict(wheel, file=filename, name=name, version=version))
        lock.append(f"{name}{'[pdf]' if name.lower() == 'markitdown' else ''}=={version} --hash=sha256:{wheel['sha256']}")
    lock_text = "\n".join(sorted(lock)) + "\n"
    (DOWNLOADS / "requirements.lock").write_text(lock_text)
    run(["-m", "pip", "--isolated", "install", "--no-index", "--no-compile", "--only-binary=:all:", "--find-links", str(DOWNLOADS / "wheels"), "--require-hashes", "-r", str(DOWNLOADS / "requirements.lock")])
    run(["-m", "pip", "check"])
    installation_seconds = time.monotonic() - started
    audit = run(["-c", "import sys,importlib.metadata as m; from pathlib import Path; r=Path.cwd().resolve(); assert all(Path(p).resolve().is_relative_to(r) for p in [sys.executable,sys.prefix,sys.base_prefix,*sys.path]); assert m.version('markitdown')=='0.1.8'; print(sys.version)"])
    tests = []
    for filename, pages in [("digital.pdf", 1), ("varias páginas ñ.pdf", 3)]:
        pdf = BASE / filename
        fixture(pdf, pages)
        output = BASE / (pdf.stem + ".md")
        before = time.monotonic()
        result = run(["-c", ENTRYPOINT, str(pdf), "-o", str(output)])
        content = output.read_text(encoding="utf-8")
        assert "Readable Markdown" in content and f"page {pages}" in content
        tests.append(dict(file=filename, pages=pages, exitCode=result.returncode, seconds=time.monotonic() - before, sha256=digest(output)))
    invalid = BASE / "corrupto.pdf"
    invalid.write_bytes(b"%PDF-1.4\nnot a valid PDF")
    raw_invalid = run(["-m", "markitdown", str(invalid), "-o", str(BASE / "raw-invalid.md")], False).returncode
    assert run(["-c", ENTRYPOINT, str(invalid), "-o", str(BASE / "invalid.md")], False).returncode != 0
    large = BASE / "cancelar.pdf"
    fixture(large, 15000)
    process = subprocess.Popen(prefix + ["-c", ENTRYPOINT, str(large), "-o", str(BASE / "cancelled.md")], cwd=BASE, env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(0.8)
    assert process.poll() is None
    process.terminate()
    process.wait(timeout=10)
    # CLI is single-process; Java Job Object cancellation is verified separately.
    assert process.poll() is not None
    import shutil
    if (RUNTIME / "Scripts").exists():
        shutil.rmtree(RUNTIME / "Scripts")  # launchers embed staging paths
    installed_bytes = size(RUNTIME)
    critical = [dict(path=p.relative_to(BASE).as_posix(), bytes=p.stat().st_size, sha256=digest(p))
                for p in sorted(RUNTIME.rglob("*")) if p.is_file() and p.name != "RECORD"]
    manifest = dict(schemaVersion=1, id="markitdown", displayName="MarkItDown", platform="windows-x64", engineVersion=VERSION,
                    minimumFreeBytes=500_000_000, installedBytes=installed_bytes, python=python, pip=pip,
                    packages=dict(lockFile="markitdown-requirements.lock", wheels=wheels), criticalFiles=critical)
    out = REPO / "src/main/resources/engines"
    (out / "markitdown-windows-x64.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (out / "markitdown-requirements.lock").write_text(lock_text, encoding="utf-8")
    evidence = dict(markitdown=VERSION, python=python, pip=pip, privatePythonBytes=private_python_bytes,
                    packagesAndBootstrapBytes=installed_bytes-private_python_bytes, installedBytes=installed_bytes,
                    downloadedBytes=python["bytes"]+pip["bytes"]+sum(w["bytes"] for w in wheels), installationSeconds=installation_seconds,
                    runtimeAudit=audit.stdout.strip(), wheels=wheels, conversions=tests, invalidPdfExitNonzero=True,
                    cancellationTerminated=True, rawCliInvalidPdfExitCode=raw_invalid, cleanWindowsVm="PENDING", entrypoint=ENTRYPOINT)
    (REPO / "docs/spikes/STAGE_6_MARKITDOWN_SPIKE.json").write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in evidence.items() if k not in ("wheels", "python", "pip")}, indent=2))


if __name__ == "__main__":
    main()
