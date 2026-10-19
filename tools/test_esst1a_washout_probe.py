"""Offline guardrails for the controlled constant-input washout experiment."""
from pathlib import Path
import unittest
from esst1a_washout_probe import configuration, MODES
from gensae_field_probe import configuration as field_configuration


class WashoutProbeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw = (Path(__file__).resolve().parents[1] /
                   'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()

    def test_only_declared_time_constants_are_accepted(self):
        self.assertEqual({.5, 1., 2.}, set(MODES.values()))
        with self.assertRaises(ValueError):
            configuration('fitted', self.raw)

    def test_only_regulator_upper_limit_and_washout_time_change(self):
        base = field_configuration('esst1a-unlimited', self.raw)
        for mode, tau in MODES.items():
            cfg = configuration(mode, self.raw)
            self.assertEqual(base['raw'], cfg['raw'])
            self.assertEqual(base['constants'], cfg['constants'])
            a, b = base['components'][0]['constants'], cfg['components'][0]['constants']
            self.assertEqual([9] if tau == 1. else [9, 15], [i for i in range(18) if a[i] != b[i]])
            self.assertEqual((2., tau), (b[9], b[15]))

    def test_current_and_terminal_derating_are_disabled_not_fitted(self):
        for mode in MODES:
            con = configuration(mode, self.raw)['components'][0]['constants']
            self.assertEqual((0., 0.), (con[13], con[16]))
            self.assertEqual((.1, 7.3, -7.3), (con[14], con[11], con[12]))

    def test_reference_event_has_no_fault_or_direct_state_forcing(self):
        for mode in MODES:
            cfg = configuration(mode, self.raw)
            self.assertFalse(cfg['apply_fault'])
            self.assertEqual((3., .2), (cfg['end_s'], cfg['vref_increment']))
            self.assertTrue(cfg['record_live_states'])
            self.assertEqual(3, len(cfg['dynamic'].splitlines()))
            self.assertEqual([1, 1], cfg['components'][0]['icons'])


if __name__ == '__main__':
    unittest.main()
