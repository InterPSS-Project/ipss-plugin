"""Analytic transition tests and negative guards for the native pole diagnostic."""
import hashlib
import math
from pathlib import Path
import tempfile
import unittest
from check_pss5c_pole_artifacts import band_response, checked_files


def responses(mixed=False,scales=(1.,1.)):
    result=[];times=(0.,0.,.001,.002,.003,.004);amplitude=1e-6
    for column in (0,1):
        for sign in (1,-1):
            observations=[]
            for index,time in enumerate(times):
                x,y=math.exp(-3*time),math.exp(-5*time)
                matrix=([[(4*x-y)/3,2*(y-x)/3],[2*(x-y)/3,(4*y-x)/3]]
                        if mixed else [[x,0.],[0.,y]])
                values=[0.]*13
                if index>0:
                    for row in (0,1):values[5+row]=sign*amplitude*scales[row]*matrix[row][column]
                observations.append(dict(time_s=time,states=values))
            result.append(dict(state_offset=5+column,sign=sign,observations=observations))
    return result


class PoleArtifactTest(unittest.TestCase):
    def test_recovers_two_known_decay_rates_without_fitting(self):
        measured=band_response(responses(),0)
        self.assertAlmostEqual(-5,measured['decay_rates'][0]['real'],places=8)
        self.assertAlmostEqual(-3,measured['decay_rates'][1]['real'],places=8)
        self.assertFalse(measured['trajectory_acceptance'])

    def test_eigenvalues_are_invariant_to_mixed_and_rescaled_state_coordinates(self):
        measured=band_response(responses(mixed=True,scales=(7.,.2)),0)
        self.assertAlmostEqual(-5,measured['decay_rates'][0]['real'],places=8)
        self.assertAlmostEqual(-3,measured['decay_rates'][1]['real'],places=8)

    def test_missing_signed_pair_is_rejected(self):
        with self.assertRaises(AssertionError):band_response(responses()[:-1],0)

    def test_clock_mismatch_is_not_fitted_away(self):
        data=responses();data[0]['observations'][-1]['time_s']+=.00001
        with self.assertRaises(AssertionError):band_response(data,0)

    def test_zero_perturbation_is_rejected(self):
        data=responses();data[0]['observations'][1]['states'][5]=data[1]['observations'][1]['states'][5]
        with self.assertRaises(AssertionError):band_response(data,0)

    def test_singular_transition_is_not_regularized(self):
        data=responses()
        for case in data:case['observations'][-2]['states']=[0.]*13
        with self.assertRaises(AssertionError):band_response(data,0)

    def test_unrecorded_cross_state_injection_is_rejected(self):
        data=responses();data[0]['observations'][1]['states'][6]=1e-6
        with self.assertRaises(AssertionError):band_response(data,0)

    def test_nonfinite_observations_are_rejected(self):
        data=responses();data[0]['observations'][-1]['states'][5]=float('nan')
        with self.assertRaises(AssertionError):band_response(data,0)

    def test_raw_file_mutation_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            folder=Path(name);file=folder/'responses.json';file.write_bytes(b'original')
            digest=hashlib.sha256(file.read_bytes()).hexdigest();file.write_bytes(b'changed')
            with self.assertRaises(AssertionError):checked_files(folder,{'responses.json':digest})

    def test_manifest_path_escape_is_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            with self.assertRaises(AssertionError):checked_files(Path(name),{'../outside':'unused'})


if __name__=='__main__':unittest.main()
