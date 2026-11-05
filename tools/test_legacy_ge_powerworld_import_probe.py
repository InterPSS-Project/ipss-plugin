import tempfile
import unittest
from pathlib import Path
from legacy_ge_powerworld_import_probe import NAMES, select_records, stage_records


class LegacyImportProbeTest(unittest.TestCase):
    def source(self):
        records = []
        for name in NAMES[:-1]:
            records.append(f"71 'USRMDL' '1' '{name}' 505 0 1 2 0 4 0 .1 .2 /")
        records.append("8 'USRMSC' 'GEWPLT2' 512 0 2 0 0 17 71 '1 ' /")
        return '\n'.join(records)

    def test_selects_same_owner_with_padded_unit_and_preserves_parameters(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[1] / 'target') as directory:
            source = Path(directory) / 'synthetic.dyr'
            source.write_text(self.source())
            selected = select_records(source)
            dynamic, inventory = stage_records(selected)
        self.assertEqual(list(NAMES), [r['model'] for r in inventory])
        for record in inventory[:-1]:
            self.assertEqual(record['original_tokens'][4:], record['staged_tokens'][4:])
            self.assertEqual('1', record['staged_tokens'][0])
        self.assertEqual(['1', '1'], inventory[-1]['staged_tokens'][9:11])
        self.assertIn("2 'GENCLS' '1' 0.0 0.0 /", dynamic)

    def test_missing_companion_is_rejected(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[1] / 'target') as directory:
            source = Path(directory) / 'synthetic.dyr'
            source.write_text(self.source().replace('GEWPLT2', 'OTHER'))
            with self.assertRaisesRegex(ValueError, 'Missing same-owner companion GEWPLT2'):
                select_records(source)

    def test_wrong_allocation_is_rejected(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[1] / 'target') as directory:
            source = Path(directory) / 'synthetic.dyr'
            source.write_text(self.source().replace('1 2 0 4', '1 3 0 4'))
            selected = select_records(source)
            with self.assertRaisesRegex(ValueError, 'cardinality'):
                stage_records(selected)


if __name__ == '__main__':
    unittest.main()
