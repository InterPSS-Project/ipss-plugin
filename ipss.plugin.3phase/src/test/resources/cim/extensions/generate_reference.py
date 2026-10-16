"""Rebuild synthetic CIM fixtures and independent native OpenDSS references.

Requires dss-python==0.15.7 (DSS C-API 0.14.5). The DSS circuits below are
specified independently of the Java importer; no mapped DSS output is used.
Run this script explicitly, never as part of Maven tests.
"""
from pathlib import Path
import csv
import math
import xml.etree.ElementTree as ET
from dss import DSS

HERE = Path(__file__).resolve().parent
C = 'http://iec.ch/TC57/CIM100#'
R = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#'
ET.register_namespace('cim', C)
ET.register_namespace('rdf', R)

class Case:
    def __init__(self, name, volts=12470):
        self.name, self.volts = name, volts
        self.root = ET.Element('{'+R+'}RDF')
        self.obj('Feeder','feeder')
        self.base('hv',volts)
        self.node('source')
        self.obj('EnergySource','grid',{'Equipment.EquipmentContainer':'#feeder',
            'EnergySource.nominalVoltage':volts,'EnergySource.voltageMagnitude':volts,
            'EnergySource.voltageAngle':0,'EnergySource.r':.01,'EnergySource.x':.03,
            'EnergySource.r0':.02,'EnergySource.x0':.06})
        self.terminal('grid',1,'source','ABC')
        self.dss = ['clear',f'new circuit.{name} bus1=source basekv={volts/1000} pu=1 phases=3 r1=.01 x1=.03 r0=.02 x0=.06',
                    'set earthmodel=carson']
    def obj(self,typ,id,props=None):
        e=ET.SubElement(self.root,'{'+C+'}'+typ,{'{'+R+'}ID':id})
        ET.SubElement(e,'{'+C+'}IdentifiedObject.name').text=id
        for key,value in (props or {}).items(): self.prop(e,key,value)
        return e
    def prop(self,e,key,value):
        p=ET.SubElement(e,'{'+C+'}'+key)
        if isinstance(value,str) and (value.startswith('#') or value.startswith(C)): p.set('{'+R+'}resource',value)
        else: p.text=str(value).lower() if isinstance(value,bool) else str(value)
    def base(self,id,volts): self.obj('BaseVoltage',id,{'BaseVoltage.nominalVoltage':volts})
    def node(self,id): self.obj('ConnectivityNode',id)
    def terminal(self,eq,seq,node,ph=None):
        props={'Terminal.ConductingEquipment':'#'+eq,'Terminal.ConnectivityNode':'#'+node,'ACDCTerminal.sequenceNumber':seq}
        if ph: props['Terminal.phases']=C+'PhaseCode.'+ph
        self.obj('Terminal',f'{eq}t{seq}',props)
        return '#'+f'{eq}t{seq}'
    def load(self,id,node,phase,base,volts,kw,kvar,conn='Y'):
        self.obj('EnergyConsumer',id,{'Equipment.EquipmentContainer':'#feeder','ConductingEquipment.BaseVoltage':'#'+base,
            'EnergyConsumer.phaseConnection':C+'PhaseShuntConnectionKind.'+conn,'EnergyConsumer.p':kw*1000,'EnergyConsumer.q':kvar*1000})
        self.terminal(id,1,node,phase)
        split=phase.startswith('s')
        dss_phase=phase
        if not split and conn=='D' and len(phase)==1:dss_phase+= 'ABC'[('ABC'.index(phase)+1)%3]
        suffix='.'.join(phase.replace('s','')) if split else '.'.join(str('ABC'.index(p)+1) for p in dss_phase)
        kv=volts/1000 if conn=='D' else volts/1000/(2 if split else math.sqrt(3))
        self.dss.append(f'new load.{id} phases=1 bus1={node}.{suffix} conn={"delta" if conn=="D" else "wye"} kv={kv} kw={kw} kvar={kvar} vminpu=0 vmaxpu=1000000')
    def save(self):
        ET.indent(self.root)
        ET.ElementTree(self.root).write(HERE/(self.name+'.xml'),encoding='utf-8',xml_declaration=True)
        commands=self.dss+['set maxiterations=200 tolerance=1e-10','solve']
        (HERE/(self.name+'.dss')).write_text('\n'.join(commands)+'\n')
        ctx=DSS.NewContext()
        for command in commands: ctx.Text.Command=command
        assert ctx.ActiveCircuit.Solution.Converged,self.name
        with (HERE/(self.name+'-voltage.csv')).open('w',newline='') as f:
            out=csv.writer(f);out.writerow(['bus','node','real_volts','imag_volts'])
            for name in ctx.ActiveCircuit.AllBusNames:
                ctx.ActiveCircuit.SetActiveBus(name)
                v=ctx.ActiveCircuit.ActiveBus.Voltages
                for i,n in enumerate(ctx.ActiveCircuit.ActiveBus.Nodes): out.writerow([name,n,v[2*i],v[2*i+1]])
        # Total source injection provides an independent power/loss check.
        with (HERE/(self.name+'-power.csv')).open('w',newline='') as f:
            csv.writer(f).writerows([['p_kw','q_kvar'],[-x for x in ctx.ActiveCircuit.TotalPower]])
        if self.name.startswith('cable'):
            ctx.ActiveCircuit.Lines.Name='cable'
            with (HERE/(self.name+'-matrix.csv')).open('w',newline='') as f:
                out=csv.writer(f);out.writerow(['r_ohm_per_m','x_ohm_per_m','c_nf_per_m'])
                out.writerows(zip(ctx.ActiveCircuit.Lines.Rmatrix,ctx.ActiveCircuit.Lines.Xmatrix,ctx.ActiveCircuit.Lines.Cmatrix))

