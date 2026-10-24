from pathlib import Path
import unittest
from expic1_native_probe import configuration,inputs
from exac2_native_probe import configuration as field_configuration
from texas7k_parameter_probe import configuration as case_configuration


class ExcitationProbeContractTest(unittest.TestCase):
    def test_exact_native_cardinality_and_zero_time_bypasses_are_preserved(self):
        c=configuration('flat','coarse','a')
        self.assertEqual(24,len(c['constants']))
        self.assertEqual([0,3.5,1,1,-.87,0,0,0],c['constants'][:8])
        root=Path(__file__).resolve().parents[1]
        raw,dyr=inputs(root,c)
        self.assertIn("'EXPIC1'",dyr);self.assertNotIn('TGOV1',dyr)
        self.assertEqual(3,dyr.count('/'));self.assertTrue(raw)

    def test_saturation_feedback_limits_and_constant_source_are_distinct(self):
        self.assertGreater(configuration('feedback','coarse','a')['constants'][10],0)
        self.assertEqual(0,configuration('feedback','coarse','a')['constants'][12])
        saturated=configuration('saturation','fine','b')['constants']
        self.assertGreater(saturated[18],0);self.assertGreater(saturated[20],0)
        self.assertEqual(3,configuration('integral-upper','coarse','a')['reference_step'])
        self.assertEqual(-3,configuration('integral-lower','coarse','a')['reference_step'])
        wide=configuration('constant-source-wide','coarse','a')['constants']
        self.assertEqual([0,0],wide[21:23]);self.assertEqual(2,wide[3]);self.assertEqual(2,wide[8])
        self.assertGreater(configuration('bridge-current','coarse','a')['constants'][22],0)

    def test_field_base_discriminator_uses_the_exact_other_model(self):
        c=field_configuration('flat','fine','a');self.assertEqual('EXAC2',c['model'])
        self.assertEqual(23,len(c['constants']));self.assertEqual(.4,c['constants'][16])
        self.assertEqual(1,c['constants'][12]);self.assertEqual(5,c['state_count'])
        self.assertIn("'EXAC2'",inputs(Path(__file__).resolve().parents[1],c)[1])

    def test_case_deadband_discriminator_changes_only_one_sign(self):
        positive=case_configuration('plant-positive-deadband-fault','fine','b')
        signed=case_configuration('plant-signed-deadband-fault','fine','b')
        self.assertEqual(27,len(positive['constants']))
        self.assertEqual(.025,positive['constants'][11]);self.assertEqual(-.025,signed['constants'][11])
        self.assertEqual(positive['constants'][:11]+positive['constants'][12:],signed['constants'][:11]+signed['constants'][12:])
        self.assertEqual(positive['icons'],signed['icons']);self.assertTrue(positive['fault'])

    def test_case_torque_discriminators_preserve_unusual_input(self):
        equal=case_configuration('torque-equal','coarse','a');decreasing=case_configuration('torque-decreasing','coarse','a')
        self.assertEqual(equal['constants'][6],equal['constants'][8])
        self.assertGreater(decreasing['constants'][6],decreasing['constants'][8])
        self.assertEqual(2,case_configuration('torque-selector2','coarse','a')['tflag'])

    def test_unknown_profiles_cannot_change_the_reference_model(self):
        with self.assertRaises(ValueError):configuration('unknown','coarse','a')
        with self.assertRaises(ValueError):field_configuration('dynamic','coarse','a')
        with self.assertRaises(ValueError):case_configuration('unknown','coarse','a')


if __name__=='__main__':unittest.main()
