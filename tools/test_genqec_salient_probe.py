"""Offline documented salient-profile and source-separation guards."""
import shlex
import unittest
from genqec_salient_probe import PROFILES,configuration
from genqec_schema_probe import configuration as original_configuration


class SalientProbeTest(unittest.TestCase):
    def test_only_documented_q_transient_branch_is_removed(self):
        for profile in PROFILES:
            _,selector,values=configuration(profile);_,_,original=original_configuration('standard-'+str(selector))
            self.assertEqual({2,9},{i for i,(a,b) in enumerate(zip(values,original)) if a!=b})
            self.assertEqual(0,values[2]);self.assertEqual(values[7],values[9]);self.assertGreater(values[1],0);self.assertGreater(values[3],0)

    def test_standard_and_wrapper_preserve_exact_native_cardinality(self):
        for profile in PROFILES:
            text,selector,values=configuration(profile);tokens=shlex.split(text.splitlines()[0]);offset=10 if profile.startswith('genqej') else 3
            self.assertEqual(selector,int(tokens[offset]));self.assertEqual(values,list(map(float,tokens[offset+1:-1])));self.assertEqual(16,len(values))
            if profile.startswith('genqej'):self.assertEqual(['1','USRMDL','1','GENQEJU','1','1','1','16','6','1'],tokens[:10])
            else:self.assertEqual(['1','GENQEC','1'],tokens[:3])

    def test_selector_changes_no_physical_parameter(self):
        for model in ['genqec','genqej']:self.assertEqual(configuration(model+'-0')[2],configuration(model+'-1')[2])

    def test_no_original_profile_or_schema_is_modified(self):
        expected=original_configuration('standard-1');configuration('genqec-1');configuration('genqej-1');self.assertEqual(expected,original_configuration('standard-1'))

    def test_unknown_profile_is_rejected(self):
        with self.assertRaises(ValueError):configuration('unknown')


if __name__=='__main__':unittest.main()
