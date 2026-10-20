"""All-row/event-side matching and independent per-channel RMSE safeguards."""
import copy
import math
import unittest
import json
from pathlib import Path
import tempfile
from compare_oel4c_powerworld import CHANNELS,ANGLES,UNSCORED,compare,validate_case
def synthetic(flat=False):
 keys=list(CHANNELS.values())+list(ANGLES)+list(UNSCORED)
 fields=[[k[0],k[1],'','',k[2],''] for k in keys];reference=[];actual=[]
 for tick in range(1001):
  time=tick*.0005
  for side in (('pre','post') if not flat and time in (.05,.1) else ('regular',)):
   values={k:0. for k in keys}
   for k in keys:
    if k[2] in ('TSVpu','TSSpeed'):values[k]=1.
   values[ANGLES[0]]=25;values[ANGLES[1]]=5
   reference.append([time]+[values[k] for k in keys])
   row={name:values[k] for name,k in CHANNELS.items()}
   for name in ('SPEED_DEV','REF_SPEED_DEV'):row[name]-=1
   row.update(time_s=time,event_side=side,RELATIVE_ANGLE=math.radians(20));actual.append(row)
 return fields,reference,actual
class ComparisonTest(unittest.TestCase):
 def test_all_raw_rows_and_explicit_coordinate_inventory_are_retained(self):
  result=compare(*synthetic(),.0005,'delayed-fault')
  self.assertEqual(1003,result['paired_rows']);self.assertEqual(17,result['compared_coordinates']);self.assertEqual(3,len(result['unscored_reference_coordinates']))
  self.assertTrue(result['numerical_rmse_pass']);self.assertFalse(result['trajectory_acceptance'])
 def test_flat_has_no_fake_event_groups(self):
  result=compare(*synthetic(True),.0005,'flat');self.assertTrue(result['numerical_rmse_pass'])
  self.assertEqual(0,result['metrics']['OEL_OUTPUT']['post']['samples']);self.assertIsNone(result['metrics']['OEL_OUTPUT']['post']['rmse'])
 def test_event_only_difference_cannot_be_hidden_by_regular_rows(self):
  fields,ref,actual=synthetic();next(r for r in actual if r['event_side']=='post')['OEL_OUTPUT']=.1
  result=compare(fields,ref,actual,.0005,'delayed-fault');self.assertFalse(result['numerical_rmse_pass'])
  self.assertEqual(0,result['metrics']['OEL_OUTPUT']['regular']['rmse']);self.assertEqual('post',result['metrics']['OEL_OUTPUT']['all']['peak_event_side'])
 def test_one_channel_failure_cannot_be_averaged_with_other_channels(self):
  fields,ref,actual=synthetic()
  for row in actual:row['SPEED_DEV']=.01
  result=compare(fields,ref,actual,.0005,'delayed-fault');self.assertFalse(result['metrics']['SPEED_DEV']['all']['passes_rmse'])
  self.assertTrue(result['metrics']['V_BUS1']['all']['passes_rmse'])
 def test_missing_duplicate_or_off_grid_rows_are_rejected(self):
  for kind in ('missing','duplicate','clock','side'):
   fields,ref,actual=synthetic()
   if kind=='missing':actual.pop()
   elif kind=='duplicate':actual[20]=actual[19]
   elif kind=='clock':actual[20]['time_s']+=1e-5
   else:next(r for r in actual if r['event_side']=='post')['event_side']='regular'
   with self.assertRaises(ValueError):compare(fields,ref,actual,.0005,'delayed-fault')
 def test_reference_inventory_cannot_be_silently_reduced(self):
  fields,ref,actual=synthetic();fields[-1]=fields[0]
  with self.assertRaises(ValueError):compare(fields,ref,actual,.0005,'delayed-fault')
 def test_nonfinite_or_missing_actual_channel_is_rejected(self):
  for kind in ('nan','missing'):
   fields,ref,actual=synthetic()
   if kind=='nan':actual[20]['Q_PU']=math.nan
   else:actual[20].pop('Q_PU')
   with self.assertRaises(ValueError):compare(fields,ref,actual,.0005,'delayed-fault')
 def test_matched_case_rejects_changed_limiter_base_or_machine_records(self):
  from oel4c_powerworld_probe import configuration
  root=Path.cwd();raw=(root/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()
  gen=next(s for s in raw.splitlines() if s.startswith("1,'1',"));values=gen.split(',');values[8]='100.0'
  with tempfile.TemporaryDirectory() as temporary:
   folder=Path(temporary)/'case-1';folder.mkdir();source=configuration('flat',raw)['dynamic']
   dyr='\n'.join(s for s in source.splitlines() if "'OEL4C'" not in s)+'\n'
   (folder/'network.raw').write_text(raw.replace(gen,','.join(values)));(folder/'model.dyr').write_text(dyr)
   data=dict(reactivePickup=1.,delay=.01,proportionalGain=.15,integralGain=.25,minimumOutput=-.25)
   case=dict(profile='flat',step_s=.0005,machine_base_mva=100.,network_base_mva=100.,typed_limiter=True,data=data)
   for mutation in ('none','gain','base','machine'):
    value=copy.deepcopy(case)
    if mutation=='gain':value['data']['integralGain']=.26
    if mutation=='base':value['network_base_mva']=200
    (folder/'case.json').write_text(json.dumps(value))
    (folder/'model.dyr').write_text(dyr.replace('99999','99998') if mutation=='machine' else dyr)
    if mutation=='none':self.assertEqual(folder/'case.json',validate_case(root,Path(temporary),'flat',.0005))
    else:
     with self.assertRaises(ValueError):validate_case(root,Path(temporary),'flat',.0005)
if __name__=='__main__':unittest.main()
