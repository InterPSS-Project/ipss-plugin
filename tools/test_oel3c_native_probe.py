"""Offline schema controls; synthetic component metadata are not native trajectories."""
import copy
from pathlib import Path
import unittest
from genrou_native_probe import f32
from oel3c_native_probe import configuration,PROFILES
from check_oel3c_artifacts import validate_component

class Oel3cNativeProbeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.raw=Path('ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
    def component(self,cfg):
        c=cfg['components'][0]
        return dict(model='OEL3C',actual_icons=list(c['icons']),actual_constants=list(map(f32,c['constants'])),
                    layout=dict(NICON=2,NCON=10,NSTATE=2,NVAR=4))
    def test_exact_two_selector_ten_constant_contract(self):
        for profile in PROFILES:
            cfg=configuration(profile,self.raw);c=cfg['components'][0];assert len(c['icons'])==2 and len(c['constants'])==10
            validate_component(self.component(cfg),cfg)
            line=next(s for s in cfg['dynamic'].splitlines() if "'OEL3C'" in s);self.assertEqual(12,len(line.split()[3:-1]))
    def test_declared_profiles_do_not_adjust_gains_or_limits(self):
        base=configuration('flat',self.raw)['components'][0]['constants']
        for profile in PROFILES:
            p=configuration(profile,self.raw)['components'][0]['constants'];self.assertEqual(base[3:],p[3:])
            self.assertEqual(.3333,p[1]);self.assertEqual(4.47 if profile=='flat' else .4,p[0])
    def test_configuration_instances_are_independent(self):
        a=configuration('flat',self.raw);a['components'][0]['constants'][0]=99
        self.assertEqual(4.47,configuration('flat',self.raw)['components'][0]['constants'][0])
    def test_only_the_zero_filter_profile_bypasses_the_filter(self):
        for profile in PROFILES:self.assertEqual(0 if profile=='zero-filter-fault' else .02,
            configuration(profile,self.raw)['components'][0]['constants'][2])
    def test_fault_and_flat_inputs_remain_distinct(self):
        for profile in PROFILES:self.assertEqual(profile!='flat',configuration(profile,self.raw)['apply_fault'])
    def test_unknown_profile_is_explicitly_rejected(self):
        with self.assertRaises(ValueError):configuration('invented',self.raw)
    def test_changed_allocations_are_rejected(self):
        cfg=configuration('flat',self.raw)
        for key in ('NICON','NCON','NSTATE','NVAR'):
            c=self.component(cfg);c['layout'][key]+=1
            with self.assertRaises(AssertionError):validate_component(c,cfg)
    def test_changed_readbacks_and_selectors_are_rejected(self):
        cfg=configuration('quadratic-fault',self.raw)
        for key,value in (('actual_icons',[1,1]),('actual_constants',cfg['components'][0]['constants'])):
            c=copy.deepcopy(self.component(cfg));c[key]=value
            with self.assertRaises(AssertionError):validate_component(c,cfg)

if __name__=='__main__':unittest.main()
