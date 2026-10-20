"""Exact OEL4C schema, readback and export-coordinate safeguards."""
import copy
from pathlib import Path
import unittest
from oel4c_powerworld_probe import PROFILES,configuration,specification,validate_readback
from check_oel4c_powerworld_artifacts import observations

class ContractTest(unittest.TestCase):
    def test_all_profiles_retain_exact_five_constant_order(self):
        raw=Path('ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
        for name,values in PROFILES.items():
            cfg=configuration(name,raw)
            records=[line for line in cfg['dynamic'].splitlines() if "'OEL4C'" in line]
            self.assertEqual(1,len(records));self.assertEqual(list(values),list(map(float,records[0].split()[3:-1])))
            self.assertNotIn('BASOEL2',cfg['dynamic']);self.assertIn("'GENROU'",cfg['dynamic'])
    def test_fault_clock_and_flat_non_event_horizon(self):
        for name in PROFILES:
            for step in (.0005,.00025):
                spec=specification(name,step,'target/source.dyr')
                self.assertFalse(spec['auto_correct']);self.assertNotIn('powerworld_model_overrides',spec)
                self.assertEqual(21,len(spec['results']))
                events=spec['contingency']['events']
                if name=='flat':self.assertTrue(all(e['time_s']>.5 for e in events))
                else:self.assertEqual([.05,.1],[e['time_s'] for e in events])
    def test_unknown_profiles_and_unreviewed_steps_are_rejected(self):
        for name,h in [('other',.0005),('flat',.001)]:
            with self.assertRaises(ValueError):specification(name,h,'source.dyr')
    def test_raw_state_and_integral_coordinates_cannot_be_conflated(self):
        names=('TSVOEL','TSOELState:1','TSOELOther:1','TSOELOther:2','TSOELOther:3','TSOELOther:4','TSMvar')
        fields=[['Generator','1 1','','',n,''] for n in names]
        rows=[[0,0,0,0,0,.3,0,5],[.05,-.1,-.1,1,.01,-.5,0,85],[.05,-.1,-.1,1,.01,-.5,0,85]]
        result=observations(fields,rows,.35)
        self.assertEqual(3,result['raw_rows']);self.assertEqual(0,result['rows_dropped'])
        self.assertEqual([.05],result['post_event_times']);self.assertTrue(result['output_equals_exported_pi_state'])
        saturated=copy.deepcopy(rows);saturated[1][3]=2
        self.assertEqual([0,1,2],observations(fields,saturated,.35)['observed_mode_values'])
        changed=copy.deepcopy(rows);changed[1][2]=-.11
        with self.assertRaises(ValueError):observations(fields,changed,.35)
    def test_missing_or_duplicate_semantic_channels_are_rejected(self):
        with self.assertRaises(ValueError):observations([],[],.35)
    def test_readback_accepts_only_exact_limiter_host_and_machine_parameters(self):
        raw=Path('ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
        cfg=configuration('delayed-fault',raw)
        def row(name,values):
            return name+' (BusNum,ID,Status,'+','.join(values)+')\n{\n1 "1" "Active" '+' '.join(map(str,values.values()))+'\n}\n'
        body=row('OverExcitationLimiter_OEL4C',dict(zip(('Qref','TDelay','Kp','Ki','Vmin'),PROFILES['delayed-fault'])))
        fields=('Tr','Tb','Tc','Ka','Ta','VaMax','VaMin','Te','Kf','Tf','Kc','Kd','Ke','E1','SE1','E2','SE2','Eefmax','Eefmin','Vfemax','Vemin')
        body+=row('Exciter_AC1C',dict(OEL=1,UEL=0,**dict(zip(fields,cfg['components'][0]['constants']))))
        c=cfg['constants']
        body+=row('MachineModel_GENROU',dict(H=c[4],D=c[5],Ra=cfg['resistance'],Xd=c[6],Xq=c[7],Xdp=c[8],Xqp=c[9],Xdpp=c[10],Xl=c[11],
            Tdop=c[0],Tqop=c[2],Tdopp=c[1],Tqopp=c[3],S1=c[12],S12=c[13],RComp=0.,XComp=0.))
        body+='"MOD_LoadModelDefault" "Impedance"\n"MOD_IntegrationMethod" "RK2"\n'
        self.assertEqual('0.35',validate_readback(body,cfg,'delayed-fault')['limiter']['Qref'])
        changed=body.replace(' 0.35 ',' 0.36 ')
        self.assertNotEqual(body,changed)
        with self.assertRaises(ValueError):validate_readback(changed,cfg,'delayed-fault')

if __name__=='__main__':unittest.main()
