"""Offline schema and event guards for declared bypass profiles."""
from pathlib import Path
import unittest
from esst1a_bypass_probe import configuration, MODES
from gensae_field_probe import configuration as field_configuration


class BypassProbeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw = (Path(__file__).resolve().parents[1] /
                   'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()

    def test_unknown_profile_is_rejected(self):
        with self.assertRaises(ValueError):
            configuration('fitted', self.raw)

    def test_only_declared_bypass_parameters_change(self):
        base = field_configuration('esst1a-unlimited', self.raw)
        for mode, indices in MODES.items():
            cfg = configuration(mode, self.raw)
            self.assertEqual(base['raw'], cfg['raw'])
            self.assertEqual(base['constants'], cfg['constants'])
            a, b = base['components'][0]['constants'], cfg['components'][0]['constants']
            self.assertEqual(list(indices), [i for i in range(18) if a[i] != b[i]])
            self.assertEqual([0.] * len(indices), [b[i] for i in indices])
            self.assertEqual(1., b[15], 'Native schema requires positive Tf')

    def test_reference_step_has_no_fault_or_state_forcing(self):
        for mode in MODES:
            cfg = configuration(mode, self.raw)
            self.assertFalse(cfg['apply_fault'])
            self.assertEqual(.02, cfg['vref_increment'])
            self.assertTrue(cfg['record_live_states'])
            self.assertEqual(3, len(cfg['dynamic'].splitlines()))
            self.assertEqual([1, 1], cfg['components'][0]['icons'])


if __name__ == '__main__':
    unittest.main()
