"""Package unmodified CIMHub files from a local checkout; no network access required.

Usage: python package_fixtures.py /path/to/CIMHub
Expected upstream commit: 5ba4c63fa525108928942d564fddc022c3fb031f.
"""
import csv
import gzip
import hashlib
import json
import subprocess
import sys
import zipfile
from pathlib import Path
from xml.etree import ElementTree

COMMIT = "5ba4c63fa525108928942d564fddc022c3fb031f"
SOURCES = {
    "ieee13x": "CPYDAR/xml/ieee13x.xml",
    "IEEE13_PV": "OEDI/xml/IEEE13_PV.xml",
    "IEEE123": "model_output_tests/IEEE123.xml",
    "IEEE123_PV": "model_output_tests/IEEE123_PV.xml",
    "IEEE390par": "lv_network/lvn_examples.zip!base/IEEE390par.xml",
    "IEEE390_PV": "OEDI/xml/IEEE390_PV.xml",
    "R2_12_47_2": "model_output_tests/R2_12_47_2.xml",
    "Transactive": "model_output_tests/Transactive.xml",
}


def package(checkout):
    revision = subprocess.check_output(["git", "-C", str(checkout), "rev-parse", "HEAD"], text=True).strip()
    if revision != COMMIT:
        raise ValueError(f"Expected CIMHub {COMMIT}, got {revision}")
    output = Path(__file__).resolve().parent
    manifest = {"repository": "https://github.com/GRIDAPPSD/CIMHub", "commit": COMMIT, "cases": []}
    rows = []
    for name, source in SOURCES.items():
        if "!" in source:
            archive, member = source.split("!", 1)
            with zipfile.ZipFile(checkout / archive) as z:
                data = z.read(member)
        else:
            data = (checkout / source).read_bytes()
        digest = hashlib.sha256(data).hexdigest()
        nodes = len(ElementTree.fromstring(data).findall("{http://iec.ch/TC57/CIM100#}ConnectivityNode"))
        (output / (name + ".xml.gz")).write_bytes(gzip.compress(data, mtime=0))
        rows.append((name, digest, nodes))
        manifest["cases"].append({"name": name, "source": source, "sha256_xml": digest,
                                   "xml_bytes": len(data), "connectivity_nodes": nodes,
                                   "source_url": f"https://github.com/GRIDAPPSD/CIMHub/blob/{COMMIT}/{source.split('!')[0]}"})
    with (output / "cases.csv").open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(("name", "sha256_xml", "connectivity_nodes"))
        writer.writerows(rows)
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (output / "CIMHub-LICENSE.md").write_bytes((checkout / "license.md").read_bytes())


if __name__ == "__main__":
    package(Path(sys.argv[1]))
