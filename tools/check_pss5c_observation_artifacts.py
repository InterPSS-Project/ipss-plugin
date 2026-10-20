"""All-row observation diagnostics with fixed source identities, never fitted bounds."""
import csv
import hashlib
import json
import math
import struct
from pathlib import Path
from check_pss5c_pole_artifacts import checked_files
from pss5c_native_probe import configuration

PROFILES=('fine-observation-flat','fine-observation-fault','fine-observation-transducer-fault')
STATE_NAMES=('lowPosition','lowRate','highPosition','highRate','highAcceleration',
             'veryLowUpperLag','veryLowLowerLag','lowUpperLag','lowLowerLag',
             'intermediateUpperLag','intermediateLowerLag','highUpperLag','highLowerLag')
f32=lambda value:struct.unpack('f',struct.pack('f',value))[0]
sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()


def rows(path):
    values=list(csv.DictReader(path.read_text().splitlines()));assert values
    for row in values:
        assert all(v is not None and math.isfinite(float(v)) for v in row.values())
    return values


def aggregation(row,constants):
    low,high=sorted(constants[28:30])
    clip=lambda v:max(low,min(high,v))
    very_low,lo,mid,hi=(f32(float(row[k])) for k in ('PSS_VVL','PSS_VL','PSS_VI','PSS_VH'))
    three=f32(clip(f32(f32(lo+mid)+hi)))
    four=f32(clip(f32(f32(f32(very_low+lo)+mid)+hi)))
    actual=f32(float(row['VOTHSG']))
    return dict(three_band_equal=actual==three,four_band_equal=actual==four,
                three_band_error=abs(actual-three),four_band_error=abs(actual-four))


def canonical(row,p):
    raw=[float(row['PSS_STATE_'+str(i)]) for i in range(1,14)]
    return dict(zip(STATE_NAMES,[raw[0],raw[1],raw[2]-p[5]*raw[4]-p[6]*raw[3],p[7]*raw[3],p[7]*raw[4],*raw[5:]]))


def validate_clock(reference,step,api_end):
    assert f32(float(reference[0]['time_s']))==f32(-2*f32(step))
    assert f32(float(reference[-1]['time_s']))==f32(api_end)
    for before,after in zip(reference,reference[1:]):
        a,b=f32(float(before['time_s'])),f32(float(after['time_s']))
        assert math.isfinite(a) and math.isfinite(b) and b>=a
        if b!=a:assert b==f32(a+f32(step)), 'Exact native clock recurrence; no fitted shift'


def residuals(reference,p,replay=None):
    if replay is not None:assert len(reference)==len(replay)>0
    maxima={};three_bad=four_bad=0
    def note(key,error,index,time):
        assert math.isfinite(error)
        if key not in maxima or error>maxima[key]['max_abs_error']:
            maxima[key]=dict(max_abs_error=error,row=index,time_s=time)
    for index,row in enumerate(reference):
        time=float(row['time_s']);q=aggregation(row,p)
        three_bad+=not q['three_band_equal'];four_bad+=not q['four_band_equal']
        note('aggregate-three',q['three_band_error'],index,time);note('aggregate-four',q['four_band_error'],index,time)
        note('low-variable-source-identity',abs(float(row['PSS_LOW_SIGNAL'])-
             (float(row['PSS_STATE_1'])+p[0]*float(row['PSS_STATE_2']))),index,time)
        note('high-variable-source-identity',abs(float(row['PSS_HIGH_SIGNAL'])-p[7]*float(row['PSS_STATE_5'])),index,time)
        for band in range(4):
            signal=float(row['PSS_HIGH_SIGNAL' if band==3 else 'PSS_LOW_SIGNAL'])*p[8 if band==3 else 3]
            upper=float(row['PSS_STATE_'+str(6+2*band)]);lower=float(row['PSS_STATE_'+str(7+2*band)])
            top=upper+(p[26]/p[27])*(signal-upper);bottom=lower+(p[25]/p[26])*(signal-lower)
            offset=9+4*band;lo,hi=sorted(p[offset+2:offset+4]);expected=max(lo,min(hi,p[offset]*(top-bottom)))
            note('band-variable-source-identity-'+str(band),abs(expected-float(row[('PSS_VVL','PSS_VL','PSS_VI','PSS_VH')[band]])),index,time)
        if replay is not None:
            actual=replay[index];assert float(actual['time_s'])==time,'No fitted clock or removed event-side row'
            for key,value in canonical(row,p).items():note('replay-state-'+key,abs(float(actual[key])-value),index,time)
            for key,raw_key in (('low_signal','PSS_LOW_SIGNAL'),('high_signal','PSS_HIGH_SIGNAL'),
                                ('VVL','PSS_VVL'),('VL','PSS_VL'),('VI','PSS_VI'),('VH','PSS_VH'),('Vstab','VOTHSG')):
                note('replay-'+key,abs(float(actual[key])-float(row[raw_key])),index,time)
    return dict(rows=len(reference),negative_history_rows=sum(float(r['time_s'])<0 for r in reference),
                duplicate_boundary_rows=sum(a['time_s']==b['time_s'] for a,b in zip(reference,reference[1:])),
                three_band_value_mismatches=three_bad,four_band_value_mismatches=four_bad,
                maxima=maxima,trajectory_acceptance=False,raw_state_coordinates_certified=False)


