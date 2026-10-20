"""Exact branch tests distinguish jump timing from source-equation changes."""
import unittest
from audit_oel3c_switching import proportional,diagnose

def row(t,integral,error):
    return dict(time_s=t,OEL_INTEGRAL=integral,OEL_GAINED_ERROR=error,OEL_PROPORTIONAL=proportional(integral,error,1))

class SwitchingTest(unittest.TestCase):
    def test_strict_zero_and_signed_zero_disable_the_proportional_branch(self):
        for integral in (0.,-0.,1e-15):self.assertEqual(0,proportional(integral,2,3))
        self.assertEqual(6,proportional(-1e-15,2,3))
    def test_equal_traces_keep_all_history_and_duplicate_event_rows(self):
        rows=[row(-.1,0,1),row(0,0,1),row(.1,-.1,-1),row(.1,-.1,-1),row(.2,0,1)]
        result=diagnose(rows,rows);self.assertEqual(5,result['reference_rows']);self.assertEqual(5,sum(result['counts'].values()))
        self.assertEqual(2,result['counts']['event-occurrence'])
        self.assertEqual(0,result['rows_dropped']);self.assertTrue(all(v['absolute']==0 for v in result['maxima'].values()))
    def test_single_step_switch_delay_has_a_finite_pointwise_error_without_an_equation_error(self):
        reference=[row(0,0,-.02),row(.1,0,-.02),row(.2,-.001,-.02)]
        actual=[row(0,0,-.02),row(.1,-.001,-.02),row(.2,-.002,-.02)]
        result=diagnose(reference,actual);self.assertEqual(1,result['counts']['different-branch'])
        self.assertEqual(.02,result['maxima']['different-branch:algebraic']['absolute'])
        self.assertTrue(result['exact_proportional_equation_both_traces']);self.assertFalse(result['trajectory_acceptance'])
    def test_interpolating_a_discontinuous_output_is_not_algebraic_reevaluation(self):
        reference=[row(.05,-.001,-1)]
        actual=[row(0,-.002,-1),row(.1,0,1)]
        result=diagnose(reference,actual);self.assertNotEqual(result['maxima']['same-branch:linear']['absolute'],result['maxima']['same-branch:algebraic']['absolute'])
    def test_corrupted_reference_equation_is_not_classified_as_timing(self):
        reference=[row(0,-.1,-1)];reference[0]['OEL_PROPORTIONAL']=0
        with self.assertRaises(ValueError):diagnose(reference,[row(0,-.1,-1)])
    def test_terminal_hold_is_explicit_and_not_silently_dropped(self):
        result=diagnose([row(-.1,0,0),row(0,0,0),row(.2,0,0)],[row(0,0,0),row(.1,0,0)])
        self.assertEqual({'history':1,'same-branch':1,'terminal-hold':1},result['counts'])

if __name__=='__main__':unittest.main()
