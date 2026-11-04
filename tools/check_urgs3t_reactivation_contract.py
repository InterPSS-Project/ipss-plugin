"""Verify independent reactivation evidence; this does not qualify a runtime reconnect path."""
import argparse
import csv
import hashlib
import json
from pathlib import Path


def audit(root):
    results = []
    for folder in sorted(root.glob('*-a')):
        manifest = json.loads((folder/'manifest.json').read_text())
        other = folder.with_name(folder.name[:-1]+'b')
        first = list(csv.DictReader((folder/'native.csv').open()))
        second = list(csv.DictReader((other/'native.csv').open()))
        assert len(first) == len(second)
        # This fixture has fixed excitation and no regulator: VREF has no owner.
        # Retain its raw values and report their difference; all defined channels are exact.
        vref_difference = max(abs(float(a['VREF'])-float(b['VREF'])) for a,b in zip(first,second))
        for a,b in zip(first,second):
            assert {k:v for k,v in a.items() if k != 'VREF'} == {k:v for k,v in b.items() if k != 'VREF'}
        for run in (folder, other):
            m = json.loads((run/'manifest.json').read_text())
            assert m['status'] == 'complete'
            for name, digest in m['files'].items():
                assert hashlib.sha256((run/name).read_bytes()).hexdigest() == digest, name
            probe = m['reenable_probe']
            assert not probe['reinitialization_called']
            assert probe['before']['status'] == 0
            assert probe['after']['status'] == (0 if probe['control'] else 1)
            for key in ('time', 'governor_states', 'governor_variables', 'machine_states'):
                assert probe['before'][key] == probe['after'][key], key
        rows = [{k: float(v) for k, v in r.items()} for r in csv.DictReader((folder/'native.csv').open())]
        h = manifest['settings']['step']
        c = manifest['initialized_constants']
        trip = next(i for i, r in enumerate(rows) if r['time_s'] > .14 and r['PMECH'] == 0)
        # Preserve the actual duplicate boundary: PRE is immediately before POST.
        assert rows[trip]['time_s'] == rows[trip-1]['time_s']
        pre, older = rows[trip-1], rows[trip-2]
        def rates(r):
            return [(r['GREF']-r['SPEED']/c[0]-r['STATE0'])/c[1],
                    (r['STATE0']-r['STATE1'])/c[2], 0.,
                    (r['STATE0']-r['STATE3'])/c[12], 0.]
        last, prior = rates(pre), rates(older)
        off = [r for r in rows[trip:] if r['time_s'] <= manifest['reenable_probe']['before']['time'] + 1e-10]
        residual = 0.
        advanced = 0
        for j in range(1, len(off)):
            if off[j]['time_s'] == off[j-1]['time_s']:
                assert all(off[j][f'STATE{k}'] == off[j-1][f'STATE{k}'] for k in range(5))
                continue
            advanced += 1
            for k in range(5):
                expected = h*(1.5*last[k]-.5*prior[k]) if advanced == 1 else h*last[k]
                residual = max(residual, abs(off[j][f'STATE{k}']-off[j-1][f'STATE{k}']-expected))
            for name in ('PMECH', 'P_PU', 'Q_PU', 'EFD', 'GREF'):
                assert off[j][name] == 0.
        assert residual < 1e-7, residual  # Native stored-coordinate rounding envelope.
        assert off[-1]['STATE0']-pre['STATE0'] > .03
        assert off[-1]['GEN1_STATE5']-pre['GEN1_STATE5'] > .001
        after = [r for r in rows if r['time_s'] > manifest['reenable_probe']['after']['time']+h/2]
        if manifest['settings']['reenable_control']:
            assert all(r['PMECH'] == r['P_PU'] == r['Q_PU'] == 0. for r in after)
        else:
            assert after[0]['P_PU'] > .5
            assert abs(after[0]['PMECH']-off[-1]['STATE1']) < 1e-11
            assert all(r['GREF'] == r['EFD'] == 0. for r in after)
            assert abs(after[0]['PMECH']-pre['STATE1']) > .02
        # Physical current magnitude is reconstructible from the retained P/Q/V channels.
        current = (after[0]['P_PU']**2+after[0]['Q_PU']**2)**.5/after[0]['V1']
        results.append(dict(tag=folder.name, samples=len(rows), defined_channels_cold_pair_identical=True, unowned_vref_max_difference=vref_difference,
            inactive_rate_residual=residual, first_resumed_current_pu=current,
            states_preserved_at_request=True, java_reactivation_qualified=False))
    assert len(results) == 2
    return results


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('capture_root', type=Path)
    print(json.dumps(audit(parser.parse_args().capture_root), indent=2))
