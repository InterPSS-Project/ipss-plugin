import unittest
from compare_cdc6t_overlap_owners import compare

class OwnershipTests(unittest.TestCase):
    def metadata(self,rows,events=None):
        return dict(columns=['V7'],external_schedule=[.05],regular_rows=rows,internal_transitions=events or [])
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
        rows,summary=compare(self.metadata([[.05,1.],[.05,.5]],[dict(time=.05)]),ref)
        self.assertEqual(0,summary['coverage'])
        self.assertTrue(all(r['owner_candidates']=='EXTERNAL+INTERNAL' for r in rows))
    def test_endpoint_does_not_consume_future_state(self):
        rows,summary=compare(self.metadata([[.0015,.9]]),[dict(time_s=.001,V7=.9)])
        self.assertEqual('AMBIGUOUS',rows[0]['owner']);self.assertEqual(0,summary['coverage'])

if __name__=='__main__':unittest.main()
