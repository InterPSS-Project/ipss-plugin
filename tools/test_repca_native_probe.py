from pathlib import Path
import unittest
from repca_native_probe import MODES,configuration,inputs


class RepcaNativeProbeTest(unittest.TestCase):
    def test_matrix_retains_exact_native_cardinality_and_string_circuit_id(self):
        root=Path(__file__).resolve().parents[1]
        self.assertEqual(15,len(MODES))
        for mode in MODES:
            p=configuration(mode,'fine','a');self.assertEqual(.00025,p['step_s'])
            self.assertEqual(7,len(p['icons']));self.assertEqual(27,len(p['constants']));self.assertIsInstance(p['icons'][3],str)
            raw,dyr=inputs(root,p);self.assertTrue(raw);self.assertEqual(4,dyr.count('/'))
            self.assertEqual(38,len(dyr.splitlines()[-1].split()));self.assertNotIn('WTTQ',dyr)

    def test_remote_signed_origin_and_opposite_terminal_are_not_conflated(self):
        self.assertEqual([2,1,2,'1'],configuration('remote-v','coarse','a')['icons'][:4])
        self.assertEqual([0,-1,2,'1'],configuration('negative-from','coarse','a')['icons'][:4])
        self.assertEqual([0,2,1,'1'],configuration('reverse-q','coarse','a')['icons'][:4])

    def test_compensation_limits_and_disabled_frequency_are_declared(self):
        self.assertEqual([0.,0.],configuration('ldc-zero','coarse','a')['constants'][6:8])
        self.assertEqual([.02,.08],configuration('ldc','coarse','a')['constants'][6:8])
        self.assertEqual(.4,configuration('droop','coarse','a')['constants'][8])
        self.assertEqual([.245,.235],configuration('p-limit','coarse','a')['constants'][22:24])
        self.assertEqual(0,configuration('frequency-off','coarse','a')['icons'][6])
        self.assertFalse(configuration('flat-v','coarse','a')['fault'])

    def test_unknown_profiles_fail_closed(self):
        for args in [('bad','coarse','a'),('q','bad','a'),('q','fine','c')]:
            with self.assertRaises(ValueError):configuration(*args)


if __name__=='__main__':unittest.main()
