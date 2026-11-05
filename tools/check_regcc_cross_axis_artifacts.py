"""Audit paired discriminators for the active-current integral clipping transition."""
import argparse,collections,csv,hashlib,json,math
from pathlib import Path

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--roots',type=Path,nargs=2,required=True)
    parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    groups=collections.defaultdict(list);libraries=set();documents=set()
    for root in args.roots:
        probes=set()
        for p in sorted(root.glob('regcc-*/manifest.json')):
            m=json.loads(p.read_text());assert m['status']=='complete'
            assert m['layout']==dict(ICON=2,CON=13,STATE=9,VAR=10)
            assert m['entered_constants']==m['initialized_constants'];assert m['channels']==27
            for name,digest in m['files'].items():assert sha(p.parent/name)==digest,(p,name)
            probes.add(m['files']['regcc_reference_probe.py']);libraries.add(m['library_sha256']);documents.add(m['source_document_sha256'])
            rows=[{k:float(v) for k,v in r.items()} for r in csv.DictReader((p.parent/'native.csv').open())]
            assert len(rows)==m['samples'] and all(len(r)==28 and all(math.isfinite(v) for v in r.values()) for r in rows)
            assert sum(r['time_s']<0 for r in rows)==2
            for readback in m['direct_readback']:
                # A zero-time call does not record a new post-intervention endpoint.
                if readback['time_s']<=.0501:continue
                matching=[r for r in rows if abs(r['time_s']-readback['time_s'])<1e-9]
                assert any(all(abs(r['STATE'+str(i)]-readback['states'][i])<1e-9 for i in range(9)) for r in matching),(p,readback['time_s'])
            groups[(m['profile'],m['variant'])].append((p,m,rows))
        assert len(probes)==1
    assert len(groups)==19 and all(len(v)==2 for v in groups.values())
    assert len(libraries)==len(documents)==1
    results=[];by_case={}
    for (profile,variant),pairs in sorted(groups.items()):
        pairs.sort(key=lambda x:x[1]['repeat']);(_,m,rows),(_,n,other)=pairs
        assert [m['repeat'],n['repeat']]==['a','b']
        for name in ['network.raw','dynamic.dyr','native.csv']:assert m['files'][name]==n['files'][name],(profile,variant,name)
        assert m['direct_readback']==n['direct_readback']
        result=dict(profile=profile,variant=variant,priority=m['icons'][1],state_start=m['indices']['STATE'],samples=m['samples'],csv_sha256=m['files']['native.csv'])
        perturbation=m['state_perturbation'];bound=m['entered_constants'][4]
        if perturbation is not None:
            q=perturbation['after'][0];initial=perturbation['after'][1]
            first=next(r for r in m['direct_readback'] if r['time_s']>.0501)
            clipped=abs(initial)>bound+1e-7
            expected=q if clipped else perturbation['integration_memory_value']
            assert abs(first['states'][1]-expected)<1e-7,(variant,first['states'][1],expected)
            result.update(entered_reactive_integral=q,entered_active_integral=initial,
                          entered_active_memory=perturbation['integration_memory_value'],clipping_required=clipped,
                          first_endpoint_s=first['time_s'],first_active_integral=first['states'][1],first_reactive_integral=first['states'][0],
                          copies_reactive_history=clipped,direct_state_api_matches_csv=True)
        else:
            assert profile=='limit-q' and not m['direct_readback']
            jumps=[]
            for a,b in zip(rows,rows[1:]):
                if abs(b['STATE1']-a['STATE1'])<=.01:continue
                assert abs(b['STATE1']-a['STATE0'])<1e-10
                assert abs(a['STATE1']-bound)<1e-7
                ordinary=m['step_s']*m['entered_constants'][7]*(abs(a['STATE4'])+bound+1e-5)
                jump=abs(b['STATE1']-a['STATE1']);assert jump>100*ordinary
                jumps.append(dict(time_s=b['time_s'],active_before=a['STATE1'],reactive_before=a['STATE0'],
                    active_after=b['STATE1'],reactive_after=b['STATE0'],absolute_jump=jump,
                    ordinary_integral_change_bound=ordinary,ratio=jump/ordinary,pll_angle_change_rad=b['STATE8']-a['STATE8']))
            assert len(jumps)==1;jump=jumps[0];assert abs(jump['pll_angle_change_rad'])<.001
            result.update(no_state_edits=True,natural_jumps=jumps)
        results.append(result);by_case[(profile,variant)]=(m,result)
    upper=by_case[('audit-ip-upper','q0p13-overshoot')][1]
    assert upper['first_active_integral']==by_case[('audit-ip-upper','overshoot-memory')][1]['first_active_integral']
    assert upper['first_active_integral']==by_case[('audit-ip-upper','overshoot-p-priority')][1]['first_active_integral']
    exact=by_case[('audit-ip-upper','q0p13-exact')][0]
    reordered=by_case[('audit-ip-upper','reordered')][0]
    assert exact['files']['native.csv']==reordered['files']['native.csv']
    assert exact['indices']==reordered['indices'] # Reordering records did not reindex this engine's states.
    for variant in ['inside','inside-no-zero','exact-no-zero']:
        assert not by_case[('audit-ip-upper',variant)][1]['copies_reactive_history']
    summary=dict(status='paired-discriminator-audit-passed',native_runs=38,cold_pairs=19,channels_per_run=27,
        results=results,conclusion='The active PI clipping branch uses the preceding reactive history instead of retaining its own history.',
        interpretation='Likely wrong-state/history indexing in the reference implementation; exact internal code and vendor confirmation unavailable.',
        limitations=['This does not identify the private source line or prove which internal array is used.',
                     'The order-control records retained identical state indices; no reindexed multi-converter case was exercised.',
                     'State and memory perturbations are diagnostic interventions, not physical disturbances; two natural fault controls have no such edits.'])
    args.output.write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary,indent=2))

if __name__=='__main__':main()
