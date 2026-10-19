"""Offline tests for direct-reference input mutations and runner event scope."""
import base64
import inspect
from pathlib import Path
import shlex
import sys
import unittest
from unittest.mock import Mock, patch
import hplntd_fault_probe
from hplntd_reference_limit_probe import MODES, variant
from audit_hplntd_control_modes import FROZEN_RUNNER_SHA256


class ReferenceLimitProbeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = base64.b64decode((Path(__file__).resolve().parents[1] / 'ipss.test.plugin.core/src/test/resources/hplntd-foundation-native/plant-low-window-coarse.dyr.b64').read_bytes())

    def test_all_variants_change_only_declared_tokens(self):
        before = self.original.decode().splitlines(keepends=True)
        index = next(i for i, line in enumerate(before) if "'USRBUS'" in line)
        tokens = shlex.split(before[index])
        for mode, (changes, _) in MODES.items():
            with self.subTest(mode=mode):
                after = variant(self.original, mode).decode().splitlines(keepends=True)
                self.assertEqual(before[:index] + before[index + 1:], after[:index] + after[index + 1:])
                expected = tokens.copy()
                for offset, value in changes.items():
                    expected[offset] = value
                self.assertEqual(expected, shlex.split(after[index]))

    def test_default_events_are_backward_compatible(self):
        self.assertEqual(((3, .05), (1, .02)), inspect.signature(hplntd_fault_probe.run_case).parameters['reference_steps'].default)

    def test_only_reference_offsets_are_allowed_before_artifact_creation(self):
        modules = {name: Mock() for name in ('psse3607', 'psspy', 'dyntools')}
        with patch.dict(sys.modules, modules):
            for events in ((), ((4, .1),), ((-1, .1),), ((0, float('nan')),), ((0, float('inf')),)):
                with self.subTest(events=events), self.assertRaises(AssertionError):
                    hplntd_fault_probe.run_case('invalid-reference-must-not-write', '.0005', reference_steps=events)

    def test_old_runner_provenance_is_pinned_not_rewritten(self):
        self.assertEqual('cea8b6133e3909cbb1cd2c38d4ff6fefe426768a6bf908c6a42939a6ece29cf8', FROZEN_RUNNER_SHA256)

    def test_changed_input_and_unknown_mode_are_rejected(self):
        with self.assertRaises(ValueError):
            variant(self.original + b'\n', 'v-inside-up')
        with self.assertRaises(KeyError):
            variant(self.original, 'invented-limit-mode')


if __name__ == '__main__':
    unittest.main()
