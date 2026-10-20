"""A repeated trajectory does not prove the entered reference participates."""
import base64,csv,gzip,hashlib,json,math
from pathlib import Path
import unittest
from audit_oel3c_reference_equation import audit,from_powerworld
from oel3c_pickup_probe import with_pickup
from oel3c_routing_probe import configuration


class ReferenceEquationTest(unittest.TestCase):
    def test_entered_reference_is_checked_against_measured_integral_and_output(self):
        rows=[dict(time_s=0,input=.45,integral=0,output=0),dict(time_s=.1,input=.6,integral=-.01,output=-.02)]
        result=audit(rows,.5)
        self.assertTrue(result['reference_identity_verified']);self.assertTrue(result['entered_reference_equation_consistent'])
        self.assertFalse(result['initialized_measurement_candidate_consistent'])

    def test_initialized_measurement_candidate_is_diagnostic_not_a_replacement(self):
        rows=[dict(time_s=0,input=.45,integral=0,output=0),dict(time_s=.1,input=.6,integral=-.01,output=-.025)]
        result=audit(rows,.5)
        self.assertFalse(result['entered_reference_equation_consistent']);self.assertFalse(result['reference_identity_verified'])
        self.assertTrue(result['initialized_measurement_candidate_consistent'])
        self.assertAlmostEqual(.45,result['inferred_powered_reference_range'][0])
        self.assertEqual(.5,result['declared_powered_reference']);self.assertFalse(result['production_reference_changed'])

    def test_inactive_rows_cannot_certify_an_unobservable_reference(self):
        result=audit([dict(time_s=0,input=.4,integral=0,output=0)],.5)
        self.assertTrue(result['entered_reference_equation_consistent']);self.assertFalse(result['reference_identity_verified'])
        self.assertIsNone(result['inferred_powered_reference_range'])

    def test_output_clipping_does_not_create_an_inferred_reference(self):
        result=audit([dict(time_s=0,input=20,integral=-1,output=-1)],.5)
        self.assertEqual(0,result['observable_active_rows']);self.assertEqual(0,result['rows_dropped'])

    def test_invalid_or_nonfinite_equation_contract_fails_closed(self):
        row=dict(time_s=0,input=.4,integral=0,output=0)
        for args in (([],.5),([dict(row,input=float('nan'))],.5)):
            with self.assertRaises(ValueError):audit(*args)
        for kwargs in (dict(gain=0),dict(exponent=0),dict(lower=1),dict(proportional_gain=0)):
            with self.assertRaises(ValueError):audit([row],.5,**kwargs)

    def test_predeclared_discriminator_changes_only_pickup(self):
        raw=Path('ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
        for pickup in (.4,.6):
            cfg=configuration('vfe-fault',raw);before=cfg['dynamic'].split()
            changed=with_pickup(cfg,pickup)['dynamic'].split()
            self.assertEqual(1,sum(a!=b for a,b in zip(before,changed)))
            self.assertEqual(pickup,cfg['components'][1]['constants'][0])
        with self.assertRaises(ValueError):with_pickup(configuration('vfe-fault',raw),.5)

    def test_native_whole_histories_pin_the_entered_reference_without_dropping_rows(self):
        folder=Path('ipss.test.plugin.core/src/test/resources/oel3c-recorded-input-reference')
        index=json.loads((folder/'index.json').read_text());self.assertEqual(6,index['cold_pairs'])
        self.assertEqual(6,len(index['profiles']));total=0
        for item in index['profiles']:
            body=gzip.decompress(base64.b64decode((folder/item['resource']).read_bytes()))
            self.assertEqual(item['raw_sha256'],hashlib.sha256(body).hexdigest())
            rows=list(csv.DictReader(body.decode().splitlines()));times=[float(r['time_s']) for r in rows]
            self.assertEqual(item['raw_rows'],len(rows));total+=len(rows)
            self.assertEqual(2,sum(t<0 for t in times));self.assertEqual(2,sum(a==b for a,b in zip(times,times[1:])))
            self.assertTrue(all(float(row['OEL_REFERENCE'])==.5 for row in rows))
            self.assertTrue(all(math.isfinite(float(value)) for row in rows for value in row.values()))
        self.assertEqual(9030,total)

    def test_pickup_only_cold_pairs_reject_metadata_only_success(self):
        resources=Path('ipss.test.plugin.core/src/test/resources')
        index=json.loads((resources/'oel3c-recorded-input-reference/pickup-discriminators.json').read_text())
        self.assertEqual(2,index['cold_pairs']);self.assertEqual(4012,index['raw_rows_all_runs'])
        self.assertFalse(index['production_reference_changed']);self.assertFalse(index['trajectory_acceptance'])
        for item in index['profiles']:
            body=gzip.decompress(base64.b64decode((resources/item['raw_resource']).read_bytes()))
            self.assertEqual(item['raw_sha256'],hashlib.sha256(body).hexdigest())
            for readback in item['limiter_readbacks']:self.assertEqual(item['pickup'],float(readback['ITFpu']))
            result=item['equation_audit']
            self.assertFalse(result['entered_reference_equation_consistent'])
            self.assertTrue(result['initialized_measurement_candidate_consistent'])
            self.assertFalse(result['reference_identity_verified'])


if __name__=='__main__':unittest.main()