def transformer(name,split=False,primary='A',connections=('wye','wye','wye'),tank=False,zero=False,negative=False,lead=False,mesh=False,clock_polarity=False):
    c=Case(name,7200*math.sqrt(3) if split else 12470)
    kv=[7.2,.12,.12] if split else [12.47,4.16,.48]
    kva=[25,12.5,12.5] if split else [10000,5000,3000]
    rs=[.4,.4,.4] if split else [0 if zero else .2,.3,.4]
    xs=[2,2,1.2] if split else ([3,4,9] if negative else [3,4,7] if zero else [6,7,8])
    taps=[1.0125,1,.9875] if split else [1.025,.9875,1]
    c.node('secondary')
    if not split:c.node('tertiary')
    c.base('lv',240 if split else 4160)
    if not split:c.base('tv',480)
    c.obj('PowerTransformer','bank',{'Equipment.EquipmentContainer':'#feeder'})
    equipment='bank'
    if tank or split:
        c.obj('TransformerTank','tank',{'TransformerTank.PowerTransformer':'#bank','TransformerTank.TransformerTankInfo':'#info'})
        c.obj('TransformerTankInfo','info')
        equipment='tank'
    phases=[primary,'s1N','Ns2'] if split else ['ABC']*3
    # CIMHub emits both Ns2 and clock 6; the ordered terminals carry the polarity.
    nodes=['source','secondary','secondary' if split else 'tertiary']
    star=[(xs[0]+xs[1]-xs[2])/2,(xs[0]+xs[2]-xs[1])/2,(xs[1]+xs[2]-xs[0])/2]
    ratings=[]
    for i in range(3):
        term=c.terminal(equipment,i+1,nodes[i])
        end=f'end{i+1}';rating=f'rating{i+1}' if tank or split else end;ratings.append(rating)
        c.obj('TransformerTankEnd' if tank or split else 'PowerTransformerEnd',end,{
            ('TransformerTankEnd.TransformerTank' if tank or split else 'PowerTransformerEnd.PowerTransformer'):'#'+equipment,
            'TransformerEnd.endNumber':i+1,'TransformerEnd.Terminal':term,'TransformerEnd.grounded':True,
            **({'TransformerTankEnd.orderedPhases':C+'OrderedPhaseCodeKind.'+phases[i]} if tank or split else {})})
        prefix='TransformerEndInfo.' if tank or split else 'PowerTransformerEnd.'
        props={prefix+'ratedU':kv[i]*1000,prefix+'ratedS':kva[i]*1000,prefix+'r':rs[i]/100*(kv[i]*1000)**2/(kva[0]*1000),
            prefix+'connectionKind':C+'WindingConnection.'+('I' if split else 'D' if connections[i]=='delta' else 'Yn'),
            prefix+'phaseAngleClock':(6 if clock_polarity and i==2 else 0) if connections[i]==connections[0]
                else (11 if lead else 1) if connections[0]=='delta' else (1 if lead else 11)}
        if tank or split:
            props.update({'TransformerEndInfo.TransformerTankInfo':'#info','TransformerEndInfo.endNumber':i+1})
            c.obj('TransformerEndInfo',rating,props)
        else:
            e=c.root.find(f"./{{{C}}}PowerTransformerEnd[@{{{R}}}ID='{end}']")
            for k,v in props.items():c.prop(e,k,v)
            if not mesh:c.prop(e,prefix+'x',star[i]/100*(kv[i]*1000)**2/(kva[0]*1000))
        c.obj('RatioTapChanger',f'tap{i+1}',{'RatioTapChanger.TransformerEnd':'#'+end,'TapChanger.neutralStep':0,
            'TapChanger.step':round((taps[i]-1)*100/.625),'TapChanger.lowStep':-16,'TapChanger.highStep':16,
            'RatioTapChanger.stepVoltageIncrement':.625})
    if mesh:
        for k,(i,j) in enumerate([(1,0),(0,2),(2,1)]):
            c.obj('TransformerMeshImpedance',f'mesh{k}',{'TransformerMeshImpedance.FromTransformerEnd':f'#end{i+1}',
                'TransformerMeshImpedance.ToTransformerEnd':f'#end{j+1}','TransformerMeshImpedance.x':xs[k]/100*(kv[i]*1000)**2/(kva[0]*1000)})
    if tank or split:
        for k,(i,j) in enumerate([(0,1),(0,2),(1,2)]):
            rp=rs[i]+rs[j]
            z=math.hypot(rp,xs[k])/100*(kv[i]*1000)**2/(kva[0]*1000)
            c.obj('ShortCircuitTest',f'test{k}',{'ShortCircuitTest.EnergisedEnd':'#'+ratings[i],
                'ShortCircuitTest.GroundedEnds':'#'+ratings[j],'TransformerTest.basePower':kva[0]*1000,
                'ShortCircuitTest.leakageImpedance':z,'ShortCircuitTest.loss':rp/100*kva[0]})
        c.obj('NoLoadTest','noload',{'NoLoadTest.EnergisedEnd':'#'+ratings[0],
            'TransformerTest.basePower':kva[0]*1000,'NoLoadTest.loss':kva[0]*.001,
            'NoLoadTest.excitingCurrent':math.hypot(.1,.2)})
    else:
        zb=(kv[1]*1000)**2/(kva[0]*1000)
        c.obj('TransformerCoreAdmittance','core',{'TransformerCoreAdmittance.TransformerEnd':'#end2',
            'TransformerCoreAdmittance.g':.001/zb,'TransformerCoreAdmittance.b':-.002/zb})
    buses=f'source.{"ABC".index(primary)+1} secondary.1.0 secondary.0.2' if split else 'source secondary tertiary'
    arr=lambda values:'['+' '.join(map(str,values))+']'
    c.dss.append(f'new transformer.bank phases={1 if split else 3} windings=3 buses=[{buses}] conns={arr(connections)} kvs={arr(kv)} kvas={arr(kva)} %rs={arr(rs)} xhl={xs[0]} xht={xs[1]} xlt={xs[2]} taps={arr(taps)} leadlag={"lead" if lead else "lag"} %noloadloss=.1 %imag=.2 ppm_antifloat=0')
    if split:
        c.node('service')
        c.obj('PerLengthPhaseImpedance','dropz',{'PerLengthPhaseImpedance.conductorCount':2})
        for i,j,r,x in [(1,1,.0006,.0003),(2,1,.0002,.0001),(2,2,.0007,.00035)]:
            c.obj('PhaseImpedanceData',f'z{i}{j}',{'PhaseImpedanceData.PhaseImpedance':'#dropz',
                'PhaseImpedanceData.row':i,'PhaseImpedanceData.column':j,'PhaseImpedanceData.r':r,'PhaseImpedanceData.x':x})
        c.obj('ACLineSegment','drop',{'Equipment.EquipmentContainer':'#feeder','Conductor.length':20,'ACLineSegment.PerLengthImpedance':'#dropz'})
        c.terminal('drop',1,'secondary','s12N');c.terminal('drop',2,'service','s12N')
        for i in [1,2]:c.obj('ACLineSegmentPhase',f'dropph{i}',{'ACLineSegmentPhase.ACLineSegment':'#drop',
            'ACLineSegmentPhase.sequenceNumber':i,'ACLineSegmentPhase.phase':C+'SinglePhaseKind.s'+str(i)})
        c.dss += ['new linecode.drop nphases=2 units=m rmatrix=[.0006 | .0002 .0007] xmatrix=[.0003 | .0001 .00035] cmatrix=[0 | 0 0]',
            'new line.drop bus1=secondary.1.2 bus2=service.1.2 phases=2 linecode=drop length=20 units=m']
        c.load('leg1','service','s1','lv',240,2,.4)
        c.load('leg2','service','s2','lv',240,1,.2)
        c.load('both','service','s12','lv',240,2,.3,'D')
    else:
        for wi,(node,base,voltage,power) in enumerate([('secondary','lv',4160,450),('tertiary','tv',480,150)],1):
            for i,p in enumerate('ABC'):c.load(node+p,node,p,base,voltage,power*(1+.2*i),power*.25,'D' if connections[wi]=='delta' else 'Y')
    c.save()

