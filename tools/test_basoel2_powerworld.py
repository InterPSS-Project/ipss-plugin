"""Exact reference schema and all-channel/event-side comparison safeguards."""
import copy
import math
from pathlib import Path
import unittest
from basoel2_native_probe import configuration
from basoel2_powerworld_probe import specification,validate_readback,model
from compare_basoel2_powerworld import CHANNELS,ANGLES,compare,require_initial_measurement


def synthetic():
    keys=list(CHANNELS.values())+list(ANGLES)
    fields=[[key[0],key[1],'','',key[2],''] for key in keys]
    initial={key:0. for key in keys}
    for key in keys:
        if key[2] in ('TSVpu','TSSpeed','TSIfd','TSEfd','TSExciterState:2','TSOELState:1','TSOELState:3'):initial[key]=1.
    initial[ANGLES[0]]=25.;initial[ANGLES[1]]=5.
    reference=[];actual=[]
    for tick in range(1001):
        time=tick*.0005;event=time in (.05,.1)
        for side in (('pre','post') if event else ('regular',)):
            reference.append([time]+[initial[key] for key in keys])
            row={name:initial[key] for name,key in CHANNELS.items()}
            for name in ('SPEED_DEV','REF_SPEED_DEV'):row[name]-=1
            row['P_PU']/=100;row['Q_PU']/=100
            row.update(RELATIVE_ANGLE=math.radians(20),time_s=time,event_side=side);actual.append(row)
    return fields,reference,actual


class AllRowComparisonTest(unittest.TestCase):
    def test_every_declared_channel_and_both_event_occurrences_are_retained(self):
        result=compare(*synthetic(),.0005)
        self.assertEqual(1003,result['paired_rows']);self.assertEqual(0,result['rows_dropped'])
        self.assertEqual(30,result['channels']);self.assertTrue(result['numerical_rmse_pass'])
        self.assertEqual(2,result['metrics']['BAS_INTEGRAL']['post']['samples'])
        self.assertFalse(result['trajectory_acceptance'])

    def test_event_only_mismatch_is_not_hidden_by_reporting_regular_rows(self):
        fields,ref,actual=synthetic();next(r for r in actual if r['event_side']=='post')['BAS_INTEGRAL']=.1
        result=compare(fields,ref,actual,.0005);metric=result['metrics']['BAS_INTEGRAL']
        self.assertFalse(metric['all']['passes_rmse']);self.assertEqual(0,metric['regular']['rmse'])
        self.assertEqual(.05,metric['all']['peak_time_s']);self.assertEqual('post',metric['all']['peak_event_side'])

    def test_one_failing_channel_cannot_be_averaged_with_the_others(self):
        fields,ref,actual=synthetic()
        for row in actual:row['SPEED_DEV']=.01
        result=compare(fields,ref,actual,.0005)
        self.assertFalse(result['numerical_rmse_pass']);self.assertTrue(result['metrics']['V_BUS1']['all']['passes_rmse'])

    def test_missing_or_duplicate_raw_rows_are_rejected(self):
        for kind in ('missing','duplicate'):
            fields,ref,actual=synthetic()
            if kind=='missing':actual.pop()
            else:actual[50]=actual[49]
            with self.assertRaises(ValueError):compare(fields,ref,actual,.0005)

    def test_state_field_cannot_be_silently_omitted_or_duplicated(self):
        for kind in ('missing','duplicate'):
            fields,ref,actual=synthetic()
            if kind=='missing':fields.pop()
            else:fields[-1]=fields[0]
            with self.assertRaises(ValueError):compare(fields,ref,actual,.0005)

    def test_off_grid_clock_and_incorrect_event_side_are_rejected(self):
        for kind in ('clock','side'):
            fields,ref,actual=synthetic()
            if kind=='clock':actual[10]['time_s']+=1e-5
            else:next(r for r in actual if r['event_side']=='post')['event_side']='regular'
            with self.assertRaises(ValueError):compare(fields,ref,actual,.0005)

    def test_nonfinite_observations_and_nonmonotonic_clocks_are_rejected(self):
        for kind in ('nan','clock'):
            fields,ref,actual=synthetic()
            if kind=='nan':actual[10]['BAS_INPUT']=float('nan')
            else:actual[10],actual[11]=actual[11],actual[10]
            with self.assertRaises(ValueError):compare(fields,ref,actual,.0005)


class ReadbackSpecificationTest(unittest.TestCase):
    def test_initialization_guard_rejects_a_zero_sensor_with_nonzero_field_voltage(self):
        fields,rows,_=synthetic();index=next(i+1 for i,f in enumerate(fields) if f[4]=='TSOELState:1')
        raw=Path('ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
        cfg=configuration('efd-fault',raw);self.assertTrue(require_initial_measurement(fields,rows,cfg)['intended_initial_measurement_consistent'])
        rows[0][index]=0
        with self.assertRaisesRegex(ValueError,'physical initial input'):require_initial_measurement(fields,rows,cfg)

    def test_exact_known_profiles_export_all_three_states_and_twelve_diagnostics(self):
        for profile in ('efd-fault','ifd-fault','vfe-fault','ifd-zero-filter','disabled-fault'):
            s=specification(profile,.0005,'source.dyr');self.assertEqual(31,len(s['results']))
            self.assertIn("GEN 1 '1'|TSOELState:3",s['results']);self.assertIn("GEN 1 '1'|TSOELOther:12",s['results'])
            self.assertFalse(s['auto_correct']);self.assertNotIn('powerworld_model_overrides',s)

    def test_no_unreviewed_profile_or_step_is_substituted(self):
        for profile,h in (('unknown',.0005),('ifd-fault',.001)):
            with self.assertRaises(ValueError):specification(profile,h,'source.dyr')

    def test_missing_or_malformed_readback_cannot_pass(self):
        with self.assertRaises(ValueError):model('','OverExcitationLimiter_BASOEL2')
        with self.assertRaises(ValueError):model('OverExcitationLimiter_BASOEL2 (BusNum,ID) {1}','OverExcitationLimiter_BASOEL2')

    def test_wrong_owner_or_inactive_model_cannot_pass(self):
        for owner,status in ((2,'Active'),(1,'Inactive')):
            body=f'OverExcitationLimiter_BASOEL2 (BusNum,ID,Status) {{{owner} "1" "{status}"}}'
            with self.assertRaises(ValueError):model(body,'OverExcitationLimiter_BASOEL2')


if __name__=='__main__':unittest.main()
