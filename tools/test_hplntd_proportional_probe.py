"""Offline input-schema tests for proportional-equilibrium discriminators."""
import base64
from pathlib import Path
import shlex
import unittest
from hplntd_proportional_probe import MODES, variant
from audit_hplntd_proportional import ACCEPTED


class ProportionalProbeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = base64.b64decode((Path(__file__).resolve().parents[1] / 'ipss.test.plugin.core/src/test/resources/hplntd-foundation-native/plant-low-window-coarse.dyr.b64').read_bytes())

    def test_only_declared_tokens_change_and_other_records_are_identical(self):
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

    def test_rejected_or_unresolved_profiles_cannot_enter_acceptance_set(self):
        self.assertEqual(9, len(ACCEPTED))
        self.assertNotIn('ki-deadband', ACCEPTED)
        self.assertFalse(any(mode.startswith('reject-') for mode in ACCEPTED))

    def test_changed_source_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'baseline'):
            variant(self.original + b'\n', 'ki-vcomp')

    def test_unknown_configuration_is_rejected(self):
        with self.assertRaises(KeyError):
            variant(self.original, 'invented-gain-mode')


if __name__ == '__main__':
    unittest.main()
