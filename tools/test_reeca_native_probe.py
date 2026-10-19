from pathlib import Path
import unittest
from reeca_native_probe import MODES,configuration,inputs


class ReecaNativeProbeTest(unittest.TestCase):
    def test_native_cardinality_and_three_record_isolation(self):
        root=Path(__file__).resolve().parents[1]
        for mode in MODES:
            p=configuration(mode,'fine','a')
            self.assertEqual(6,len(p['icons']));self.assertEqual(45,len(p['constants']))
            self.assertEqual(.00025,p['step_s'])
            raw,dyr=inputs(root,p);self.assertTrue(raw)
            self.assertEqual(3,dyr.count('/'))
            self.assertNotIn('REPCA',dyr);self.assertNotIn('WTTQ',dyr)
            self.assertEqual(55,len(dyr.splitlines()[1].split()))

    def test_bias_and_selector_discriminators_are_declared(self):
        self.assertEqual([0,1,0,1,0,0],configuration('pf-voltage','coarse','a')['icons'])
        self.assertEqual(.03,configuration('voltage-bias','coarse','a')['constants'][21])
        self.assertEqual([2,0,0,1,0,0],configuration('remote','coarse','a')['icons'])
        self.assertFalse(configuration('pf-voltage-flat','coarse','a')['fault'])

    def test_optional_tables_and_hold_directions_are_preserved(self):
        q=configuration('q-priority','coarse','a');p=configuration('p-priority','coarse','a')
        self.assertEqual(q['constants'],p['constants']);self.assertEqual(0,q['icons'][5]);self.assertEqual(1,p['icons'][5])
        self.assertEqual([-1.,.1,2.,.1,0.,0.,0.,0.],q['constants'][29:37])
        self.assertEqual([-1.,.1,.5,.2,1.,.15,2.,.05],configuration('descending','coarse','a')['constants'][29:37])
        self.assertEqual([.15,.03,.04],configuration('hold-plus','fine','b')['constants'][9:12])
        self.assertEqual([.15,-.03,.04],configuration('hold-minus','fine','b')['constants'][9:12])

    def test_unknown_inputs_fail_closed(self):
        for args in [('unknown','fine','a'),('q','bad','a'),('q','fine','c')]:
            with self.assertRaises(ValueError):configuration(*args)


if __name__=='__main__':unittest.main()
