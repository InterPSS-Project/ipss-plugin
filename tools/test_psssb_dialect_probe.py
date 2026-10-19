"""Negative guards for allocation evidence; no simulator is required."""
import json
from pathlib import Path
import tempfile
import unittest
from audit_psssb_dialect import sha, verify


class DialectEvidenceTest(unittest.TestCase):
    def fixture(self, folder):
        for name in ('probe.py', 'network.raw', 'dynamic.dyr'):
            (folder / name).write_text('input')
        (folder / 'native.log').write_text('Undefined ModelPSSSB\nRECORD IGNORED\n')
        m = dict(model='PSSSB', evidence='allocation-only', load_code=0, attached=False,
                 layout={k: [4, None] for k in ('CON', 'ICON', 'STATE', 'VAR', 'NCON', 'NICON', 'NSTATE', 'NVAR')},
                 constants=[], files={name: sha(folder / name) for name in
                                     ('probe.py', 'network.raw', 'dynamic.dyr', 'native.log')})
        (folder / 'manifest.json').write_text(json.dumps(m))
        return m

    def test_absence_requires_explicit_unknown_model_log(self):
        with tempfile.TemporaryDirectory() as name:
            folder = Path(name); m = self.fixture(folder)
            self.assertFalse(verify(folder)['attached'])
            (folder / 'native.log').write_text('installation error')
            m['files']['native.log'] = sha(folder / 'native.log')
            (folder / 'manifest.json').write_text(json.dumps(m))
            with self.assertRaises(AssertionError): verify(folder)

    def test_changed_input_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            folder = Path(name); self.fixture(folder)
            (folder / 'dynamic.dyr').write_text('changed')
            with self.assertRaises(AssertionError): verify(folder)

    def test_loader_failure_is_not_model_rejection_evidence(self):
        with tempfile.TemporaryDirectory() as name:
            folder = Path(name); m = self.fixture(folder); m['load_code'] = 1
            (folder / 'manifest.json').write_text(json.dumps(m))
            with self.assertRaises(AssertionError): verify(folder)

    def test_absence_cannot_override_attached_allocation(self):
        with tempfile.TemporaryDirectory() as name:
            folder = Path(name); m = self.fixture(folder); m['layout']['STATE'] = [0, 9]
            (folder / 'manifest.json').write_text(json.dumps(m))
            with self.assertRaises(AssertionError): verify(folder)

    def test_missing_allocation_queries_are_not_absence_evidence(self):
        with tempfile.TemporaryDirectory() as name:
            folder = Path(name); m = self.fixture(folder); m['layout'] = {}
            (folder / 'manifest.json').write_text(json.dumps(m))
            with self.assertRaises(AssertionError): verify(folder)


if __name__ == '__main__': unittest.main()
