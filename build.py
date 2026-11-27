import glob
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import zipfile

VERSION = "1.1.0"
JAR_NAME = f"dmz_spellbooks_regen_fix-{VERSION}.jar"
PROJ = os.path.dirname(os.path.abspath(__file__))
CNW = 0x08000000 if os.name == "nt" else 0


def resolve_jdk_tool(name: str) -> str:
    ext = ".exe" if os.name == "nt" else ""
    jdk_bin = os.environ.get("JDK17_BIN")
    if jdk_bin:
        cand = os.path.join(jdk_bin, name + ext)
        if os.path.exists(cand):
            return cand
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        cand = os.path.join(java_home, "bin", name + ext)
        if os.path.exists(cand):
            return cand
    if os.name == "nt":
        cand = os.path.join(r"C:\Program Files\Java\jdk-17\bin", name + ext)
        if os.path.exists(cand):
            return cand
    found = shutil.which(name)
    if found:
        return found
    raise FileNotFoundError(f"Could not locate JDK 17 tool '{name}'. Set JDK17_BIN or JAVA_HOME.")


def find_mod_jar(mods_dir: str, pattern: str, env_var: str) -> str:
    override = os.environ.get(env_var)
    if override and os.path.exists(override):
        return override
    matches = sorted(glob.glob(os.path.join(mods_dir, pattern)))
    if not matches:
        raise FileNotFoundError(f"Missing {pattern} in {mods_dir}. Set {env_var} or MODS_DIR.")
    return matches[-1]


JAVAC = resolve_jdk_tool("javac")
JAVA = resolve_jdk_tool("java")

HOME = os.path.expanduser("~")
CF_INSTALL = os.environ.get("CF_INSTALL", os.path.join(HOME, "curseforge", "minecraft", "Install"))
CF_LIB = os.environ.get("CF_LIBRARIES", os.path.join(CF_INSTALL, "libraries"))
MODS_DIR = os.environ.get(
    "MODS_DIR",
    os.path.join(HOME, "curseforge", "minecraft", "Instances", "Dragon Mine Z - Definitive Edition", "mods"),
)

JAR_DMZ = find_mod_jar(MODS_DIR, "dragonminez-*.jar", "DRAGONMINEZ_JAR")
JAR_IRONS = find_mod_jar(MODS_DIR, "irons_spellbooks-*.jar", "IRONS_SPELLBOOKS_JAR")
JAR_DMZ_SB = find_mod_jar(MODS_DIR, "dmz_spellbooks-*.jar", "DMZ_SPELLBOOKS_JAR")


def collect_classpath():
    jars = [
        os.path.join(CF_LIB, "net", "minecraft", "client", "1.20.1-20230612.114412", "client-1.20.1-20230612.114412-srg.jar"),
        os.path.join(CF_LIB, "net", "minecraft", "client", "1.20.1-20230612.114412", "client-1.20.1-20230612.114412-extra.jar"),
        os.path.join(CF_LIB, "net", "minecraftforge", "forge", "1.20.1-47.4.28", "forge-1.20.1-47.4.28-universal.jar"),
        os.path.join(CF_LIB, "net", "minecraftforge", "fmlcore", "1.20.1-47.4.28", "fmlcore-1.20.1-47.4.28.jar"),
        os.path.join(CF_LIB, "net", "minecraftforge", "javafmllanguage", "1.20.1-47.4.28", "javafmllanguage-1.20.1-47.4.28.jar"),
        os.path.join(CF_LIB, "net", "minecraftforge", "mclanguage", "1.20.1-47.4.28", "mclanguage-1.20.1-47.4.28.jar"),
        JAR_DMZ,
        JAR_IRONS,
        JAR_DMZ_SB,
    ]

    for jp in (
        os.path.join(CF_INSTALL, "versions", "forge-47.4.28", "forge-47.4.28.json"),
        os.path.join(CF_INSTALL, "versions", "1.20.1", "1.20.1.json"),
    ):
        if os.path.exists(jp):
            with open(jp, encoding="utf-8") as f:
                data = json.load(f)
            for lib in data.get("libraries", []):
                p = lib.get("downloads", {}).get("artifact", {}).get("path")
                if p:
                    full = os.path.join(CF_LIB, p.replace("/", os.sep))
                    if os.path.exists(full) and os.path.getsize(full) > 0:
                        jars.append(full)

    seen = set()
    dedup = []
    for p in jars:
        if not os.path.exists(p):
            continue
        norm = os.path.normcase(os.path.abspath(p))
        if norm not in seen:
            seen.add(norm)
            dedup.append(p)
    return dedup


def collect_java_files(root):
    files = []
    for dirpath, _, filenames in os.walk(root):
        for name in sorted(filenames):
            if name.endswith(".java"):
                files.append(os.path.join(dirpath, name))
    return files


