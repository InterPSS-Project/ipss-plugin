import unittest
from tgov1_limit_probe import COMMANDS, PARAMETERS, STEPS, checked, profile


class Tgov1LimitProbeTest(unittest.TestCase):
    def test_exact_native_constants(self):
        self.assertEqual((.05,.04,1.2,.2,.1,.3,.5),PARAMETERS)
        self.assertEqual(7,len(PARAMETERS))
        self.assertGreater(PARAMETERS[1],0)
        self.assertGreater(PARAMETERS[5],PARAMETERS[4])

    def test_four_profiles_keep_all_but_direction_and_step_fixed(self):
        self.assertEqual(STEPS['coarse']/2,STEPS['fine'])
        self.assertEqual(-COMMANDS['lower'],COMMANDS['upper'])
        for direction in COMMANDS:
            for resolution in STEPS:
                for repeat in ('a','b'):
                    p=profile(direction,resolution,repeat)
                    self.assertEqual(list(PARAMETERS),p['constants'])
                    self.assertEqual((.05,.20,.5),(p['apply_s'],p['release_s'],p['end_s']))

    def test_unknown_profile_is_not_substituted(self):
        for args in [('other','coarse','a'),('upper','other','a'),('upper','fine','c')]:
            with self.assertRaises(ValueError):profile(*args)

    def test_native_error_codes_cannot_be_silently_accepted(self):
        self.assertEqual(7,checked('ok',(0,7)))
        self.assertIsNone(checked('ok',0))
        for result in (1,-1,(2,0)):
            with self.assertRaises(RuntimeError):checked('fail',result)


if __name__=='__main__':unittest.main()
