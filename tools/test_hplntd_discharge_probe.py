"""Offline exact-input guards for the discharge/reversal discriminator."""
import base64
from pathlib import Path
import shlex
import unittest
from hplntd_discharge_probe import DELAYS, EVENTS, variant
from hplntd_switching_probe import configuration
from audit_hplntd_discharge import toggles


class DischargeProbeTest(unittest.TestCase):
    def setUp(self):
        root = Path(__file__).resolve().parents[1]
        self.original = base64.b64decode((root / 'ipss.test.plugin.core/src/test/resources/hplntd-foundation-native/plant-low-window-coarse.dyr.b64').read_bytes())

    def test_only_discharge_delay_changes(self):
        baseline, devices = configuration(self.original, 'positive-fast')
        for mode, delay in DELAYS.items():
            body, actual = variant(self.original, mode)
            before = shlex.split(baseline.decode())
            after = shlex.split(body.decode())
            plant = next(line for line in body.decode().splitlines() if "'USRBUS'" in line)
            self.assertEqual(delay, float(shlex.split(plant)[95]))
            self.assertEqual(devices, actual)
            self.assertEqual(len(before), len(after))
            differences = [(a, b) for a, b in zip(before, after) if a != b]
            self.assertEqual(1, len(differences))
            self.assertEqual(('.03', str(delay)), differences[0])

    def test_wrapper_other_records_and_all_sixty_one_constants_remain_exact(self):
        baseline, _ = configuration(self.original, 'positive-fast')
        for mode in DELAYS:
            body, _ = variant(self.original, mode)
            original = [line for line in baseline.decode().splitlines() if "'USRBUS'" not in line]
            actual = [line for line in body.decode().splitlines() if "'USRBUS'" not in line]
            self.assertEqual(original, actual)
            fields = shlex.split(next(line for line in body.decode().splitlines() if "'USRBUS'" in line))
            self.assertEqual(['504', '0', '35', '61', '13', '45'], fields[3:9])
            self.assertEqual(61, len(fields[44:-1]))

    def test_three_cycle_event_and_physical_impedance_are_explicit(self):
        self.assertAlmostEqual(3 / 60, EVENTS[1]['time_s'] - EVENTS[0]['time_s'])
        self.assertEqual([0, 1], EVENTS[0]['impedance_system_pu'])
        self.assertEqual([0, 529], EVENTS[0]['impedance_ohm'])
        self.assertEqual(230 ** 2 / 100, EVENTS[0]['impedance_ohm'][1])

    def test_changed_baseline_and_unknown_mode_fail(self):
        with self.assertRaises(ValueError):
            variant(self.original + b'\n', 'long')
        with self.assertRaises(ValueError):
            variant(self.original, 'inferred')

    def test_toggle_audit_keeps_opening_and_reclose_times(self):
        initial = {'time_s': -.001, **{kind + str(i) + '_STATUS': 0 for kind in ('C', 'R') for i in range(1, 6)}}
        rows = [initial, {**initial, 'time_s': .01, 'C1_STATUS': 1},
                {**initial, 'time_s': .03}, {**initial, 'time_s': .08, 'C1_STATUS': 1}]
        self.assertEqual([{'device': 'C1', 'time_s': .01, 'in_service': 1},
                          {'device': 'C1', 'time_s': .03, 'in_service': 0},
                          {'device': 'C1', 'time_s': .08, 'in_service': 1}], toggles(rows))

    def test_duplicate_query_is_not_invented_as_another_action(self):
        row = {'time_s': 0, **{kind + str(i) + '_STATUS': 1 for kind in ('C', 'R') for i in range(1, 6)}}
        self.assertEqual([], toggles([row, dict(row), dict(row)]))


if __name__ == '__main__':
    unittest.main()
