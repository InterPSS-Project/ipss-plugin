"""Quality classification retains event sides and rejects actual action failures."""
import unittest
from verify_tiocr_reference_quality import assess, audit

class RelayQualityTest(unittest.TestCase):
    table='time_s,VAR0,VAR1\n-0.001,0.3,0\n0,0.3,0\n0.05,0.5,1\n0.05,0.2,1\n0.1,0.2,1\n'
    good='Initial conditions check O.K.\n'
    def test_contact_closure_does_not_override_rejected_target(self):
        result=assess(self.good+'TIOCR1 circuit 1 from 1 to 2: invalid load specified',self.table)
        self.assertEqual(['invalid-load-action'],result['reasons'])
        self.assertEqual(5,result['rows']);self.assertEqual(1,result['event_side_duplicates'])
    def test_historical_complete_manifest_cannot_override_action_error(self):
        import tempfile,zipfile
        from pathlib import Path
        Path("target").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir="target") as directory:
            root=Path(directory);assert root.resolve().is_relative_to(Path('target').resolve())
            folder=root/'tiocr-negative';folder.mkdir()
            with zipfile.ZipFile(folder/'captures.zip','w') as archive:
                archive.writestr('a/native.log',self.good+'TIOCR1 circuit 1 from 1 to 2: invalid load specified')
                archive.writestr('a/native.csv',self.table)
                archive.writestr('a/manifest.json','{"status":"complete"}')
            from unittest.mock import patch
            with patch.object(Path,"read_bytes",side_effect=AssertionError("Archive hashing must stream")):
                result=audit(root)
            self.assertEqual(1,result['captures']);self.assertEqual(1,result['rejected'])
            self.assertEqual('complete',result['results'][0]['original_manifest_status'])
            self.assertEqual('rejected-capture-quality',result['results'][0]['status'])
    def test_valid_capture_preserves_every_sample(self):
        result=assess(self.good,self.table)
        self.assertEqual([],result['reasons']);self.assertEqual(5,result['rows']);self.assertEqual(1,result['event_side_duplicates'])
    def test_specific_failures_not_benign_warning_text(self):
        for message,reason in [('Network not converged','network-not-converged'),('Incomplete data record','incomplete-input-record')]:
            self.assertIn(reason,assess(self.good+message,self.table)['reasons'])
        self.assertEqual([],assess(self.good+'Warning: plot label unavailable; error tolerance = 0.0001',self.table)['reasons'])
    def test_time_only_header_is_not_measurement_evidence(self):
        self.assertIn("invalid-channel-header",assess(self.good,"time_s\n0\n.1\n")["reasons"])
    def test_positive_initialization_required(self):
        self.assertIn('missing-positive-initialization-check',assess('INITIAL CONDITIONS SUSPECT',self.table)['reasons'])
    def test_finite_complete_ordered_channels(self):
        for table,reason in [(self.table.replace('0.2','NaN'),'nonfinite-channel-value'),('time_s,V\n0,1\n-.1,1\n','decreasing-time'),('time_s,V\n0\n','incomplete-channel-row')]:
            self.assertIn(reason,assess(self.good,table)['reasons'])

if __name__=='__main__':unittest.main()
