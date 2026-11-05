"""Private synthetic import discriminator; conversion is not equation equivalence."""
import argparse
import hashlib
import importlib.util
import json
import re
import shlex
import shutil
import subprocess
from pathlib import Path

NAMES = ('GEWTG2', 'GEWTE2', 'GEWTA2', 'GEWTP2', 'GEWTT1', 'GEWGD1', 'GEWPLT2')
STATE_RESULTS = tuple("GEN 1 '1'|" + family + ':' + str(index)
                      for family, count in (('TSMachineState', 5), ('TSExciterState', 10),
                                            ('TSGovernorState', 4), ('TSStabilizerState', 3))
                      for index in range(1, count + 1))


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def select_records(source):
    text = Path(source).read_text(errors='strict')
    candidates = []
    for match in re.finditer(r"(?im)^\s*\d+[^\n]*['\"](?:" + '|'.join(NAMES) + r")[ '\"]", text):
        end = text.find('/', match.start())
        if end < 0:
            raise ValueError('Unterminated source record')
        raw = text[match.start():end].strip()
        tokens = shlex.split(raw.replace(',', ' '))
        name = next((n for n in NAMES if n in tokens), None)
        if name:
            candidates.append((name, tokens, raw + ' /'))
    owner = next(t[:3:2] for n, t, _ in candidates if n == 'GEWTG2')
    selected = []
    for name in NAMES:
        def matches(item):
            n, t, _ = item
            if n != name:
                return False
            return [v.strip() for v in (t[9:11] if name == 'GEWPLT2' else t[:3:2])] == [v.strip() for v in owner]
        item = next((i for i in candidates if matches(i)), None)
        if item is None:
            raise ValueError('Missing same-owner companion ' + name)
        selected.append(item)
    return selected


def stage_records(selected):
    records, inventory = [], []
    for name, original, raw in selected:
        t = original.copy()
        pos = t.index(name)
        allocation = [int(x) for x in t[pos + 1:pos + 7]]
        if len(t) != pos + 7 + allocation[2] + allocation[3]:
            raise ValueError('Unexpected record cardinality: ' + name)
        if name == 'GEWPLT2':
            t[0], t[9], t[10] = '1', '1', '1'
        else:
            t[0], t[2] = '1', '1'
        t[1] = "'" + t[1] + "'"
        t[pos] = "'" + name + "'"
        records.append(' '.join(t) + ' /')
        inventory.append(dict(model=name, allocation=allocation,
                              original_tokens=original, staged_tokens=t,
                              original_record=raw))
    records.append("2 'GENCLS' '1' 0.0 0.0 /")
    return '\n'.join(records) + '\n', inventory


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-dyr', required=True, type=Path)
    parser.add_argument('--runner', required=True, type=Path)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--executable', required=True, type=Path)
    parser.add_argument('--diagnostic-states', action='store_true')
    args = parser.parse_args()
    if not re.fullmatch('[A-Za-z0-9_-]+', args.tag):
        raise ValueError('Invalid run tag')
    root = Path.cwd()
    out = root / 'target' / ('legacy-ge-import-' + args.tag)
    out.mkdir(parents=True, exist_ok=False)
    dynamic, records = stage_records(select_records(args.source_dyr))
    (out / 'source.dyr').write_text(dynamic)
    shutil.copy2(__file__, out / 'probe.py')
    shutil.copy2(args.runner, out / 'runner.py')
    (out / 'source-contract-private.json').write_text(json.dumps(dict(
        source_sha256=sha(args.source_dyr), runner_sha256=sha(args.runner),
        executable_sha256=sha(args.executable), records=records,
        transformations=['Synthetic owner bus/unit 1/1', 'Plot owner 1/1'],
        note='All constants and other integer values unchanged; this tests import, not equivalence.'), indent=2))
    spec = dict(name='legacy-ge-import', raw='ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33_wt2g1.raw',
                raw_format='PTI33', dyr=(out / 'source.dyr').relative_to(root).as_posix(),
                bootstrap_powerworld=True, bootstrap_wait_s=8, auto_correct=False,
                simulation=dict(start_s=0, stop_s=.15, step_s=.0005),
                contingency=dict(name='LegacyImport', memo='Synthetic model conversion discriminator',
                    events=[dict(time_s=.05, object='Bus 1', action='FAULT 3PB IMP 0 0.2'),
                            dict(time_s=.1, object='Bus 1', action='CLEARFAULT')]),
                results=['Bus 1 | TSVpu', 'Bus 2 | TSVpu', "GEN 1 '1'|TSGenP", "GEN 1 '1'|TSGenQ"])
    if args.diagnostic_states:
        spec['results'].extend(STATE_RESULTS)
    (out / 'spec.json').write_text(json.dumps(spec, indent=2))
    module = importlib.util.spec_from_file_location('legacy_import_runner', out / 'runner.py')
    runner = importlib.util.module_from_spec(module)
    module.loader.exec_module(runner)
    original_popen = subprocess.Popen
    def hidden(*a, **kw):
        startup = subprocess.STARTUPINFO()
        startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
        startup.wShowWindow = subprocess.SW_HIDE
        kw.setdefault('startupinfo', startup)
        return original_popen(*a, **kw)
    runner.subprocess.Popen = hidden
    render = runner.render_aux
    def audited(settings, directory):
        text = render(settings, directory)
        for marker in ('DYR_LOADED', 'POWER_FLOW_SOLVED', 'INITIALIZED'):
            anchor = f'WriteTextToFile("progress.txt", "{marker};");'
            if text.count(anchor) != 1:
                raise ValueError('Missing stage marker ' + marker)
            text = text.replace(anchor, anchor + f'\nTSWriteOptions("{marker.lower()}-models.aux", [YES, YES, YES, NO, YES, NO, YES], PRIMARY);\nLogSave("{marker.lower()}.log", NO);')
        return text
    runner.render_aux = audited
    outcome = dict(status='not-run')
    try:
        runner.run(out / 'spec.json', root, out, str(args.executable), 90, False)
        outcome = dict(status='completed', manifest='manifest.json')
    except Exception as error:
        outcome = dict(status='failed', error_type=type(error).__name__, error=str(error))
    finally:
        (out / 'probe-outcome.json').write_text(json.dumps(outcome, indent=2))
        artifacts = {f.name: dict(bytes=f.stat().st_size, sha256=sha(f))
                     for f in out.iterdir() if f.is_file()}
        (out / 'import-evidence-manifest.json').write_text(json.dumps(dict(
            status=outcome['status'], purpose='conversion-discriminator-not-exact-model-qualification',
            artifacts=artifacts), indent=2))
    print(json.dumps(dict(status=outcome['status'], output=out.relative_to(root).as_posix())))
    if outcome['status'] != 'completed':
        raise SystemExit(1)


if __name__ == '__main__':
    main()
