from pathlib import Path
import unittest
from tgov1_base_probe import inputs,profile


class Tgov1BaseProbeTest(unittest.TestCase):
    def test_profile_matrix_preserves_positive_native_time_constants(self):
        for rating in ('50','200'):
            for damping in ('zero','damped'):
                for grid in ('coarse','fine'):
                    p=profile(rating,damping,grid,'a');self.assertEqual(int(rating),p['machine_mva'])
                    self.assertEqual([.05,.04,1.2,.2,.1,.3],p['constants'][:6])
                    self.assertEqual(0 if damping=='zero' else .7,p['constants'][6])
                    self.assertEqual(.005,p['increment'])

    def test_unknown_profiles_fail_closed(self):
        for args in [('100','zero','coarse','a'),('50','other','coarse','a'),('50','zero','other','a'),('50','zero','fine','c'),('50','zero','fine','a','other')]:
            with self.assertRaises(ValueError):profile(*args)

    def test_tight_network_changes_only_numerical_settings_and_namespace(self):
        default=profile('50','zero','fine','a');tight=profile('50','zero','fine','a','tight')
        self.assertEqual(default,profile('50','zero','fine','a','default'))
        self.assertEqual(default['name']+'-tight',tight.pop('name'));self.assertEqual(1e-8,tight.pop('network_tolerance'));self.assertEqual(200,tight.pop('network_max_iterations'))
        default.pop('name');self.assertEqual(default,tight)
        root=Path(__file__).resolve().parents[1]
        self.assertEqual(inputs(root,profile('50','zero','fine','a')),inputs(root,profile('50','zero','fine','a','tight')))

    def test_only_first_generator_base_changes_in_raw(self):
        root=Path(__file__).resolve().parents[1]
        original=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text().splitlines()
        for rating in ('50','200'):
            raw,dyr=inputs(root,profile(rating,'damped','fine','a'));new=raw.splitlines()
            changes=[(a,b) for a,b in zip(original,new,strict=True) if a!=b];self.assertEqual(1,len(changes))
            left,right=[line.split(',') for line in changes[0]];self.assertEqual([8],[i for i,(a,b) in enumerate(zip(left,right,strict=True)) if a!=b])
            self.assertIn("1 'TGOV1' 1 0.05 0.04 1.2 0.2 0.1 0.3 0.7 /",dyr)
            self.assertEqual(3,dyr.count('/'))


if __name__=='__main__':unittest.main()
