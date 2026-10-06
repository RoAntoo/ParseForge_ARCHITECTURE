"""Read license metadata from the exact private spike wheels, not global packages."""
from email.parser import Parser
import json
from pathlib import Path
import sys

repo = Path(__file__).resolve().parents[2]
runtime = Path(sys.argv[1])
manifest = json.loads((repo / "src/main/resources/engines/markitdown-windows-x64.json").read_text(encoding="utf-8"))
rows = []
for wheel in manifest["packages"]["wheels"]:
    matches = list((runtime / "runtime/python/Lib/site-packages").glob(wheel["name"].replace("-", "_").replace(".", "_") + "-" + wheel["version"] + ".dist-info/METADATA"))
    assert len(matches) == 1, wheel["name"]
    metadata = Parser().parsestr(matches[0].read_text(encoding="utf-8"))
    assert metadata["Version"] == wheel["version"]
    license = metadata.get("License-Expression") or metadata.get("License") or "; ".join(c for c in metadata.get_all("Classifier", []) if c.startswith("License ::")) or "See bundled license files"
    rows.append(f"| {wheel['name']} | {wheel['version']} | {' '.join(license.split()).replace('|', '/')} |")
out = repo / "docs/release/MARKITDOWN_LICENSE_INVENTORY.md"
out.write_text(f"# MarkItDown document wheel license inventory\n\nDerived from the exact private runtime wheels; 2026-10-05. These {len(rows)} components are downloaded separately, not bundled in the setup. CPython 3.12.10 (PSF) and bootstrap pip 25.0.1 (MIT) are additional runtime components. Native/bundled third-party terms remain installed alongside each package.\n\n| Name | Version | License metadata |\n| --- | --- | --- |\n" + "\n".join(sorted(rows)) + "\n", encoding="utf-8")
print(out)
