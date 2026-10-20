"""Routing configuration discriminators; no simulator required."""
import unittest
from pathlib import Path
from oel3c_routing_probe import configuration

class RoutingConfigurationTest(unittest.TestCase):
    def setUp(self):
        self.raw=(Path(__file__).resolve().parents[1]/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
    def test_distinct_physical_selectors_keep_exact_controller_allocations(self):
        for profile,selector in (('efd-flat',1),('ifd-flat',2),('vfe-flat',3),('fallback-flat',17)):
            with self.subTest(profile=profile):
                cfg=configuration(profile,self.raw);exc,oel=cfg['components']
                self.assertEqual((1,0),exc['icons']);self.assertEqual(21,len(exc['constants']))
                self.assertEqual([.2,.1,1.1],exc['constants'][10:13])
                self.assertEqual((selector,1),oel['icons']);self.assertEqual(10,len(oel['constants']))
                self.assertEqual(0,oel['constants'][2]);self.assertEqual(4.47,oel['constants'][0])
    def test_fault_discriminators_change_only_declared_pickup(self):
        for name in ('efd','ifd','vfe'):
            flat=configuration(name+'-flat',self.raw);fault=configuration(name+'-fault',self.raw)
            self.assertEqual(flat['components'][0],fault['components'][0])
            a,b=flat['components'][1],fault['components'][1]
            self.assertEqual(a['icons'],b['icons']);self.assertEqual(a['constants'][1:],b['constants'][1:])
            self.assertEqual(.5,b['constants'][0])
    def test_takeover_is_a_discriminator_not_a_silent_summation_substitute(self):
        cfg=configuration('takeover-flat',self.raw);self.assertEqual((2,0),cfg['components'][0]['icons'])
        self.assertEqual((1,1),cfg['components'][1]['icons'])
    def test_unknown_profiles_are_rejected(self):
        with self.assertRaises(ValueError):configuration('invented',self.raw)

if __name__=='__main__':unittest.main()
