"""Artifact-audit negative controls, independent of a reference installation."""
import csv
import json
from pathlib import Path
import tempfile
import unittest

from audit_hplntd_control_modes import digest, read_run


class ControlArtifactAuditTest(unittest.TestCase):
    def fixture(self, directory, *, duplicate=True, finite=True, channels=89):
        csv_path = directory / 'native.csv'
        with csv_path.open('w', newline='') as stream:
            writer = csv.writer(stream)
            writer.writerow(['time_s'] + ['channel' + str(i) for i in range(channels)])
            times = [-.001, 0., 0.] + [i * .0005 for i in range(1, 400)] + [.2, .2, .2005]
            self.assertEqual(405, len(times))
            if not duplicate:
                times = [i * .0005 for i in range(405)]
            for time in times:
                writer.writerow([time] + [0 if finite else float('nan')] * channels)
        manifest = dict(status='native-converged-profile', step_s=.0005, sample_count=405,
                        channel_count=89, events=[dict(time_s=.05, offset=3), dict(time_s=.05, offset=1)],
                        files={'native.csv': digest(csv_path)})
        (directory / 'manifest.json').write_text(json.dumps(manifest))

    def test_complete_finite_raw_stream_is_accepted(self):
        with tempfile.TemporaryDirectory() as name:
            directory = Path(name)
            self.fixture(directory)
            manifest, rows = read_run(directory)
            self.assertEqual(405, len(rows))
            self.assertEqual(89, manifest['channel_count'])

    def test_changed_output_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            directory = Path(name)
            self.fixture(directory)
            with (directory / 'native.csv').open('a') as stream:
                stream.write('changed\n')
            with self.assertRaisesRegex(ValueError, 'hash/path'):
                read_run(directory)

    def test_nonfinite_samples_are_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            directory = Path(name)
            self.fixture(directory, finite=False)
            with self.assertRaisesRegex(ValueError, 'Nonfinite'):
                read_run(directory)

    def test_missing_channel_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            directory = Path(name)
            self.fixture(directory, channels=88)
            with self.assertRaisesRegex(ValueError, 'channels'):
                read_run(directory)

    def test_removed_duplicate_samples_are_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            directory = Path(name)
            self.fixture(directory, duplicate=False)
            with self.assertRaisesRegex(ValueError, 'duplicate'):
                read_run(directory)

    def test_custom_reference_offsets_require_explicit_profile_contract(self):
        with tempfile.TemporaryDirectory() as name:
            directory = Path(name)
            self.fixture(directory)
            path = directory / 'manifest.json'
            manifest = json.loads(path.read_text())
            manifest['events'] = [dict(time_s=.05, offset=0)]
            path.write_text(json.dumps(manifest))
            self.assertEqual(405, len(read_run(directory, (0,))[1]))
            with self.assertRaisesRegex(ValueError, 'reference events'):
                read_run(directory)


if __name__ == '__main__':
    unittest.main()
