"""Offline schema and raw-output reader guards; no simulator required."""
import tempfile
from pathlib import Path
import shlex
import unittest
from wt12a1b_native_probe import PROFILES, configuration
from audit_wt12a1b_powerworld import table, canonical


class ActiveStallProbeTest(unittest.TestCase):
    def test_exact_standard_fourteen_constant_contract(self):
        for profile in PROFILES:
            text, constants, _ = configuration(profile)
            tokens = shlex.split(next(line for line in text.splitlines() if "'WT12A1B'" in line))
            self.assertEqual(['1','WT12A1B','1'],tokens[:3])
            self.assertEqual(14,len(constants))
            self.assertEqual(constants,list(map(float,tokens[3:-1])))
            self.assertEqual('/',tokens[-1])

    def test_discriminators_change_only_the_declared_parameters(self):
        _, baseline, _ = configuration('fault')
        for profile, positions in [('direct',{0}),('below',{5}),('rate',{1,2})]:
            _, values, _ = configuration(profile)
            self.assertEqual(positions,{i for i,(a,b) in enumerate(zip(baseline,values)) if a!=b})

    def test_event_scope_is_small_and_repeatable(self):
        self.assertEqual([],configuration('flat')[2])
        self.assertEqual([(.05,'fault'),(.1,'clear')],configuration('fault')[2])
        self.assertEqual([(.05,'fault'),(.1,'clear'),(.35,'fault'),(.4,'clear')],configuration('repeat')[2])
        with self.assertRaises(ValueError): configuration('unknown')

    def test_reader_preserves_event_side_duplicates(self):
        with tempfile.TemporaryDirectory(dir=Path.cwd()/'target',prefix='wt12a1b-test-') as folder:
            path=Path(folder)/'sample.csv'
            path.write_text('ObjectFields\nEND\nResults,"test"\n0,'+','.join(['1']*8)+'\n0,'+','.join(['2']*8)+'\n')
            rows=table(path)
            self.assertEqual(2,len(rows));self.assertEqual(rows[0][0],rows[1][0]);self.assertNotEqual(rows[0][1],rows[1][1])

    def test_reader_rejects_nonfinite_or_incomplete_channels(self):
        with tempfile.TemporaryDirectory(dir=Path.cwd()/'target',prefix='wt12a1b-test-') as folder:
            path=Path(folder)/'sample.csv'
            for row in ('0,1,2','0,'+','.join(['1']*7+['NaN'])):
                path.write_text('Results,"test"\n'+row+'\n')
                with self.assertRaises(ValueError): table(path)

    def test_canonicalization_changes_line_endings_only(self):
        with tempfile.TemporaryDirectory(dir=Path.cwd()/'target',prefix='wt12a1b-test-') as folder:
            path=Path(folder)/'sample.csv';path.write_bytes(b'a, b\r\nc,d\r')
            self.assertEqual(b'a, b\nc,d\n',canonical(path))


if __name__=='__main__': unittest.main()
