"""Guard the single changed native constant and unchanged event/input contract."""
from pathlib import Path
import unittest
from ieeest_zero_numerator_probe import configuration
from ieeest_input_probe import configuration as input_configuration,MODES

class ZeroNumeratorProbeTest(unittest.TestCase):
    def test_only_T5_changes(self):
        raw=(Path(__file__).resolve().parents[1]/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
        for mode in MODES:
            base=input_configuration(mode,raw);cfg=configuration(mode,raw)
            self.assertEqual([10],[i for i,(a,b) in enumerate(zip(base['components'][1]['constants'],cfg['components'][1]['constants'])) if a!=b])
            self.assertEqual(0,cfg['components'][1]['constants'][10]);self.assertEqual(5,cfg['components'][1]['constants'][11])
            self.assertEqual(base['raw'],cfg['raw']);self.assertEqual(base['apply_fault'],cfg['apply_fault'])
            self.assertEqual(base['components'][0],cfg['components'][0]);self.assertEqual(base['components'][1]['icons'],cfg['components'][1]['icons'])
    def test_unknown_profile_is_rejected(self):
        with self.assertRaises(ValueError):configuration('fitted','')

if __name__=='__main__':unittest.main()