def verify(folder):
    m=json.loads((folder/'manifest.json').read_text());assert m['status']=='native-converged-profile'
    checked_files(folder,m['files'])
    assert m['profile'] in PROFILES and m['step_s'] in (.00005,.000025)
    component=m['components']['STAB'];p=component['actual_constants'];layout=component['layout']
    assert component['model']=='PSS5C' and component['actual_icons']==[0]
    assert [layout['N'+key] for key in ('ICON','CON','STATE','VAR')]==[1,30,13,6]
    text=(folder/'dynamic.dyr').read_text();record=next(line.split() for line in text.splitlines() if "'PSS5C'" in line)
    assert len(record[3:-1])==30 and list(map(f32,map(float,record[3:-1])))==p
    log=(folder/'native.log').read_text(errors='replace')
    assert 'Initial conditions check O.K.' in log and 'Network not converged' not in log and 'Undefined Model' not in log
    assert m['actual_constants']==m['final_constants']
    reference=rows(folder/'native.csv');assert len(reference)==m['sample_count'] and len(reference[0])==44
    assert all(float(b['time_s'])>=float(a['time_s']) for a,b in zip(reference,reference[1:]))
    live=json.loads((folder/'live-states.json').read_text());snapshots=[]
    assert len(reference)>9000 and live[-1]['stage']=='interval-end'
    validate_clock(reference,m['step_s'],live[-1]['time_s'])
    for item in live:
        candidates=[r for r in reference if f32(float(r['time_s']))==f32(item['time_s'])]
        assert candidates,'Every API snapshot has its raw clock in the complete channel history'
        chosen=candidates[-1] if item['stage'].startswith('post-') else candidates[0]
        differences={key:abs(value-f32(float(chosen[key]))) for source in ('states','variables') for key,value in item[source].items()}
        snapshots.append(dict(stage=item['stage'],time_s=item['time_s'],max_abs_error=max(differences.values()),
                              mismatches=sum(error!=0 for error in differences.values())))
    return m,p,reference,snapshots


def check(root):
    raw=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
    replay_folder=root/'target/pss5c-observation-replay-v1';manifest=json.loads((replay_folder/'manifest.json').read_text())
    assert not manifest['trajectory_acceptance'] and len(manifest['results'])==12
    for item in manifest['results']:
        assert sha(replay_folder/item['replay'])==item['replay_sha256']
        assert sha(replay_folder/item['log'])==item['log_sha256']
    result=[];identity=None
    for profile in PROFILES:
        for scale in ('coarse','fine'):
            name=profile+'-'+scale
            folders=[root/'target'/('pss5c-native-observation-v1-'+name+'-'+tag) for tag in ('a','b')]
            entries=[verify(f) for f in folders];a,b=(v[0] for v in entries)
            assert a['profile']==b['profile']==profile
            assert a['components']['STAB']['actual_constants']==list(map(f32,configuration(profile,raw)['components'][1]['constants']))
            for key in ('components','actual_constants','final_constants','step_s','sample_count','channel_count','events',
                        'library_sha256','source_document_sha256','python_sha256','wrapper_sha256','engine_module'):
                assert a[key]==b[key],(name,key)
            current={key:a[key] for key in ('library_sha256','source_document_sha256','python_sha256','wrapper_sha256','engine_module')}
            if identity is None:identity=current
            else:assert current==identity
            for file in ('network.raw','dynamic.dyr','probe.py','genrou_native_probe.py','native.csv','live-states.json'):
                assert (folders[0]/file).read_bytes()==(folders[1]/file).read_bytes(),(name,file)
            outputs=[replay_folder/(name+'-'+str(repeat)+'.csv') for repeat in (1,2)]
            assert outputs[0].read_bytes()==outputs[1].read_bytes(),name
            values=residuals(entries[0][2],entries[0][1],rows(outputs[0]))
            assert values['negative_history_rows']==2
            assert values['duplicate_boundary_rows']==2 # Segmented run calls retain boundaries even for a flat case.
            assert values['three_band_value_mismatches']==0,'Fixed three-band hypothesis, no fitted subset or gain'
            result.append(dict(name=name,step_s=a['step_s'],requested_end_s=a['end_s'],
                               actual_last_time_s=float(entries[0][2][-1]['time_s']),api_snapshots=entries[0][3],**values))
    # Retain the earlier independently generated single-band counterexample.
    legacy=root/'target/pss5c-native-very-low-coarse-a';m=json.loads((legacy/'manifest.json').read_text())
    checked_files(legacy,m['files']);reference=rows(legacy/'native.csv');p=m['components']['STAB']['actual_constants']
    single=residuals(reference,p)
    # Zero-gain branches contradict BOTH simple aggregation formulas. Do not
    # generalize the nonzero-profile three-band identity into a model equation.
    assert single['three_band_value_mismatches']>0 and single['four_band_value_mismatches']>0
    assert all(float(r[k])==0 for r in reference for k in ('PSS_VL','PSS_VI','PSS_VH'))
    single['max_abs_very_low_variable']=max(abs(float(r['PSS_VVL'])) for r in reference)
    single['max_abs_machine_output']=max(abs(float(r['VOTHSG'])) for r in reference)
    assert single['max_abs_very_low_variable']>0 and single['max_abs_machine_output']>0
    return dict(cold_pairs=6,replay_pairs=6,engine_identity=identity,results=result,very_low_only=single,
                parameters_fitted=False,clock_shift_fitted=False,trajectory_acceptance=False)


if __name__=='__main__':print(json.dumps(check(Path.cwd()),indent=2))
