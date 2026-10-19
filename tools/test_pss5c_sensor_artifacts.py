"""Sensor-order classifier counterexamples, independent of installed simulators."""
import math
import unittest
from check_pss5c_sensor_artifacts import low_response
from check_pss5c_pole_artifacts import two_state_response
from test_pss5c_pole_artifacts import responses


def sample(erased=False):
    data=responses()
    for case in data:
        case['state_offset']-=5
        for i,row in enumerate(case['observations']):
            row['states'][0:2]=row['states'][5:7];row['states'][5:7]=[0.,0.]
            row['variables']=[0.]*6
            if erased and case['state_offset']==1 and i>=2:row['states']=[0.]*13
    return data


class SensorArtifactTest(unittest.TestCase):
    def test_two_state_diagnostic_accepts_sensor_offsets(self):
        r=two_state_response(sample(),(0,1))
        self.assertAlmostEqual(-5,r['decay_rates'][0]['real'],places=8)
        self.assertAlmostEqual(-3,r['decay_rates'][1]['real'],places=8)

    def test_full_order_response_is_not_regularized_or_relabelled(self):
        r=low_response(sample(),[.001,1/15,8/15]+[0.]*27)
        self.assertEqual(2,r['observed_order']);self.assertEqual(2,r['published_order'])
        self.assertFalse(r['second_state_response_erased']);self.assertFalse(r['trajectory_acceptance'])

    def test_erased_state_retains_the_declared_second_order_counterexample(self):
        r=low_response(sample(True),[.001,1/15,8/15]+[0.]*27)
        self.assertEqual(1,r['observed_order']);self.assertEqual(2,r['published_order'])
        self.assertTrue(r['second_state_response_erased']);self.assertAlmostEqual(-3,r['decay_rates'][0]['real'],places=8)

    def test_explicit_first_order_contract_is_separate(self):
        r=low_response(sample(True),[.001,0,1/3]+[0.]*27)
        self.assertEqual(1,r['observed_order']);self.assertEqual(1,r['published_order'])
        self.assertAlmostEqual(-3,r['published_poles'][0]['real'],places=12)

    def test_variable_effect_prevents_false_unused_state_classification(self):
        data=sample(True);data[2]['observations'][2]['variables'][0]=1e-12
        with self.assertRaises(AssertionError):low_response(data,[.001,1/15,8/15]+[0.]*27)

    def test_duplicate_offsets_are_not_a_valid_subspace(self):
        with self.assertRaises(AssertionError):two_state_response(sample(),(0,0))

    def test_out_of_range_offsets_are_rejected(self):
        with self.assertRaises(AssertionError):two_state_response(sample(),(0,13))

    def test_nonfinite_rate_observations_are_rejected(self):
        data=sample(True);data[0]['observations'][-1]['states'][0]=math.inf
        with self.assertRaises(AssertionError):low_response(data,[.001,0,1/3]+[0.]*27)


if __name__=='__main__':unittest.main()
