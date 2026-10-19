"""Offline contract and discriminator checks for salient-pole references."""
from pathlib import Path
import unittest
from gensal_native_probe import BASE_CON, PROFILES, configuration, state_names


class GensalNativeProbeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw = (Path(__file__).resolve().parents[1] /
                   'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()

    def test_twelve_constants_and_exact_model_names(self):
        for name in PROFILES:
            cfg = configuration(name, self.raw)
            self.assertEqual(12, len(cfg['constants']))
            self.assertEqual('GENSAE' if name.startswith('exponential-') else 'GENSAL', cfg['model'])
            self.assertEqual(list(BASE_CON[:4]), cfg['constants'][:4])
            self.assertEqual(list(BASE_CON[5:10]), cfg['constants'][5:10])

    def test_source_allocation_orders_are_distinct(self):
        for name in PROFILES:
            names = state_names(name)
            self.assertEqual(5, len(set(names)))
            self.assertEqual(('SPEED_DEV', 'ANGLE'), names[-2:])
            self.assertEqual(('EP_Q', 'PSIQ_PP', 'PSIK_D') if name.startswith('exponential-')
                             else ('EP_Q', 'PSIK_D', 'PSIQ_PP'), names[:3])

    def test_alias_discriminator_changes_only_the_identifier(self):
        for suffix in ('flat', 'fault'):
            a, b = (configuration(prefix + suffix, self.raw) for prefix in ('saturated-', 'exponential-'))
            self.assertEqual(a['constants'], b['constants'])
            self.assertEqual(a['raw'], b['raw'])
            self.assertEqual(a['dynamic'].replace('GENSAL', 'GENSAE'), b['dynamic'])

    def test_damping_changes_only_one_entered_constant(self):
        for prefix in ('', 'exponential-'):
            a, b = (configuration(prefix + name, self.raw) for name in ('fault', 'damping'))
            self.assertEqual(a['raw'], b['raw'])
            self.assertEqual([4], [i for i in range(12) if a['constants'][i] != b['constants'][i]])

    def test_bases_and_resistance_change_only_the_declared_raw_field(self):
        for prefix in ('', 'exponential-'):
            a = configuration(prefix + 'fault', self.raw)
            for name, field, value in [('mva50', 8, 50.), ('mva200', 8, 200.), ('resistive', 9, .01)]:
                b = configuration(prefix + name, self.raw)
                self.assertEqual(a['dynamic'], b['dynamic'])
                changed = [(x, y) for x, y in zip(a['raw'].splitlines(), b['raw'].splitlines()) if x != y]
                self.assertEqual(1, len(changed))
                x, y = (line.split(',') for line in changed[0])
                self.assertEqual([field], [i for i in range(len(x)) if x[i] != y[i]])
                self.assertEqual(value, float(y[field]))

    def test_only_flat_profiles_omit_the_fault(self):
        for name in PROFILES:
            self.assertEqual(not name.endswith('flat'), configuration(name, self.raw)['apply_fault'])

    def test_unknown_profiles_are_not_silently_defaulted(self):
        self.assertEqual(16, len(set(PROFILES)))
        with self.assertRaises(ValueError):
            configuration('guess', self.raw)
        with self.assertRaises(ValueError):
            state_names('guess')

    def test_network_fixture_must_match_the_exact_generator_record(self):
        with self.assertRaises(AssertionError):
            configuration('fault', self.raw.replace("1,'1',50.001,", "1,'1',50.002,"))


if __name__ == '__main__':
    unittest.main()
