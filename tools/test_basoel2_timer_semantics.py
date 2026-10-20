"""Controlled timer hypotheses remain separate from automatic model semantics."""
import unittest
from pathlib import Path
from audit_basoel2_timer_semantics import Timer
from basoel2_timer_probe import configuration,SCHEDULES


class TimerProbeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw=(Path(__file__).resolve().parents[1]/'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()

    def test_every_intervention_is_explicit_and_does_not_apply_a_physical_fault(self):
        for name,schedule in SCHEDULES.items():
            for suffix in ('','-active'):
                cfg=configuration(name+suffix,self.raw);limiter=cfg['components'][1]
                self.assertFalse(cfg['apply_fault']);self.assertEqual(schedule,cfg['basoel2_timer_state_schedule'])
                self.assertEqual(1e6,limiter['constants'][0]);self.assertEqual(0,limiter['icons'][1])
                if suffix:self.assertEqual([0.,0.],limiter['constants'][1:3])
                else:self.assertEqual(0,limiter['icons'][0])

    def test_fractional_delay_discriminator_is_not_a_clock_shift(self):
        cfg=configuration('timer-fractional-active',self.raw);p=cfg['components'][1]['constants']
        self.assertEqual([.0202]*3,p[4:9:2]);self.assertEqual(((.05,4.),(.25,.5)),cfg['basoel2_timer_state_schedule'])

    def test_unknown_profiles_are_rejected(self):
        with self.assertRaises(ValueError):configuration('unlisted-active',self.raw)

    def test_long_exposure_is_not_clamped_at_the_activation_delay(self):
        timer=Timer();timer.advance(0,.02,4,3,.01);self.assertTrue(timer.active)
        timer.advance(.02,.2,4,3,.01);timer.advance(.2,.21,.5,3,.01)
        self.assertEqual('cooling',timer.phase);self.assertAlmostEqual(.4,timer.deadline)

    def test_unexpired_exposure_is_cancelled_when_input_falls_below_threshold(self):
        timer=Timer();timer.advance(0,.01,4,3,.1);timer.advance(.01,.02,.5,3,.1)
        self.assertEqual('idle',timer.phase);timer.advance(.02,.03,4,3,.1)
        self.assertEqual(.02,timer.start);self.assertAlmostEqual(.12,timer.deadline)

    def test_reheating_pauses_an_already_started_cooldown(self):
        timer=Timer();timer.advance(0,.02,4,3,.01);timer.advance(.02,.1,4,3,.01)
        timer.advance(.1,.11,.5,3,.01);deadline=timer.deadline
        timer.advance(.11,.12,4,3,.01);self.assertEqual('cooling',timer.phase)
        self.assertAlmostEqual(deadline+.01,timer.deadline)

    def test_hysteresis_uses_strict_return_boundary(self):
        timer=Timer();timer.advance(0,.02,4,3,.01);timer.advance(.02,.03,2.998,3,.01)
        self.assertTrue(timer.above);timer.advance(.03,.04,3*.999,3,.01);self.assertFalse(timer.above)

    def test_both_endpoint_and_nearest_step_hypotheses_are_explicit(self):
        a,b=Timer(),Timer();a.advance(0,.02,4,3,.0202);b.advance(0,.02,4,3,.0202,.00025)
        self.assertFalse(a.active);self.assertTrue(b.active)


if __name__=='__main__':unittest.main()
