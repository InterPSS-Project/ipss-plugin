"""Generate typed stage-input arithmetic from the controller's annotations.

Block equations stay in their existing Java implementations. This generator
only compiles the fixed wiring, preserving the CML operand-read and reduction
order. Run with --check to detect stale generated regions without writing.
"""
from pathlib import Path
import argparse
import json
import re

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT / "ipss.plugin.core/src/main/java/org/interpss/dstab/control/pss/ieee/y1992/pss2a"
FIELDS = ["washoutBlock1", "washoutBlock2", "delayBlock"] * 2 + [
    "filterNthBlock", "filterBlock1", "filterBlock2", "outputBlock"]


def render(model_source):
    child = model_source[model_source.index("class CustomExciter"):model_source.index("// UI Editor panel")]
    parent = model_source[:model_source.index("class CustomExciter")]
    expressions = []
    for index, name in enumerate(FIELDS):
        source = child if index < 6 else parent
        found = re.search(r"@AnControllerField\((.*?)\)\s*(?:public\s+)?\w+\s+" + name + r"\b", source, re.S)
        if found is None:
            raise ValueError(f"Cannot find annotated input for {name}")
        annotation = found.group(1).rsplit("@AnControllerField(", 1)[-1]
        expressions.append(re.search(r'input\s*=\s*"([^"]+)"', annotation).group(1))
    bindings = {"this.refPoint": "model.getRefPoint()",
                "this.customBlock1.y": "first.model.getRuntimeOutputValue()",
                "this.customBlock2.y": "second.model.getRuntimeOutputValue()",
                "this.ks3": "model.ks3", "this.ks4": "model.ks4",
                "this.filterNthBlock.y": "y(6)", "this.filterBlock1.y": "y(7)", "this.filterBlock2.y": "y(8)"}
    body = "    private double input(int id) {\n        switch(id) {\n"
    signatures = []
    for index, expression in enumerate(expressions):
        pairs = [(op or "+", symbol.strip()) for op, symbol in re.findall(r"([+*/-]?)\s*([^+*/-]+)", expression.strip())]
        signatures.append("".join(op + symbol for op, symbol in pairs))
        symbols = bindings.copy()
        if index < 6:
            owner, offset = ("first", 0) if index < 3 else ("second", 3)
            symbols.update({"this.input": f"{owner}.model.getRuntimeInputValue()",
                            "this.refPoint": f"{owner}.model.getRefPoint()",
                            "this.washoutBlock1.y": f"y({offset})", "this.washoutBlock2.y": f"y({offset + 1})"})
        body += f"            case {index}: {{\n"
        for slot, (_, symbol) in enumerate(pairs):
            if symbol not in symbols:
                raise ValueError(f"Typed binding required for {symbol}")
            body += f"                double v{slot} = {symbols[symbol]};\n"
        for slot, (operator, _) in enumerate(pairs):
            if operator == "-":
                body += f"                v{slot} = -v{slot};\n"
        for slot, (operator, _) in enumerate(pairs):
            if operator in ("*", "/"):
                body += f"                v{slot} = v{slot - 1} {operator} v{slot}; v{slot - 1} = 0.;\n"
        body += "                return " + " + ".join(["0."] + [f"v{slot}" for slot in range(len(pairs))]) + ";\n            }\n"
    body += '            default: throw new IllegalArgumentException("Unknown input slot");\n        }\n    }\n'
    signature_source = "    private static final String[] INPUTS={" + ",\n        ".join(json.dumps(s) for s in signatures) + "};\n"
    return {"SIGNATURES": signature_source, "ARITHMETIC": body}


def generate(model_source, stage_source):
    for name, body in render(model_source).items():
        begin = f"    // BEGIN GENERATED {name}\n"
        end = f"    // END GENERATED {name}\n"
        start = stage_source.index(begin) + len(begin)
        stop = stage_source.index(end, start)
        stage_source = stage_source[:start] + body + stage_source[stop:]
    return stage_source


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    model = (PACKAGE / "Ieee1992PSS2AStabilizer.java").read_text(encoding="utf-8")
    destination = PACKAGE / "Pss2aCompiledStage.java"
    existing = destination.read_text(encoding="utf-8")
    generated = generate(model, existing)
    if args.check:
        if existing != generated:
            raise SystemExit("Generated PSS2A stage inputs are stale; run tools/generate_pss2a_stage_inputs.py")
        print("PSS2A generated stage inputs match the model annotations")
    else:
        destination.write_bytes(generated.encode("utf-8"))
        print("Generated PSS2A stage input signatures and arithmetic")


if __name__ == "__main__":
    main()
