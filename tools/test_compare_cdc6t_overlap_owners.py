import unittest
from compare_cdc6t_overlap_owners import compare

class OwnershipTests(unittest.TestCase):
    def metadata(self,rows,events=None):
        return dict(columns=['V7'],external_schedule=[.05],regular_rows=rows,internal_transitions=events or [])
    def event(self,time):
        fields=['rectifierReal','rectifierImaginary','inverterReal','inverterImaginary','measuredInverterVolts','measuredAmperes','measuredRectifierVolts','rectifierP','rectifierQ','alphaRadians','inverterP','inverterQ','gammaRadians','rectifierVolts','inverterVolts']
        return dict(time=time,before=dict.fromkeys(fields,0.),after=dict.fromkeys(fields,0.))
    def test_external_sides_preserved_without_value_matching(self):
        ref=[dict(time_s=.05,V7=1.),dict(time_s=.05,V7=.5)]
        rows,summary=compare(self.metadata([[.05,.5],[.05,1.]]),ref)
        self.assertEqual(['PRE','POST'],[r['side'] for r in rows])
        self.assertEqual([-.5,.5],[r['V7'] for r in rows])
        self.assertEqual(1,summary['coverage'])
    def test_missing_extra_and_future_observations_remain_ambiguous(self):
        ref=[dict(time_s=.05,V7=1.),dict(time_s=.05,V7=.5)]
        for actual in [[[.05,1.]],[[.05,1.],[.05,.5],[.05,.5]],[[.0505,1.],[.0505,.5]]]:
            rows,summary=compare(self.metadata(actual),ref)
            self.assertEqual(2,len(rows));self.assertEqual(0,summary['coverage'])
            self.assertTrue(all(r['owner']=='AMBIGUOUS' and 'V7' not in r for r in rows))
    def test_coincident_external_internal_owner_is_not_guessed(self):
        ref=[dict(time_s=.05,V7=1.),dict(time_s=.05,V7=.5)]
        rows,summary=compare(self.metadata([[.05,1.],[.05,.5]],[self.event(.05)]),ref)
        self.assertEqual(0,summary['coverage'])
        self.assertTrue(all(r['owner_candidates']=='EXTERNAL+INTERNAL' for r in rows))
    def test_endpoint_does_not_consume_future_state(self):
        rows,summary=compare(self.metadata([[.0015,.9]]),[dict(time_s=.001,V7=.9)])
        self.assertEqual('AMBIGUOUS',rows[0]['owner']);self.assertEqual(0,summary['coverage'])

    def test_invalid_shape_finiteness_order_and_clock_are_rejected(self):
        ref=[dict(time_s=.001,V7=.9)]
        for actual in [[[.001]],[[.001,.9,1.]],[[.001,float('nan')]],[[.001,float('inf')]],[[.001,.9],[0.,.9]],[[.0012,.9]]]:
            with self.assertRaises(ValueError):compare(self.metadata(actual),ref)
        for keys in [[],['V7','V7'],['unknown']]:
            m=self.metadata([[.001,.9]]);m['columns']=keys
            with self.assertRaises(ValueError):compare(m,ref)
        with self.assertRaises(ValueError):compare(self.metadata([[.001,.9]]),[dict(time_s=.001)])
    def test_multiple_internal_events_and_actual_only_rows_retained(self):
        m=self.metadata([[.001,0.],[.0015,0.]],[self.event(.001),self.event(.001)])
        rows,summary=compare(m,[dict(time_s=.001,V7=0.),dict(time_s=.001,V7=0.)])
        self.assertEqual(0,summary['coverage'])
        self.assertEqual(2,len(summary['unpaired_actual_regular']))
        self.assertEqual(2,len(summary['unpaired_actual_internal']))
    def test_clock_residual_is_reported_without_time_shift(self):
        rows,summary=compare(self.metadata([[.001,.9]]),[dict(time_s=.00100001,V7=.9)])
        self.assertAlmostEqual(1e-8,rows[0]['reference_clock_residual'])
        self.assertEqual(.00100001,rows[0]['reference_time'])
        self.assertEqual(.001,rows[0]['actual_time'])

if __name__=='__main__':unittest.main()
