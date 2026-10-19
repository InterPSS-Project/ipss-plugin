"""Offline guards for the exact round-rotor benchmark input matrix."""
from pathlib import Path
import unittest
from genrou_native_probe import BASE_CON, PROFILES, configuration, f32


class GenrouNativeProbeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw = (Path(__file__).resolve().parents[1] /
                   'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()

    def test_profiles_are_explicit_and_unknowns_rejected(self):
        self.assertEqual(10, len(set(PROFILES)))
        with self.assertRaises(ValueError):
            configuration('guess', self.raw)

    def test_native_parameter_cardinality_and_order(self):
        for name in PROFILES:
            cfg = configuration(name, self.raw)
            self.assertEqual(14, len(cfg['constants']))
            self.assertEqual(list(BASE_CON[:5]), cfg['constants'][:5])
            self.assertEqual(list(BASE_CON[6:12]), cfg['constants'][6:12])
            self.assertEqual(cfg['model'], cfg['dynamic'].split("'")[1])
            self.assertIn("2 'GENCLS' '1' 99999 0 /", cfg['dynamic'])

    def test_saturation_discriminator_changes_only_identifier(self):
        for suffix in ('flat', 'fault'):
            a = configuration('saturated-' + suffix, self.raw)
            b = configuration('exponential-' + suffix, self.raw)
            self.assertEqual(a['raw'], b['raw'])
            self.assertEqual(a['constants'], b['constants'])
            self.assertEqual(a['dynamic'].replace('GENROU', 'GENROE'), b['dynamic'])

    def test_machine_base_and_resistance_are_declared_raw_changes_only(self):
        baseline = configuration('fault', self.raw)
        for name, field, value in (('mva50', 8, 50.), ('mva200', 8, 200.), ('resistive', 9, .01)):
            cfg = configuration(name, self.raw)
            self.assertEqual(baseline['dynamic'], cfg['dynamic'])
            changed = [(a, b) for a, b in zip(baseline['raw'].splitlines(), cfg['raw'].splitlines()) if a != b]
            self.assertEqual(1, len(changed))
            a, b = (line.split(',') for line in changed[0])
            self.assertEqual([field], [i for i in range(len(a)) if a[i] != b[i]])
            self.assertEqual(value, float(b[field]))

    def test_damping_discriminator_changes_only_damping(self):
        a, b = (configuration(name, self.raw) for name in ('fault', 'damping'))
        self.assertEqual(a['raw'], b['raw'])
        self.assertEqual([5], [i for i in range(14) if a['constants'][i] != b['constants'][i]])
        self.assertEqual(2.5, b['constants'][5])

    def test_only_flat_profiles_omit_the_fault(self):
        for name in PROFILES:
            self.assertEqual(not name.endswith('flat'), configuration(name, self.raw)['apply_fault'])

    def test_fixture_must_match_exact_public_generator_record(self):
        with self.assertRaises(AssertionError):
            configuration('fault', self.raw.replace("1,'1',50.001,", "1,'1',50.002,"))

    def test_readback_conversion_is_exact_binary32(self):
        self.assertEqual(.5, f32(.5))
        self.assertNotEqual(.1, f32(.1))
        self.assertEqual(f32(.1), f32(f32(.1)))


if __name__ == '__main__':
    unittest.main()
