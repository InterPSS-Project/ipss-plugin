"""Reject semantic misrouting even when all entered parameters round-trip."""
import copy
import base64
import gzip
import hashlib
import json
import tempfile
from pathlib import Path
import unittest
from audit_oel3c_measurement import read_measurements, initial_measurement, require_initial_measurement, rmse
from oel3c_powerworld_probe import measurement_specification
from oel3c_routing_probe import configuration


class MeasurementTest(unittest.TestCase):
    def setUp(self):
        raw=Path('ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
        self.cfg=configuration('efd-fault',raw)
        self.fields=[['Generator','1 1','','',name,''] for name in
                     ('TSEfd','TSIfd','TSExciterState:1','TSOELState:1')]
        self.rows=[[0,1.105876207,1.105876207,1.233494282,.4890948832]]

    def test_observed_vfe_cannot_certify_entered_efd_selector(self):
        result=initial_measurement(self.fields,self.rows,self.cfg)
        self.assertEqual('mismatch',result['status'])
        self.assertEqual(['Vfe'],result['matching_physical_inputs'])
        with self.assertRaisesRegex(ValueError,'intended Efd'):
            require_initial_measurement(self.fields,self.rows,self.cfg)

    def test_equilibrium_coincidence_is_ambiguous_not_verified(self):
        self.rows[0][-1]=.3333*self.rows[0][1]
        result=require_initial_measurement(self.fields,self.rows,self.cfg)
        self.assertEqual('ambiguous',result['status'])
        self.assertEqual(['Efd','Ifd'],result['matching_physical_inputs'])
        self.assertFalse(result['runtime_routing_verified'])

    def test_unique_vfe_initial_match_still_does_not_accept_trajectory(self):
        self.cfg['components'][1]['icons']=(3,1)
        result=require_initial_measurement(self.fields,self.rows,self.cfg)
        self.assertEqual('consistent',result['status'])
        self.assertFalse(result['trajectory_acceptance'])

    def test_missing_or_duplicate_physical_channels_fail_closed(self):
        for fields in (self.fields[:-1],self.fields+[self.fields[0]]):
            with self.assertRaisesRegex(ValueError,'physical channel'):
                initial_measurement(fields,self.rows,self.cfg)

    def test_different_equation_or_nonzero_filter_is_not_silently_assumed(self):
        for component,index in ((0,14),(0,16),(1,2)):
            cfg=copy.deepcopy(self.cfg);cfg['components'][component]['constants'][index]=.1
            with self.assertRaisesRegex(ValueError,'requires zero TF'):
                initial_measurement(self.fields,self.rows,cfg)

    def test_rmse_strict_per_channel_limit_and_peak_are_separate(self):
        self.assertTrue(rmse([0,.0009])['passes_rmse'])
        self.assertFalse(rmse([.001])['passes_rmse'])
        result=rmse([.002]+[0]*99)
        self.assertTrue(result['passes_rmse']);self.assertEqual(.002,result['maximum_absolute'])
        self.assertEqual(100,result['samples'])

    def test_missing_or_nonfinite_errors_cannot_pass(self):
        for errors in ([],[float('nan')],[float('inf')]):
            with self.assertRaises(ValueError):rmse(errors)

    def test_extended_exports_retain_original_channels_and_physical_states(self):
        spec=measurement_specification('efd-fault',.0005,'source.dyr','translated.dyr')
        self.assertEqual(22,len(spec['results']))
        for name in ("GEN 2 '1'|TSRotorAngle","GEN 1 '1'|TSEfd","GEN 1 '1'|TSIfd",
                     "GEN 1 '1'|TSVOEL","GEN 1 '1'|TSOELState:1","GEN 1 '1'|TSOELState:2"):
            self.assertIn(name,spec['results'])

    def test_complete_portable_measurement_evidence_preserves_rejected_and_ambiguous_results(self):
        folder=Path('ipss.test.plugin.core/src/test/resources/oel3c-measurement-reference')
        index=json.loads((folder/'index.json').read_text())
        self.assertEqual(3,index['cold_pairs']);self.assertEqual(6018,index['raw_rows_all_cold_runs'])
        self.assertFalse(index['runtime_routing_verified']);self.assertFalse(index['trajectory_acceptance'])
        raw=Path('ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
        self.assertEqual(['efd-fault','ifd-fault','vfe-fault'],[p['name'] for p in index['profiles']])
        with tempfile.TemporaryDirectory() as name:
            for item,status in zip(index['profiles'],('mismatch','ambiguous','consistent')):
                body=gzip.decompress(base64.b64decode((folder/item['resource']).read_bytes()))
                self.assertEqual(item['raw_sha256'],hashlib.sha256(body).hexdigest())
                path=Path(name)/'complete.csv';path.write_bytes(body)
                fields,rows=read_measurements(path)
                self.assertEqual(22,len(fields));self.assertEqual(1003,len(rows))
                self.assertEqual([.05,.1],[a[0] for a,b in zip(rows,rows[1:]) if a[0]==b[0]])
                result=initial_measurement(fields,rows,configuration(item['name'],raw))
                self.assertEqual(status,result['status']);self.assertEqual(item['initial_measurement'],result)

    def test_missing_initialization_and_nonfinite_raw_channels_are_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            path=Path(name)/'invalid.csv'
            for row in ('0,nan','0.1,1','0,1\n-1,2'):
                path.write_text('ObjectFields\nObjectType,PrimaryKey,SecondaryKey,Label,VariableName,ColHeader\n'
                                'Generator,1 1,,,TSEfd,Efd\nEND\nResults,Test\n'+row+'\nEND\n')
                with self.assertRaises(ValueError):read_measurements(path)


if __name__=='__main__':unittest.main()
