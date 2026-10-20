import json
import math
import struct
import tempfile
import unittest
from pathlib import Path
from benchmark_observations import iter_observations


class ObservationReaderTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.metadata = dict(schema="texas2k-full-network-observations-v1",
                             trace_format="ipss-observations-binary64-v1", trace_file="full-network.bin",
                             channels=[dict(name="voltage")], samples=2, first_time_s=1., last_time_s=1.)
        self.data = b"IPSSOBS1" + struct.pack(">I", 1)
        for side, value in [(b"STEP_END", -0.), (b"EVENT_POST", math.nextafter(1., 2.))]:
            self.data += struct.pack(">dI", 1., len(side)) + side + struct.pack(">d", value)

    def read(self, data=None):
        (self.root / "full-network-channels.json").write_text(json.dumps(self.metadata))
        (self.root / "full-network.bin").write_bytes(self.data if data is None else data)
        return list(iter_observations(self.root))

    def test_exact_values_signed_zero_and_duplicate_event_times(self):
        rows = self.read()
        self.assertEqual([r[:2] for r in rows], [(1., "STEP_END"), (1., "EVENT_POST")])
        self.assertEqual(struct.pack(">d", rows[0][2][0]), struct.pack(">d", -0.))
        self.assertEqual(rows[1][2][0], math.nextafter(1., 2.))

    def test_truncation_and_extra_rows_rejected(self):
        for data in [self.data[:4], self.data[:-1], self.data + b"x"]:
            with self.subTest(size=len(data)), self.assertRaises(ValueError):
                self.read(data)

    def test_wrong_channel_count_and_nonfinite_rejected(self):
        for data in [self.data[:8] + struct.pack(">I", 2) + self.data[12:],
                     self.data[:-8] + struct.pack(">d", math.nan)]:
            with self.assertRaises(ValueError):
                self.read(data)

    def test_metadata_mismatch_rejected(self):
        for field, value in [("samples", 1), ("first_time_s", 0.), ("trace_file", "../other.bin")]:
            original = self.metadata[field]
            self.metadata[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.read()
            self.metadata[field] = original


if __name__ == "__main__":
    unittest.main()
