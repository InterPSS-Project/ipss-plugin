"""Offline rejection guards for the cold-reference auditor."""
import hashlib
from pathlib import Path
import tempfile
import unittest
from audit_pss_canonical_schema import checked_files, input_values, table


class SchemaAuditTest(unittest.TestCase):
    def test_rejects_altered_bytes(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); (root / 'input').write_bytes(b'new')
            with self.assertRaises(AssertionError):
                checked_files(root, {'files': {'input': hashlib.sha256(b'old').hexdigest()}})

    def test_rejects_relative_escape(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(AssertionError):
                checked_files(Path(folder), {'files': {'../input': 'unused'}})

    def test_exact_count_rejects_vendor_filter(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'dynamic.dyr'
            for model, width in [('PSS6C', 34), ('PSS7C', 38)]:
                path.write_text("1 '" + model + "' '1' " + ' '.join(['0'] * width) + ' /\n')
                self.assertEqual(width, sum(map(len, input_values(path, model))))
                path.write_text(path.read_text().replace(' /', ' .123 /'))
                with self.assertRaises(AssertionError): input_values(path, model)

    def test_rejects_fractional_selectors(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'dynamic.dyr'
            path.write_text("1 'PSS6C' '1' .5 " + ' '.join(['0'] * 33) + ' /\n')
            with self.assertRaises(AssertionError): input_values(path, 'PSS6C')

    def test_rejects_nonfinite_data(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'trace.csv'; path.write_text('time,value\n0,nan\n')
            with self.assertRaises(AssertionError): table(path)

    def test_retains_duplicate_event_rows(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'trace.csv'; path.write_text('time,value\n0,1\n.05,1\n.05,.3\n')
            self.assertEqual([[0, 1], [.05, 1], [.05, .3]], table(path)[1])


if __name__ == '__main__': unittest.main()
