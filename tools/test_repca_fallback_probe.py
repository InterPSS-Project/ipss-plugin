from pathlib import Path
import unittest
from repca_fallback_probe import MODES,configuration
from repca_native_probe import inputs

class RepcaFallbackProbeTest(unittest.TestCase):
    def test_every_profile_retains_remote_voltage_and_the_native_cardinality(self):
        root=Path(__file__).resolve().parents[1]
        self.assertEqual(7,len(MODES))
        for mode in MODES:
            p=configuration(mode,'fine','a');self.assertEqual(2,p['icons'][0]);self.assertEqual(7,len(p['icons']))
            self.assertEqual(27,len(p['constants']));self.assertEqual(.00025,p['step_s'])
            raw,dyr=inputs(root,p);self.assertTrue(raw);self.assertEqual(4,dyr.count('/'));self.assertEqual(38,len(dyr.splitlines()[-1].split()))

    def test_missing_zero_and_signed_target_identifiers_remain_distinct(self):
        expected={'missing-edge':[1,3,'1'],'missing-circuit':[1,2,'Z'],'from-zero':[0,2,'1'],
                  'to-zero':[1,0,'1'],'circuit-zero':[1,2,'0'],'all-zero':[0,0,'0'],'negative-target':[1,-2,'1']}
        for mode,identifiers in expected.items():self.assertEqual(identifiers,configuration(mode,'coarse','a')['icons'][1:4])

    def test_only_branch_identifiers_change_between_physical_reference_profiles(self):
        base=configuration('all-zero','coarse','a')
        for mode in MODES:
            p=configuration(mode,'coarse','a')
            self.assertEqual(base['constants'],p['constants']);self.assertEqual(base['icons'][4:],p['icons'][4:])
            self.assertEqual([.02,.08],p['constants'][6:8]);self.assertTrue(p['fault'])

    def test_unknown_arguments_fail_closed(self):
        for args in [('bad','coarse','a'),('all-zero','bad','a'),('all-zero','fine','c')]:
            with self.assertRaises(ValueError):configuration(*args)

if __name__=='__main__':unittest.main()
