"""Stage only the five case inputs and a hashed fixed-response oracle in private test data."""
import argparse, hashlib, json, shlex, shutil
from pathlib import Path

FILES=tuple('Texas7k_20210804'+suffix for suffix in ('.RAW','.dyr','_basegen.bsg','_gnet.idv','_MODREMOVE.idv'))

def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def stage(source, destination):
    source=Path(source);destination=Path(destination)
    for name in FILES:
        if not (source/name).is_file():raise ValueError('Missing input: '+name)
        target=destination/name
        if target.exists() and digest(target)!=digest(source/name):
            raise ValueError('Refusing to replace changed private input: '+name)
    aux=source/'Texas7k_20210804_dynamics.AUX'
    oracle={}
    if aux.is_file():
        inside=False
        for line in aux.read_text().splitlines():
            if line.startswith('DATA (Gen, [BusNum,GenID,GenMVABase,TSGovRespLimit]'):
                inside=True;continue
            if inside and line.strip()=='}':break
            if inside and line.strip() not in ('','{'):
                fields=shlex.split(line)
                if len(fields)!=4:raise ValueError('Unexpected generator response oracle row')
                oracle[fields[0]+'/'+fields[1]]=fields[3]
        listed={shlex.split(line)[0]+'/'+shlex.split(line)[3] for line in (source/FILES[2]).read_text().splitlines() if line.strip()}
        fixed={key for key,value in oracle.items() if value=='Fixed'}
        if listed!=fixed:raise ValueError('BSG entries differ from the companion fixed-response oracle')
    destination.mkdir(parents=True,exist_ok=True)
    for name in FILES:
        if not (destination/name).exists():shutil.copy2(source/name,destination/name)
    manifest=dict(dataset='Texas7k_20210804',files={name:digest(destination/name) for name in FILES})
    if oracle:
        manifest['response_oracle']=dict(source=aux.name,sha256=digest(aux),generator_count=len(oracle),
            fixed_count=len(listed),fixed_generators=sorted(listed))
    (destination/'source-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    return manifest

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('source',type=Path)
    parser.add_argument('--destination',type=Path,default=Path('ipss.test.plugin.core/testData/private/texas7k'))
    args=parser.parse_args();manifest=stage(args.source,args.destination)
    print(json.dumps(dict(inputs=len(manifest['files']),fixed_generators=manifest.get('response_oracle',{}).get('fixed_count'))))
