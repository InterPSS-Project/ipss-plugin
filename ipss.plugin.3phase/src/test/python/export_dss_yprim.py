#!/usr/bin/env python3
"""Print DSS-Python Yprim matrices for selected OpenDSS elements.

Example:
  python3 export_dss_yprim.py testData/feeder/IEEE8500/Master-InterPSS.dss \
      Line.LN6290228-5 Capacitor.CAPBank0B Transformer.T5338976B
"""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
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
    if index == 0:
        raise ValueError(f"OpenDSS element not found: {element_name}")

    element = circuit.ActiveCktElement
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
    print("  Yprim:")
    matrix = record["yprim"]
    for row_label, row in zip(record["node_labels"], matrix, strict=False):
        values = "  ".join(format_complex(value) for value in row)
        print(f"    {row_label:>24s}: {values}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("master", help="Path to the OpenDSS master file")
    parser.add_argument("elements", nargs="+", help="DSS element names, e.g. Line.L1")
    parser.add_argument(
        "--command",
        action="append",
        default=[],
        help="DSS command to run after compile and before optional solve; may be repeated",
    )
    parser.add_argument("--solve", action="store_true", help="Run Solve before exporting")
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
    records = [element_yprim(circuit, element) for element in args.elements]
    if args.json:
        print(json.dumps(json_ready(records), indent=2, sort_keys=True))
    else:
        print(f"compiled={master}")
        print(f"buses={circuit.NumBuses} elements={circuit.NumCktElements}")
        for record in records:
            print_text(record)


if __name__ == "__main__":
    main()
