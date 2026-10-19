from pathlib import Path
import unittest
from wttqa_native_probe import configuration,dynamic


class WttqaNativeProbeTest(unittest.TestCase):
    def test_native_selector_and_constants_are_explicit(self):
        for mode in ('flat','speed','power','curve','dip'):
            for grid in ('coarse','fine'):
                settings=configuration(mode,grid,'a')
                self.assertEqual(1 if mode=='power' else 0,settings['tflag'])
                self.assertEqual([2.,1.,.02,.1,2.,0.],settings['constants'][:6])
                self.assertEqual(15,len(settings['constants']));self.assertEqual(100,settings['constants'][-1])
                self.assertEqual((.05,.1,.5),(settings['apply_s'],settings['clear_s'],settings['end_s']))

    def test_curves_and_dips_are_separate_discriminators(self):
        self.assertEqual([.1,.8,.2,.9,.4,1.,.8,1.1],configuration('curve','fine','a')['constants'][6:14])
        self.assertEqual([0.]*8,configuration('speed','fine','a')['constants'][6:14])
        self.assertLess(configuration('dip','fine','a')['fault_ohms'],configuration('speed','fine','a')['fault_ohms'])

    def test_native_input_retains_exact_torque_record_and_no_plant_alias(self):
        root=Path(__file__).resolve().parents[1];text=dynamic(root,configuration('speed','fine','a'))
        self.assertEqual(7,text.count('/'));self.assertNotIn('REPCA',text)
        self.assertIn("1 'WTTQA1' '1' 0 2.0 1.0 0.02 0.1 2.0 0.0",text)
        self.assertIn("1 'WTPTA1' '1' 0 0 0 0 0 .3 27 0 10 -10 /",text)

    def test_unknown_profiles_do_not_fall_back(self):
        for args in [('other','fine','a'),('speed','other','b'),('power','coarse','c')]:
            with self.assertRaises(ValueError):configuration(*args)


if __name__=='__main__':unittest.main()
