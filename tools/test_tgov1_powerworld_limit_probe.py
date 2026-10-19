import unittest
from tgov1_powerworld_limit_probe import RESULTS,canonical_sha,specification


class Tgov1PowerWorldLimitProbeTest(unittest.TestCase):
    def test_exact_command_and_reversal_for_all_four_profiles(self):
        for direction in ['upper','lower']:
            for grid in ['coarse','fine']:
                spec=specification(direction,grid,'a');events=spec['contingency']['events']
                sign='' if direction=='upper' else '-';reverse='-' if direction=='upper' else ''
                self.assertEqual([.05,.2],[x['time_s'] for x in events])
                self.assertEqual(f'CHANGEBY Governor_Setpoint {sign}0.1 pu',events[0]['action'])
                self.assertEqual(f'CHANGEBY Governor_Setpoint {reverse}0.1 pu',events[1]['action'])
                self.assertTrue(all(x['object']=="Gen 1 '1'" for x in events))
                self.assertFalse(spec['auto_correct']);self.assertNotIn('powerworld_model_overrides',spec)

    def test_reference_machine_and_all_electrical_states_are_recorded(self):
        self.assertEqual(14,len(RESULTS));self.assertEqual(14,len(set(RESULTS)))
        for i in range(3,7):self.assertIn(f"GEN 1 '1'|TSMachineState:{i}",RESULTS)
        self.assertIn("GEN 2 '1'|TSSpeed",RESULTS);self.assertIn("GEN 2 '1'|TSRotorAngle",RESULTS)
        spec=specification('upper','coarse','a');spec['results'].clear();self.assertEqual(14,len(RESULTS))

    def test_unknown_profile_is_rejected_and_steps_are_matched(self):
        for args in [('other','coarse','a'),('upper','other','b'),('upper','fine','other')]:
            with self.assertRaises(ValueError):specification(*args)
        self.assertEqual(.5,specification('lower','coarse','a')['simulation']['stop_s'])
        self.assertEqual(.0005,specification('lower','coarse','a')['simulation']['step_s'])
        self.assertEqual(.00025,specification('lower','fine','b')['simulation']['step_s'])

    def test_digest_only_normalizes_newlines(self):
        self.assertEqual(canonical_sha(b'a\r\nb\r'),canonical_sha(b'a\nb\n'))
        self.assertNotEqual(canonical_sha(b'a\nb\n'),canonical_sha(b'a\nb'))


if __name__=='__main__':unittest.main()
