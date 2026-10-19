from pathlib import Path
import unittest
from wtdta_native_probe import MODES,configuration,inputs


class WtdtaNativeProbeTest(unittest.TestCase):
    def test_exact_native_matrix_and_frequency_discriminator(self):
        self.assertEqual(9,len(MODES))
        for mode in MODES:
            p=configuration(mode,'fine','a');self.assertEqual(5,len(p['constants']));self.assertEqual(.00025,p['step_s'])
            self.assertEqual(mode not in ('flat','curve-flat'),p['fault'])
        self.assertEqual(3,configuration('frequency','coarse','a')['constants'][3])
        self.assertEqual(50,configuration('grid50','coarse','a')['nominal_hz'])

    def test_single_mass_and_nonunit_damping_are_independent_branches(self):
        self.assertEqual(0,configuration('single','coarse','a')['constants'][2])
        self.assertTrue(configuration('single-curve','coarse','a')['curve'])
        self.assertEqual(0,configuration('undamped','coarse','a')['constants'][1])
        self.assertTrue(configuration('undamped','coarse','a')['curve'])

    def test_inputs_change_only_declared_native_data(self):
        root=Path(__file__).resolve().parents[1];raw,dyr=inputs(root,configuration('grid50','coarse','a'))
        self.assertIn(' 50.00 /',raw.splitlines()[0]);self.assertEqual(7,dyr.count('/'))
        self.assertIn("1 'WTDTA1' '1' 5.0 0.1 0.8 1.5 0.2 /",dyr)
        self.assertNotIn('USRMDL',dyr);self.assertNotIn('REPCA',dyr)

    def test_unknown_profiles_fail_closed(self):
        for args in [('other','coarse','a'),('flat','other','a'),('fault','fine','c')]:
            with self.assertRaises(ValueError):configuration(*args)


if __name__=='__main__':unittest.main()
