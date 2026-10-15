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

    required_docs = (
        "README.md", "PRISM.md", "AGENTS.md", "AUTOMATION.md", "AUTOMATION-MOTION.md", "COMBAT-ESP.md", "SCHEMATIC-CONSTRUCTION.md", "SAFETY.md", "UI-OVERHAUL-PLAN.md", "UI-OVERHAUL.md",
        "UI-REFERENCES.md", "HOME-SCREEN.md", "HOME-REFERENCES.md", "MODULE-TESTS.md", "STUDIO-COMPLETE.md",
        "SERVER-PRIVACY.md", "LIQUID-GLASS.md", "MOVING-PCS.md", "VERSION-HISTORY.md", "GITHUB-BACKUP.md",
        "ESP-READABILITY.md", "GAMEPLAY-SETTINGS.md", "XRAY.md", "MODULES.md",
        "MODULES.json", "ROADMAP.md", "RESEARCH.md", "RESEARCH-VISUALS.md",
        "RESEARCH-COSMETICS.md", "VALIDATION.md", "LICENSE", "FONT-LICENSE.txt",
        "THIRD-PARTY.md", "DEPENDENCY-SOURCES.md", "LATO-OFL.txt",
    )
    optional_logs = ("VALIDATION.log", "PIXEL-VALIDATION.log")
    updater = repo / "tools/prism/Update-Analyzer.ps1"
    native_evidence = Path(f"production-validation/native-{manifest['version']}/acceptance.json")
    reference_dir = Path("design/ui-references")
    reference_images = (
        "reference-1-temple-simple.png", "reference-2-temple-glass.png",
        "reference-3-rounded-cards.png", "reference-4-red-workspace.png",
        "reference-5-diablo.png",
    )
    home_reference_dir = Path("design/home-references")
    home_reference_images = (
        "reference-1-panorama.jpg", "reference-2-options.jpg", "reference-3-orbit.jpg",
        "reference-4-worlds.jpg", "reference-5-servers.jpg",
    )
    required_files = [repo / doc for doc in required_docs] + [updater, repo / native_evidence]
    dependency_source = repo / "dependencies/fabric-api-0.119.4+1.21.4-source.zip"
    required_files.append(dependency_source)
    required_files.extend(repo / reference_dir / name for name in reference_images)
    required_files.extend(repo / home_reference_dir / name for name in home_reference_images)
    missing = [str(path.relative_to(repo)) for path in required_files
               if not path.is_file() or path.stat().st_size == 0]
    if missing:
        raise ValueError("Required packaging files are missing or empty: " + ", ".join(missing))
    omitted_logs = [name for name in optional_logs if not (repo / name).is_file()]
    with zipfile.ZipFile(fabric) as archive:
        fabric_license = archive.read("LICENSE-fabric-api")
    if not fabric_license:
        raise ValueError("Fabric API license is empty")
    if hashlib.sha256(dependency_source.read_bytes()).hexdigest() != "4bacc01e2d54d026ac6e5de1ed4802166ca6f032ac888ca126ee22c0f7407c30":
        raise ValueError("Pinned Fabric API source archive checksum mismatch")
    with zipfile.ZipFile(dependency_source) as archive:
        if archive.testzip() is not None:
            raise ValueError("Fabric API source archive integrity check failed")

    # Preflight redistribution inputs before creating any staging instance files.
    instance = args.instance_folder.resolve()
    if instance.exists() and any(instance.iterdir()):
        raise ValueError("Refusing to overwrite a staging instance")
    instance.mkdir(parents=True, exist_ok=True)
    mods = instance / "minecraft" / "mods"
    mods.mkdir(parents=True)
    shutil.copyfile(analyzer, mods / artifact)
    shutil.copyfile(fabric, mods / fabric.name)
    scripts = instance / "analyzer-updater"
    scripts.mkdir()
    shutil.copyfile(updater, scripts / "Update-Analyzer.ps1")
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
    for doc in required_docs + tuple(name for name in optional_logs if name not in omitted_logs):
        shutil.copyfile(repo / doc, instance / doc)
    (instance / native_evidence.parent).mkdir(parents=True)
    shutil.copyfile(repo / native_evidence, instance / native_evidence)
    # Unmodified comparison references accompany the design docs, not runtime assets.
    packaged_references = instance / reference_dir
    packaged_references.mkdir(parents=True)
    for name in reference_images:
        shutil.copyfile(repo / reference_dir / name, packaged_references / name)
    packaged_home_references = instance / home_reference_dir
    packaged_home_references.mkdir(parents=True)
    for name in home_reference_images:
        shutil.copyfile(repo / home_reference_dir / name, packaged_home_references / name)
    screenshots = instance / "screenshots"
    screenshots.mkdir()
    shutil.copyfile(repo / "screenshots/README.md", screenshots / "README.md")
    for capture in sorted((repo / "screenshots").glob("*.png")):
        if capture.name in {"analyzer-ui.png", "analyzer-gui-classic.png"} or capture.name.startswith(("analyzer-esp-", "analyzer-bot-", "analyzer-automation-", "analyzer-native-", "analyzer-safety-", "analyzer-gameplay-settings-", "analyzer-ui-overhaul-", "analyzer-studio-", "analyzer-home-", "analyzer-module-sweep-", "analyzer-refresh-", "analyzer-watermark-", "analyzer-liquid-", "analyzer-privacy-")):
            shutil.copyfile(capture, screenshots / capture.name)
    licenses = instance / "licenses"
    licenses.mkdir()
    (licenses / "FABRIC-API-LICENSE.txt").write_bytes(fabric_license)
    (instance / "dependencies").mkdir()
    shutil.copyfile(dependency_source, instance / "dependencies" / dependency_source.name)
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
                      "version": manifest["version"], "bytes": output.stat().st_size,
                      "omittedOptionalFiles": omitted_logs}))


if __name__ == "__main__":
    main()
