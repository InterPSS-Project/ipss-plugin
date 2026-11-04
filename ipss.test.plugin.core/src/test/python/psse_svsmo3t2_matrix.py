"""Cold-capture model-specific synthetic SVSMO3T2 discriminator profiles."""
import argparse
import json
import subprocess
import sys
from pathlib import Path

PROFILES = {
    "short-block-sensor-bypass": ["--fault-ohms", "10", "--fault-clear", ".055", "--stop", ".3", "--con", "2", "0"],
    "long-block-sensed": ["--fault-ohms", "10", "--fault-clear", ".2", "--stop", ".4"],
    "short-overvoltage-zero-delay": ["--fault-ohms", "-1000", "--fault-clear", ".055", "--stop", ".3", "--con", "20", "1.01", "--con", "21", "1.02", "--con", "42", "0"],
    "short-overvoltage-block-half": ["--step", ".00025", "--fault-ohms", "-1000", "--fault-clear", ".055", "--stop", ".3", "--con", "20", "1.01", "--con", "21", "1.02"],
    "short-overvoltage-block": ["--fault-ohms", "-1000", "--fault-clear", ".055", "--stop", ".3", "--con", "20", "1.01", "--con", "21", "1.02"],
    "short-block": ["--fault-ohms", "10", "--fault-clear", ".055", "--stop", ".3"],
    "short-block-zero-delay": ["--fault-ohms", "10", "--fault-clear", ".055", "--stop", ".3", "--con", "42", "0"],
    "slow-reset-block-zero-deadband": ["--icon", "2", "1", "--con", "12", ".04", "--con", "13", ".2", "--con", "14", "0", "--fault-ohms", "10"],
    "slow-reset-block-double-deadband": ["--icon", "2", "1", "--con", "12", ".04", "--con", "13", ".2", "--con", "14", ".02", "--fault-ohms", "10"],
    "flat": ["--profile", "flat"],
    "fault": [],
    "half-fault": ["--step", ".00025"],
    "reference-positive": ["--profile", "reference-step"],
    "reference-negative": ["--profile", "reference-step", "--reference-change", "-.03"],
    "lead-lags": ["--con", "1", ".02", "--con", "27", ".01", "--con", "28", ".03"],
    "slow-reset": ["--icon", "2", "1", "--con", "12", ".04", "--con", "13", ".2"],
    "slow-reset-limits": ["--icon", "2", "1", "--con", "12", "2", "--con", "13", "2",
                          "--con", "15", ".02", "--con", "16", "-.02"],
    "nonlinear-droop": ["--icon", "3", "1", "--con", "33", ".03", "--con", "34", ".05"],
    "short-rating": ["--con", "23", ".01"],
    "short-rating-no-ov": ["--con", "23", ".01", "--con", "20", "1.4", "--con", "21", "1.5"],
    "short-rating-wide-ov": ["--con", "23", ".01", "--con", "20", "1.05", "--con", "21", "1.35"],
    "short-rating-narrow-ov": ["--con", "23", ".01", "--con", "21", "1.2"],
    "short-rating-high-multiplier": ["--con", "23", ".01", "--con", "17", "1.5"],
    "thermal-rating": ["--con", "29", ".0002", "--con", "30", ".001", "--con", "31", ".00005"],
    "thermal-active": ["--con", "23", "0", "--con", "29", ".0002", "--con", "30", ".001", "--con", "31", ".00005"],
    "thermal-active-half": ["--step", ".00025", "--con", "23", "0", "--con", "29", ".0002",
                            "--con", "30", ".001", "--con", "31", ".00005"],
    "thermal-no-ov": ["--con", "23", "0", "--con", "29", ".0002", "--con", "30", ".001", "--con", "31", ".00005",
                      "--con", "20", "1.4", "--con", "21", "1.5"],
    "slow-reset-block": ["--icon", "2", "1", "--con", "12", ".04", "--con", "13", ".2", "--fault-ohms", "10"],
    "deadband-held": ["--profile", "reference-step", "--reference-change", ".001",
                      "--con", "9", ".005", "--con", "10", "2", "--con", "11", ".01"],
    "deadband-release": ["--con", "9", ".005", "--con", "10", "2", "--con", "11", ".01"],
    "undervoltage-block": ["--fault-ohms", "10"],
    "overvoltage-trip": ["--fault-ohms", "-100", "--con", "24", ".01"],
    "overvoltage-trip-half": ["--step", ".00025", "--fault-ohms", "-100", "--con", "24", ".01"],
    "overvoltage-trip-mild": ["--fault-ohms", "-1000", "--con", "22", "1.02", "--con", "24", ".01"],
    "overvoltage-trip-mild-half": ["--step", ".00025", "--fault-ohms", "-1000", "--con", "22", "1.02", "--con", "24", ".01"],
    "overvoltage-no-trip": ["--fault-ohms", "-1000", "--con", "22", "1.02", "--con", "24", ".08"],
    "overvoltage-trip-rebuild": ["--fault-ohms", "-1000", "--con", "22", "1.02", "--con", "24", ".01", "--trip-rebuild-time", ".065"],
    "overvoltage-no-trip-rebuild": ["--fault-ohms", "-1000", "--con", "22", "1.02", "--con", "24", ".08", "--trip-rebuild-time", ".065"],
    "bypasses": ["--con", "1", "0", "--con", "2", "0", "--con", "7", "0",
                 "--con", "27", "0", "--con", "28", "0"],
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
            subprocess.run([sys.executable, str(probe), "--output", str(output), *PROFILES[profile]], check=True)
            manifest = json.loads((output / "manifest.json").read_text(encoding="utf8"))
            hashes.append(manifest["canonical_sha256"])
            entries.append({"profile": profile, "repeat": repeat, "path": output.name,
                            "canonical_sha256": hashes[-1], "raw_sha256": manifest["files"]["reference.csv"],
                            "step_s": manifest["step_s"]})
            (args.output / "matrix.json").write_text(json.dumps(entries, indent=2) + "\n", encoding="utf8")
        if hashes[0] != hashes[1]:
            raise RuntimeError(f"Non-repeating trajectory: {profile}")


if __name__ == "__main__":
    main()
