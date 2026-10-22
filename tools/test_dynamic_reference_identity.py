import unittest
from dynamic_reference_identity import validate_reference_identity


class ReferenceIdentityTest(unittest.TestCase):
    def check_event(self, **changes):
        event = dict(case_number=1, event_type="bus_fault", target_bus=6307,
                     start_s=1., clear_s=1.0833333333333333)
        event.update(changes)
        validate_reference_identity(event, case_number=1, event_type="bus_fault", target_bus=6307,
                                    reference_event_times=[.999990701675415, 1.0839946269989014],
                                    reference_step_s=.001)

    def test_grid_and_roundoff_are_allowed(self):
        self.check_event()

    def test_wrong_bus_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "target_bus"):
            self.check_event(target_bus=7249)

    def test_wrong_case_and_event_type_are_rejected(self):
        for changes in (dict(case_number=2), dict(event_type="generator_trip")):
            with self.assertRaisesRegex(ValueError, "Reference event mismatch"):
                self.check_event(**changes)

    def test_wrong_time_and_nonfinite_time_are_rejected(self):
        for changes in (dict(start_s=.5), dict(clear_s=1.2), dict(start_s=float('nan'))):
            with self.assertRaises(ValueError):
                self.check_event(**changes)


if __name__ == "__main__":
    unittest.main()
