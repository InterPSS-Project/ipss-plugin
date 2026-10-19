"""Offline switching-schema and raw-reference audit guards."""
import base64
import hashlib
from pathlib import Path
import shlex
import tempfile
import unittest
from audit_hplntd_switching import table
from hplntd_switching_probe import MODES, configuration

ROOT = Path(__file__).resolve().parents[1]
RESOURCE = ROOT / 'ipss.test.plugin.core/src/test/resources/hplntd-foundation-native'


class SwitchingProbeTest(unittest.TestCase):
    def setUp(self):
        self.original = base64.b64decode((RESOURCE / 'plant-low-window-coarse.dyr.b64').read_bytes())

    def fields(self, body):
        line, = [v for v in body.decode().splitlines() if "'USRBUS'" in v]
        return shlex.split(line.rstrip().removesuffix('/'))

    def test_exact_wrapper_and_unmodified_other_records(self):
        for mode in MODES:
            body, _ = configuration(self.original, mode)
            tokens = self.fields(body)
            self.assertEqual(105, len(tokens))
            self.assertEqual(['504', '0', '35', '61', '13', '45'], tokens[3:9])
            self.assertEqual([v for v in self.original.splitlines() if b"'USRBUS'" not in v],
                             [v for v in body.splitlines() if b"'USRBUS'" not in v])
            before = self.fields(self.original)
            allowed = set(range(18, 42)) | set(range(88, 96))
            for i, (a, b) in enumerate(zip(before, tokens)):
                if i not in allowed:
                    self.assertEqual(a, b)

    def test_physical_mvar_fast_and_slow_thresholds(self):
        for mode in ('positive-fast', 'positive-slow'):
            body, _ = configuration(self.original, mode)
            values = list(map(float, self.fields(body)[88:96]))
            outer = 20 if mode.endswith('slow') else 10
            self.assertEqual([-5, -outer, 5, outer, .01, .003, .004, .03], values)

    def test_signed_branch_and_ten_input_order_devices(self):
        for mode in ('positive-fast', 'negative-fast'):
            body, shunts = configuration(self.original, mode)
            tokens = self.fields(body)
            self.assertEqual('-2' if mode.startswith('negative') else '2', tokens[19])
            self.assertEqual(['C' + str(i) for i in range(1, 6)] + ['R' + str(i) for i in range(1, 6)], [s[0] for s in shunts])
            for i, (name, status, b) in enumerate(shunts):
                self.assertEqual(['2', name], tokens[22 + 2*i:24 + 2*i])
                self.assertEqual(.01 if i < 5 else -.01, b)
                self.assertEqual(int((i < 5) == mode.startswith('negative')), status)

    def test_disabled_invalid_missing_and_zero_parameters_remain_explicit(self):
        fields = {m: self.fields(configuration(self.original, m)[0]) for m in MODES}
        self.assertEqual('0', fields['disabled'][18])
        self.assertEqual('-1', fields['invalid-thresholds'][89])
        self.assertEqual(['1', '3', '1'], fields['missing-branch'][19:22])
        self.assertEqual(['0', '0', '0'], fields['zero-delay'][92:95])
        self.assertEqual('.03', fields['zero-delay'][95])

    def test_changed_source_or_unknown_mode_is_rejected(self):
        with self.assertRaises(ValueError):
            configuration(self.original + b'\n', 'positive-fast')
        with self.assertRaises(ValueError):
            configuration(self.original, 'invented')

    def test_frozen_networks_keep_exact_ten_individual_device_inputs(self):
        expected = {'positive': 'dc251adc48a2942de91ee74a118ac296afa7858f3227b431e1d9b8726948b0c1',
                    'negative': '556b4b9c6e0ebe979729e8d8029df5db8e6b20f496b2b1ddfdacfff5787dbc24'}
        for direction, sha in expected.items():
            body = base64.b64decode((RESOURCE / ('switching-' + direction + '.raw.b64')).read_bytes())
            self.assertEqual(sha, hashlib.sha256(body).hexdigest())
            section = body.decode().split('0 / END OF LOAD DATA')[1].split('0 / END OF FIXED SHUNT DATA')[0]
            records = [line.split(',') for line in section.splitlines()[1:] if line.strip()]
            self.assertEqual(10, len(records))
            _, shunts = configuration(self.original, direction + '-fast')
            for fields, (name, status, b) in zip(records, shunts):
                self.assertEqual(['2', "'" + name + "'", str(status), '0', str(b)], fields)

    def test_raw_table_keeps_duplicate_and_history_rows(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'target') as directory:
            path = Path(directory) / 'trace.csv'
            path.write_text('time_s,x\n-.001,1\n0,2\n0,3\n')
            rows = table(path, 2, 3)
            self.assertEqual([-.001, 0, 0], [r['time_s'] for r in rows])
            self.assertEqual([1, 2, 3], [r['x'] for r in rows])

    def test_raw_table_rejects_ragged_nonfinite_and_wrong_counts(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'target') as directory:
            path = Path(directory) / 'trace.csv'
            for text, width, count in [('t,x\n0,nan\n', 2, 1), ('t,x\n0,inf\n', 2, 1),
                                       ('t,x\n0\n', 2, 1), ('t,x\n0,1,2\n', 2, 1),
                                       ('t,t\n0,1\n', 2, 1), ('t,x\n0,1\n', 3, 1),
                                       ('t,x\n0,1\n', 2, 2)]:
                with self.subTest(text=text, width=width, count=count):
                    path.write_text(text)
                    with self.assertRaises(ValueError):
                        table(path, width, count)


if __name__ == '__main__':
    unittest.main()
