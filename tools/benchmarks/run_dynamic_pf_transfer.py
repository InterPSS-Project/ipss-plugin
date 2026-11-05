"""Run matched dynamic preparation/operator trials with complete selected recordings."""
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream,'sha256').hexdigest()

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--classpath-report',type=Path,required=True)
    parser.add_argument('--config',type=Path,default=Path(__file__).with_name('texas7k_single_cpu.json'))
    parser.add_argument('--cpu',type=int,default=6)
    parser.add_argument('--warmups',type=int,default=4)
    parser.add_argument('--measurements',type=int,default=5)
    parser.add_argument('--small-warmups',type=int,default=32)
    parser.add_argument('--small-measurements',type=int,default=15)
    parser.add_argument('--overlay',type=Path,help='Compiled maintained benchmark driver; production classes remain on the verified classpath')
    parser.add_argument('--bounded-child',action='store_true',help=argparse.SUPPRESS)
    args=parser.parse_args()
    if os.name!='nt':parser.error('Windows affinity verification is required')
    if min(args.warmups,args.measurements,args.small_warmups,args.small_measurements)<1 or args.cpu<0:parser.error('Positive run counts and a valid CPU are required')
    output=args.output.absolute()
    if output.exists():parser.error('Refusing to overwrite evidence')
    output.parent.mkdir(parents=True,exist_ok=True)
    if not args.bounded_child:
        return subprocess.call([sys.executable,str(ROOT/'tools/run_bounded.py'),'--log',str(ROOT/'ipss.test.plugin.core/target'/f'{output.name}.log'),'--',sys.executable,str(Path(__file__).resolve()),*sys.argv[1:],'--bounded-child'],cwd=ROOT)
    inputs=args.input.absolute()
    for name in ('Texas7k_20210804.RAW','Texas7k_20210804.dyr','Texas7k_20210804_basegen.bsg'):
        if not (inputs/name).is_file():parser.error(f'Missing input: {name}')
    suite=ET.parse(args.classpath_report).getroot()
    if int(suite.get('tests','0'))<1 or any(int(suite.get(k,'0')) for k in ('failures','errors','skipped')):
        parser.error('A passing, non-skipped DynamicPowerFlowTransferTest report is required')
    cp=next(p.get('value') for p in suite.findall('.//property') if p.get('name')=='java.class.path')
    if args.overlay is not None:cp=str(args.overlay.absolute())+os.pathsep+cp
    entries=[Path(x) for x in cp.split(os.pathsep) if x]
    config=args.config.absolute();flags=json.loads(config.read_text())['optimization_properties']
    java=Path(os.environ['JAVA_HOME'])/'bin/java.exe' if 'JAVA_HOME' in os.environ else Path(shutil.which('java') or 'java')
    command=[str(java),'-Xmx2g','-XX:ActiveProcessorCount=1','-XX:+UseSerialGC','-Xbatch',*[f'-D{k}={v}' for k,v in flags.items()],'-cp',cp,'org.interpss.core.dstab.DynamicPowerFlowTransferBenchmark',str(inputs),str(output),str(config),str(args.warmups),str(args.measurements),str(args.small_warmups),str(args.small_measurements)]
    spec=importlib.util.spec_from_file_location('affinity',Path(__file__).with_name('run_texas7k.py'))
    affinity=importlib.util.module_from_spec(spec);spec.loader.exec_module(affinity)
    observed=affinity.run_windows(command,ROOT/'ipss.test.plugin.core',args.cpu)
    refs={name:subprocess.check_output(['git','-C',str(repo),'rev-parse','HEAD'],text=True).strip() for name,repo in [('core',ROOT.parent/'core'),('plugin',ROOT)]}
    for name,repo in [('core',ROOT.parent/'core'),('plugin',ROOT)]:
        (output/f'{name}-working.patch').write_bytes(subprocess.check_output(['git','-C',str(repo),'diff','HEAD','--binary']))
        untracked=subprocess.check_output(['git','-C',str(repo),'ls-files','--others','--exclude-standard','-z']).decode().split('\0')
        for relative in untracked:
            if relative.endswith('.java'):
                source=repo/relative;target=output/'untracked-sources'/name/relative
                target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(source.read_bytes())
    directory_receipts={str(base):{str(p.relative_to(base)):digest(p) for p in sorted(base.rglob('*')) if p.is_file()} for base in entries if base.is_dir()}
    (output/'directory-runtime.json').write_text(json.dumps(directory_receipts,indent=2)+'\n',encoding='utf-8')
    metadata={'affinity':observed,'command':command,'source_commits':refs,'classpath_report_sha256':digest(args.classpath_report),
              'configuration_sha256':digest(config),'inputs':{p.name:digest(p) for p in inputs.iterdir() if p.is_file() and p.suffix.lower() in ('.raw','.dyr','.bsg','.idv')},
              'dependency_jars':{str(p):digest(p) for p in entries if p.is_file()},'directory_runtime_sha256':digest(output/'directory-runtime.json')}
    (output/'launch.json').write_text(json.dumps(metadata,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'status':'completed','output':str(output),'logical_cpu':args.cpu,'warmups_per_mode':args.warmups,'measured_pairs':args.measurements,'small_warmups_per_mode':args.small_warmups,'small_measured_pairs':args.small_measurements}))
    return 0

if __name__=='__main__':sys.exit(main())
