"""Frozen, erased and active high-coordinate classifier counterexamples."""
import math
import unittest
from check_pss5c_high_artifacts import high_response


def sample(kinds=('frozen','active','active')):
    result=[];amplitude=1e-6
    for column,kind in enumerate(kinds,2):
        for sign in (1,-1):
            observations=[]
            for index,time in enumerate((0.,0.,.0001,.0002,.0003,.0004)):
                state=[0.]*13
                if index>0:
                    state[column]=sign*amplitude*(1 if kind=='frozen' or index==1 else
                       (0 if kind=='erased' else math.exp(-(1 if column==3 else 80)*time)))
                observations.append(dict(time_s=time,states=state,variables=[0.]*6))
            result.append(dict(state_offset=column,sign=sign,observations=observations))
    return result


PARAMETERS=[0.]*4+[0.,1.,81.,80.]+[0.]*22


class HighArtifactTest(unittest.TestCase):
    def test_frozen_unobserved_coordinate_is_not_a_zero_physical_pole(self):
        result=high_response(sample(),PARAMETERS)
        self.assertEqual('frozen-unobserved',result['coordinate_responses'][2])
        self.assertEqual(2,result['active_coordinate_count'])
        self.assertAlmostEqual(-80,result['observed_poles'][0]['real'],places=7)
        self.assertAlmostEqual(-1,result['observed_poles'][1]['real'],places=7)

    def test_erased_coordinates_retain_the_declared_order_counterexample(self):
        result=high_response(sample(('active','erased','erased')),PARAMETERS)
        self.assertEqual(1,result['active_coordinate_count']);self.assertEqual(2,result['published_order'])
        self.assertEqual('erased',result['coordinate_responses'][3]);self.assertFalse(result['trajectory_acceptance'])

    def test_three_active_coordinates_cannot_select_a_convenient_two_state_pair(self):
        result=high_response(sample(('active','active','active')),PARAMETERS)
        self.assertEqual(3,result['active_coordinate_count']);self.assertIsNone(result['observed_poles'])

    def test_zero_active_coordinates_are_not_promoted_to_parity(self):
        result=high_response(sample(('erased','erased','erased')),PARAMETERS)
        self.assertEqual(0,result['active_coordinate_count']);self.assertIsNone(result['observed_poles'])
        self.assertFalse(result['trajectory_acceptance'])

    def test_nonfinite_variables_are_rejected(self):
        data=sample();data[0]['observations'][-1]['variables'][1]=math.nan
        with self.assertRaises(AssertionError):high_response(data,PARAMETERS)

    def test_missing_signed_response_is_rejected(self):
        with self.assertRaises(AssertionError):high_response(sample()[:-1],PARAMETERS)

    def test_clock_mismatch_cannot_be_fitted_away(self):
        data=sample();data[2]['observations'][-1]['time_s']+=1e-5
        with self.assertRaises(AssertionError):high_response(data,PARAMETERS)

    def test_singular_active_response_is_not_regularized(self):
        data=sample()
        for case in data:
            if case['state_offset'] in (3,4):case['observations'][4]['states']=[0.]*13
        with self.assertRaises(AssertionError):high_response(data,PARAMETERS)


if __name__=='__main__':unittest.main()
