"""Independent dialect/schema checks; no simulator required."""
from pathlib import Path
import unittest
from oel3c_routing_probe import configuration
from oel3c_powerworld_probe import translate,read_aux_model,validate_readback,specification,validate_options

class IndependentSchemaTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.raw=Path('ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
    def test_all_three_physical_selectors_have_explicit_target_meaning(self):
        for name,flag in (('efd',1),('ifd',0),('vfe',2)):
            cfg=configuration(name+'-fault',self.raw);line=next(s for s in translate(cfg).splitlines() if "'OEL3C'" in s)
            self.assertEqual([flag,.5,.3333,0,1,.1,.1,1,0,-1,0,-1],list(map(float,line.split()[3:-1])))
            native=next(s for s in cfg['dynamic'].splitlines() if "'OEL3C'" in s)
            self.assertNotEqual(line,native)
    def test_only_the_limiter_record_is_translated(self):
        cfg=configuration('vfe-fault',self.raw)
        self.assertEqual([s for s in cfg['dynamic'].splitlines() if "'OEL3C'" not in s],
            [s for s in translate(cfg).splitlines() if "'OEL3C'" not in s])
    def test_native_and_target_inputs_remain_distinct_and_no_correction_is_enabled(self):
        spec=specification('ifd-fault',.0005,'source.dyr','translated.dyr')
        self.assertNotEqual(spec['dyr'],spec['powerworld_dyr']);self.assertFalse(spec['auto_correct'])
        self.assertNotIn('powerworld_model_overrides',spec)
    def test_invalid_profile_and_time_step_are_rejected(self):
        for name,step in (('invalid',.0005),('vfe-fault',.001)):
            with self.assertRaises(ValueError):specification(name,step,'source.dyr','translated.dyr')
    def test_model_reader_requires_exact_cardinality(self):
        with self.assertRaises(ValueError):read_aux_model('OverExcitationLimiter_OEL3C (BusNum,ID) {1}','OverExcitationLimiter_OEL3C')
    def test_missing_model_cannot_be_a_successful_readback(self):
        with self.assertRaises(ValueError):validate_readback('',configuration('vfe-fault',self.raw))
    def test_untranslated_native_parameter_order_is_rejected(self):
        body='OverExcitationLimiter_OEL3C (BusNum,ID,Status,OELInput,ITFpu,Kscale,TF,K1,Koel,Toel,KPoel,VOELmax1,VOELmin1,VOELmax2,VOELmin2) {1 "1" "Active" 3 1 .5 .3333 0 .1 .1 1 0 -1 0 -1}'
        with self.assertRaisesRegex(ValueError,'OELInput'):validate_readback(body,configuration('vfe-fault',self.raw))
    def test_correct_selector_with_shifted_exponent_is_still_rejected(self):
        body='OverExcitationLimiter_OEL3C (BusNum,ID,Status,OELInput,ITFpu,Kscale,TF,K1,Koel,Toel,KPoel,VOELmax1,VOELmin1,VOELmax2,VOELmin2) {1 "1" "Active" 2 1 .5 .3333 0 .1 .1 1 0 -1 0 -1}'
        with self.assertRaisesRegex(ValueError,'ITFpu'):validate_readback(body,configuration('vfe-fault',self.raw))
    def test_missing_or_mismatched_load_and_solver_options_are_rejected(self):
        good='"MOD_LoadModelDefault" "Impedance"\n"MOD_IntegrationMethod" "RK2"'
        self.assertEqual('Impedance',validate_options(good)['MOD_LoadModelDefault'])
        for body in ('',good.replace('Impedance','Constant Power'),good.replace('RK2','Euler')):
            with self.assertRaises(ValueError):validate_options(body)

if __name__=='__main__':unittest.main()
