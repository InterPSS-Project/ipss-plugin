"""Audit diagnostic cold pairs; this does not qualify a Java reconnect path."""
import argparse
import csv
import hashlib
import json
from pathlib import Path


def rates(row, c, reference_increment):
    s = [row[f'STATE{k}'] for k in range(6)]
    speed = row['VAR3']
    result = [((1-c[2]/c[1])*c[0]*speed-s[0])/c[1],
              max(c[5], min(c[4], (row['VAR0']+reference_increment-s[0]
                  -c[0]*c[2]/c[1]*speed-row['VAR7'])/c[3]))]
    # This discriminator uses the fixture's identity valve curve and zero deadbands.
    assert c[20:23] == [0., 0., 0.] and c[23:27] == [0., 0., 1., 1.]
    upstream = s[1]
    for k in range(4):
        t = c[8+3*k]
        result.append((upstream-s[k+2])/t if t else 0.)
        if t:
            upstream = s[k+2]
    return result


def audit(root):
    results = []
    for folder in sorted(root.glob('*-a')):
        manifest = json.loads((folder/'manifest.json').read_text())
        other = folder.with_name(folder.name[:-1]+'b')
        assert (folder/'native.csv').read_bytes() == (other/'native.csv').read_bytes(), folder
        for run in (folder, other):
            info = json.loads((run/'manifest.json').read_text())
            assert info['status'] == 'diagnostic-only'
            for name, digest in info['files'].items():
                assert hashlib.sha256((run/name).read_bytes()).hexdigest() == digest, name
            request = info['status_reenable_diagnostic']
            assert request['return_code'] == 0
            assert request['before']['machine_status']['value'] == 0
            assert request['after']['machine_status']['value'] == 1
            assert request['before']['governor_states'] == request['after']['governor_states']
        rows = [{key: float(value) for key, value in row.items()}
                for row in csv.DictReader((folder/'native.csv').open())]
        c = manifest['initialized_constants']
        h = manifest['settings']['step']
        # Find the actual recorded trip duplicate rather than rounding event time.
        trip = next(i for i in range(1, len(rows)) if rows[i]['time_s'] > .04
                    and rows[i]['time_s'] == rows[i-1]['time_s'])
        off = [r for r in rows[trip:] if r['time_s'] <= .1+h*.02]
        if manifest['settings']['event'] == 'primary-trip':
            off = [r for r in off if r['PMECH'] == 0.]
        after = [r for r in rows if r['time_s'] > .1+h*.5]
        output_error = 0.
        for r in after:
            primary = sum(c[9+3*k]*r[f'STATE{k+2}'] for k in range(4))
            secondary = r['VAR1']*sum(c[10+3*k]*r[f'STATE{k+2}'] for k in range(4))
            output_error = max(output_error, abs(primary-r['PMECH']), abs(secondary-r['PMECH_LP']))
        assert output_error < 1e-7, output_error
        result = dict(tag=folder.name[:-2], samples=len(rows), cold_pair_identical=True,
                      state_preserved_at_request=True, recovered_output_max_error=output_error)
        if manifest['settings']['event'] == 'primary-trip':
            last = rates(rows[trip-1], c, manifest['settings']['pretrip_reference'])
            older = rates(rows[trip-2], c, manifest['settings']['pretrip_reference'])
            maximum = 0.
            for j in range(1, len(off)):
                for k in range(6):
                    if k >= 2 and c[8+3*(k-2)] == 0.:
                        assert off[j][f'STATE{k}'] == off[j-1][f'STATE{k}']
                        continue
                    expected = h*(1.5*last[k]-.5*older[k]) if j == 1 else h*last[k]
                    error = off[j][f'STATE{k}']-off[j-1][f'STATE{k}']-expected
                    maximum = max(maximum, abs(error))
                assert off[j]['PMECH'] == 0.
                assert off[j]['PMECH_LP'] == off[0]['PMECH_LP']
            assert maximum < 3e-8, maximum  # Stored binary32 coordinates.
            result.update(stale_rate_increment_max_error=maximum, off_steps=len(off)-1,
                          valve_off_change=off[-1]['STATE1']-off[0]['STATE1'])
        results.append(result)
    assert len(results) == 4
    return dict(status='diagnostic-only', java_reconnection_qualified=False, pairs=results)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('capture_root', type=Path)
    args = parser.parse_args()
    print(json.dumps(audit(args.capture_root), indent=2))
