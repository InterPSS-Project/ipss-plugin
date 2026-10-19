"""Verify all recovered inputs and exact full-CSV cold/historical comparisons."""
import argparse
import csv
import io
import json
from pathlib import Path
import re
import subprocess
from recover_benchmark_inputs import canonical,digest


def hash_file(path):return digest(canonical(path.read_bytes(),path))


def audit(root,classes):
    recovered=root/'target/exciter-recovered-20261019';recovery=json.loads((recovered/'recovery.json').read_text());assert recovery['status']=='complete';assert len(recovery['benchmarks'])==11
    contracts=json.loads((root/'ipss.test.plugin.core/src/test/resources/exciter-input-contracts.json').read_text());by_name={item['name']:item for item in contracts['benchmarks']};result=[]
    def compare(left,right):
        text=subprocess.check_output(['java','-cp',str(classes),'org.interpss.core.dstab.reference.PowerWorldCsvRepeatability',str(left),str(right)],text=True)
        match=re.fullmatch(r'Summary\[samples=(\d+), channels=(\d+), repeatedTimeSamples=(\d+)\]\s*',text);assert match,text
        return dict(zip(['samples','channels','repeated_time_samples'],map(int,match.groups()),strict=True))
    for recovered_item in recovery['benchmarks']:
        name=recovered_item['name'];contract=by_name[name];workspace=recovered/name/'workspace';old=root/'ipss.test.plugin.core/testData/reference/powerworld'/name;original=json.loads((old/'manifest.json').read_text())
        assert contract['inputs']==original['inputs']
        for role,item in contract['inputs'].items():assert hash_file(workspace/item['path'])==item['sha256'],(name,role)
        assert hash_file(old/'powerworld.csv')==original['artifacts']['powerworld.csv']['sha256']
        directories=[]
        for repeat in ['a','b']:
            directory=root/'target'/('exciter-exact-'+name+'-'+repeat);manifest=json.loads((directory/'manifest.json').read_text())
            assert manifest['status']=='complete' and manifest['benchmark']==name and manifest['auto_correct'] is False
            assert not manifest.get('powerworld_model_overrides',[])
            assert manifest['inputs']==contract['inputs']
            assert manifest['result_selectors']==original['result_selectors'] and manifest['simulation']==original['simulation'] and manifest['contingency']==original['contingency']
            assert manifest['powerworld_executable']['sha256']=='06e5b23f5511440efa682000b63114f88c1dc919264ccfcc30cbca9adadcac0c'
            for key,item in manifest['artifacts'].items():assert hash_file(directory/key)==item['sha256'],(name,key)
            assert hash_file(directory/'inputs/network.raw')==contract['inputs']['raw']['sha256']
            assert hash_file(directory/'inputs/powerworld.dyr')==contract['inputs'].get('powerworld_dyr',contract['inputs']['dyr'])['sha256']
            for file,source in [('probe.py',root/'tools/replay_recovered_exciter.py'),('runner.py',root/'ipss.test.plugin.core/src/test/python/powerworld_transient_benchmark.py')]:
                assert (directory/file).read_bytes()==source.read_bytes(),(name,file)
            progress=(directory/'progress.txt').read_text()
            for marker in ['START','RAW_LOADED','DYR_LOADED','POWER_FLOW_SOLVED','CONTINGENCY_CREATED','VALIDATED','INITIALIZED','SOLVED','EXPORTED','FINALIZED']:assert marker+';' in progress
            directories.append(directory)
        summary=compare(directories[0]/'powerworld.csv',directories[1]/'powerworld.csv')
        assert summary['samples']==2003 and summary['repeated_time_samples']==2
        text=subprocess.check_output(['java','-cp',str(classes),'org.interpss.core.dstab.reference.PowerWorldCsvRepeatability','--differences',str(old/'powerworld.csv'),str(directories[0]/'powerworld.csv')],text=True)
        differences=[dict(column=int(row['column']),changed_samples=int(row['changed_samples']),max_abs_error=float(row['max_abs_error']),time_s=float(row['time_s'])) for row in csv.DictReader(io.StringIO(text))]
        assert len(differences)==summary['channels']
        with (old/'powerworld.csv').open(newline='') as stream:metadata=list(csv.reader(stream))
        end=metadata.index(['END']);fields=metadata[2:end];assert len(fields)==len(differences)
        for difference,field in zip(differences,fields,strict=True):difference.update(object_type=field[0],primary_key=field[1],variable=field[4],column_header=field[5])
        identical=all(row['changed_samples']==0 for row in differences)
        if identical:assert summary==compare(old/'powerworld.csv',directories[0]/'powerworld.csv')
        result.append(dict(name=name,cold_runs=2,**summary,historical_numerically_identical=identical,historical_differences=differences,historical_executable_sha256=original['powerworld_executable']['sha256'],current_executable_sha256=manifest['powerworld_executable']['sha256']))
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--classes',type=Path,required=True);args=parser.parse_args();print(json.dumps(audit(Path.cwd(),args.classes),indent=2))
