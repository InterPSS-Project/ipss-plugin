from pathlib import Path
import unittest
from wtara_native_probe import MODES,configuration,inputs

class WtaraNativeProbeTest(unittest.TestCase):
    def test_matrix_preserves_two_constants_and_two_timesteps(self):
        self.assertEqual(8,len(MODES))
        for mode in MODES:
            self.assertEqual(2,len(configuration(mode,'coarse','a')['constants']))
            self.assertEqual(.00025,configuration(mode,'fine','b')['step_s'])

    def test_nonzero_pitch_exercises_both_product_signs_and_returns_to_equilibrium(self):
        p=configuration('nonzero','coarse','a');self.assertEqual([.007,5],p['constants'])
        self.assertEqual([7,3,5],[e['pitch_deg'] for e in p['pitch_events']])
        self.assertTrue(configuration('nonunit','fine','a')['curve'])
        self.assertEqual([0,0],configuration('zero-gain','coarse','a')['constants'])
        self.assertEqual([8,9,0],[e['pitch_deg'] for e in configuration('negative','coarse','a')['pitch_events']])

    def test_native_inputs_change_only_aerodynamics_and_declared_nonunit_curve(self):
        root=Path(__file__).resolve().parents[1];raw,dyr=inputs(root,configuration('nonzero','coarse','a'))
        self.assertEqual(7,dyr.count('/'));self.assertIn("1 'WTARA1' '1' 0.007 5.0 /",dyr)
        self.assertIn("1 'WTPTA1' '1' 0 0 0 0 0 .3 27 0 10 -10 /",dyr)
        self.assertNotIn('USRMDL',dyr);self.assertNotIn('REPCA',dyr);self.assertTrue(raw)

    def test_unknown_profile_fails_closed(self):
        for args in [('other','coarse','a'),('flat','other','a'),('pitch','fine','c')]:
            with self.assertRaises(ValueError):configuration(*args)

if __name__=='__main__':unittest.main()
