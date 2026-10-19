"""Audit exact active-stall inputs, cold repeatability and embedded raw histories."""
import base64
import csv
import gzip
import hashlib
import json
import math
from pathlib import Path
import struct
from wt12a1b_native_probe import PROFILES, configuration


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def audit(root):
    resource = root / 'ipss.test.plugin.core/src/test/resources/wt12a1b-native'
    result = []
    identity = None
    for mode in PROFILES:
        dynamic, constants, events = configuration(mode)
        for label, step in [('coarse', .0005), ('fine', .00025)]:
            cold = []
            for repeat in ('a', 'b'):
                run = root / 'target' / f'wt12a1b-native-{mode}-{label}-cold-{repeat}'
                m = json.loads((run / 'manifest.json').read_text())
                if (m['status'], m['model'], m['profile'], m['step_s'], m['end_s']) != ('native-converged-profile', 'WT12A1B', mode, step, 1.):
                    raise ValueError('Changed profile identity')
                expected_files = {'network.raw', 'dynamic.dyr', 'probe.py', 'hplntd_fault_probe.py', 'allocation.json', 'native.log', 'native.out', 'native.csv', 'switch-status.csv'}
                if set(m['files']) != expected_files:
                    raise ValueError('Incomplete manifest')
                for name, sha in m['files'].items():
                    if digest(run / name) != sha:
                        raise ValueError('Changed raw artifact: ' + name)
                for archived, source in [('probe.py', 'wt12a1b_native_probe.py'), ('hplntd_fault_probe.py', 'hplntd_fault_probe.py')]:
                    if digest(run / archived) != digest(root / 'tools' / source):
                        raise ValueError('Changed generating source')
                if (run / 'dynamic.dyr').read_text() != dynamic or m['declared_constants'] != constants:
                    raise ValueError('Changed exact entered parameters')
                log=(run/'native.log').read_text(errors='replace')
                if 'Initial conditions check O.K.' not in log or 'Network not converged' in log:
                    raise ValueError('Rejected initialization/network solution')
                if m['fault']!={'bus':1,'units':3,'base_kv':100,'impedance_ohm':[0,10],'impedance_system_pu':[0,.1]}:
                    raise ValueError('Changed fault base/units')
                if m['actual_constants'] != [struct.unpack('f', struct.pack('f', v))[0] for v in constants]:
                    raise ValueError('Changed allocated parameters')
                current = tuple(m[k] for k in ('source_document_sha256', 'library_sha256', 'wrapper_sha256', 'python_sha256'))
                if current[:2] != ('40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d', 'c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6'):
                    raise ValueError('Changed reference implementation')
                if identity is None:
                    identity = current
                if current != identity:
                    raise ValueError('Mixed reference runtimes')
                for prefix, counts in [('AERO', (14, 1, 3, 2)), ('MECH', (5, 0, 4, 3)), ('GEN', (10, 0, 5, 3))]:
                    if tuple(m['layout'][prefix]['N' + field] for field in ('CON', 'ICON', 'STATE', 'VAR')) != counts:
                        raise ValueError('Changed allocated state/parameter contract')
                if [(v['time_s'], v['action']) for v in m['events']] != events:
                    raise ValueError('Changed events')
                if any(abs(v['actual_time_s'] - v['time_s']) > step for v in m['events']):
                    raise ValueError('Excessive event clock offset')
                with (run / 'native.csv').open(newline='') as stream:
                    reader = csv.DictReader(stream)
                    fields = reader.fieldnames
                    rows = [{k: float(v) for k, v in row.items()} for row in reader]
                if len(fields) != 28 or m['channel_count'] != 27 or len(rows) != m['sample_count']:
                    raise ValueError('Changed channel/history cardinality')
                expected_fields=['time_s']
                for prefix,states,variables in [('AERO',3,2),('MECH',4,3),('GEN',5,3)]:
                    expected_fields += [prefix+'_STATE'+str(i) for i in range(states)]+[prefix+'_VAR'+str(i) for i in range(variables)]
                expected_fields += ['V1','A1_DEG','V2','A2_DEG','P_PU','Q_PU','SPEED_DEV']
                if fields!=expected_fields:
                    raise ValueError('Changed semantic channel ordering')
                if not all(math.isfinite(v) for row in rows for v in row.values()):
                    raise ValueError('Nonfinite reference')
                times = [row['time_s'] for row in rows]
                if any(b < a for a, b in zip(times, times[1:])) or not any(a == b for a, b in zip(times, times[1:])):
                    raise ValueError('Missing duplicate history or invalid clock')
                if abs(times[-1] - 1.) > step:
                    raise ValueError('Incomplete reference interval')
                with (run / 'switch-status.csv').open(newline='') as stream:
                    status = list(csv.DictReader(stream))
                if len(status) != round(1 / step) + 1 or len(status) != m['status_count']:
                    raise ValueError('Incomplete endpoint queries')
                if not all(math.isfinite(float(v)) for row in status for v in row.values()):
                    raise ValueError('Nonfinite timer/internal-ICON query')
                # This is the internal allocated ICON, not the diagram SW1 signal.
                if any(float(row['SWITCH_ICON']) != 0 for row in status):
                    raise ValueError('Changed internal ICON diagnostic')
                cold.append(tuple(m['files'][name] for name in ('native.csv', 'switch-status.csv', 'dynamic.dyr', 'network.raw')))
                for suffix, name, zipped in [('csv', 'native.csv', True), ('status', 'switch-status.csv', True), ('dyr', 'dynamic.dyr', False)]:
                    body = base64.b64decode((resource / f'{mode}-{label}.{suffix}.b64').read_bytes())
                    if zipped:
                        body = gzip.decompress(body)
                    if body != (run / name).read_bytes():
                        raise ValueError('Embedded reference does not preserve raw bytes')
                if base64.b64decode((resource / 'network.raw.b64').read_bytes()) != (run / 'network.raw').read_bytes():
                    raise ValueError('Changed embedded network')
            if cold[0] != cold[1]:
                raise ValueError('Cold reference does not repeat exactly')
            result.append(dict(profile=mode, step_s=step, samples=m['sample_count'], replay='diagnostic' if mode == 'direct' else 'bounded'))
    return dict(status='raw-provenance-and-cold-repeatability-audited', profiles=result, runs=24)


if __name__ == '__main__':
    print(json.dumps(audit(Path.cwd()), indent=2))
