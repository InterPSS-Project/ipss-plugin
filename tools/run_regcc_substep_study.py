"""Run complete local-integration studies using the current reactor test classpath."""
import argparse,ctypes,hashlib,json,os,subprocess,xml.etree.ElementTree as E
from pathlib import Path

def affinity(cpu):
    api=ctypes.WinDLL('kernel32',use_last_error=True)
    api.GetCurrentProcess.restype=ctypes.c_void_p
    api.SetProcessAffinityMask.argtypes=[ctypes.c_void_p,ctypes.c_size_t]
    api.GetProcessAffinityMask.argtypes=[ctypes.c_void_p,ctypes.POINTER(ctypes.c_size_t),ctypes.POINTER(ctypes.c_size_t)]
    process=api.GetCurrentProcess()
    if cpu is not None:
        if cpu<0 or not api.SetProcessAffinityMask(process,ctypes.c_size_t(1<<cpu)):raise ctypes.WinError(ctypes.get_last_error())
    current,system=ctypes.c_size_t(),ctypes.c_size_t()
    if not api.GetProcessAffinityMask(process,ctypes.byref(current),ctypes.byref(system)):raise ctypes.WinError(ctypes.get_last_error())
    if cpu is not None:assert current.value==1<<cpu
    return current.value

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--reference-root',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True);parser.add_argument('--mode',choices=['accuracy','timing'],required=True)
    parser.add_argument('--network-interval',type=float,default=1/240);parser.add_argument('--cpu',type=int)
    args=parser.parse_args();root=Path.cwd();args.output.mkdir(exist_ok=False)
    report=root/'ipss.test.plugin.core/target/surefire-reports/TEST-org.interpss.core.adapter.builder.dstab.RegccReferenceTest.xml'
    cp=next(v.get('value') for v in E.parse(report).getroot().findall('./properties/property') if v.get('name')=='java.class.path')
    java=Path(os.environ['JAVA_HOME'])/'bin/java.exe';before=affinity(args.cpu)
    command=[str(java),'-Xmx2g','-cp',cp,'org.interpss.core.adapter.builder.dstab.RegccSubstepStudy',
             str(args.reference_root.resolve()),str(args.output.resolve()),args.mode,str(args.network_interval)]
    result=subprocess.run(command,check=False);after=affinity(args.cpu);assert before==after
    files={str(p.relative_to(root)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest()
           for p in [root/'ipss.plugin.core/target/classes/org/interpss/dstab/renewable/RegccModel.class',
                     root/'ipss.test.plugin.core/target/test-classes/org/interpss/core/adapter/builder/dstab/RegccSubstepStudy.class']}
    (args.output/'runtime.json').write_text(json.dumps(dict(status='completed' if result.returncode==0 else 'failed',
        mode=args.mode,network_interval_s=args.network_interval,prepared_network_interval_s=1/240,
        cpu=args.cpu,affinity_before=before,affinity_after=after,heap='-Xmx2g',class_hashes=files,
        java_version=subprocess.check_output([str(java),'-version'],stderr=subprocess.STDOUT,text=True)),indent=2)+'\n')
    if result.returncode:raise SystemExit(result.returncode)

if __name__=='__main__':main()
