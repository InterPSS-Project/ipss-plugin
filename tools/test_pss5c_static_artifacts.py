"""Synthetic failure-metadata guards; these fixtures are never native references."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from pss5c_native_probe import PSS_CON,configuration
from genrou_native_probe import f32
from check_pss5c_static_artifacts import rejected


class StaticArtifactTest(unittest.TestCase):
    def fixture(self,folder):
        p=list(PSS_CON);p[:3]=[0.,0.,0.]
        pre=dict(status='native-diagnostic-preflight-not-trajectory-acceptance',trajectory_acceptance=False,
                 engine_module='synthetic-unit-test-only',**{k:'a'*64 for k in ('library_sha256','source_document_sha256','python_sha256','wrapper_sha256')},
                 components={'STAB':dict(model='PSS5C',actual_constants=list(map(f32,p)),actual_icons=[0],
                                        layout=dict(NICON=1,NCON=30,NSTATE=13,NVAR=6))})
        (folder/'diagnostic-preflight.json').write_text(json.dumps(pre))
        (folder/'dynamic.dyr').write_text("1 'PSS5C' '1' "+' '.join(map(str,p))+' /\n')
        (folder/'native.log').write_text('Initial conditions check O.K.\nNaN PSS_LOW_SIGNAL\n')
        for name in ('network.raw','probe.py','genrou_native_probe.py','native.out'):
            (folder/name).write_bytes(b'Synthetic rejected-output unit fixture, not native evidence')
        self.refresh(folder)
        rejected(folder,p) # Every negative control starts from a valid rejected-metadata fixture.
        return p

    def refresh(self,folder):
        files={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in folder.iterdir() if p.name!='rejected-run.json'}
        (folder/'rejected-run.json').write_text(json.dumps(dict(status='rejected-native-probe',trajectory_acceptance=False,
                                                              exception_type='TypeError',files=files)))

    def test_valid_failure_metadata_remains_explicitly_unaccepted(self):
        with tempfile.TemporaryDirectory() as name:
            folder=Path(name);p=self.fixture(folder);value=rejected(folder,p)
            self.assertFalse(value['trajectory_acceptance'])

    def test_a_generated_csv_cannot_promote_the_rejected_case(self):
        with tempfile.TemporaryDirectory() as name:
            folder=Path(name);p=self.fixture(folder);(folder/'native.csv').write_text('not-a-valid-reference')
            with self.assertRaises(AssertionError):rejected(folder,p)

    def test_export_exception_without_nonfinite_low_signal_is_not_model_evidence(self):
        with tempfile.TemporaryDirectory() as name:
            folder=Path(name);p=self.fixture(folder);(folder/'native.log').write_text('Initial conditions check O.K.\n')
            self.refresh(folder)
            with self.assertRaises(AssertionError):rejected(folder,p)

    def test_missing_preflight_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            folder=Path(name);p=self.fixture(folder);(folder/'diagnostic-preflight.json').unlink();self.refresh(folder)
            with self.assertRaises(AssertionError):rejected(folder,p)

    def test_wrong_readback_cannot_be_hidden_by_updating_a_hash(self):
        with tempfile.TemporaryDirectory() as name:
            folder=Path(name);p=self.fixture(folder);file=folder/'diagnostic-preflight.json';m=json.loads(file.read_text())
            m['components']['STAB']['actual_constants'][3]=33;file.write_text(json.dumps(m));self.refresh(folder)
            with self.assertRaises(AssertionError):rejected(folder,p)

    def test_wrong_allocation_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            folder=Path(name);p=self.fixture(folder);file=folder/'diagnostic-preflight.json';m=json.loads(file.read_text())
            m['components']['STAB']['layout']['NSTATE']=12;file.write_text(json.dumps(m));self.refresh(folder)
            with self.assertRaises(AssertionError):rejected(folder,p)

    def test_raw_file_mutation_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            folder=Path(name);p=self.fixture(folder);(folder/'native.out').write_bytes(b'changed')
            with self.assertRaises(AssertionError):rejected(folder,p)

    def test_acceptance_flag_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            folder=Path(name);p=self.fixture(folder);file=folder/'rejected-run.json';m=json.loads(file.read_text())
            m['trajectory_acceptance']=True;file.write_text(json.dumps(m))
            with self.assertRaises(AssertionError):rejected(folder,p)

    def test_ignored_model_is_not_a_static_model_counterexample(self):
        with tempfile.TemporaryDirectory() as name:
            folder=Path(name);p=self.fixture(folder);file=folder/'native.log';file.write_text(file.read_text()+'Undefined Model\n')
            self.refresh(folder)
            with self.assertRaises(AssertionError):rejected(folder,p)

    def test_static_discriminators_preserve_all_other_constants(self):
        raw=Path('ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
        for mode in ('static-low-flat','static-low-fault'):
            cfg=configuration(mode,raw);p=cfg['components'][1]['constants']
            self.assertEqual({0,1,2},{i for i,(a,b) in enumerate(zip(PSS_CON,p)) if a!=b})
            self.assertTrue(cfg['record_rejection_preflight']);self.assertTrue(cfg['record_live_variables'])
            self.assertEqual(mode.endswith('-fault'),cfg['apply_fault']);self.assertNotIn('pss5c_observation_diagnostic',cfg)
        self.assertNotIn('record_rejection_preflight',configuration('flat',raw))


if __name__=='__main__':unittest.main()
