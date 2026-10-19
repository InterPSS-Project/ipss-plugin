"""Recover benchmark inputs only by their archived canonical content hashes.

No nearest-name substitution, source-path restoration or reference overwrite.
Manifest hashes follow the benchmark runner's text-newline canonicalization.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path, PurePosixPath
import subprocess

TEXT_SUFFIXES={'.aux','.csv','.dyr','.idv','.json','.log','.raw','.txt'}


def relative_path(value):
    path=PurePosixPath(value.replace('\\','/'))
    if not value or path.is_absolute() or '..' in path.parts or ':' in value or path==PurePosixPath('.'):
        raise ValueError('Input path must be repository-relative and contained')
    return path.as_posix()


def canonical(body,path):
    return body.replace(b'\r\n',b'\n').replace(b'\r',b'\n') if Path(path).suffix.lower() in TEXT_SUFFIXES else body


def digest(body):return hashlib.sha256(body).hexdigest()


def git(root,*args,input=None):
    return subprocess.check_output(['git',*args],cwd=root,input=input)


def blobs(root,identifiers):
    if not identifiers:return []
    stream=git(root,'cat-file','--batch',input=''.join(oid+'\n' for oid in identifiers).encode())
    result=[];offset=0
    for expected in identifiers:
        end=stream.index(b'\n',offset);fields=stream[offset:end].split();offset=end+1
        if len(fields)!=3 or fields[1]!=b'blob':raise ValueError('Expected an existing immutable Git blob')
        size=int(fields[2]);body=stream[offset:offset+size];offset+=size+1
        assert fields[0].decode()==expected
        result.append((expected,body))
    return result


def find(root,path,expected,assets=None):
    path=relative_path(path)
    if len(expected)!=64 or any(ch not in '0123456789abcdef' for ch in expected):raise ValueError('Expected SHA-256 is invalid')
    candidate=root/path
    if candidate.resolve().is_relative_to(root.resolve()) and candidate.is_file():
        body=candidate.read_bytes()
        if digest(canonical(body,path))==expected:
            return body,dict(path=path,canonical_sha256=expected,git_blob=None,source='pinned-worktree',raw_sha256=digest(body))
    if assets is not None and (assets/(expected+'.b64')).is_file():
        body=base64.b64decode((assets/(expected+'.b64')).read_bytes(),validate=False)
        if digest(canonical(body,path))!=expected:raise ValueError('Content-addressed asset is corrupted')
        return body,dict(path=path,canonical_sha256=expected,git_blob=None,source='content-addressed-asset',raw_sha256=digest(body))
    head=git(root,'ls-tree','HEAD','--',path).decode().strip()
    head_ids=[head.split()[2]] if head else []
    history=git(root,'rev-list','--objects','--all','--',path).decode().splitlines()
    ids=list(dict.fromkeys(head_ids+[line.split(' ',1)[0] for line in history if ' ' in line and line.split(' ',1)[1]==path]))
    for oid,body in blobs(root,ids):
        if digest(canonical(body,path))==expected:
            return body,dict(path=path,canonical_sha256=expected,git_blob=oid,source='head' if oid in head_ids else 'history',raw_sha256=digest(body))
    raise ValueError('No exact immutable input: '+path+' SHA-256='+expected)


def recover(root,catalog,output,assets=None):
    root=root.resolve();output=output.resolve();target=(root/'target').resolve()
    if not target.is_relative_to(root) or output==target or not output.is_relative_to(target):raise ValueError('Recovery output must be a new directory inside target')
    if output.exists():raise FileExistsError('Recovery output already exists')
    baseline=git(root,'rev-parse','HEAD').decode().strip()
    prepared=[]
    # Resolve every object before creating any output. One missing contract
    # must not leave a partial workspace masquerading as a complete recovery.
    names=set()
    for benchmark in catalog['benchmarks']:
        name=relative_path(benchmark['name'])
        if '/' in name:raise ValueError('Benchmark name must be one path component')
        if name in names:raise ValueError('Duplicate benchmark name')
        names.add(name);destinations={}
        entries=[]
        for role,item in benchmark['inputs'].items():
            body,evidence=find(root,item['path'],item['sha256'],assets)
            if evidence['path'] in destinations and destinations[evidence['path']]!=body:raise ValueError('Conflicting input roles')
            destinations[evidence['path']]=body;entries.append((role,body,evidence))
        prepared.append((name,entries))
    output.mkdir(parents=True)
    reports=[]
    for name,entries in prepared:
        workspace=output/name/'workspace';evidence={}
        for role,body,item in entries:
            destination=workspace/item['path'];destination.parent.mkdir(parents=True,exist_ok=True)
            if destination.exists() and destination.read_bytes()!=body:raise ValueError('Conflicting input roles')
            destination.write_bytes(body);evidence[role]=item
        reports.append(dict(name=name,inputs=evidence))
    result=dict(schema='interpss-benchmark-input-recovery-v1',status='complete',baseline=baseline,benchmarks=reports)
    (output/'recovery.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('catalog',type=Path);parser.add_argument('--repository',type=Path,default=Path.cwd());parser.add_argument('--output',type=Path,required=True);parser.add_argument('--assets',type=Path);parser.add_argument('--benchmark',action='append')
    args=parser.parse_args();catalog=json.loads(args.catalog.read_text())
    if args.benchmark:
        selected=set(args.benchmark);available={b['name'] for b in catalog['benchmarks']}
        if not selected<=available:raise ValueError('Unknown benchmark selection')
        catalog=dict(catalog,benchmarks=[b for b in catalog['benchmarks'] if b['name'] in selected])
    result=recover(args.repository,catalog,args.output,args.assets)
    print(json.dumps(dict(status=result['status'],benchmarks=len(result['benchmarks']),historical_inputs=sum(item['source']=='history' for b in result['benchmarks'] for item in b['inputs'].values()))))


if __name__=='__main__':main()