def compile_main(cp):
    out_dir = os.path.join(PROJ, "build", "classes", "main")
    shutil.rmtree(out_dir, ignore_errors=True)
    os.makedirs(out_dir, exist_ok=True)
    srcs = collect_java_files(os.path.join(PROJ, "src", "main", "java"))
    print(f"[1/4] Compiling {len(srcs)} main Java sources...")
    r = subprocess.run(
        [JAVAC, "--release", "17", "-proc:none", "-encoding", "UTF-8", "-cp", os.pathsep.join(cp), "-d", out_dir] + srcs,
        capture_output=True, text=True, creationflags=CNW,
    )
    if r.stdout:
        print(r.stdout)
    if r.stderr:
        print(r.stderr)
    if r.returncode != 0:
        raise RuntimeError(f"Main compilation failed (rc={r.returncode})")
    print("      Main compilation OK!")
    return out_dir


def package_jar(classes_dir):
    libs_dir = os.path.join(PROJ, "build", "libs")
    os.makedirs(libs_dir, exist_ok=True)
    jar_path = os.path.join(libs_dir, JAR_NAME)
    res_root = os.path.join(PROJ, "src", "main", "resources")
    manifest_src = os.path.join(res_root, "META-INF", "MANIFEST.MF")
    print(f"[2/4] Packaging {jar_path}...")
    with zipfile.ZipFile(jar_path, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.write(manifest_src, "META-INF/MANIFEST.MF")
        for root, _, files in os.walk(res_root):
            for f in sorted(files):
                full = os.path.join(root, f)
                arc = os.path.relpath(full, res_root).replace("\\", "/")
                if arc == "META-INF/MANIFEST.MF":
                    continue
                zf.write(full, arc)
        for root, _, files in os.walk(classes_dir):
            for f in sorted(files):
                full = os.path.join(root, f)
                arc = os.path.relpath(full, classes_dir).replace("\\", "/")
                zf.write(full, arc)
    sha256 = hashlib.sha256(open(jar_path, "rb").read()).hexdigest()
    print(f"      Packaged OK ({os.path.getsize(jar_path):,} bytes, SHA-256: {sha256})")
    return jar_path


def verify_jar_metadata(jar_path):
    print("[3/4] Verifying JAR metadata and Mixin config...")
    with zipfile.ZipFile(jar_path, "r") as zf:
        names = zf.namelist()
        assert names.count("META-INF/MANIFEST.MF") == 1
        for required in (
            "META-INF/mods.toml",
            "dmzspellbooksfix.mixins.json",
            "pack.mcmeta",
            "com/dmzspellbooksfix/DmzSpellbooksFixHelpers.class",
            "com/dmzspellbooksfix/mixin/UtilsMixin.class",
            "com/dmzspellbooksfix/mixin/TechniqueTimingBridgeMixin.class",
        ):
            assert required in names, f"Missing {required} in {jar_path}"
        pack_meta = json.loads(zf.read("pack.mcmeta").decode("utf-8"))
        assert pack_meta.get("pack", {}).get("pack_format") == 15
        mixins_json = json.loads(zf.read("dmzspellbooksfix.mixins.json").decode("utf-8"))
        for mx in (
            "MagicManagerMixin",
            "ServerPlayerEventsMixin",
            "DmzIronsIntegrationEventsMixin",
            "UtilsMixin",
            "TechniqueTimingBridgeMixin",
        ):
            assert mx in mixins_json["mixins"], f"Missing mixin {mx}"
    print("      JAR metadata & Mixin config verified!")


def run_tests(cp, jar_path):
    test_out = os.path.join(PROJ, "build", "classes", "test")
    shutil.rmtree(test_out, ignore_errors=True)
    os.makedirs(test_out, exist_ok=True)
    test_srcs = collect_java_files(os.path.join(PROJ, "src", "test", "java"))
    test_cp = [jar_path] + cp
    print("[4/4] Compiling & running unit tests...")
    r1 = subprocess.run(
        [JAVAC, "--release", "17", "-proc:none", "-encoding", "UTF-8", "-cp", os.pathsep.join(test_cp), "-d", test_out] + test_srcs,
        capture_output=True, text=True, creationflags=CNW,
    )
    if r1.returncode != 0:
        print(r1.stdout, r1.stderr)
        raise RuntimeError("Test compilation failed!")

    run_cp = [test_out, jar_path, os.path.join(MODS_DIR, "*")] + cp
    with tempfile.TemporaryDirectory() as tmpdir:
        tmp_cfg = os.path.join(tmpdir, "dmz_spellbooks_regen_fix-common.toml")
        cmd = [JAVA, "-cp", os.pathsep.join(run_cp), "VerifyFix", tmp_cfg]
        dmz_cfg = os.path.join(os.path.dirname(MODS_DIR), "config", "dmz_spellbooks-common.toml")
        if os.path.exists(dmz_cfg):
            cmd.append(dmz_cfg)
        r2 = subprocess.run(cmd, capture_output=True, text=True, creationflags=CNW)
        if r2.stdout:
            print("     ", r2.stdout.strip())
        if r2.stderr:
            print("     ", r2.stderr.strip())
        if r2.returncode != 0:
            raise RuntimeError(f"Tests failed (rc={r2.returncode})")


if __name__ == "__main__":
    cp = collect_classpath()
    classes_dir = compile_main(cp)
    jar_path = package_jar(classes_dir)
    verify_jar_metadata(jar_path)
    run_tests(cp, jar_path)
    print("\nDMZ SPELLBOOKS REGEN FIX v1.1.0 BUILD + VERIFICATION + TESTS: 100% GREEN!")
