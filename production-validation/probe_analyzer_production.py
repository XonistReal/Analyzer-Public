"""Prepare or run a local, offline-name production Fabric startup probe.

Uses the actual remapped Analyzer artifact and unchanged official mods. It never
opens user Prism, reads account credentials, joins a world or contacts a game server.
The tiny test-only mod checks actual production namespaces, opens Automation,
captures the rendered frame, and asks Minecraft to stop normally.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import time
import uuid
import zipfile

ROOT = Path(__file__).resolve().parent.parent
WORK = ROOT / "work"
JDK = WORK / "jdk21/jdk-21.0.12.1+1/bin"
CACHE = WORK / "gradle-cache/caches"
LIBRARY_CACHE = CACHE / "modules-2/files-2.1"
LOOM = CACHE / "fabric-loom"
BOT_SHA = "bfb79c3cdb8fe1f7697999b1f9c7f08ea29775f3b69d48ad90c387690ee67b05"

JAVA = r'''
package dev.analyzer.probe;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Original test-only probe. No Minecraft types are linked in its descriptors. */
public final class ProductionProbe implements ClientModInitializer {
    private final AtomicBoolean finished = new AtomicBoolean();
    private int stage, stable;
    private String screenshot;
    private Object client;
    private Class<?> minecraft;
    public void onInitializeClient() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) throw new IllegalStateException("Expected real production Fabric environment");
        System.out.println("ANALYZER_PRODUCTION_PROBE_ENVIRONMENT: development=false");
        Thread thread = new Thread(() -> {
            try {
                minecraft = Class.forName("net.minecraft.class_310");
                client = minecraft.getMethod("method_1551").invoke(null);
                for (int i=0; i<180 && !finished.get(); i++) {
                    ((Executor)client).execute(this::inspect);
                    Thread.sleep(500);
                }
                if (!finished.get()) {
                    System.out.println("ANALYZER_PRODUCTION_PROBE_FAILED: title/automation rendering deadline");
                    ((Executor)client).execute(this::stop);
                }
            } catch (Throwable problem) {
                problem.printStackTrace();
                System.out.println("ANALYZER_PRODUCTION_PROBE_FAILED: " + problem.getClass().getName());
            }
        }, "Analyzer isolated production probe");
        thread.setDaemon(true); thread.start();
    }
    private void inspect() {
        if (finished.get()) return;
        try {
            Object screen = minecraft.getField("field_1755").get(client);
            if (stage==0) {
                if (screen==null || !screen.getClass().getName().equals("net.minecraft.class_442")) {stable=0; return;}
                if (++stable<4) return;
                boolean expected = Boolean.getBoolean("analyzer.probeBaritone");
                boolean loaded = FabricLoader.getInstance().isModLoaded("baritone");
                Class<?> engineClass = Class.forName("dev.analyzer.automation.AutomationEngine");
                Object controller = engineClass.getField("INSTANCE").get(null);
                boolean available = (Boolean)engineClass.getMethod("available").invoke(controller);
                if (loaded!=expected || available!=expected) throw new IllegalStateException("Optional dependency state mismatched: loaded="+loaded+", available="+available);
                if (expected && !FabricLoader.getInstance().isModLoaded("dev_babbaj_nether-pathfinder")) throw new IllegalStateException("Official nested helper was not loaded");
                Class<?> coreClass = Class.forName("dev.analyzer.core.AnalyzerCore");
                Object core = Class.forName("dev.analyzer.AnalyzerClient").getField("CORE").get(null);
                int count = ((java.util.List<?>)coreClass.getMethod("modules").invoke(core)).size();
                if (count!=123) throw new IllegalStateException("Expected 123 production modules");
                Object view = engineClass.getMethod("snapshot").invoke(controller);
                if ((Boolean)view.getClass().getMethod("baritoneAvailable").invoke(view)!=expected) throw new IllegalStateException("Automation screen dependency indicator mismatched");
                System.out.println("ANALYZER_PRODUCTION_PROBE_TITLE: production title rendered; modules="+count+", baritone="+available+", nestedHelper="+FabricLoader.getInstance().isModLoaded("dev_babbaj_nether-pathfinder"));
                Class<?> screenClass = Class.forName("net.minecraft.class_437");
                Class<?> automation = Class.forName("dev.analyzer.ui.AutomationScreen");
                Class<?> controllerType = Class.forName("dev.analyzer.ui.AutomationScreen$Controller");
                Object menu = automation.getConstructor(screenClass,controllerType,coreClass).newInstance(screen,controller,core);
                minecraft.getMethod("method_1507",screenClass).invoke(client,menu);
                screenshot = expected ? "analyzer-production-baritone.png" : "analyzer-production-without-baritone.png";
                stable=0;stage=1;
            } else if (stage==1) {
                if (screen==null || !screen.getClass().getName().equals("dev.analyzer.ui.AutomationScreen")) throw new IllegalStateException("Expected actual Automation screen");
                if (++stable<4) return;
                int error=(Integer)Class.forName("org.lwjgl.opengl.GL11").getMethod("glGetError").invoke(null);
                if (error!=0) throw new IllegalStateException("Production GL error "+error);
                Object framebuffer = minecraft.getMethod("method_1522").invoke(client);
                File directory = (File)minecraft.getField("field_1697").get(client);
                Method save = null;
                for (Method method : Class.forName("net.minecraft.class_318").getMethods())
                    if (method.getName().equals("method_22690") && method.getParameterCount()==4) {save=method;break;}
                if (save==null) throw new IllegalStateException("Production screenshot method unavailable");
                Consumer<Object> callback = text -> System.out.println("ANALYZER_PRODUCTION_PROBE_CAPTURE_SAVED: "+screenshot);
                save.invoke(null,directory,screenshot,framebuffer,callback);
                System.out.println("ANALYZER_PRODUCTION_PROBE_SCREEN: Automation rendered; GL=0; screenshot="+screenshot);
                stable=0;stage=2;
            } else if (stage==2 && ++stable>=4) {
                System.out.println("ANALYZER_PRODUCTION_PROBE_COMPLETE: production startup and optional automation UI passed");
                finished.set(true);stop();
            }
        } catch (Throwable problem) {
            finished.set(true);problem.printStackTrace();
            System.out.println("ANALYZER_PRODUCTION_PROBE_FAILED: "+problem.getClass().getName());stop();
        }
    }
    private void stop() {
        try {minecraft.getMethod("method_1592").invoke(client);}
        catch(Throwable problem){problem.printStackTrace();}
    }
}
'''


def sha1(path):
    return hashlib.sha1(path.read_bytes()).hexdigest()


def single_jar(group, artifact, version, filename=None):
    directory = LIBRARY_CACHE / group / artifact / version
    name = filename or f"{artifact}-{version}.jar"
    matches = list(directory.rglob(name))
    if len(matches) != 1:
        raise RuntimeError(f"Expected cached {group}:{artifact}:{version}: found {len(matches)}")
    return matches[0]


def permitted(library):
    rules = library.get("rules")
    allowed = not rules
    for rule in rules or []:
        os_rule = rule.get("os", {})
        if os_rule.get("name", "windows") != "windows":
            continue
        if os_rule.get("arch", "x86_64") not in ("x86_64", "amd64"):
            continue
        allowed = rule["action"] == "allow"
    name = library["downloads"]["artifact"]["path"]
    return allowed and "-natives-windows-arm64" not in name and "-natives-windows-x86" not in name


def prepare(with_bot, analyzer):
    probe_root = WORK / "production-probe" / (("with-baritone-" if with_bot else "without-baritone-") + uuid.uuid4().hex[:8])
    probe_root.mkdir(parents=True)
    client = LOOM / "1.21.4/minecraft-client.jar"
    info = json.loads((LOOM / "1.21.4/mojang_minecraft_info.json").read_text(encoding="utf-8"))
    assert sha1(client) == info["downloads"]["client"]["sha1"], "Minecraft client cache is not the official artifact"
    index = {}
    for file in LIBRARY_CACHE.rglob("*.jar"):
        index.setdefault(file.name, []).append(file)
    libraries = []
    for library in info["libraries"]:
        if not permitted(library) or library["name"].startswith("org.ow2.asm:"):
            continue
        artifact = library["downloads"]["artifact"]
        matches = [p for p in index.get(Path(artifact["path"]).name, []) if sha1(p) == artifact["sha1"]]
        if len(matches) != 1:
            raise RuntimeError("Missing verified cached vanilla library " + artifact["path"])
        libraries.append(matches[0])
    loader = single_jar("net.fabricmc", "fabric-loader", "0.16.14")
    libraries.extend([loader, single_jar("net.fabricmc", "sponge-mixin", "0.15.5+mixin.0.8.7"),
                      single_jar("net.fabricmc", "intermediary", "1.21.4", "intermediary-1.21.4-v2.jar")])
    for artifact in ("asm", "asm-analysis", "asm-commons", "asm-tree", "asm-util"):
        libraries.append(single_jar("org.ow2.asm", artifact, "9.7.1"))
    # Loader's production remapper needs these dependencies in the launcher classpath.
    libraries.append(single_jar("net.fabricmc", "tiny-remapper", "0.10.4"))
    mapping_versions = list((LIBRARY_CACHE / "net.fabricmc/mapping-io").glob("*"))
    mapping_versions.sort(key=lambda p: p.name)
    libraries.append(single_jar("net.fabricmc", "mapping-io", mapping_versions[-1].name))
    game = probe_root / "game"
    mods = game / "mods"
    mods.mkdir(parents=True)
    # A new isolated folder would otherwise stop at Minecraft's first-launch accessibility dialog.
    (game / "options.txt").write_text("onboardAccessibility:false\nfullscreen:false\nguiScale:3\ntelemetryOptInExtra:false\n", encoding="utf-8")
    with zipfile.ZipFile(analyzer) as jar:
        assert jar.testzip() is None and json.loads(jar.read("fabric.mod.json"))["version"] == "0.5.2"
        assert b"net/minecraft/class_310" in jar.read("dev/analyzer/AnalyzerClient.class"), "Probe needs the production remapped Analyzer JAR"
    shutil.copyfile(analyzer, mods / "analyzer-0.5.2.jar")
    shutil.copyfile(WORK / "release-deps/fabric-api-0.119.4+1.21.4.jar", mods / "fabric-api-0.119.4+1.21.4.jar")
    if with_bot:
        bot = ROOT / "outputs/analyzer/third-party/baritone/baritone-api-fabric-1.13.1.jar"
        assert hashlib.sha256(bot.read_bytes()).hexdigest() == BOT_SHA
        shutil.copyfile(bot, mods / bot.name)
    source = probe_root / "src/dev/analyzer/probe/ProductionProbe.java"
    source.parent.mkdir(parents=True)
    source.write_text(JAVA, encoding="utf-8")
    classes = probe_root / "classes"
    classes.mkdir()
    subprocess.run([str(JDK / "javac.exe"), "--release", "21", "-encoding", "UTF-8", "-cp", str(loader), "-d", str(classes), str(source)], check=True)
    with zipfile.ZipFile(mods / "analyzer-production-probe.jar", "w", zipfile.ZIP_DEFLATED) as jar:
        for file in classes.rglob("*.class"):
            jar.write(file, file.relative_to(classes).as_posix())
        jar.writestr("fabric.mod.json", json.dumps({"schemaVersion": 1, "id": "analyzer-production-probe", "version": "1.0.0", "environment": "client", "entrypoints": {"client": ["dev.analyzer.probe.ProductionProbe"]}, "depends": {"fabricloader": ">=0.16.14", "analyzer": "0.5.2"}}))
    classpath = os.pathsep.join(str(p.resolve()) for p in [client, *libraries])
    args = ["-Xmx2G", "-Dfabric.development=false", "-Dfabric.gameVersion=1.21.4", f"-Danalyzer.probeBaritone={str(with_bot).lower()}",
            "-cp", classpath, "net.fabricmc.loader.impl.launch.knot.KnotClient",
            "--username", "AnalyzerProductionProbe", "--uuid", "00000000000000000000000000000001", "--accessToken", "0",
            "--version", "1.21.4", "--gameDir", str(game), "--assetsDir", str(LOOM / "assets"), "--assetIndex", "1.21.4-19",
            "--userType", "legacy", "--versionType", "release", "--width", "1280", "--height", "800"]
    argfile = probe_root / "launch.args"
    # Java argument files support quoted Windows paths when backslashes are escaped.
    argfile.write_text("\n".join(json.dumps(value) for value in args) + "\n", encoding="utf-8")
    details = {"directory": str(probe_root), "gameDirectory": str(game), "withBaritone": with_bot, "production": True,
               "analyzerJar": str(analyzer), "analyzerSha256": hashlib.sha256(analyzer.read_bytes()).hexdigest(),
               "command": [str(JDK / "java.exe"), "@" + str(argfile)], "classpathCount": len(libraries) + 1}
    (probe_root / "prepared.json").write_text(json.dumps(details, indent=2) + "\n", encoding="utf-8")
    return details


def run(details):
    directory = Path(details["directory"])
    log = directory / "startup.log"
    with log.open("w", encoding="utf-8") as output:
        process = subprocess.Popen(details["command"], cwd=details["gameDirectory"], stdout=output, stderr=subprocess.STDOUT)
        try:
            code = process.wait(timeout=120)
        except subprocess.TimeoutExpired:
            process.terminate()
            code = process.wait(timeout=15)
            raise RuntimeError(f"Production probe deadline; isolated process stopped; see {log}")
    text = log.read_text(encoding="utf-8", errors="replace")
    assert code == 0, f"Production startup failed with exit {code}; see {log}"
    assert "ANALYZER_PRODUCTION_PROBE_ENVIRONMENT: development=false" in text
    assert "ANALYZER_PRODUCTION_PROBE_COMPLETE:" in text and "ANALYZER_PRODUCTION_PROBE_FAILED:" not in text
    image = directory / "game/screenshots" / ("analyzer-production-baritone.png" if details["withBaritone"] else "analyzer-production-without-baritone.png")
    assert image.is_file(), "Production framebuffer capture was not saved"
    details["passed"] = True
    details["log"] = str(log)
    details["screenshot"] = str(image)
    (directory / "result.json").write_text(json.dumps(details, indent=2) + "\n", encoding="utf-8")
    return details


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workspace", type=Path, default=ROOT, help="Workspace containing the work/ dependency cache")
    parser.add_argument("--mode", choices=["with", "without", "both"], default="both")
    parser.add_argument("--analyzer", type=Path, help="Production remapped Analyzer JAR; defaults to the workspace build artifact")
    parser.add_argument("--run", action="store_true", help="Launch the prepared isolated startup probes sequentially")
    options = parser.parse_args()
    ROOT = options.workspace.resolve()
    WORK = ROOT / "work"
    JDK = WORK / "jdk21/jdk-21.0.12.1+1/bin"
    CACHE = WORK / "gradle-cache/caches"
    LIBRARY_CACHE = CACHE / "modules-2/files-2.1"
    LOOM = CACHE / "fabric-loom"
    analyzer = (options.analyzer or WORK / "analyzer-build/libs/analyzer-0.5.2.jar").resolve()
    results = []
    for with_bot in ([True, False] if options.mode == "both" else [options.mode == "with"]):
        details = prepare(with_bot, analyzer)
        if options.run:
            details = run(details)
        results.append(details)
        print(json.dumps({key: value for key, value in details.items() if key != "command"}, indent=2))
    target = WORK / "production-probe/latest.json"
    target.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
