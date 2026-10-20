"""Assemble the original procedural geometry into the durable inline fragment."""
from pathlib import Path

HERE = Path(__file__).resolve().parent
source = (HERE / "hats-geometry.js").read_text(encoding="utf-8")
template = (HERE / "hats-review-template.html").read_text(encoding="utf-8")
assert template.count("__GEOMETRY_SOURCE__") == 1
assert "</script" not in source.lower()
fragment = template.replace("__GEOMETRY_SOURCE__", source)
assert len(fragment.encode("utf-8")) < 1_000_000
assert "<!doctype" not in fragment.lower()
target = HERE / "ornate-hat-designs.html"
target.write_text(fragment, encoding="utf-8", newline="\n")
print(f"{target}: {target.stat().st_size} bytes")