def cable(name,kind='cn',phases='ABC',neutral=False):
    c=Case(name);c.node('end')
    c.obj('WireSpacingInfo','spacing',{'WireSpacingInfo.isCable':True})
    c.obj('ACLineSegment','cable',{'Equipment.EquipmentContainer':'#feeder','Conductor.length':500,'ACLineSegment.WireSpacingInfo':'#spacing'})
    c.terminal('cable',1,'source',phases);c.terminal('cable',2,'end',phases)
    for i,phase in enumerate(phases+('N' if neutral else '')):
        is_neutral=phase=='N';wire=f'wire{i}'
        props={'WireInfo.rAC50':.0002 if not is_neutral else .0005,'WireInfo.rDC20':.00019,'WireInfo.gmr':.006,'WireInfo.radius':.008}
        if not is_neutral:
            props.update({'CableInfo.relativePermittivity':2.3,'WireInfo.insulationThickness':.005,
                'CableInfo.diameterOverInsulation':.026,'CableInfo.diameterOverJacket':.032})
            if kind=='cn':props.update({'ConcentricNeutralCableInfo.neutralStrandCount':13,'ConcentricNeutralCableInfo.neutralStrandGmr':.0006,
                'ConcentricNeutralCableInfo.neutralStrandRadius':.0008,'ConcentricNeutralCableInfo.neutralStrandRDC20':.008})
            else:props.update({'CableInfo.diameterOverScreen':.028,'TapeShieldCableInfo.tapeThickness':.00025,'TapeShieldCableInfo.tapeLap':20})
        c.obj('OverheadWireInfo' if is_neutral else 'ConcentricNeutralCableInfo' if kind=='cn' else 'TapeShieldCableInfo',wire,props)
        c.obj('WirePosition',f'position{i}',{'WirePosition.WireSpacingInfo':'#spacing','WirePosition.sequenceNumber':i+1,'WirePosition.xCoord':i*.15,'WirePosition.yCoord':-1})
        c.obj('ACLineSegmentPhase',f'phase{i}',{'ACLineSegmentPhase.ACLineSegment':'#cable','ACLineSegmentPhase.sequenceNumber':i+1,
            'ACLineSegmentPhase.phase':C+'SinglePhaseKind.'+phase,'ACLineSegmentPhase.WireInfo':'#'+wire})
        if is_neutral:c.dss.append(f'new wiredata.{wire} rac=.0005 rdc=.00019 runits=m gmrac=.006 gmrunits=m radius=.008 radunits=m')
        else:
            extra='k=13 gmrstrand=.0006 diastrand=.0016 rstrand=.008' if kind=='cn' else 'diashield=.0285 tapelayer=.00025 tapelap=20'
            c.dss.append(f'new {kind}data.{wire} rac=.0002 rdc=.00019 runits=m gmrac=.006 gmrunits=m radius=.008 radunits=m epsr=2.3 inslayer=.005 diains=.026 diacable=.032 {extra}')
    c.dss.append(f'new linegeometry.cable nconds={len(phases)+neutral} nphases={len(phases)} reduce=yes')
    for i,phase in enumerate(phases+('N' if neutral else '')):
        c.dss.append(f'~ cond={i+1} {"wire" if phase=="N" else kind+"cable"}=wire{i} x={i*.15} h=-1 units=m')
    suffix='.'.join(str('ABC'.index(p)+1) for p in phases)
    c.dss.append(f'new line.cable phases={len(phases)} bus1=source.{suffix} bus2=end.{suffix} geometry=cable length=500 units=m')
    for i,p in enumerate(phases):c.load('load'+p,'end',p,'hv',12470,200*(i+1),40)
    c.save()

if __name__=='__main__':
    for phase in 'ABC':transformer('split_'+phase.lower(),split=True,primary=phase)
    transformer('split_clock',split=True,clock_polarity=True)
    for name,conns in [('yyy',('wye',)*3),('dyy',('delta','wye','wye')),('ydy',('wye','delta','wye')),('yyd',('wye','wye','delta'))]:
        transformer('three_'+name,connections=conns)
    transformer('three_tank',tank=True)
    transformer('three_zero',zero=True)
    transformer('three_negative',negative=True)
    transformer('three_mesh',mesh=True)
    transformer('three_dyy_lead',connections=('delta','wye','wye'),lead=True)
    transformer('three_ydy_lead',connections=('wye','delta','wye'),lead=True)
    for kind in ['cn','ts']:
        cable('cable_'+kind,kind=kind)
        cable('cable_'+kind+'_neutral',kind=kind,neutral=True)
        cable('cable_'+kind+'_one',kind=kind,phases='B')
        cable('cable_'+kind+'_two',kind=kind,phases='AC')
    cable('cable_cn_permuted',phases='CA')
    print('Generated 23 independently specified reference circuits with',DSS.Version)
