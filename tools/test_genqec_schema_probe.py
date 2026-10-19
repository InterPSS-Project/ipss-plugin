"""Offline exact-record and semantic raw-history reader tests."""
import tempfile
from pathlib import Path
import shlex
import unittest
from genqec_schema_probe import PROFILES,configuration
from audit_genqec_schema import rows
from audit_genqec_powerworld import table,canonical


class GeneratorSchemaProbeTest(unittest.TestCase):
    def test_standard_has_one_integer_selector_and_sixteen_constants(self):
        for profile in PROFILES:
            text,selector,constants=configuration(profile)
            tokens=shlex.split(text.splitlines()[0]);offset=10 if profile.startswith('wrapper') else 3
            self.assertEqual(selector,int(tokens[offset]));self.assertEqual(16,len(constants));self.assertEqual(constants,list(map(float,tokens[offset+1:-1])));self.assertEqual('/',tokens[-1])

    def test_wrapper_allocation_is_exact(self):
        tokens=shlex.split(configuration('wrapper-1')[0].splitlines()[0])
        self.assertEqual(['1','USRMDL','1','GENQECU','1','1','1','16','6','1'],tokens[:10])

    def test_selector_discriminators_do_not_change_constants(self):
        vectors=[configuration(profile)[2] for profile in PROFILES]
        self.assertTrue(all(vector==vectors[0] for vector in vectors))
        with self.assertRaises(ValueError):configuration('unknown')

    def test_raw_reader_preserves_event_duplicates(self):
        with tempfile.TemporaryDirectory(dir=Path.cwd()/'target',prefix='genqec-test-') as folder:
            path=Path(folder)/'raw.csv';header=','.join(['time_s']+['field'+str(i) for i in range(16)])
            path.write_text(header+'\n0,'+','.join(['1']*16)+'\n0,'+','.join(['2']*16)+'\n');result=rows(path)
            self.assertEqual(2,len(result));self.assertEqual(result[0]['time_s'],result[1]['time_s']);self.assertNotEqual(result[0]['field0'],result[1]['field0'])

    def test_raw_reader_rejects_missing_or_nonfinite_values(self):
        with tempfile.TemporaryDirectory(dir=Path.cwd()/'target',prefix='genqec-test-') as folder:
            path=Path(folder)/'raw.csv'
            for text in ['time_s,x\n0,1\n',','.join(['time_s']+['field'+str(i) for i in range(16)])+'\n0,'+','.join(['1']*15+['NaN'])+'\n']:
                path.write_text(text)
                with self.assertRaises(ValueError):rows(path)

    def test_commercial_reader_preserves_duplicate_event_rows(self):
        with tempfile.TemporaryDirectory(dir=Path.cwd()/'target',prefix='genqec-test-') as folder:
            path=Path(folder)/'raw.csv';path.write_text('Results,"test"\n0,'+','.join(['1']*11)+'\n0,'+','.join(['2']*11)+'\nEND\n')
            result=table(path);self.assertEqual(2,len(result));self.assertEqual(0,result[0][0]);self.assertNotEqual(result[0][1],result[1][1])

    def test_commercial_reader_rejects_nonfinite_or_missing_channels(self):
        with tempfile.TemporaryDirectory(dir=Path.cwd()/'target',prefix='genqec-test-') as folder:
            path=Path(folder)/'raw.csv'
            for row in ['0,1','0,'+','.join(['1']*10+['NaN'])]:
                path.write_text('Results,"test"\n'+row+'\nEND\n')
                with self.assertRaises(ValueError):table(path)

    def test_canonical_hash_normalization_only_changes_line_endings(self):
        with tempfile.TemporaryDirectory(dir=Path.cwd()/'target',prefix='genqec-test-') as folder:
            path=Path(folder)/'raw.csv';path.write_bytes(b'a, b\r\nc,d\r');self.assertEqual(b'a, b\nc,d\n',canonical(path))


if __name__=='__main__':unittest.main()
