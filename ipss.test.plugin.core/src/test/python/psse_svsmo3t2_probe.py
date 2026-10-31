"""Capture every published SVSMO3T2 state and diagnostic in a synthetic SMIB."""

from __future__ import annotations

import argparse
import csv
import hashlib
import itertools
import json
import re
import shutil
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]
FIXTURE = REPO / "ipss.test.plugin.core/testData/psse/v33/SVSMO3T2"
RESERVE = 2 * 1024**3
FILE_LIMIT = 512 * 1024**2


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def checked(name, result):
    error, value = result if isinstance(result, tuple) else (result, None)
    if error:
        raise RuntimeError(f"{name}: reference API error {error}")
    return value


def canonical_csv_sha256(path, excluded):
    """Hash every row of defined channels; retain the complete raw CSV separately.

    Inactive MSS slots are not initialized by the reference model. Their raw
    diagnostic cells can contain process-memory values and have no timer meaning.
    """
    digest = hashlib.sha256()
    with path.open(newline="", encoding="utf8") as stream:
        rows = csv.reader(stream)
        header = next(rows)
        selected = [i for i, name in enumerate(header) if name not in excluded]
        for row in itertools.chain((header,), rows):
            digest.update((",".join(row[i] for i in selected) + "\n").encode("utf8"))
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--inputs", type=Path, default=FIXTURE)
    parser.add_argument("--step", type=float, default=.0005)
    parser.add_argument("--stop", type=float, default=.8)
    parser.add_argument("--profile", choices=("flat", "fault", "reference-step"), default="fault")
    parser.add_argument("--con", type=float, nargs=2, action="append", default=[], metavar=("OFFSET", "VALUE"))
    parser.add_argument("--icon", type=int, nargs=2, action="append", default=[], metavar=("OFFSET", "VALUE"))
    parser.add_argument("--fault-ohms", type=float, default=100.)
    parser.add_argument("--reference-change", type=float, default=.01)
    parser.add_argument("--load-law", choices=("constant-impedance", "constant-power"), default="constant-impedance")
    args = parser.parse_args()
    if not 0 < args.step <= .01 or not .1 <= args.stop <= 20:
        parser.error("step and duration exceed the reviewed small-case budget")
    if args.output.exists():
        parser.error("output must be a new directory; immutable captures are never overwritten")
    args.output.mkdir(parents=True)
    if shutil.disk_usage(args.output).free < RESERVE + 64 * 1024**2:
        raise OSError("insufficient free space for reference capture and reserve")
    for name in ("smib.raw", "smib.dyr"):
        shutil.copy2(args.inputs / name, args.output / name)
    (args.output / "probe.py").write_bytes(Path(__file__).read_bytes())
    if args.con or args.icon:
        dyr_path = args.output / "smib.dyr"
        text = dyr_path.read_text(encoding="utf8")
        begin = text.index("'STATCOM A'")
        fields = re.findall(r"'(?:''|[^'])*'|[^,\s/]+", text[begin:])
        if len(fields) != 66:
            raise RuntimeError("unexpected fixture cardinality")
        for offset, value in args.con:
            if not offset.is_integer() or not 0 <= offset < 44:
                parser.error("CON offsets are zero-based integers in [0,43]")
            fields[22 + int(offset)] = format(value, ".12g")
        for offset, value in args.icon:
            if not 0 <= offset < 20 or offset in range(5, 20, 2):
                parser.error("ICON overrides accept only the numeric fields")
            fields[2 + offset] = str(value)
        dyr_path.write_text(text[:begin] + " ".join(fields) + " /\n", encoding="utf8")
    text = (args.output / "smib.dyr").read_text(encoding="utf8")
    fields = re.findall(r"'(?:''|[^'])*'|[^,\s/]+", text[text.index("'STATCOM A'"):])
    unused_mss_channels = [f"VAR_{13 + i}" for i in range(8) if int(fields[6 + 2 * i]) == 0]
    if int(fields[3]) == 0:
        unused_mss_channels.append("VAR_21")
    import psse3607  # noqa: F401 - exact installed API selection
    import psspy
    import dyntools
    library = Path(psspy.__file__).resolve().parents[1] / "PSSBIN/DSMDL.DLL"
    checked("initialize", psspy.psseinit(50))
    for kind in ("progress", "prompt", "report", "alert"):
        checked(kind, getattr(psspy, kind + "_output")(
            2, str(args.output / (kind + ".log")), [0, 0]))
    checked("library", psspy.addmodellibrary(str(library)))
    checked("read", psspy.read(0, str(args.output / "smib.raw")))
    checked("solve", psspy.fnsl([0, 0, 0, 1, 1, 0, 99, 0]))
    checked("solved", psspy.solved())
    initial_voltage = {str(bus): checked("voltage", psspy.busdat(bus, "PU")) for bus in (1, 2, 3)}
    initial_q_into_device = checked("QSHNT", psspy.fcddat_2("STATCOM A", "QSHNT"))
    checked("cong", psspy.cong(0))
    for stage in (1, 2, 3):
        load = [0., 100., 0., 100.] if args.load_law == "constant-impedance" else [0.] * 4
        checked("conl", psspy.conl(0, 1, stage, [0, 0], load))
    checked("ordr", psspy.ordr(0))
    checked("fact", psspy.fact())
    checked("tysl", psspy.tysl(0))
    checked("dyr", psspy.dyre_new([1, 1, 1, 1], str(args.output / "smib.dyr"), "", "", ""))
    allocation = {key: checked(key, psspy.fcdmind("STATCOM A", key))
                  for key in ("ICON", "NICON", "CON", "NCON", "STATE", "NSTATE", "VAR", "NVAR")}
    if [allocation[key] for key in ("NICON", "NCON", "NSTATE", "NVAR")] != [20, 44, 6, 27]:
        raise RuntimeError(f"unexpected allocation: {allocation}")
    numeric_icon_offsets = [0, 1, 2, 3, *range(4, 20, 2)]
    def icon_values():
        return {str(i): checked("ICON", psspy.dsival("ICON", allocation["ICON"] + i))
                for i in numeric_icon_offsets}
    before_initialization = {"ICON": icon_values(), "CON": [
        checked("CON", psspy.dsrval("CON", allocation["CON"] + i)) for i in range(44)]}
    for offset in range(allocation["NSTATE"]):
        checked("state channel", psspy.state_channel([-1, allocation["STATE"] + offset], f"STATE_{offset}"))
    for offset in range(allocation["NVAR"]):
        checked("var channel", psspy.var_channel([-1, allocation["VAR"] + offset], f"VAR_{offset}"))
    for bus in (1, 2, 3):
        checked("voltage channel", psspy.voltage_and_angle_channel([-1, -1, -1, bus], [f"V_{bus}", f"ANG_{bus}"]))
    checked("machine channels", psspy.machine_array_channel([-1, 1, 2], "1", "MACHINE_ANGLE"))
    default_i, default_f = psspy.getdefaultint(), psspy.getdefaultreal()
    checked("step", psspy.dynamics_solution_param_2(
        [default_i] * 8, [default_f, default_f, args.step] + [default_f] * 5))
    output = args.output / "reference.out"
    checked("start", psspy.strt_2([0, 0], str(output)))
    initial = {key: [checked(key, psspy.dsrval(key, allocation[key] + i))
                     for i in range(allocation["N" + key])]
               for key in ("CON", "STATE", "VAR")}
    initial["ICON"] = icon_values()
    checked("prefault", psspy.run(0, .05, 1000000, 1, 0))
    if args.profile == "fault":
        checked("fault", psspy.dist_bus_fault(3, 3, 230., [0., args.fault_ohms]))
        checked("fault interval", psspy.run(0, .1, 1000000, 1, 0))
        checked("clear", psspy.dist_clear_fault(1))
    elif args.profile == "reference-step":
        checked("scheduled voltage step", psspy.change_fctmod_var("STATCOM A", "SVSMO3T2", 26,
                initial["VAR"][25] + args.reference_change))
    checked("postfault", psspy.run(0, args.stop, 1000000, 1, 0))
    _, names, values = dyntools.CHNF(str(output)).get_data()
    channels = sorted(key for key in names if isinstance(key, int))
    if args.profile == "reference-step":
        scheduled = next(key for key in channels if names[key] == "VAR_25")
        if max(abs(value - initial["VAR"][25]) for value in values[scheduled]) < abs(args.reference_change) * .9:
            raise RuntimeError("the requested reference disturbance was not observed")
    with (args.output / "reference.csv").open("x", newline="", encoding="utf8") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(["time_s", *(names[key] for key in channels)])
        for row, time in enumerate(values["time"]):
            writer.writerow([format(time, ".12g"), *(format(values[key][row], ".12g") for key in channels)])
            if row % 1000 == 0:
                stream.flush()
                if stream.tell() > FILE_LIMIT or shutil.disk_usage(args.output).free < RESERVE:
                    raise OSError("capture exceeded the file budget or free-space reserve")
    manifest = {"model": "SVSMO3T2", "status": "independent-capture",
                "reference_version": "36.7.0", "model_library_sha256": sha256(library),
                "model_document_sha256": sha256(library.parents[1] / "DOCS/ModelLibrary.pdf"),
                "profile": args.profile, "step_s": args.step, "stop_s": args.stop,
                "con_overrides": args.con, "icon_overrides": args.icon,
                "fault_ohms": args.fault_ohms, "reference_change": args.reference_change,
                "load_conversion": args.load_law, "allocation": allocation,
                "initial_voltage": initial_voltage, "initial_QSHNT_Mvar": initial_q_into_device,
                "initialized": initial, "samples": len(values["time"]),
                "before_initialization": before_initialization,
                "canonical_sha256": canonical_csv_sha256(args.output / "reference.csv", unused_mss_channels),
                "canonical_algorithm": "CSV header and every row, original decimal tokens and order, LF; omit only uninitialized timer channels for MSS slots whose bus is zero and VAR_21 when MSS coordination is disabled. Complete raw CSV retained.",
                "canonical_excluded_channels": unused_mss_channels,
                "script_sha256": sha256(args.output / "probe.py"),
                "files": {path.name: sha256(path) for path in args.output.iterdir() if path.is_file()}}
    (args.output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf8")
    print(json.dumps({"allocation": allocation, "samples": manifest["samples"],
                      "csv_sha256": manifest["files"]["reference.csv"]}))


if __name__ == "__main__":
    main()
