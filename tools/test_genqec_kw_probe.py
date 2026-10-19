"""Offline exact Kw profile and dq-product derivation guards."""
import math
import shlex
import unittest
from genqec_kw_probe import VALUES,configuration
from genqec_schema_probe import configuration as original_configuration
from audit_genqec_kw import product


class FieldCompensationProbeTest(unittest.TestCase):
    def test_only_kw_changes_from_the_original_exact_record(self):
        _,_,original=original_configuration('standard-1')
        for profile,kw in VALUES.items():
            _,selector,values=configuration(profile);self.assertEqual(1,selector);self.assertEqual(kw,values[15]);self.assertEqual(original[:15],values[:15])

    def test_native_standard_cardinality_is_unchanged(self):
        for profile in VALUES:
            text,selector,values=configuration(profile);tokens=shlex.split(text.splitlines()[0]);self.assertEqual(['1','GENQEC','1','1'],tokens[:4]);self.assertEqual(values,list(map(float,tokens[4:-1])));self.assertEqual(16,len(values));self.assertEqual('/',tokens[-1])

    def test_factory_does_not_modify_the_original_profile(self):
        before=original_configuration('standard-1');configuration('kw-high');self.assertEqual(before,original_configuration('standard-1'))

    def test_raw_dq_product_preserves_both_signs_before_clipping(self):
        row=dict(ANGLE=math.pi/2,A_BUS1=0,P_PU=1,Q_PU=0,V_BUS1=1);self.assertEqual(1.5,product(row,1.5));row['ANGLE']=-math.pi/2;self.assertEqual(-1.5,product(row,1.5));self.assertEqual(0,product(row,0))

    def test_machine_angle_radians_and_bus_angle_degrees_are_distinct(self):
        row=dict(ANGLE=math.pi/2,A_BUS1=90,P_PU=1,Q_PU=0,V_BUS1=1);self.assertAlmostEqual(0,product(row,1.5),places=14)

    def test_unknown_profile_is_rejected(self):
        with self.assertRaises(ValueError):configuration('unknown')


if __name__=='__main__':unittest.main()
