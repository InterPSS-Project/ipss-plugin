"""Exact input-schema and observation-only reference-producer checks."""
import unittest
from pathlib import Path
from basoel2_native_probe import PROFILES,configuration
from basoel2_terminal_probe import configuration as observed_configuration


class Basoel2ConfigurationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw=(Path(__file__).resolve().parents[1]/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()

    def test_all_profiles_have_three_entered_selectors_and_twelve_constants(self):
        for profile in PROFILES:
            with self.subTest(profile=profile):
                cfg=configuration(profile,self.raw);exc,limiter=cfg['components']
                self.assertEqual('AC1C',exc['model']);self.assertEqual((1,0),exc['icons'])
                self.assertEqual('BASOEL2',limiter['model'])
                self.assertEqual(3,len(limiter['icons']));self.assertEqual(12,len(limiter['constants']))
                self.assertEqual(12,limiter['reserved_icon_count'])
                self.assertEqual(3,len(limiter['states']));self.assertEqual(9,len(limiter['variables']))
                record=next(line for line in cfg['dynamic'].splitlines() if "'BASOEL2'" in line)
                self.assertEqual(19,len(record.split())) # bus, model, id, 3 selectors, 12 constants, slash

    def test_selectors_are_not_translated_to_another_tools_numbering(self):
        for profile,selected in [('efd-fault',0),('ifd-fault',1),('vfe-fault',2)]:
            self.assertEqual(selected,configuration(profile,self.raw)['components'][1]['icons'][2])

    def test_frequency_observer_changes_no_physical_configuration(self):
        for profile in PROFILES:
            observed=observed_configuration(profile,self.raw)
            self.assertEqual((1,),observed.pop('frequency_buses'))
            self.assertEqual(configuration(profile,self.raw),observed)

    def test_zero_filter_profile_changes_only_declared_filter(self):
        normal=configuration('ifd-fault',self.raw)
        bypass=configuration('ifd-zero-filter',self.raw)
        a,b=normal['components'][1],bypass['components'][1]
        self.assertEqual(a['icons'],b['icons']);self.assertEqual(a['constants'][1:],b['constants'][1:])
        self.assertEqual(0,b['constants'][0]);self.assertEqual(.02,a['constants'][0])

    def test_unknown_profile_is_rejected(self):
        with self.assertRaises(ValueError):configuration('not-a-profile',self.raw)


if __name__=='__main__':unittest.main()
