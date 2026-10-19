"""Guard the native two-selector/seventeen-constant input discriminators."""
from pathlib import Path
import unittest
from ieeest_input_probe import configuration,MODES,PSS_CON,EXC_CON
from genrou_native_probe import configuration as machine_configuration

class IeeestInputProbeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw=(Path(__file__).resolve().parents[1]/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
    def test_only_declared_selector_and_event_change(self):
        for mode,selector in MODES.items():
            cfg=configuration(mode,self.raw);base=machine_configuration('flat' if mode.endswith('flat') else 'fault',self.raw)
            self.assertEqual(base['raw'],cfg['raw']);self.assertEqual(base['constants'],cfg['constants'])
            self.assertEqual(base['apply_fault'],cfg['apply_fault'])
            self.assertEqual([selector,0],cfg['components'][1]['icons'])
            self.assertEqual(PSS_CON,tuple(cfg['components'][1]['constants']))
            self.assertEqual(EXC_CON,tuple(cfg['components'][0]['constants']))
    def test_native_layout_and_typed_dialect_are_not_aliased(self):
        for mode in MODES:
            cfg=configuration(mode,self.raw);record=cfg['dynamic'].splitlines()[-1].split()
            self.assertEqual("'IEEEST'",record[1]);self.assertEqual(23,len(record))
            self.assertEqual(17,len(PSS_CON));self.assertEqual(7,len(cfg['components'][1]['states']))
            self.assertEqual(2,cfg['components'][1]['variable_count'])
            self.assertNotIn('variables',cfg['components'][1],'Unused voltage-derivative memory is not a physical power-input channel')
            self.assertNotIn("'PSS1A'",cfg['dynamic']);self.assertTrue(cfg['record_live_states'])
    def test_unknown_profile_is_rejected(self):
        with self.assertRaises(ValueError):configuration('fitted',self.raw)

if __name__=='__main__':unittest.main()
