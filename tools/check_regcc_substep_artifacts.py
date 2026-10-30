"""Audit paired fixed-coefficient references and discriminate the current PI limits."""
import argparse,base64,csv,gzip,hashlib,json,math
from pathlib import Path

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def rows(path):return [{k:float(v) for k,v in row.items()} for row in csv.DictReader(path.open())]

def pair(root,profile,step):
    folders=[root/f'regcc-{profile}-h{step:g}-{repeat}' for repeat in ['a','b']]
    manifests=[]
    for folder in folders:
        m=json.loads((folder/'manifest.json').read_text());assert m['status']=='complete'
        assert m['layout']==dict(ICON=2,CON=13,STATE=9,VAR=10)
        assert m['entered_constants']==m['initialized_constants']
        for name,digest in m['files'].items():assert sha(folder/name)==digest,(folder,name)
        data=rows(folder/'native.csv');assert len(data)==m['samples'] and m['channels']==27
        assert all(len(r)==28 and all(math.isfinite(v) for v in r.values()) for r in data)
        assert sum(r['time_s']<0 for r in data)==2
        assert sum(a['time_s']==b['time_s'] for a,b in zip(data,data[1:]))==2
        manifests.append(m)
    for name in ['network.raw','dynamic.dyr','native.csv']:
        assert manifests[0]['files'][name]==manifests[1]['files'][name],(profile,step,name)
    return folders[0],manifests[0],rows(folders[0]/'native.csv')

def pi_residual(data,constants):
    sq=[0.,0.];sq_i=[0.,0.];count=0;clipped=0
    for a,b,c in zip(data,data[1:],data[2:]):
        # Central differences only on smooth interior triples: trajectory comparisons retain all rows.
        if min(b['time_s']-a['time_s'],c['time_s']-b['time_s'])<.0002 or not .052<b['time_s']<.098:continue
        theta=math.radians(b['A1_DEG'])-b['STATE8'];vd=b['V1']*math.cos(theta)
        error_d=b['STATE4']-b['VAR8'];error_q=b['STATE3']-b['VAR9']
        ip=b['STATE1']+constants[6]*error_d;iq=b['STATE0']+constants[6]*error_q
        bound=constants[4];limit=lambda x:max(-bound,min(bound,x))
        clipped+=int(abs(ip)>bound or abs(iq)>bound)
        derivative=(c['STATE6']-a['STATE6'])/(c['time_s']-a['time_s'])
        integral=(c['STATE0']-a['STATE0'])/(c['time_s']-a['time_s'])
        for i,(p,q) in enumerate([(ip,iq),(limit(ip),limit(iq))]):
            rhs=(vd+.01*p+.1*q-b['STATE6'])/constants[5]
            sq[i]+=(derivative-rhs)**2
        sq_i[0]+=(integral-constants[7]*error_q)**2
        sq_i[1]+=(integral-constants[7]*(error_q+limit(iq)-iq))**2
        count+=1
    return dict(smooth_triples=count,output_clipped_triples=clipped,
                internal_voltage_derivative_rms_raw_vs_clipped=[math.sqrt(s/count) for s in sq],
                current_integral_derivative_rms_plain_vs_backcalculation=[math.sqrt(s/count) for s in sq_i])

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--root',type=Path,required=True)
    parser.add_argument('--audit-root',type=Path,required=True);parser.add_argument('--stage',action='store_true');args=parser.parse_args()
    profiles=[];manifests=[];residuals={};audits=[]
    destination=Path('ipss.test.plugin.core/src/test/resources/regcc/substeps')
    if args.stage:destination.mkdir(exist_ok=True)
    for profile in ['fault','high-gain','minimum']:
        for step in [1/240,1/1920,1/3840]:
            folder,m,data=pair(args.root,profile,step);manifests.append(m)
            assert m['prepared_step_s']==1/240
            assert max(r['STATE3'] for r in data)-min(r['STATE3'] for r in data)<2e-6
            assert max(r['STATE4'] for r in data)-min(r['STATE4'] for r in data)<2e-6
            assert len(data)==round(.2/step)+5
            info=dict(profile=profile,step_s=step,samples=len(data),sha256=m['files']['native.csv'])
            profiles.append(info)
            if step==1/3840:
                residuals[profile]=pi_residual(data,m['entered_constants'])
                if args.stage:
                    for source,suffix in [('network.raw','.raw.b64'),('dynamic.dyr','.dyr.b64'),('native.csv','.csv.gz.b64')]:
                        body=(folder/source).read_bytes()
                        if source.endswith('.csv'):body=gzip.compress(body,mtime=0)
                        (destination/(profile+suffix)).write_bytes(base64.encodebytes(body))
    for profile in ['audit-iq-upper','audit-iq-lower','audit-ip-upper','audit-ip-lower']:
        _,m,data=pair(args.audit_root,profile,.0005);manifests.append(m)
        boundary=[r for r in data if .0499<r['time_s']<.0511]
        coordinate='STATE'+str(m['state_perturbation']['offset'])
        assert abs(abs(boundary[1][coordinate])-.26)<1e-7
        audits.append(dict(profile=profile,perturbation=m['state_perturbation'],boundary_rows=boundary))
    assert len({m['files']['regcc_reference_probe.py'] for m in manifests})==1
    assert len({m['library_sha256'] for m in manifests})==1
    assert residuals['high-gain']['internal_voltage_derivative_rms_raw_vs_clipped'][0]>100*residuals['high-gain']['internal_voltage_derivative_rms_raw_vs_clipped'][1]
    assert residuals['high-gain']['current_integral_derivative_rms_plain_vs_backcalculation'][1]>10*residuals['high-gain']['current_integral_derivative_rms_plain_vs_backcalculation'][0]
    summary=dict(status='paired-artifact-integrity-passed',reference_runs=18,integral_boundary_runs=8,
                 profiles=profiles,pi_equation_discriminator=residuals,integral_boundary_discriminators=audits,
                 probe_sha256=manifests[0]['files']['regcc_reference_probe.py'])
    (args.root/'verified-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
    if args.stage:(destination/'profiles.json').write_text(json.dumps([p for p in profiles if p['step_s']==1/3840],indent=2)+'\n')
    print(json.dumps(summary,indent=2))

if __name__=='__main__':main()
