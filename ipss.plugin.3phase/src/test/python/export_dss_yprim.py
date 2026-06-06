#!/usr/bin/env python3
"""Print DSS-Python solved quantities for selected OpenDSS elements.

Example:
  python3 export_dss_yprim.py testData/feeder/IEEE8500/Master-InterPSS.dss \
      Line.LN6290228-5 Capacitor.CAPBank0B Transformer.T5338976B
"""

from __future__ import annotations

import argparse
import csv
import sys
import json
import math
import numbers
from pathlib import Path
from contextlib import nullcontext
from typing import Any

import dss


def complex_pairs(flat: list[float]) -> list[complex]:
    if len(flat) % 2 != 0:
        raise ValueError(f"Yprim has odd real/imag array length: {len(flat)}")
    return [complex(flat[i], flat[i + 1]) for i in range(0, len(flat), 2)]


def square_matrix(values: list[complex]) -> list[list[complex]]:
    n_float = math.sqrt(len(values))
    n = int(n_float)
    if n * n != len(values):
        raise ValueError(f"Yprim complex value count is not square: {len(values)}")
    return [values[row * n : (row + 1) * n] for row in range(n)]


def element_yprim(circuit: Any, element_name: str) -> dict[str, Any]:
    index = circuit.SetActiveElement(element_name)
    element = circuit.ActiveCktElement
    if index <= 0 or element.Name.lower() != element_name.lower():
        raise ValueError(f"OpenDSS element not found: {element_name}")

    values = complex_pairs(list(element.Yprim))
    matrix = square_matrix(values)
    bus_names = list(element.BusNames)
    node_order = [int(node) for node in element.NodeOrder]
    labels = node_labels(bus_names, node_order, element.NumConductors)
    return {
        "requested_name": element_name,
        "active_name": element.Name,
        "index": index,
        "bus_names": bus_names,
        "num_conductors": element.NumConductors,
        "num_terminals": element.NumTerminals,
        "node_order": node_order,
        "node_labels": labels,
        "yprim": matrix,
        "currents": complex_pairs(list(element.Currents)),
        "powers_kva": complex_pairs(list(element.Powers)),
    }


def node_labels(bus_names: list[str], node_order: list[int], num_conductors: int) -> list[str]:
    labels: list[str] = []
    for index, node in enumerate(node_order):
        terminal = index // num_conductors
        bus = bus_names[terminal] if terminal < len(bus_names) else f"terminal{terminal + 1}"
        labels.append(f"{bus}:{node}")
    return labels


def json_ready(value: Any) -> Any:
    if isinstance(value, complex):
        return {"re": value.real, "im": value.imag}
    if isinstance(value, numbers.Integral):
        return int(value)
    if isinstance(value, numbers.Real):
        return float(value)
    if isinstance(value, list):
        return [json_ready(item) for item in value]
    if isinstance(value, dict):
        return {key: json_ready(item) for key, item in value.items()}
    return value


def format_complex(value: complex) -> str:
    return f"{value.real: .9g}{value.imag:+.9g}j"


def print_text(record: dict[str, Any]) -> None:
    print(
        f"{record['requested_name']} active={record['active_name']} "
        f"index={record['index']}"
    )
    print(f"  buses={record['bus_names']}")
    print(f"  nodeOrder={record['node_order']}")
    print(f"  nodeLabels={record['node_labels']}")
    print("  Currents:")
    for label, value in zip(record["node_labels"], record["currents"], strict=False):
        print(f"    {label:>24s}: {format_complex(value)}")
    print("  Powers kVA:")
    for label, value in zip(record["node_labels"], record["powers_kva"], strict=False):
        print(f"    {label:>24s}: {format_complex(value)}")
    print("  Yprim:")
    matrix = record["yprim"]
    for row_label, row in zip(record["node_labels"], matrix, strict=False):
        values = "  ".join(format_complex(value) for value in row)
        print(f"    {row_label:>24s}: {values}")


def bus_voltage_records(circuit: Any) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for bus_name in circuit.AllBusNames:
        circuit.SetActiveBus(bus_name)
        bus = circuit.ActiveBus
        nodes = list(bus.Nodes)
        vmag_angle = complex_pairs(list(bus.puVmagAngle))
        records.append(
            {
                "bus": bus_name,
                "nodes": nodes,
                "pu_vmag_angle": [
                    {"node": node, "vmag": value.real, "angle_deg": value.imag}
                    for node, value in zip(nodes, vmag_angle, strict=False)
                ],
            }
        )
    return records


def print_bus_voltages(records: list[dict[str, Any]]) -> None:
    print("Bus voltages:")
    for record in records:
        values = " ".join(
            f"{item['node']}:{item['vmag']:.9g}@{item['angle_deg']:.9g}"
            for item in record["pu_vmag_angle"]
        )
        print(f"  {record['bus']}: {values}")


def active_element_record(circuit: Any, class_name: str, name: str) -> dict[str, Any]:
    element = circuit.ActiveCktElement
    return {
        "class": class_name,
        "name": name,
        "full_name": element.Name,
        "bus_names": list(element.BusNames),
        "node_order": list(element.NodeOrder),
        "currents": complex_pairs(list(element.Currents)),
        "powers_kva": complex_pairs(list(element.Powers)),
    }


def load_records(circuit: Any) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    loads = circuit.Loads
    index = loads.First
    while index > 0:
        name = loads.Name
        records.append(active_element_record(circuit, "Load", name))
        index = loads.Next
    return records


def aggregate_complex(values: list[complex]) -> complex:
    total = complex(0.0, 0.0)
    for value in values:
        total += value
    return total


