"""Clean-build the retained Case 2 source checkpoint, without frozen classes.

Run through tools/run_bounded.py. This builds the benchmark configuration;
it does not qualify the repository regression suite or reference accuracy.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

from replay_case2_fast_checkpoint import destination, digest, verified


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    root = (repo / args.output).resolve()
    if root.exists() or not root.is_relative_to((repo / "target").resolve()):
        parser.error("Output must be a new directory under target")
    if shutil.disk_usage(repo).free < 2 * 1024**3 + 128 * 1024**2:
        raise RuntimeError("Need the 2 GiB reserve plus 128 MiB for this source build")
    index = json.loads((repo / "docs/benchmarks/checkpoints/case2-recovered-source-2026-10-21.json").read_text())
    manifest_path = verified(repo / index["private_manifest"], index["private_manifest_digest"])
    manifest = json.loads(manifest_path.read_text())
    archive = verified(manifest_path.parent / "source-evidence.zip", index["archive"])
    base = json.loads((repo / "docs/benchmarks/checkpoints/case2-recovered-fast-2026-10-21.json").read_text())
    dependencies = json.loads(verified(repo / base["private_manifest"], base["private_manifest_digest"]).read_text())["external_dependencies"]
    jars = []
    for item in dependencies:
        if item["maven_repository_path"].startswith("com/interpss/"):
            continue
        jars.append(str(verified(destination(Path.home() / ".m2/repository", item["maven_repository_path"]),
                                 {k: item[k] for k in ("bytes", "sha256")})))
    root.mkdir()
    with zipfile.ZipFile(archive) as bundle:
        for name, expected in manifest["sources"].items():
            if not name.startswith(("core-src/", "plugin-src/", "harness-src/", "resources/")):
                raise ValueError("Unexpected source entry")
            data = bundle.read(name)
            if len(data) != expected["bytes"] or hashlib.sha256(data).hexdigest() != expected["sha256"]:
                raise ValueError("Source entry differs from manifest")
            path = destination(root, name)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    outputs = []
    for module in ("core", "plugin", "harness"):
        output = root / (module + "-classes")
        sources = sorted((root / (module + "-src")).rglob("*.java"))
        if len(sources) != manifest["source_counts"][module]:
            raise RuntimeError("Incomplete module source set")
        arguments = ["-proc:none", "-encoding", "UTF-8", "-g", "-cp",
                     ";".join([*map(str, reversed(outputs)), *jars]), "-d", str(output), *map(str, sources)]
        argfile = root / (module + "-javac.args")
        argfile.write_text("\n".join('"' + a.replace("\\", "/") + '"' for a in arguments), encoding="utf-8")
        subprocess.run(["javac", "-J-Xmx1g", "@" + str(argfile)], check=True)
        outputs.append(output)
    inventory = {}
    for output in outputs:
        for path in output.rglob("*.class"):
            inventory[path.relative_to(root).as_posix()] = digest(path)
    (root / "build.json").write_text(json.dumps(dict(status="compiled", frozen_main_classes_used=False,
        source_archive=index["archive"], classes=inventory), indent=2))
    print(json.dumps(dict(status="compiled", source_counts=manifest["source_counts"], classes=len(inventory))))


if __name__ == "__main__":
    main()
