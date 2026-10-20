"""Audit whole raw BASOEL2 exports; report nonrepeatable channels without filtering them."""
import csv
import hashlib
import json
import math
from pathlib import Path
from basoel2_native_probe import PROFILES


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def read(path):
    with Path(path).open(newline='') as stream:
        reader=csv.reader(stream);header=next(reader);rows=[list(map(float,row)) for row in reader]
    if len(header)!=len(set(header)) or not rows:
        raise ValueError('Missing rows or duplicate channel names')
    if any(len(row)!=len(header) or not all(map(math.isfinite,row)) for row in rows):
        raise ValueError('Nonfinite or malformed raw sample')
    if any(a[0]>b[0] for a,b in zip(rows,rows[1:])):
        raise ValueError('Nonmonotonic raw clock')
    return header,rows


def compare(a,b):
    ha,ra=read(a);hb,rb=read(b)
    if ha!=hb or len(ra)!=len(rb):
        raise ValueError('Cold export shape changed')
    result={}
    for index,name in enumerate(ha):
        errors=[abs(x[index]-y[index]) for x,y in zip(ra,rb)]
        if any(errors):
            result[name]=dict(maximum=max(errors),different_rows=sum(value!=0 for value in errors))
    return dict(rows=len(ra),columns=len(ha),whole_csv_identical=digest(a)==digest(b),
                numerical_differences=result,negative_time_rows=sum(row[0]<0 for row in ra),
                duplicate_time_rows=sum(x[0]==y[0] for x,y in zip(ra,ra[1:])))


def verify_physical_invariants(profile,header,rows):
    def values(name):return [row[header.index(name)] for row in rows]
    result={}
    if profile=='ifd-zero-filter':
        errors=[abs(a-b) for a,b in zip(values('BAS_INPUT'),values('XAD_IFD'))]
        result['maximum_zero_filter_ifd_identity_error']=max(errors)
        if any(errors):raise ValueError('Zero-filter Ifd input differs from the physical machine channel')
    if profile=='disabled-fault':
        for name in ('BAS_OEL_OUTPUT','BAS_VHZ_OUTPUT','VOEL_MACHINE'):
            if any(values(name)):raise ValueError('Disabled limiter output is not zero')
        result['disabled_outputs_identically_zero']=True
    return result


def audit(root):
    root=Path(root);pairs=[];total=0
    for profile in PROFILES:
        for scale in ('coarse','fine'):
            paths=[root/('genrou-native-basoel2-final-v1-'+profile+'-'+scale+'-'+repeat) for repeat in ('a','b')]
            for folder in paths:
                manifest=json.loads((folder/'manifest.json').read_text())
                if manifest['status']!='native-converged-profile':raise ValueError('Reference did not converge')
                for name,expected in manifest['files'].items():
                    if Path(name).name!=name or digest(folder/name)!=expected:
                        raise ValueError('Reference file hash or local path is invalid: '+name)
                header,rows=read(folder/'native.csv')
                if len(rows)!=manifest['sample_count'] or len(header)-1!=manifest['channel_count']:
                    raise ValueError('Reference sample/channel counts disagree')
                invariants=verify_physical_invariants(profile,header,rows)
                component=manifest['components']['MAXXL']
                if tuple(component['layout'][key] for key in ('NCON','NICON','NSTATE','NVAR'))!=(12,15,3,9):
                    raise ValueError('Unexpected model allocation')
                if len(component['actual_icons'])!=3 or component['reserved_icon_count']!=12:
                    raise ValueError('Entered and internal selectors were conflated')
                if any(len(component[key])!=12 for key in ('reserved_icons_initial','reserved_icons_final')):
                    raise ValueError('Internal selector provenance is incomplete')
            result=compare(*(p/'native.csv' for p in paths));total+=result['rows']*2
            result.update(profile=profile,scale=scale,runs=[p.name for p in paths],
                          csv_sha256=[digest(p/'native.csv') for p in paths],physical_invariants=invariants)
            pairs.append(result)
    return dict(status='reference-repeatability-diagnostic-not-trajectory-acceptance',
                pairs=pairs,raw_rows=total,full_system_dynamics_run=False)


if __name__=='__main__':
    import sys
    print(json.dumps(audit(sys.argv[1] if len(sys.argv)>1 else 'target'),indent=2))
