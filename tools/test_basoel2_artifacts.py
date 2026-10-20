"""Whole-export checks preserve timer anomalies, event duplicates and initialization history."""
import csv
import tempfile
import unittest
from pathlib import Path
from check_basoel2_artifacts import compare,read,verify_physical_invariants


class Basoel2ArtifactTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.a=Path(self.temp.name)/'a.csv';self.b=Path(self.temp.name)/'b.csv'

    def write(self,path,rows,header=('time_s','BAS_INPUT','BAS_T2_B')):
        with path.open('w',newline='') as stream:
            writer=csv.writer(stream);writer.writerow(header);writer.writerows(rows)

    def test_all_rows_and_event_duplicates_are_counted(self):
        rows=[(-.001,1,0),(0,1,0),(.05,1,0),(.05,1,0),(.1,2,0)]
        self.write(self.a,rows);self.write(self.b,rows)
        result=compare(self.a,self.b)
        self.assertTrue(result['whole_csv_identical']);self.assertEqual(5,result['rows'])
        self.assertEqual(1,result['negative_time_rows']);self.assertEqual(1,result['duplicate_time_rows'])

    def test_timer_anomaly_is_reported_not_normalized_away(self):
        self.write(self.a,[(0,1,0)]);self.write(self.b,[(0,1,2)])
        result=compare(self.a,self.b)
        self.assertFalse(result['whole_csv_identical'])
        self.assertEqual({'BAS_T2_B':dict(maximum=2,different_rows=1)},result['numerical_differences'])

    def test_clock_changes_are_reported_as_a_channel_difference(self):
        self.write(self.a,[(0,1,0)]);self.write(self.b,[(.001,1,0)])
        self.assertIn('time_s',compare(self.a,self.b)['numerical_differences'])

    def test_shape_changes_are_rejected(self):
        self.write(self.a,[(0,1,0)]);self.write(self.b,[(0,1,0),(1,1,0)])
        with self.assertRaises(ValueError):compare(self.a,self.b)

    def test_wrong_ifd_routing_cannot_pass_a_zero_filter_identity(self):
        header=['BAS_INPUT','XAD_IFD']
        self.assertEqual(0,verify_physical_invariants('ifd-zero-filter',header,[[1,1],[2,2]])['maximum_zero_filter_ifd_identity_error'])
        with self.assertRaises(ValueError):verify_physical_invariants('ifd-zero-filter',header,[[1,2]])

    def test_disabled_output_gate_checks_machine_boundary_too(self):
        header=['BAS_OEL_OUTPUT','BAS_VHZ_OUTPUT','VOEL_MACHINE']
        self.assertTrue(verify_physical_invariants('disabled-fault',header,[[0,0,0]])['disabled_outputs_identically_zero'])
        for index in range(3):
            row=[0,0,0];row[index]=.1
            with self.assertRaises(ValueError):verify_physical_invariants('disabled-fault',header,[row])

    def test_nonfinite_ragged_nonmonotonic_and_duplicate_channels_are_rejected(self):
        for rows,header in [([(0,float('nan'),0)],('t','x','y')),([(0,1)],('t','x','y')),
                            ([(1,1,0),(0,1,0)],('t','x','y')),([(0,1,0)],('t','x','x'))]:
            with self.subTest(rows=rows,header=header):
                self.write(self.a,rows,header)
                with self.assertRaises(ValueError):read(self.a)


if __name__=='__main__':unittest.main()
