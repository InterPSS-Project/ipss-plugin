"""Generate ST4B stage wiring from annotations; keep block equations authoritative."""
from pathlib import Path
import argparse
import json
import re

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT / 'ipss.plugin.core/src/main/java/org/interpss/dstab/control/exc/ieee/y2005/st4b'
FIELDS = ['trDelayBlock', 'vrPIBlock', 'taDelayBlock', 'kgGainBlock', 'vmPIBlock', 'customBlock']
BINDINGS = {'mach.vt': 'voltage()', 'pss.vs': 'stabilizer()', 'this.refPoint': 'model.getRefPoint()',
            'this.vuel': 'model.vuel', 'this.voel': 'model.voel', 'this.voelGate.y': 'gate()'}
BINDINGS.update({'this.' + name + '.y': f'y({index})' for index, name in enumerate(FIELDS)})


def render(source):
    annotations = {}
    for name in FIELDS + ['voelGate']:
        declaration = re.search(r'\b(?:DelayControlBlock|Esst4bPIControlBlock|GainBlock|ICMLStaticBlock|LowValueFunction)\s+' + name + r'\b', source)
        marker = '@AnFunctionField(' if name == 'voelGate' else '@AnControllerField('
        start = source.rfind(marker, 0, declaration.start())
        if start < 0: raise ValueError('Missing annotation for ' + name)
        annotations[name] = source[start:declaration.start()]
    expressions = [re.search(r'input\s*=\s*"([^"]+)"', annotations[name]).group(1) for name in FIELDS]
    gate = re.findall(r'"([^"]+)"', re.search(r'input\s*=\s*\{([^}]+)\}', annotations['voelGate']).group(1))
    if len(gate) != 2: raise ValueError('Expected two low-value gate inputs')
    signatures = []
    body = '    private double input(int id) {\n        switch (id) {\n'
    for index, expression in enumerate(expressions):
        terms = [(op or '+', symbol.strip()) for op, symbol in re.findall(r'([+*/-]?)\s*([^+*/-]+)', expression)]
        signatures.append(''.join(op + symbol for op, symbol in terms))
        body += f'            case {index}: {{\n'
        for slot, (_, symbol) in enumerate(terms):
            body += f'                double v{slot} = {BINDINGS[symbol]};\n'
        for slot, (op, _) in enumerate(terms):
            if op == '-': body += f'                v{slot} = -v{slot};\n'
        for slot, (op, _) in enumerate(terms):
            if op in ('*', '/'):
                body += f'                v{slot} = v{slot-1} {op} v{slot}; v{slot-1} = 0.;\n'
        body += '                return ' + ' + '.join(['0.'] + [f'v{i}' for i in range(len(terms))]) + ';\n            }\n'
    body += '            default: throw new IllegalArgumentException("Unknown input slot");\n        }\n    }\n'
    body += '    private double gate() {\n'
    for i, symbol in enumerate(gate): body += f'        gateInputs[{i}] = {BINDINGS[symbol]};\n'
    body += '        return gateFunction.eval(gateInputs);\n    }\n'
    declarations = '    private static final String[] INPUTS = {' + ', '.join(map(json.dumps, signatures)) + '};\n'
    declarations += '    private static final String[] GATE_INPUTS = {' + ', '.join(map(json.dumps, gate)) + '};\n'
    return {'SIGNATURES': declarations, 'ARITHMETIC': body}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    model = (PACKAGE / 'IEEE2005ST4BExciter.java').read_text(encoding='utf-8')
    destination = PACKAGE / 'St4bCompiledStage.java'
    existing = destination.read_text(encoding='utf-8')
    generated = existing
    for name, text in render(model).items():
        start = generated.index(f'    // BEGIN GENERATED {name}\n') + len(f'    // BEGIN GENERATED {name}\n')
        end = generated.index(f'    // END GENERATED {name}\n', start)
        generated = generated[:start] + text + generated[end:]
    if args.check:
        if generated != existing: raise SystemExit('ST4B generated wiring is stale')
        print('ST4B generated wiring matches the annotations')
    else:
        destination.write_bytes(generated.encode('utf-8'))
        print('Generated ST4B stage wiring')


if __name__ == '__main__': main()
