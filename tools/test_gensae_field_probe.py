"""Offline contracts for field-current and composed-exciter references."""
from pathlib import Path
import unittest
from gensae_field_probe import configuration, machine_channels, MODES, EXC_STATES
from gensal_native_probe import configuration as generator_configuration


class GensaeFieldProbeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw = (Path(__file__).resolve().parents[1] /
                   'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()

    def test_modes_are_explicit(self):
        self.assertEqual(11, len(set(MODES)))
        with self.assertRaises(ValueError):
            configuration('guess', self.raw)

    def test_undefined_regulator_reference_is_never_recorded_without_a_regulator(self):
        for mode in MODES:
            cfg = configuration(mode, self.raw)
            channels = machine_channels(cfg)
            self.assertEqual(((8, 'XAD_IFD'), (9, 'ECOMP')), channels[:2])
            self.assertEqual(mode.startswith('esst1a-'), (11, 'VREF') in channels)

    def test_open_field_references_preserve_existing_generator_inputs(self):
        for mode, base in [('open-flat', 'exponential-flat'), ('open-fault', 'exponential-fault'),
                           ('open-resistive', 'exponential-resistive'), ('open-mva50', 'exponential-mva50'),
                           ('open-mva200', 'exponential-mva200'), ('open-zero', 'exponential-zero-fault')]:
            self.assertEqual(generator_configuration(base, self.raw), configuration(mode, self.raw))

    def test_all_generator_records_remain_twelve_constants(self):
        for mode in MODES:
            cfg = configuration(mode, self.raw)
            self.assertEqual('GENSAE', cfg['model'])
            self.assertEqual(12, len(cfg['constants']))
            self.assertEqual("1 'GENSAE' '1'", cfg['dynamic'].splitlines()[0][:14])

    def test_two_selectors_eighteen_constants_and_five_exciter_states(self):
        for mode in MODES:
            cfg = configuration(mode, self.raw)
            if mode.startswith('esst1a-'):
                spec, = cfg['components']
                self.assertEqual(('EXC', 'ESST1A', [1, 1]), (spec['kind'], spec['model'], spec['icons']))
                self.assertEqual(18, len(spec['constants']))
                self.assertEqual(5, len(set(spec['states'])))
                self.assertEqual(EXC_STATES, spec['states'])
            else:
                self.assertNotIn('components', cfg)

    def test_field_current_discriminator_changes_only_the_two_gains(self):
        a, b = (configuration(mode, self.raw) for mode in ('esst1a-fault', 'esst1a-unlimited'))
        self.assertEqual(a['raw'], b['raw'])
        self.assertEqual(a['constants'], b['constants'])
        ac, bc = a['components'][0]['constants'], b['components'][0]['constants']
        self.assertEqual([13, 16], [i for i in range(18) if ac[i] != bc[i]])
        self.assertEqual((.2, 2., 1.3), (ac[13], ac[16], ac[17]))

    def test_compensation_adds_only_the_declared_algebraic_record(self):
        a, b = (configuration(mode, self.raw) for mode in ('esst1a-fault', 'esst1a-compensated'))
        self.assertEqual(a['raw'], b['raw'])
        self.assertEqual(a['components'], b['components'])
        self.assertEqual(a['dynamic'] + "1 'IEEEVC' '1' .01 .02 /\n", b['dynamic'])

    def test_only_two_flat_modes_omit_the_fault(self):
        self.assertEqual({'open-flat', 'esst1a-flat'}, {m for m in MODES if not configuration(m, self.raw)['apply_fault']})

    def test_controller_time_constants_are_not_timestep_fitted(self):
        for mode in MODES:
            cfg = configuration(mode, self.raw)
            if 'components' in cfg:
                c = cfg['components'][0]['constants']
                self.assertEqual((.02, .2, .8, .1, .4, .04, 1.), (c[0], c[3], c[4], c[5], c[6], c[8], c[15]))

    def test_ceiling_discriminator_keeps_current_derating_and_disables_only_the_lr_branch(self):
        a, b = (configuration(mode, self.raw) for mode in ('esst1a-fault', 'esst1a-ceiling'))
        self.assertEqual(a['raw'], b['raw'])
        self.assertEqual(a['constants'], b['constants'])
        ac, bc = a['components'][0]['constants'], b['components'][0]['constants']
        self.assertEqual([11, 16], [i for i in range(18) if ac[i] != bc[i]])
        self.assertEqual((1.6, .2, 0.), (bc[11], bc[13], bc[16]))


if __name__ == '__main__':
    unittest.main()
