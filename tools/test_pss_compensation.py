"""Independent base/sign checks for the native initialization audit."""
import math
import unittest
from audit_pss_compensation import initial_compensation


class CompensationAuditTest(unittest.TestCase):
    def test_nonunit_machine_mva_is_not_system_mva(self):
        self.assertAlmostEqual(math.atan(.2)/.02,initial_compensation(.5,0,1,50,.2,.02))
        self.assertAlmostEqual(math.atan(.05)/.02,initial_compensation(.5,0,1,200,.2,.02))

    def test_negative_compensation_preserves_sign(self):
        self.assertAlmostEqual(-math.atan(.05)/.1,initial_compensation(.5,0,1,200,-.2,.1))

    def test_reactive_power_changes_the_real_voltage_component(self):
        self.assertAlmostEqual(math.atan2(.2,1.04)/.02,initial_compensation(.5,.1,1,50,.2,.02))

    def test_nonfinite_inputs_are_not_reference_evidence(self):
        for value in [math.nan,math.inf,-math.inf]:
            with self.assertRaises(AssertionError): initial_compensation(value,0,1,100,.2,.02)

    def test_zero_time_or_base_is_rejected_by_this_profile_contract(self):
        with self.assertRaises(AssertionError): initial_compensation(.5,0,1,0,.2,.02)
        with self.assertRaises(AssertionError): initial_compensation(.5,0,1,100,.2,0)

    def test_zero_reactance_has_zero_angle(self):
        self.assertEqual(0,initial_compensation(.5,.1,1,50,0,.02))


if __name__=='__main__':unittest.main()
