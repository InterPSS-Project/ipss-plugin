"""Offline exact-input tests; no licensed runtime, Java, or network needed."""
import base64
from pathlib import Path
import shlex
import unittest

from hplntd_control_mode_probe import MODES, variant


class HybridControlProbeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[1]
        cls.original = base64.b64decode((root / 'ipss.test.plugin.core/src/test/resources/hplntd-foundation-native/plant-low-window-coarse.dyr.b64').read_bytes())

    def test_closed_profile_is_byte_identical(self):
        self.assertEqual(self.original, variant(self.original, 'closed'))

    def test_every_profile_changes_only_declared_tokens(self):
        before = self.original.decode().splitlines(keepends=True)
        index = next(i for i, s in enumerate(before) if "'USRBUS'" in s)
        tokens = shlex.split(before[index].rstrip().removesuffix('/'))
        for mode, changes in MODES.items():
            with self.subTest(mode=mode):
                after = variant(self.original, mode).decode().splitlines(keepends=True)
                self.assertEqual(len(before), len(after))
                self.assertEqual(before[:index] + before[index + 1:], after[:index] + after[index + 1:])
                actual = shlex.split(after[index].rstrip().removesuffix('/'))
                self.assertEqual(len(tokens), len(actual))
                self.assertEqual(set(changes), {i for i, pair in enumerate(zip(tokens, actual)) if pair[0] != pair[1]})
                for i, value in enumerate(actual):
                    self.assertEqual(changes.get(i, tokens[i]), value)

    def test_semantic_offsets_are_not_interchangeable(self):
        self.assertEqual({'p-disabled': {15: '0'}, 'q-disabled': {43: '0'},
                          'ki-zero': {46: '0'}, 'kig-zero': {60: '0'}},
                         {key: MODES[key] for key in ('p-disabled', 'q-disabled', 'ki-zero', 'kig-zero')})

    def test_changed_baseline_is_rejected_before_mutation(self):
        with self.assertRaisesRegex(ValueError, 'baseline'):
            variant(self.original + b'\n', 'p-disabled')

    def test_unknown_mode_is_rejected(self):
        with self.assertRaises(KeyError):
            variant(self.original, 'invented-selector')


if __name__ == '__main__':
    unittest.main()
