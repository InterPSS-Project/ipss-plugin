"""Cold synthetic SMIB references for SVSMO3T2 shunt coordination."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

BASE = ["--profile", "reference-step", "--con", "26", ".9", "--con", "37", ".01",
        "--con", "38", ".25", "--con", "39", ".03", "--con", "40", ".35", "--con", "41", ".1"]
PROFILES = {
    "close-capacitor": ["--mss-case", "capacitor-off", "--reference-change", ".04"],
    "open-capacitor": ["--mss-case", "capacitor-on", "--reference-change", "-.04", "--con", "41", ".15"],
    "open-reactor": ["--mss-case", "reactor-on", "--reference-change", ".04", "--con", "40", ".5"],
    "close-reactor": ["--mss-case", "reactor-off", "--reference-change", "-.04", "--con", "41", ".25"],
    "capacitor-discharge": ["--mss-case", "capacitor-off", "--reference-change", ".04",
                            "--reference-point", ".25", "-.04", "--reference-point", ".35", ".04"],
    "capacitor-pair": ["--mss-case", "capacitor-pair", "--reference-change", ".08"],
    "cooling-slot-skip": ["--mss-case", "capacitor-transfer", "--reference-change", "-.04",
                          "--reference-point", ".15", ".04", "--con", "41", ".15"],
    "mixed-removal-first": ["--mss-case", "mixed", "--reference-change", ".04", "--con", "40", ".5"],
    "mixed-insertion-first": ["--mss-case", "mixed-reverse", "--reference-change", ".04", "--con", "40", ".5"],
    "latched-breaker": ["--mss-case", "capacitor-off", "--reference-change", ".04", "--reference-point", ".082", "0"],
    "zero-delays": ["--mss-case", "capacitor-off", "--reference-change", ".04", "--con", "37", "0", "--con", "39", "0"],
    "conductance": ["--mss-case", "capacitor-off", "--reference-change", ".04", "--mss-conductance-mw", ".5"],
    "remote-bank": ["--mss-case", "capacitor-off", "--reference-change", ".04", "--mss-bus", "2"],
    "close-reactor-pair": ["--mss-case", "reactor-pair-off", "--reference-change", "-.08", "--con", "41", ".25"],
    "open-reactor-pair": ["--mss-case", "reactor-pair-on", "--reference-change", ".08", "--con", "40", ".6"],
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--profiles", nargs="+", choices=PROFILES, default=list(PROFILES))
    args = parser.parse_args()
    if args.output.exists():
        parser.error("output must be a new directory")
    args.output.mkdir(parents=True)
    (args.output / "matrix-probe.py").write_bytes(Path(__file__).read_bytes())
    probe = Path(__file__).with_name("psse_svsmo3t2_probe.py")
    entries = []
    for profile in args.profiles:
        hashes = []
        for repeat in ("a", "b"):
            output = args.output / (profile + "-" + repeat)
            print(f"Capturing {profile} {repeat}", flush=True)
            subprocess.run([sys.executable, str(probe), "--output", str(output), *BASE, *PROFILES[profile]], check=True)
            manifest = json.loads((output / "manifest.json").read_text(encoding="utf8"))
            observer = hashlib.sha256((output / "mss-status.csv").read_bytes()).hexdigest()
            hashes.append((manifest["canonical_sha256"], observer))
            entries.append({"profile": profile, "repeat": repeat, "path": output.name,
                            "canonical_sha256": hashes[-1][0], "observer_sha256": observer})
            (args.output / "matrix.json").write_text(json.dumps(entries, indent=2) + "\n", encoding="utf8")
        if hashes[0] != hashes[1]:
            raise RuntimeError(f"Non-repeating defined trajectory or shunt status: {profile}")


if __name__ == "__main__":
    main()
