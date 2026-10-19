"""Audit cold compensation references, machine bases and initialization meaning."""
import json
import math
from pathlib import Path
import struct
from audit_pss_canonical_schema import checked_files, digest, input_values, table
from pss_canonical_schema_probe import LAYOUTS


def initial_compensation(p_system,q_system,voltage,mva,xcomp,tcomp):
    assert all(math.isfinite(value) for value in [p_system,q_system,voltage,mva,xcomp,tcomp])
    assert mva>0 and voltage>0 and tcomp>0
    factor=mva/100
    return math.atan2(p_system*xcomp/factor,voltage*voltage+q_system*xcomp/factor)/tcomp


def audit(root):
    result=[]
    for model, layout in LAYOUTS.items():
        for profile, mva, xcomp, tcomp in [('mva50-positive',50.,.2,.02),('mva200-negative',200.,-.2,.1)]:
            for scale, step in [('coarse',.0005),('fine',.00025)]:
                name=model.lower()+'-'+profile+'-'+scale; histories=[]
                for repeat in ('a','b'):
                    directory=root/'target'/('pss-comp-'+name+'-'+repeat)
                    manifest=json.loads((directory/'manifest.json').read_text());checked_files(directory,manifest)
                    assert manifest['status']=='complete' and manifest['model']==model and manifest['profile']==profile
                    assert (manifest['machine_mva'],manifest['xcomp'],manifest['tcomp'],manifest['step_s'],manifest['end_s'])==(mva,xcomp,tcomp,step,.3)
                    assert tuple(manifest['layout']['N'+kind] for kind in ('ICON','CON','STATE','VAR'))==layout
                    assert manifest['library_sha256']=='c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6'
                    assert manifest['source_document_sha256']=='40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d'
                    assert digest(directory/'probe.py')==digest(root/'tools/pss_compensation_probe.py')
                    icons, constants=input_values(directory/'dynamic.dyr',model)
                    assert icons==manifest['icons'] and constants[-2:]==[xcomp,tcomp]
                    assert [struct.unpack('f',struct.pack('f',value))[0] for value in constants]==manifest['constants']
                    original=root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB'
                    original_icons,original_constants=input_values(original/('SMIB_v33_genrou_esst1a_'+model.lower()+'.dyr'),model)
                    assert icons==original_icons and constants[:-2]==original_constants[:-2]
                    actual=(directory/'network.raw').read_text().splitlines();source=(original/'SMIB_v33.raw').read_text().splitlines()
                    indices=[i for i,line in enumerate(source) if line.startswith("1,'1',50.001")];assert len(indices)==1
                    index=indices[0];fields=actual[index].split(',');assert float(fields[8])==mva;fields[8]=source[index].split(',')[8];actual[index]=','.join(fields);assert actual==source
                    labels,rows=table(directory/'native.csv');width=layout[2]+12
                    assert len(labels)==width+1 and manifest['channel_count']==width
                    assert len(rows)==manifest['sample_count']==(605 if scale=='coarse' else 1205)
                    assert rows[0][0]<0 and abs(rows[-1][0]-.3)<step
                    assert all(a[0]<=b[0] for a,b in zip(rows,rows[1:]))
                    for event,target in zip(manifest['events'],(.05,.1),strict=True):assert abs(event['actual_time_s']-target)<1e-6
                    first=dict(zip(labels,rows[0],strict=True));factor=mva/100
                    p,q,v=first['P_PU']/factor,first['Q_PU']/factor,first['V_BUS1']
                    expected=initial_compensation(first['P_PU'],first['Q_PU'],v,mva,xcomp,tcomp)
                    init_error=abs(first[f'PSS_STATE_{layout[2]:02d}']-expected)
                    assert init_error<8e-6,(name,init_error)
                    expected_input=constants[1]*p if model=='PSS6C' else p
                    assert abs(first['PSS_STATE_03' if model=='PSS6C' else 'PSS_STATE_04']-expected_input)<2e-6
                    log=(directory/'native.log').read_text(errors='replace');assert 'Initial conditions check O.K.' in log and 'Network not converged' not in log
                    assert min(row[labels.index('V_BUS1')] for row in rows)<.95
                    histories.append((labels,rows))
                assert histories[0]==histories[1]
                result.append(dict(name=name,cold_runs=2,samples=len(rows),channels=width,initial_compensation_error=init_error))
    return result


if __name__=='__main__': print(json.dumps(audit(Path.cwd()),indent=2))
