"""Offline exact-selector token checks, with no reference runtime dependency."""
import base64
from pathlib import Path
import shlex
import unittest
from hplntd_selector_probe import MODES, variant
from audit_hplntd_selectors import compare_repeats


class SelectorProbeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = base64.b64decode((Path(__file__).resolve().parents[1] / 'ipss.test.plugin.core/src/test/resources/hplntd-foundation-native/plant-low-window-coarse.dyr.b64').read_bytes())

    def test_every_selector_preserves_all_other_tokens_and_records(self):
        before = self.original.decode().splitlines(keepends=True)
        index = next(i for i, line in enumerate(before) if "'USRBUS'" in line)
        tokens = shlex.split(before[index])
        for mode, changes in MODES.items():
            with self.subTest(mode=mode):
                after = variant(self.original, mode).decode().splitlines(keepends=True)
                self.assertEqual(before[:index] + before[index + 1:], after[:index] + after[index + 1:])
                expected = tokens.copy()
                for offset, value in changes.items():
                    expected[offset] = value
                self.assertEqual(expected, shlex.split(after[index]))

    def test_changed_source_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'baseline'):
            variant(self.original + b'\n', 'p-disabled-pf')

    def test_unknown_selector_is_rejected(self):
        with self.assertRaises(KeyError):
            variant(self.original, 'unsupported')

    def test_unused_disabled_variables_do_not_change_observed_comparison(self):
        before = [{'time_s': 0., 'PLANT_STATE2': 0., 'PLANT_VAR39': .3, 'PLANT_VAR0': 0.}]
        after = [{**before[0], 'PLANT_VAR0': 1e28}]
        ignored, digest = compare_repeats('q-disabled-pf', before, after)
        self.assertEqual(['PLANT_VAR' + str(i) for i in (0, 1, 7, 9)], ignored)
        self.assertEqual(digest, compare_repeats('q-disabled-pf', before, before)[1])
        self.assertEqual(1e28, after[0]['PLANT_VAR0'], 'Raw observations must not be rewritten')

    def test_state_command_reference_or_event_changes_are_never_normalized(self):
        before = [{'time_s': 0., 'PLANT_STATE2': 0., 'PLANT_VAR39': .3, 'PLANT_VAR3': .3}]
        for channel in before[0]:
            with self.subTest(channel=channel), self.assertRaisesRegex(ValueError, 'observed channel'):
                compare_repeats('q-disabled-pf', before, [{**before[0], channel: 1.}])

    def test_unused_storage_exception_cannot_hide_enabled_path_changes(self):
        with self.assertRaisesRegex(ValueError, 'observed channel'):
            compare_repeats('p-disabled-pf', [{'PLANT_VAR0': 0.}], [{'PLANT_VAR0': 1.}])


if __name__ == '__main__':
    unittest.main()