def terminal_count(record: dict[str, Any]) -> int:
    bus_count = max(1, len(record["bus_names"]))
    return max(1, len(record["powers_kva"]) // bus_count)


def print_load_csv(records: list[dict[str, Any]], output: Path | None = None) -> None:
    context = output.open("w", newline="") if output else nullcontext(sys.stdout)
    with context as stream:
        writer = csv.writer(stream, lineterminator="\n")
        write_load_csv(records, writer)


def print_element_csv(records: list[dict[str, Any]], output: Path | None = None) -> None:
    context = output.open("w", newline="") if output else nullcontext(sys.stdout)
    with context as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow([
            "source",
            "class",
            "name",
            "bus",
            "terminals",
            "bus_count",
            "p_kw",
            "q_kvar",
            "current_abs_sum_a",
            "current_abs_max_a",
            "currents",
            "powers_kva",
        ])
        for record in records:
            currents = record["currents"]
            powers = record["powers_kva"]
            total_power = aggregate_complex(powers)
            writer.writerow([
                "dss",
                record["class"],
                record["name"].lower(),
                ";".join(record["bus_names"]).lower(),
                terminal_count(record),
                len(record["bus_names"]),
                f"{total_power.real:.12g}",
                f"{total_power.imag:.12g}",
                f"{sum(abs(current) for current in currents):.12g}",
                f"{max((abs(current) for current in currents), default=0.0):.12g}",
                ";".join(format_complex(current) for current in currents),
                ";".join(format_complex(power) for power in powers),
            ])


def class_records(circuit: Any, class_names: list[str]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for class_name in class_names:
        if class_name.lower() == "line":
            lines = circuit.Lines
            index = lines.First
            while index > 0:
                name = lines.Name
                circuit.SetActiveElement(f"Line.{name}")
                records.append(active_element_record(circuit, "Line", name))
                index = lines.Next
            continue
        if class_name.lower() == "transformer":
            transformers = circuit.Transformers
            index = transformers.First
            while index > 0:
                name = transformers.Name
                circuit.SetActiveElement(f"Transformer.{name}")
                records.append(active_element_record(circuit, "Transformer", name))
                index = transformers.Next
            continue
        if not circuit.SetActiveClass(class_name):
            continue
        active_class = circuit.ActiveClass
        index = active_class.First
        while index > 0:
            name = active_class.Name
            circuit.SetActiveElement(f"{class_name}.{name}")
            records.append(active_element_record(circuit, class_name, name))
            index = active_class.Next
    return records


def write_load_csv(records: list[dict[str, Any]], writer: Any) -> None:
    writer.writerow([
        "source",
        "name",
        "bus",
        "terminals",
        "bus_count",
        "p_kw",
        "q_kvar",
        "current_abs_sum_a",
        "current_abs_max_a",
        "currents",
        "powers_kva",
    ])
    for record in records:
        currents = record["currents"]
        powers = record["powers_kva"]
        total_power = aggregate_complex(powers)
        writer.writerow([
            "dss",
            record["name"].lower(),
            ";".join(record["bus_names"]).lower(),
            terminal_count(record),
            len(record["bus_names"]),
            f"{total_power.real:.12g}",
            f"{total_power.imag:.12g}",
            f"{sum(abs(current) for current in currents):.12g}",
            f"{max((abs(current) for current in currents), default=0.0):.12g}",
            ";".join(format_complex(current) for current in currents),
            ";".join(format_complex(power) for power in powers),
        ])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("master", help="Path to the OpenDSS master file")
    parser.add_argument("elements", nargs="*", help="DSS element names, e.g. Line.L1")
    parser.add_argument(
        "--command",
        action="append",
        default=[],
        help="DSS command to run after compile and before optional solve; may be repeated",
    )
    parser.add_argument("--solve", action="store_true", help="Run Solve before exporting")
    parser.add_argument("--voltages", action="store_true", help="Export all bus voltage magnitudes/angles")
    parser.add_argument("--loads-csv", action="store_true", help="Emit solved load terminal powers/currents as CSV")
    parser.add_argument("--class-csv", nargs="*", help="Emit solved element terminal powers/currents for DSS classes")
    parser.add_argument("--output", help="Optional output path for CSV exports")
    parser.add_argument("--json", action="store_true", help="Emit JSON instead of text")
    args = parser.parse_args()

    master = Path(args.master).resolve()
    dss.DSS.Text.Command = "clear"
    dss.DSS.Text.Command = f'compile "{master}"'
    for command in args.command:
        dss.DSS.Text.Command = command
    if args.solve:
        dss.DSS.Text.Command = "solve"

    circuit = dss.DSS.ActiveCircuit
    if args.loads_csv:
        output = Path(args.output) if args.output else None
        if output:
            output.parent.mkdir(parents=True, exist_ok=True)
        print_load_csv(load_records(circuit), output)
        return
    if args.class_csv is not None:
        output = Path(args.output) if args.output else None
        if output:
            output.parent.mkdir(parents=True, exist_ok=True)
        print_element_csv(class_records(circuit, args.class_csv), output)
        return

    records = [element_yprim(circuit, element) for element in args.elements]
    voltage_records = bus_voltage_records(circuit) if args.voltages else []
    if args.json:
        print(json.dumps(json_ready({"elements": records, "bus_voltages": voltage_records}), indent=2, sort_keys=True))
    else:
        print(f"compiled={master}")
        print(f"buses={circuit.NumBuses} elements={circuit.NumCktElements}")
        if args.voltages:
            print_bus_voltages(voltage_records)
        for record in records:
            print_text(record)


if __name__ == "__main__":
    main()
