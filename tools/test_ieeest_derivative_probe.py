"""Derivative profiles preserve the exact controller parameters and native layout."""
from pathlib import Path
import unittest
from ieeest_derivative_probe import configuration
from ieeest_measurement_probe import configuration as measurement_configuration


class IeeestDerivativeProbeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw=(Path(__file__).resolve().parents[1]/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()

    def test_only_selector_and_declared_event_change(self):
        for location in ('local','remote'):
            for event in ('flat','fault','reference'):
                cfg=configuration(location+'-'+event,self.raw)
                base=measurement_configuration('voltage-'+location+'-'+('fault' if event=='fault' else 'flat'),self.raw)
                self.assertEqual(base['raw'],cfg['raw']);self.assertEqual(base['constants'],cfg['constants'])
                for actual,expected in zip(cfg['components'],base['components']):
                    self.assertEqual(expected['constants'],actual['constants'])
                    self.assertEqual(expected['states'],actual['states'])
                self.assertEqual([6,0 if location=='local' else 2],cfg['components'][-1]['icons'])
                self.assertEqual(('PSS_MEMORY','PSS_VDOT'),cfg['components'][-1]['variables'])
                self.assertEqual(23,len(cfg['dynamic'].splitlines()[-1].split()))
                self.assertTrue(cfg['record_live_variables']);self.assertTrue(cfg['record_live_fault_states'])
                self.assertEqual(.01 if event=='reference' else 0,cfg.get('vref_increment',0))

    def test_unknown_profile_rejects(self):
        for profile in ('local-impulse','missing-fault','local'):
            with self.assertRaises(ValueError):configuration(profile,self.raw)


if __name__=='__main__':unittest.main()
