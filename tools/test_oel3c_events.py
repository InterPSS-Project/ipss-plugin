"""Event-side pairing and unit conversions cannot manufacture acceptance."""
import copy
import base64
import gzip
import hashlib
import json
import tempfile
from pathlib import Path
import unittest
from compare_oel3c_events import CHANNELS, compare, grid_tick
from oel3c_routing_probe import configuration
from audit_oel3c_measurement import read_measurements,initial_measurement


class EventComparisonTest(unittest.TestCase):
    def setUp(self):
        raw=Path('ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
        self.cfg=configuration('vfe-fault',raw)
        sources=list(CHANNELS.values())+[('Generator','1 1','TSRotorAngle'),('Generator','2 1','TSRotorAngle')]
        self.fields=[[kind,owner,'','',name,''] for kind,owner,name in sources]
        self.actual=[];self.reference=[]
        for time,side in ((0,'regular'),(.05,'pre'),(.05,'post'),(.1,'pre'),(.1,'post'),(.15,'regular'),(.2,'regular')):
            row={key:0. for key in CHANNELS}
            row.update(time_s=time,event_side=side,V_BUS1=.5 if side=='post' and time==.05 else 1.,V_BUS2=1.,
                       MACH_SPEED=.0001,REF_SPEED=.00002,PMW=50.,QMVAR=10.,EFD=1.1,IFD_EXC=1.2,
                       EXC_FIELD=1.,MACH_ANGLE=20.+time,REF_ANGLE=2.+time/2)
            row['OEL_INPUT']=.3333*(1.1*row['EXC_FIELD']+.1*row['IFD_EXC'])
            row['EXC_SENSE']=row['V_BUS1']
            self.actual.append(row)
            values=[]
            for key in CHANNELS:
                value=row[key]+1 if key in ('MACH_SPEED','REF_SPEED') else row[key]
                values.append(value)
            values += [row['MACH_ANGLE']+30,row['REF_ANGLE']+30]
            self.reference.append([time]+values)

    def result(self):
        return compare(self.fields,self.reference,self.actual,self.cfg,.05)

    def test_every_row_and_both_event_sides_are_paired_without_interpolation(self):
        result=self.result()
        self.assertEqual(7,result['paired_rows']);self.assertEqual(0,result['rows_dropped'])
        self.assertEqual(18,result['channels']);self.assertTrue(result['numerical_rmse_pass'])
        self.assertFalse(result['trajectory_acceptance']);self.assertFalse(result['runtime_routing_verified'])
        self.assertEqual([0,.05,.05,.1,.1,.15,.2],[p['reference_time_s'] for p in result['paired']])
        for metrics in result['metrics'].values():
            self.assertEqual([7,3,2,2],[metrics[side]['samples'] for side in ('all','regular','pre','post')])

    def test_power_is_normalized_on_declared_100_mva_base(self):
        self.actual[0]['PMW']+=.1
        result=self.result()['metrics']['PMW']['all']
        self.assertAlmostEqual(.001,result['maximum_absolute'])
        self.assertTrue(result['passes_rmse'])

    def test_speed_offset_and_relative_angle_use_physical_coordinates(self):
        result=self.result()
        self.assertLess(result['metrics']['MACH_SPEED']['all']['rmse'],1e-15)
        self.assertLess(result['metrics']['REL_ANGLE_RAD']['all']['rmse'],1e-15)
        self.assertLess(result['metrics']['REL_ANGLE_EXCURSION_RAD']['all']['rmse'],1e-15)

    def test_initial_angle_difference_is_reported_not_hidden_by_excursion(self):
        for row in self.reference:row[-1]+=1
        result=self.result()
        self.assertFalse(result['metrics']['REL_ANGLE_RAD']['all']['passes_rmse'])
        self.assertTrue(result['metrics']['REL_ANGLE_EXCURSION_RAD']['all']['passes_rmse'])
        self.assertFalse(result['numerical_rmse_pass'])

    def test_using_pre_fault_voltage_for_post_side_fails(self):
        self.actual[2]['V_BUS1']=1.
        result=self.result()['metrics']['V_BUS1']
        self.assertEqual(.5,result['post']['maximum_absolute'])
        self.assertEqual(.05,result['post']['peak_reference_time_s'])
        self.assertFalse(result['all']['passes_rmse'])

    def test_missing_post_side_is_not_silently_dropped(self):
        self.actual.pop(2)
        with self.assertRaisesRegex(ValueError,'Missing matching event-side'):self.result()

    def test_duplicate_actual_row_is_rejected(self):
        self.actual.insert(1,copy.deepcopy(self.actual[0]))
        with self.assertRaisesRegex(ValueError,'Duplicate actual'):self.result()

    def test_extra_actual_row_is_not_silently_dropped(self):
        row=copy.deepcopy(self.actual[-1]);row['time_s']=.25;self.actual.append(row)
        with self.assertRaisesRegex(ValueError,'Unpaired actual'):self.result()

    def test_clock_shifts_are_not_fitted_or_interpolated(self):
        self.actual[-1]['time_s']+=1e-5
        with self.assertRaisesRegex(ValueError,'Off-grid'):self.result()
        for time,step in ((0,0),(-.1,.05),(float('nan'),.05)):
            with self.assertRaises(ValueError):grid_tick(time,step)

    def test_missing_or_duplicate_metadata_is_rejected(self):
        saved=self.fields
        self.fields=saved+[saved[0]]
        for row in self.reference:row.append(1.)
        with self.assertRaisesRegex(ValueError,'Duplicate reference'):self.result()
        self.fields=saved
        for row in self.reference:row.pop()
        self.fields[0][4]='Unknown'
        with self.assertRaisesRegex(ValueError,'Missing reference channel'):self.result()

    def test_missing_actual_channel_and_nonfinite_reference_fail_closed(self):
        row=self.actual[0];saved=row.pop('IFD_EXC')
        with self.assertRaisesRegex(ValueError,'Missing actual channel'):self.result()
        row['IFD_EXC']=saved;self.reference[-1][1]=float('nan')
        with self.assertRaisesRegex(ValueError,'Invalid reference'):self.result()

    def test_missing_clearing_occurrence_is_rejected(self):
        self.reference.pop(4)
        with self.assertRaisesRegex(ValueError,'both three-cycle'):self.result()

    def test_stale_zero_tr_reference_sensor_is_reported_not_replaced(self):
        index=list(CHANNELS).index('EXC_SENSE')+1
        self.reference[2][index]=1.
        result=self.result()
        self.assertEqual(.5,result['zero_tr_uncompensated_sensor_consistency']['reference']['post']['maximum_absolute'])
        self.assertEqual(0.,result['zero_tr_uncompensated_sensor_consistency']['actual']['post']['maximum_absolute'])
        self.assertEqual(1.,result['paired'][2]['reference']['EXC_SENSE'])
        self.assertFalse(result['metrics']['EXC_SENSE']['all']['passes_rmse'])

    def test_whole_fine_step_fixtures_and_linked_coarse_evidence_are_immutable(self):
        resources=Path('ipss.test.plugin.core/src/test/resources')
        folder=resources/'oel3c-event-reference';index=json.loads((folder/'index.json').read_text())
        self.assertEqual(6,index['combined_cold_pairs']);self.assertEqual(18036,index['combined_raw_rows_all_cold_runs'])
        self.assertEqual(index['linked_coarse_sha256'],hashlib.sha256((resources/index['linked_coarse_index']).read_bytes()).hexdigest())
        self.assertFalse(index['trajectory_acceptance']);self.assertFalse(index['runtime_routing_verified'])
        raw=Path('ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
        with tempfile.TemporaryDirectory() as name:
            for item,status in zip(index['profiles'],('mismatch','ambiguous','consistent')):
                body=gzip.decompress(base64.b64decode((folder/item['resource']).read_bytes()))
                self.assertEqual(item['raw_sha256'],hashlib.sha256(body).hexdigest())
                path=Path(name)/'whole.csv';path.write_bytes(body)
                fields,rows=read_measurements(path)
                self.assertEqual(2003,len(rows));self.assertEqual(22,len(fields));self.assertEqual(.00025,item['step_s'])
                self.assertEqual([.05,.1],[a[0] for a,b in zip(rows,rows[1:]) if a[0]==b[0]])
                result=initial_measurement(fields,rows,configuration(item['name'],raw))
                self.assertEqual(status,result['status']);self.assertEqual(item['initial_measurement'],result)


if __name__=='__main__':unittest.main()
