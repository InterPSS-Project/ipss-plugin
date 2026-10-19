from pathlib import Path
import struct,unittest
from wtpta_native_probe import MODES,configuration,inputs

class WtptaNativeProbeTest(unittest.TestCase):
    def test_matrix_retains_exact_ten_constants_and_both_timesteps(self):
        self.assertEqual(10,len(MODES))
        for mode in MODES:
            p=configuration(mode,'fine','a');self.assertEqual(10,len(p['constants']));self.assertEqual(.00025,p['step_s'])
        self.assertEqual(2.005,configuration('order-upper','coarse','a')['constants'][6])

    def test_external_order_driver_and_moving_reference_are_explicit(self):
        p=configuration('order-upper','coarse','a');self.assertEqual([.34,.24],[e['value'] for e in p['order_events']]);self.assertFalse(p['fault'])
        self.assertTrue(configuration('moving','fine','b')['curve']);self.assertEqual(0,configuration('lower','coarse','a')['theta0'])

    def test_zero_time_rate_discriminator_changes_only_the_declared_rate_bounds(self):
        a=configuration('zero','coarse','a');b=configuration('zero-rate','coarse','a')
        self.assertEqual(a['constants'][:8],b['constants'][:8]);self.assertEqual([.01,-.01],b['constants'][8:]);self.assertEqual(0,b['constants'][5])
        root=Path(__file__).resolve().parents[1];raw,dyr=inputs(root,b);self.assertTrue(raw);self.assertEqual(7,dyr.count('/'))
        record=[s for s in dyr.splitlines() if "'WTPTA1'" in s][0];self.assertEqual(14,len(record.split()))
        self.assertNotIn('USRMDL',dyr);self.assertNotIn('REPCA',dyr)

    def test_unknown_profiles_fail_closed_and_decimal_readback_is_not_a_tolerance(self):
        for args in [('bad','coarse','a'),('fault','bad','a'),('flat','fine','c')]:
            with self.assertRaises(ValueError):configuration(*args)
        native=struct.unpack('f',struct.pack('f',23.72))[0];self.assertGreater(abs(native-23.72),1e-7)

if __name__=='__main__':unittest.main()
