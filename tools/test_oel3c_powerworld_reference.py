"""Public whole-file artifacts are portable; importing them does not assert trajectory acceptance."""
import base64,gzip,hashlib,json,tempfile
from pathlib import Path
import unittest
from check_oel3c_powerworld_artifacts import trajectory
from oel3c_routing_probe import configuration
from oel3c_powerworld_probe import translate

class PublicReferenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root=Path(__file__).resolve().parents[1]
        cls.base=cls.root/'ipss.test.plugin.core/src/test/resources/oel3c-powerworld-reference'
        cls.index=json.loads((cls.base/'index.json').read_text())
    def test_six_profiles_preserve_full_metadata_and_all_event_rows(self):
        self.assertEqual(6,len(self.index['profiles']))
        with tempfile.TemporaryDirectory() as name:
            for item in self.index['profiles']:
                body=gzip.decompress(base64.b64decode((self.base/item['resource']).read_bytes()))
                self.assertEqual(item['raw_sha256'],hashlib.sha256(body).hexdigest())
                file=Path(name)/'raw.csv';file.write_bytes(body);fields,rows=trajectory(file)
                self.assertEqual(12,len(fields));self.assertEqual(item['raw_rows'],len(rows))
                self.assertEqual([.05,.1],[a[0] for a,b in zip(rows,rows[1:]) if a[0]==b[0]])
    def test_declared_source_and_translated_dialects_have_no_parameter_fit(self):
        raw=(self.root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
        for item in self.index['profiles']:
            profile=item['name'].rsplit('-',1)[0];cfg=configuration(profile,raw)
            self.assertEqual(cfg['dynamic'],item['native_input']);self.assertEqual(translate(cfg),item['translated_input'])
    def test_repeatability_and_observation_scope_remain_explicit(self):
        self.assertEqual(18036,self.index['raw_rows_all_cold_runs']);self.assertEqual(6,self.index['cold_pairs'])
        self.assertTrue(self.index['model_readback_verified']);self.assertFalse(self.index['trajectory_acceptance'])
        self.assertEqual('Impedance',self.index['load_model']);self.assertEqual('RK2',self.index['integration_method'])

if __name__=='__main__':unittest.main()
