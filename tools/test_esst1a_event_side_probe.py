"""No input or event is modified by post-reference live-state observation."""
import unittest
from pathlib import Path
from esst1a_event_side_probe import configuration,MODES
from esst1a_bypass_probe import configuration as bypass_configuration

class EventSideProbeTest(unittest.TestCase):
    def test_only_live_observation_is_added(self):
        raw=(Path(__file__).resolve().parents[1]/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
        for mode in MODES:
            cfg=configuration(mode,raw)
            self.assertTrue(cfg.pop('record_live_event_states'))
            self.assertEqual(bypass_configuration(mode,raw),cfg)
    def test_unlisted_profile_is_rejected(self):
        with self.assertRaises(ValueError):configuration('fitted','')

if __name__=='__main__':unittest.main()
