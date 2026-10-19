"""Offline rejection guards for the fixed PSS1A/IEEEST allocation contract."""
import json
from pathlib import Path
import tempfile
import unittest
from audit_psssb_dialect import sha
from audit_pss1a_dialect import verify_pss1a


class Pss1aDialectEvidenceTest(unittest.TestCase):
    def fixture(self, folder, model='PSS1A'):
        for name in ('probe.py', 'psssb_dialect_probe.py', 'network.raw', 'dynamic.dyr'):
            (folder / name).write_text('input')
        (folder / 'native.log').write_text('Undefined ModelPSS1A\nRECORD IGNORED\n' if model=='PSS1A' else 'model read')
        keys=('ICON', 'CON', 'STATE', 'VAR', 'NICON', 'NCON', 'NSTATE', 'NVAR')
        layout=({key: [4, None] for key in keys} if model=='PSS1A' else
                {key: [0, count] for key, count in zip(keys, (1, 17, 9, 1, 2, 17, 7, 2))})
        m=dict(model=model, evidence='allocation-only', load_code=0, attached=model=='IEEEST',
               layout=layout, constants=[] if model=='PSS1A' else [0.] * 17,
               files={name: sha(folder / name) for name in
                      ('probe.py', 'psssb_dialect_probe.py', 'network.raw', 'dynamic.dyr', 'native.log')})
        self.save(folder, m)
        return m

    def save(self, folder, m):
        (folder / 'manifest.json').write_text(json.dumps(m))

    def test_both_exact_contracts_are_distinguished(self):
        for model in ('PSS1A', 'IEEEST'):
            with tempfile.TemporaryDirectory() as name:
                folder=Path(name);self.fixture(folder, model)
                self.assertEqual(model=='IEEEST', verify_pss1a(folder)['attached'])

    def test_wrong_native_allocation_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            folder=Path(name);m=self.fixture(folder, 'IEEEST');m['layout']['NSTATE']=[0, 16]
            self.save(folder,m)
            with self.assertRaises(AssertionError):verify_pss1a(folder)

    def test_generic_loader_success_does_not_prove_model_absence(self):
        with tempfile.TemporaryDirectory() as name:
            folder=Path(name);m=self.fixture(folder);m['layout']={};self.save(folder,m)
            with self.assertRaises(AssertionError):verify_pss1a(folder)

    def test_rejection_log_cannot_name_another_model(self):
        with tempfile.TemporaryDirectory() as name:
            folder=Path(name);m=self.fixture(folder)
            (folder/'native.log').write_text('Undefined ModelPSSSB')
            m['files']['native.log']=sha(folder/'native.log');self.save(folder,m)
            with self.assertRaises(AssertionError):verify_pss1a(folder)

    def test_changed_helper_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            folder=Path(name);self.fixture(folder)
            (folder/'psssb_dialect_probe.py').write_text('changed')
            with self.assertRaises(AssertionError):verify_pss1a(folder)


if __name__=='__main__':unittest.main()
