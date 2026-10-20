"""Fixed observation hypotheses and completeness guards, not trajectory tolerances."""
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import patch
import genrou_native_probe as machine_probe
from pss5c_native_probe import configuration,PSS_CON
from check_pss5c_observation_artifacts import aggregation,canonical,residuals,validate_clock,f32,STATE_NAMES


def sample():
    reference=[];replay=[]
    for time in (-.001,0.,.05,.05,.1):
        raw={'time_s':str(time),'PSS_LOW_SIGNAL':'0','PSS_HIGH_SIGNAL':'0','PSS_VVL':'0','PSS_VL':'0','PSS_VI':'0','PSS_VH':'0','VOTHSG':'0'}
        raw.update({'PSS_STATE_'+str(i):'0' for i in range(1,14)})
        expected={'time_s':str(time),**{name:'0' for name in STATE_NAMES},
                  **{name:'0' for name in ('low_signal','high_signal','VVL','VL','VI','VH','Vstab')}}
        reference.append(raw);replay.append(expected)
    return reference,replay


class ObservationArtifactTest(unittest.TestCase):
    def test_fixed_three_band_identity_does_not_certify_the_four_band_equation(self):
        row={'PSS_VVL':.02,'PSS_VL':.03,'PSS_VI':-.01,'PSS_VH':.04}
        row['VOTHSG']=f32(f32(f32(.03)+f32(-.01))+f32(.04));result=aggregation(row,list(PSS_CON))
        self.assertTrue(result['three_band_equal']);self.assertFalse(result['four_band_equal'])

    def test_zero_gain_counterexample_may_disprove_both_aggregation_hypotheses(self):
        row={'PSS_VVL':.0001,'PSS_VL':0,'PSS_VI':0,'PSS_VH':0,'VOTHSG':.03}
        result=aggregation(row,list(PSS_CON));self.assertFalse(result['three_band_equal']);self.assertFalse(result['four_band_equal'])

    def test_float32_aggregation_preserves_both_declared_output_limits(self):
        for sign in (-1,1):
            row={key:sign for key in ('PSS_VVL','PSS_VL','PSS_VI','PSS_VH')};row['VOTHSG']=f32(sign*.15)
            result=aggregation(row,list(PSS_CON));self.assertTrue(result['three_band_equal']);self.assertTrue(result['four_band_equal'])

    def test_source_coordinate_map_uses_only_declared_coefficients(self):
        raw={'PSS_STATE_'+str(i):i*.001 for i in range(1,14)};p=list(PSS_CON);x=canonical(raw,p)
        self.assertAlmostEqual(.003-p[5]*.005-p[6]*.004,x['highPosition'])
        self.assertAlmostEqual(p[7]*.004,x['highRate']);self.assertAlmostEqual(p[7]*.005,x['highAcceleration'])
        self.assertEqual(raw['PSS_STATE_6'],x['veryLowUpperLag']);self.assertEqual(raw['PSS_STATE_13'],x['highLowerLag'])

    def test_residuals_retain_negative_history_and_duplicate_boundaries(self):
        raw,replay=sample();result=residuals(raw,list(PSS_CON),replay)
        self.assertEqual(5,result['rows']);self.assertEqual(1,result['negative_history_rows']);self.assertEqual(1,result['duplicate_boundary_rows'])
        self.assertFalse(result['trajectory_acceptance']);self.assertFalse(result['raw_state_coordinates_certified'])

    def test_missing_replay_rows_cannot_pass(self):
        raw,replay=sample()
        with self.assertRaises(AssertionError):residuals(raw,list(PSS_CON),replay[:-1])

    def test_replay_clock_shift_cannot_be_fitted_away(self):
        raw,replay=sample();replay[3]['time_s']='.0500001'
        with self.assertRaises(AssertionError):residuals(raw,list(PSS_CON),replay)

    def test_nonfinite_replay_is_rejected(self):
        raw,replay=sample();replay[3]['highRate']='NaN'
        with self.assertRaises(AssertionError):residuals(raw,list(PSS_CON),replay)

    def test_fine_profiles_change_only_declared_discriminator_fields(self):
        raw=Path('ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
        for name in ('fine-observation-flat','fine-observation-fault','fine-observation-transducer-fault'):
            cfg=configuration(name,raw);values=cfg['components'][1]['constants']
            expected={10,14,18,22}|({0,1,2,4,5,6,7} if 'transducer' in name else set())
            self.assertEqual(expected,{i for i,(a,b) in enumerate(zip(PSS_CON,values)) if a!=b})
            self.assertTrue(cfg['record_live_variables']);self.assertTrue(cfg['record_live_fault_states'])
            self.assertTrue(cfg['pss5c_observation_diagnostic']);self.assertEqual(not name.endswith('-flat'),cfg['apply_fault'])
        self.assertNotIn('pss5c_observation_diagnostic',configuration('fault',raw))

    def test_fine_step_permission_cannot_leak_into_other_machine_probes(self):
        modules={name:types.ModuleType(name) for name in ('psse3607','psspy','dyntools')}
        nonexistent=Path('target/diagnostic-guard-must-not-create');self.assertFalse(nonexistent.exists())
        for prefix,cfg in (('genrou-native-',{'pss5c_observation_diagnostic':True}),('pss5c-native-',{})):
            with patch.dict(sys.modules,modules),patch.object(machine_probe.Path,'cwd',return_value=nonexistent):
                with self.assertRaises(AssertionError):machine_probe.run_case('guard','.00005','fault',cfg,directory_prefix=prefix)

    def test_exact_float32_clock_recurrence_keeps_duplicate_boundaries(self):
        h=.00005;times=[f32(-2*f32(h))]
        for i in range(5):times.append(f32(times[-1]+f32(h)))
        times.insert(4,times[3]);validate_clock([{'time_s':str(t)} for t in times],h,times[-1])

    def test_native_clock_anchor_rejects_a_global_shift(self):
        h=.00005;times=[f32(-2*f32(h))]
        for i in range(5):times.append(f32(times[-1]+f32(h)))
        shifted=[{'time_s':str(f32(t+.001))} for t in times]
        with self.assertRaises(AssertionError):validate_clock(shifted,h,float(shifted[-1]['time_s']))


if __name__=='__main__':unittest.main()
