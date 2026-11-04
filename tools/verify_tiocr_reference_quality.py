"""Read-only quality audit of retained relay captures; not a trajectory acceptance test."""
import argparse
import csv
import hashlib
import io
import json
import math
from pathlib import Path, PurePosixPath
import re
import zipfile

MAX_ENTRY_BYTES=512*1024*1024
FAILURES=(
    ('invalid-load-action',re.compile(r'TIOCR1 circuit[^\n]*invalid load specified',re.I)),
    ('network-not-converged',re.compile(r'Network not converged',re.I)),
    ('incomplete-input-record',re.compile(r'Incomplete data record',re.I)),
)

def assess(log, table):
    reasons=[]
    if 'Initial conditions check O.K.' not in log:
        reasons.append('missing-positive-initialization-check')
    for label,pattern in FAILURES:
        if pattern.search(log):reasons.append(label)
    rows=duplicates=0
    previous=None
    channel_count=0
    try:
        reader=csv.reader(io.StringIO(table))
        header=next(reader)
        if len(header)<2 or header[0]!='time_s' or len(set(header))!=len(header):
            reasons.append('invalid-channel-header')
        channel_count=len(header)-1
        for fields in reader:
            rows+=1
            if len(fields)!=len(header):
                reasons.append('incomplete-channel-row');break
            values=[float(value) for value in fields]
            if not all(math.isfinite(value) for value in values):
                reasons.append('nonfinite-channel-value');break
            time=values[0]
            if previous is not None:
                if time<previous:reasons.append('decreasing-time');break
                if time==previous:duplicates+=1
            previous=time
        if rows==0:reasons.append('empty-capture')
    except (ValueError,StopIteration):reasons.append('unreadable-channel-data')
    return dict(status='rejected-capture-quality' if reasons else 'capture-quality-checks-pass',
                reasons=reasons,rows=rows,channels=channel_count,event_side_duplicates=duplicates)

def archives(root):
    selected=[]
    for folder in sorted(root.iterdir()):
        if not folder.is_dir():continue
        if (folder.name.startswith('tiocr') or folder.name.startswith('relay-composite')
                or folder.name.startswith('cmldblu1-') and 'shedding' in folder.name):
            selected.extend(sorted(folder.glob('*.zip')))
    legacy=root/'2026-10-30-hsp-usage'/'cold-runs'
    if legacy.is_dir():selected.extend(sorted(legacy.rglob('tiocr*.zip')))
    return selected

def audit(root):
    result=[]
    for path in archives(root):
        with path.open('rb') as stream:
            archive_hash=hashlib.file_digest(stream,'sha256').hexdigest()
        with zipfile.ZipFile(path) as archive:
            for name in archive.namelist():
                if not name.endswith('native.log'):continue
                parent=PurePosixPath(name).parent
                def read(file):
                    entry=archive.getinfo(str(parent/file))
                    if entry.file_size>MAX_ENTRY_BYTES:raise ValueError('entry exceeds reviewed capture budget')
                    return archive.read(entry)
                log=read('native.log');table=read('native.csv')
                item=assess(log.decode(errors='replace'),table.decode('utf-8'))
                item.update(archive=path.relative_to(root).as_posix(),log_entry=name,
                    archive_sha256=archive_hash,log_sha256=hashlib.sha256(log).hexdigest(),
                    csv_sha256=hashlib.sha256(table).hexdigest())
                # A historical generic complete flag never overrides source diagnostics.
                try:item['original_manifest_status']=json.loads(read('manifest.json'))['status']
                except (KeyError,ValueError):item['original_manifest_status']=None
                result.append(item)
    return dict(scope='TIOCR1 retained capture prerequisites; physical trajectory gates remain separate',
        captures=len(result),rejected=sum(bool(x['reasons']) for x in result),results=result)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,default=Path('ipss.test.plugin.core/testData/private/verification-artifacts'))
    parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args();report=audit(args.root)
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k!='results'}))
    raise SystemExit(1 if report['rejected'] or not report['captures'] else 0)
