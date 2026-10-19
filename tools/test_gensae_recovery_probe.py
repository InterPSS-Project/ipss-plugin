"""Offline contracts for the composed three-second recovery matrix."""
from pathlib import Path
import unittest
from gensae_recovery_probe import configuration, MODES, GOV_STATES
from gensae_field_probe import configuration as field_configuration


class GensaeRecoveryProbeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw = (Path(__file__).resolve().parents[1] /
                   'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()

    def test_explicit_profiles_reject_unknown_names(self):
        self.assertEqual(9, len(set(MODES)))
        with self.assertRaises(ValueError):
            configuration('guess', self.raw)

    def test_all_profiles_extend_the_same_exact_model_composition(self):
        for mode in MODES:
            cfg = configuration(mode, self.raw)
            self.assertEqual('GENSAE', cfg['model'])
            self.assertEqual(12, len(cfg['constants']))
            self.assertEqual(3., cfg['end_s'])
            self.assertEqual(['EXC', 'GOV'], [c['kind'] for c in cfg['components']])
            self.assertEqual(mode != 'flat', cfg['apply_fault'])

    def test_governor_cardinality_state_order_and_reference_are_exact(self):
        for mode in MODES:
            gov = configuration(mode, self.raw)['components'][1]
            self.assertEqual(('TGOV1', [], GOV_STATES, ('GOV_REFERENCE',)),
                             (gov['model'], gov['icons'], gov['states'], gov['variables']))
            self.assertEqual(7, len(gov['constants']))

    def test_positive_time_constants_are_not_fitted_to_the_step(self):
        for mode in MODES:
            con = configuration(mode, self.raw)['components'][1]['constants']
            self.assertEqual((.05, .04, .1, .3), (con[0], con[1], con[4], con[5]))

    def test_damping_changes_only_the_declared_constant(self):
        a, b = (configuration(m, self.raw) for m in ('fault', 'zero-damping'))
        self.assertEqual(a['raw'], b['raw'])
        ac, bc = a['components'][1]['constants'], b['components'][1]['constants']
        self.assertEqual([6], [i for i in range(7) if ac[i] != bc[i]])

    def test_valve_profile_changes_only_both_declared_limits(self):
        a, b = (configuration(m, self.raw) for m in ('fault', 'valve-limits'))
        self.assertEqual(a['constants'], b['constants'])
        self.assertEqual(a['components'][0], b['components'][0])
        ac, bc = a['components'][1]['constants'], b['components'][1]['constants']
        self.assertEqual([2, 3], [i for i in range(7) if ac[i] != bc[i]])

    def test_rating_profiles_do_not_change_machine_or_controller_constants(self):
        base = configuration('fault', self.raw)
        for mode, rating in (('mva50', 50.), ('mva200', 200.)):
            cfg = configuration(mode, self.raw)
            self.assertEqual(rating, cfg['mva'])
            self.assertEqual(base['dynamic'], cfg['dynamic'])
            self.assertEqual(base['constants'], cfg['constants'])
            self.assertEqual(base['components'], cfg['components'])

    def test_lower_bound_discriminator_changes_only_the_minimum(self):
        a, b = (configuration(m, self.raw) for m in ('fault', 'valve-lower'))
        self.assertEqual(a['raw'], b['raw'])
        self.assertEqual(a['components'][0], b['components'][0])
        ac, bc = a['components'][1]['constants'], b['components'][1]['constants']
        self.assertEqual([3], [i for i in range(7) if ac[i] != bc[i]])

    def test_existing_short_profile_is_not_mutated(self):
        before = field_configuration('esst1a-fault', self.raw)
        for mode in MODES:
            configuration(mode, self.raw)
        self.assertEqual(before, field_configuration('esst1a-fault', self.raw))


if __name__ == '__main__':
    unittest.main()
