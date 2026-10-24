"""Portable checks for the SMIB discriminator setup, without the reference engine."""
import unittest
from pathlib import Path
from bsg_native_probe import configuration,inputs

class BsgNativeProbeTest(unittest.TestCase):
    def test_explicit_limits_change_only_active_power_bounds(self):
        root=Path(__file__).resolve().parents[1]
        raw,normal=inputs(root,configuration('normal-up','coarse','a'))
        _,fixed=inputs(root,configuration('fixed-up','coarse','a'))
        _,flagged=inputs(root,configuration('baseload2-up','coarse','a'))
        self.assertEqual(normal,flagged)
        self.assertNotEqual(normal,fixed)
        a=[line.split() for line in normal.splitlines()];b=[line.split() for line in fixed.splitlines()]
        changes=[(x[1],i) for x,y in zip(a,b) for i,(u,v) in enumerate(zip(x,y)) if u!=v]
        self.assertEqual(changes,[("'REECA1'",34),("'REECA1'",35),("'REPCA1'",32),("'REPCA1'",33)])
    def test_half_step_and_both_reference_directions(self):
        self.assertEqual(.00025,configuration('fixed-up','fine','b')['step_s'])
        self.assertEqual(-.1,configuration('fixed-down','coarse','a')['increment'])
        for args in [('fixed-up','bad','a'),('unknown-up','fine','a'),('fixed-up','fine','c')]:
            with self.assertRaises(ValueError):configuration(*args)

if __name__=='__main__':unittest.main()
