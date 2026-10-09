"""Create an importable native Prism instance using only Python's standard library."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import zipfile


def jar_metadata(path: Path) -> dict:
    with zipfile.ZipFile(path) as archive:
        return json.loads(archive.read("fabric.mod.json"))


def main() -> None:
    repo = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--feed", type=Path, default=repo.parent / "analyzer-updates")
    parser.add_argument("--fabric-api", type=Path, required=True)
    parser.add_argument("--instance-folder", type=Path, required=True,
                        help="A new, empty staging directory; existing files are never replaced.")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    feed = args.feed.resolve()
    manifest = json.loads((feed / "release.json").read_text(encoding="utf-8"))
    artifact = manifest["artifact"]
    if Path(artifact).name != artifact or "\\" in artifact or ":" in artifact:
        raise ValueError("Release artifact must be a single filename")
    analyzer = feed / artifact
    if hashlib.sha256(analyzer.read_bytes()).hexdigest() != manifest["sha256"]:
        raise ValueError("Release checksum mismatch")
    mod = jar_metadata(analyzer)
    if (manifest["schemaVersion"] != 1 or manifest["modId"] != "analyzer"
            or manifest["minecraftVersion"] != "1.21.4" or mod["id"] != "analyzer"
            or mod["version"] != manifest["version"] or mod["depends"]["minecraft"] != "1.21.4"):
        raise ValueError("Release metadata does not match the supported Analyzer instance")
    fabric = args.fabric_api.resolve()
    api = jar_metadata(fabric)
    if api["id"] != "fabric-api" or api["version"] != "0.119.4+1.21.4":
        raise ValueError("Fabric API must be the verified 0.119.4+1.21.4 build")
    baritone = repo / "third-party/baritone/baritone-api-fabric-1.13.1.jar"
    if hashlib.sha256(baritone.read_bytes()).hexdigest() != "bfb79c3cdb8fe1f7697999b1f9c7f08ea29775f3b69d48ad90c387690ee67b05":
        raise ValueError("Official Baritone API checksum mismatch")
    bot = jar_metadata(baritone)
    if bot["id"] != "baritone" or bot["version"] != "1.13.1":
        raise ValueError("Expected official Baritone API 1.13.1")
    instance = args.instance_folder.resolve()
    if instance.exists() and any(instance.iterdir()):
        raise ValueError("Refusing to overwrite a staging instance")
    instance.mkdir(parents=True, exist_ok=True)
    mods = instance / "minecraft" / "mods"
    mods.mkdir(parents=True)
    shutil.copyfile(analyzer, mods / artifact)
    shutil.copyfile(fabric, mods / fabric.name)
    shutil.copyfile(baritone, mods / baritone.name)
    scripts = instance / "analyzer-updater"
    scripts.mkdir()
    shutil.copyfile(repo / "tools/prism/Update-Analyzer.ps1", scripts / "Update-Analyzer.ps1")
    (instance / "analyzer-updater.json").write_text(json.dumps({
        "schemaVersion": 1,
        "enabled": True,
        "manifestPath": str(feed / "release.json"),
    }, indent=2) + "\n", encoding="utf-8")
    (instance / "mmc-pack.json").write_text(json.dumps({
        "formatVersion": 1,
        "components": [
            {"uid": "net.minecraft", "version": "1.21.4", "important": True},
            {"uid": "net.fabricmc.fabric-loader", "version": "0.16.14", "important": True},
        ],
    }, indent=2) + "\n", encoding="utf-8")
    (instance / "instance.cfg").write_text("""[General]
ConfigVersion=1.3
InstanceType=OneSix
name=Analyzer
iconKey=default
OverrideMemory=true
MinMemAlloc=512
MaxMemAlloc=4096
OverrideJavaLocation=false
OverrideWindow=true
MinecraftWinWidth=1280
MinecraftWinHeight=800
LaunchMaximized=false
OverrideCommands=true
PreLaunchCommand=powershell.exe -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$INST_DIR/analyzer-updater/Update-Analyzer.ps1"
PostExitCommand=
WrapperCommand=
""", encoding="utf-8")
    (instance / "Update-Analyzer.cmd").write_text(
        '@echo off\r\npowershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass '
        '-File "%~dp0analyzer-updater\\Update-Analyzer.ps1" -InstanceDir "%~dp0."\r\npause\r\n',
        encoding="ascii")
    for doc in ("README.md", "PRISM.md", "AUTOMATION.md", "ESP-READABILITY.md", "MODULES.md", "MODULES.json", "ROADMAP.md", "RESEARCH.md", "RESEARCH-VISUALS.md", "RESEARCH-COSMETICS.md", "VALIDATION.md", "VALIDATION.log", "PIXEL-VALIDATION.log", "LICENSE", "FONT-LICENSE.txt", "THIRD-PARTY.md"):
        shutil.copyfile(repo / doc, instance / doc)
    screenshots = instance / "screenshots"
    screenshots.mkdir()
    for capture in sorted((repo / "screenshots").glob("*.png")):
        if capture.name in {"analyzer-ui.png", "analyzer-gui-classic.png"} or capture.name.startswith(("analyzer-esp-", "analyzer-bot-", "analyzer-automation-")):
            shutil.copyfile(capture, screenshots / capture.name)
    licenses = instance / "licenses"
    licenses.mkdir()
    with zipfile.ZipFile(fabric) as archive:
        (licenses / "FABRIC-API-LICENSE.txt").write_bytes(archive.read("LICENSE-fabric-api"))
    # Separate, replaceable LGPL release, corresponding source and upstream notices.
    shutil.copytree(repo / "third-party/baritone", instance / "baritone-source-and-notices",
                    ignore=shutil.ignore_patterns("*.jar"))
    output = (args.output or repo.parent / f"Analyzer-{manifest['version']}-Prism-1.21.4.zip").resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    pending = output.with_suffix(".zip.pending")
    with zipfile.ZipFile(pending, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(instance.rglob("*")):
            if path.is_file():
                archive.write(path, path.relative_to(instance).as_posix())
    with zipfile.ZipFile(pending) as archive:
        if archive.testzip() is not None:
            raise ValueError("Package integrity check failed")
    pending.replace(output)
    print(json.dumps({"package": str(output), "instanceFolder": str(instance),
                      "version": manifest["version"], "bytes": output.stat().st_size}))


if __name__ == "__main__":
    main()
