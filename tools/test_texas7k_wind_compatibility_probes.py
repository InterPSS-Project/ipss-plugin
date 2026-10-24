import unittest
from pathlib import Path
from wttqa_compatibility_probe import configuration,RECORDS
from reaxb_host_probe import configuration as auxiliary,inputs


class WindCompatibilityProbeContracts(unittest.TestCase):
    def test_curve_discriminators_keep_the_source_order_and_constants(self):
        for mode in ('equal','decreasing-first','decreasing-last'):
            c=configuration(mode,'coarse','a',.24)
            self.assertEqual(RECORDS[mode][1],c['constants']);self.assertEqual(15,len(c['constants']))
        self.assertEqual(.4,configuration('equal','fine','b',.4)['constants'][8])
        self.assertGreater(configuration('decreasing-first','coarse','a',.6)['constants'][6],.4)
        self.assertGreater(configuration('decreasing-last','coarse','a',.9)['constants'][10],.86)

    def test_selector_discriminator_changes_only_the_selector(self):
        a=configuration('selector1','coarse','a',.24);b=configuration('selector2','coarse','a',.24)
        self.assertEqual(a['constants'],b['constants']);self.assertEqual((1,2),(a['tflag'],b['tflag']))
        self.assertEqual(a['step_s'],b['step_s']);self.assertEqual(a['initial_power_pu'],b['initial_power_pu'])
        self.assertEqual('speed',a['mode']);self.assertEqual('speed',b['mode'])

    def test_settings_are_copied_and_operating_points_have_distinct_names(self):
        a=configuration('equal','coarse','a',.4);a['constants'][6]=99
        self.assertEqual(.4,configuration('equal','coarse','a',.4)['constants'][6])
        self.assertNotEqual(configuration('equal','coarse','a',.4)['name'],configuration('equal','coarse','a',.4001)['name'])

    def test_exact_legacy_allocation_and_standard_reference_are_separate(self):
        root=Path(__file__).resolve().parents[1]
        for mode,name in [('legacy3','REAX3BU1'),('legacy4','REAX4BU1')]:
            raw,dyr=inputs(root,auxiliary(mode,'coarse','a'))
            self.assertIn("'PLNTBU1' 504 0 7 28 7 15",dyr)
            self.assertIn("'"+name+"' 107 0 1 7 2 4",dyr);self.assertTrue(raw)
        self.assertIn("'REAXB1'",inputs(root,auxiliary('standard','coarse','a'))[1])

    def test_machine_bases_and_iteration_budget_are_explicit(self):
        root=Path(__file__).resolve().parents[1]
        for base in (50,100,200):
            settings=auxiliary('legacy3','coarse','a',base)
            raw,_=inputs(root,settings);line=next(line for line in raw.splitlines() if line.startswith("1,'1',"))
            self.assertEqual(base,float(line.split(',')[8]));self.assertEqual(24,float(line.split(',')[2]))
        original=auxiliary('legacy3','coarse','a',200)
        larger=auxiliary('legacy3','coarse','a',200,200)
        self.assertNotEqual(original['name'],larger['name'])
        self.assertEqual(original['plant_constants'],larger['plant_constants'])
        self.assertEqual(original['auxiliary_constants'],larger['auxiliary_constants'])

    def test_unreviewed_profiles_cannot_silently_fall_back(self):
        for args in [('equal','other','a',.4),('selector2','coarse','c',.24),('equal','coarse','a',0)]:
            with self.assertRaises(ValueError):configuration(*args)
        for args in [('legacy2','coarse','a'),('legacy3','coarse','a',25),('legacy3','coarse','a',200,100)]:
            with self.assertRaises(ValueError):auxiliary(*args)


if __name__=='__main__':unittest.main()
