"""Check cold auxiliary-source captures without changing samples or event ordering."""
import argparse
import csv
import hashlib
import json
from pathlib import Path


def check_rows(rows, index):
    assert rows and index in (1, 2)
    source = [float(row["AUX_VAR0"]) for row in rows]
    assert max(map(abs, source)) > 1e-4, "Auxiliary source was not excited"
    for row, value in zip(rows, source):
        expected = value if index == 1 else 0.
        assert float(row["VAR0"]) == expected, "Rectifier auxiliary routing mismatch"
        assert float(row["VAR1"]) == expected, "Inverter auxiliary routing mismatch"
    return dict(rows=len(rows), source_peak=max(map(abs, source)), route_index=index)


def check_batch(folder):
    results = []
    for index in (1, 2):
        directories = sorted(folder.glob(f"*aux{index}-*"))
        assert len(directories) == 2, "One cold pair per signal index required"
        contents = []
        for directory in directories:
            manifest = json.loads((directory/"manifest.json").read_text())
            assert manifest["status"] == "complete" and manifest["initialization_and_network_valid"]
            assert manifest["settings"]["form"] == "legacy"
            assert manifest["settings"]["auxiliary_index"] == index
            content = (directory/"native.csv").read_bytes()
            assert hashlib.sha256(content).hexdigest() == manifest["files"]["native.csv"]
            contents.append(content)
        assert contents[0] == contents[1], "Cold complete trajectories differ"
        rows = list(csv.DictReader(contents[0].decode().splitlines()))
        result = check_rows(rows, index)
        result["sha256"] = hashlib.sha256(contents[0]).hexdigest()
        result["event_duplicate_rows"] = sum(rows[i]["time_s"] == rows[i-1]["time_s"] for i in range(1,len(rows)))
        results.append(result)
    return results


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("folder", type=Path)
    args = parser.parse_args()
    print(json.dumps(check_batch(args.folder), indent=2))
