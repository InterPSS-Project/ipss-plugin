"""Run an exact recovered exciter specification without modifying old artifacts."""
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys


def main():
    root=Path.cwd();name,repeat=sys.argv[1:]
    contracts=json.loads((root/'ipss.test.plugin.core/src/test/resources/exciter-input-contracts.json').read_text())
    contract=next(item for item in contracts['benchmarks'] if item['name']==name)
    assert repeat in ('a','b')
    recovered=root/'target/exciter-recovered-20261019';recovery=json.loads((recovered/'recovery.json').read_text())
    assert any(item['name']==name for item in recovery['benchmarks'])
    workspace=recovered/name/'workspace';spec=workspace/contract['inputs']['spec']['path']
    out=root/'target'/('exciter-exact-'+name+'-'+repeat);out.mkdir(exist_ok=False)
    shutil.copy2(__file__,out/'probe.py');shutil.copy2(root/'ipss.test.plugin.core/src/test/python/powerworld_transient_benchmark.py',out/'runner.py')
    module=importlib.util.spec_from_file_location('recovered_exciter_runner',out/'runner.py');runner=importlib.util.module_from_spec(module);module.loader.exec_module(runner)
    original=subprocess.Popen
    def hidden(*args,**kwargs):
        startup=subprocess.STARTUPINFO();startup.dwFlags|=subprocess.STARTF_USESHOWWINDOW;startup.wShowWindow=subprocess.SW_HIDE;kwargs.setdefault('startupinfo',startup);return original(*args,**kwargs)
    runner.subprocess.Popen=hidden
    print(runner.run(spec,workspace,out,'C:/Program Files/PowerWorld/Simulator Education-Evalution 24/pwrworld.exe',90,False))


if __name__=='__main__':main()
