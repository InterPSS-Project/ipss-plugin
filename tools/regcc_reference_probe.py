"""Exact nine-state converter reference profiles on a public three-bus fixture."""
import argparse,csv,json,math,os,re,shutil
from pathlib import Path
from tgov1_limit_probe import checked,sha

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--profile',choices=['flat','fault','power','charging','negative-q','limit-p','limit-q','freeze','pll-limit','minimum','limited-rate','electrical'],required=True)
    parser.add_argument('--step',type=float,default=.0005)
    parser.add_argument('--repeat',choices=['a','b'],required=True)
    parser.add_argument('--batch',default='')
    args=parser.parse_args();root=Path.cwd()
    if args.step not in [.0005,.00025]:raise ValueError('Use a declared comparison grid')
    if args.batch and not re.fullmatch('[a-z0-9-]+',args.batch):raise ValueError('Use a plain batch directory name')
    parent=root/'target'/args.batch;parent.mkdir(exist_ok=True)
    out=parent/f'regcc-{args.profile}-h{args.step:g}-{args.repeat}';out.mkdir(exist_ok=False)
    for name in ['regcc_reference_probe.py','tgov1_limit_probe.py']:shutil.copy2(root/'tools'/name,out/name)
    lines=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/renewable/regca_reeca_repca_bus1062.raw').read_text().splitlines()
    lines[1]='PUBLIC THREE-BUS PLL CONVERTER REFERENCE'
    for index,line in enumerate(lines):
        if line.startswith("1,'1',"):
            fields=line.split(',');fields[9]='.01';fields[10]='.1'
            if args.profile=='charging':fields[2]='-20'
            if args.profile=='negative-q':fields[3]='-5';fields[4]='-5';fields[5]='-5'
            lines[index]=','.join(fields)
    icons=[int(args.profile in ['power','limited-rate']),int(args.profile=='limit-p')]
    constants=[9999.,-9999.,.01,100.,1.2,.01,.5,5.,20.,100.,100.,-100.,.09]
    if args.profile in ['limit-p','limit-q']:constants[4]=.26
    if args.profile=='freeze':constants[12]=.99
    if args.profile=='pll-limit':constants[10:12]=[.1,-.1]
    if args.profile=='minimum':constants[5]=.0001
    if args.profile=='limited-rate':constants[0:2]=[.1,-.1];constants[3]=.1
    dyr="1 'REGCC' '1' "+' '.join(map(str,icons+constants))+" /\n3 'GENCLS' '1' 99999 0 /\n"
    if args.profile=='electrical':
        from reeca_native_probe import configuration
        settings=configuration('q','coarse','a')
        dyr+="1 'REECA1' '1' "+' '.join(map(str,settings['icons']+settings['constants']))+' /\n'
        shutil.copy2(root/'tools/reeca_native_probe.py',out/'reeca_native_probe.py')
    (out/'network.raw').write_text('\n'.join(lines)+'\n');(out/'dynamic.dyr').write_text(dyr)
    import psse3607
    import psspy,dyntools
    os.chdir(out);checked('initialize',psspy.psseinit(50))
    for fn in [psspy.progress_output,psspy.report_output,psspy.alert_output,psspy.prompt_output]:checked('log',fn(2,'native.log',[0,0]))
    library=Path(psspy.__file__).resolve().parents[1]/'PSSBIN/DSMDL.DLL';document=library.parent.parent/'DOCS/ModelLibrary.pdf'
    checked('library',psspy.addmodellibrary(str(library)));checked('network',psspy.read(0,'network.raw'))
    di,df=psspy.getdefaultint(),psspy.getdefaultreal();flags=[di]*7;flags[5]=1
    checked('renewable',psspy.machine_chng_4(1,'1',flags,[df]*17,''))
    checked('flow',psspy.fnsl([0,0,0,1,1,0,99,0]));assert psspy.solved()==0
    checked('generators',psspy.cong(0))
    for stage in [1,2,3]:checked('loads',psspy.conl(0,1,stage,[0,0],[0.]*4))
    checked('order',psspy.ordr(0));checked('factor',psspy.fact());checked('converted',psspy.tysl(0))
    checked('dynamic',psspy.dyre_new([1,1,1,1],'dynamic.dyr','','',''))
    layout={kind:checked('count',psspy.windmind(1,'1','WGEN','N'+kind)) for kind in ['ICON','CON','STATE','VAR']}
    assert list(layout.values())==[2,13,9,10],layout
    indices={kind:checked('index',psspy.windmind(1,'1','WGEN',kind)) for kind in ['ICON','CON','STATE','VAR']}
    for kind,fn in [('STATE',psspy.state_channel),('VAR',psspy.var_channel)]:
        for i in range(layout[kind]):checked('channel',fn([-1,indices[kind]+i],kind+str(i)))
    electrical_layout={}
    if args.profile=='electrical':
        for kind,fn in [('STATE',psspy.state_channel),('VAR',psspy.var_channel)]:
            count=checked('electrical count',psspy.windmind(1,'1','WELEC','N'+kind))
            start=checked('electrical index',psspy.windmind(1,'1','WELEC',kind));electrical_layout[kind]=count
            for i in range(count):checked('electrical channel',fn([-1,start+i],'ELEC_'+kind+str(i)))
    before=[checked('constant',psspy.dsrval('CON',indices['CON']+i)) for i in range(13)]
    for code,name in [(2,'P_PU'),(3,'Q_PU')]:checked('machine',psspy.machine_array_channel([-1,code,1],'1',name))
    for bus in [1,2,3]:checked('voltage',psspy.voltage_and_angle_channel([-1,-1,-1,bus],['V'+str(bus),'A'+str(bus)+'_DEG']))
    checked('step',psspy.dynamics_solution_param_2([di]*8,[df,df,args.step]+[df]*5));checked('start',psspy.strt_2([0,0],'native.out'))
    after=[checked('constant',psspy.dsrval('CON',indices['CON']+i)) for i in range(13)]
    assert before==after
    initial_commands=[checked('initial command',psspy.dsrval('VAR',indices['VAR']+i)) for i in [0,1]]
    fault=args.profile!='flat'
    checked('prefault',psspy.run(0,.05,1000000,1,0))
    if fault:checked('fault',psspy.dist_bus_fault(2,3,230.,[0.,100.]))
    checked('during',psspy.run(0,.10,1000000,1,0))
    if fault:checked('clear',psspy.dist_clear_fault(1))
    checked('postfault',psspy.run(0,.20,1000000,1,0))
    _,labels,data=dyntools.CHNF('native.out').get_data();keys=sorted(k for k in labels if isinstance(k,int))
    assert all(math.isfinite(v) for k in ['time']+keys for v in data[k])
    with Path('native.csv').open('w',newline='') as stream:
        writer=csv.writer(stream,lineterminator='\n');writer.writerow(['time_s']+[labels[k] for k in keys])
        for i,time in enumerate(data['time']):writer.writerow([format(time,'.12g')]+[format(data[k][i],'.12g') for k in keys])
    log=Path('native.log').read_text(errors='replace');quality='Initial conditions check O.K.' in log and 'Network not converged' not in log
    files=['network.raw','dynamic.dyr','regcc_reference_probe.py','tgov1_limit_probe.py','native.log','native.out','native.csv']
    if args.profile=='electrical':files.append('reeca_native_probe.py')
    manifest=dict(status='complete' if quality else 'rejected-quality',model='REGCC',profile=args.profile,step_s=args.step,repeat=args.repeat,
        layout=layout,electrical_layout=electrical_layout,entered_constants=before,initialized_constants=after,icons=icons,fault=fault,initial_commands=initial_commands,apply_s=.05,clear_s=.10,end_s=.20,
        samples=len(data['time']),channels=len(keys),library_sha256=sha(library),source_document_sha256=sha(document),engine_module=psse3607.__name__,files={name:sha(name) for name in files})
    Path('manifest.json').write_text(json.dumps(manifest,indent=2)+'\n');print(json.dumps(manifest),flush=True);assert quality

if __name__=='__main__':main()
