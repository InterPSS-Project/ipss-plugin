import unittest
from audit_nr_consolidation import compare


def record(outcome="passed", detail="", frames=()):
    return dict(outcome=outcome, exception="AssertionError" if outcome == "failure" else "",
                detail=detail, source_frames=list(frames))


class RegressionIdentityAuditTest(unittest.TestCase):
    def test_unchanged_baseline_failure_is_retained(self):
        tests = {"old": record("failure", "expected true", ("Example.java:12",))}
        result = compare(dict(tests=tests), dict(tests=tests))
        self.assertFalse(result["requires_review"])

    def test_changed_failure_stage_requires_review(self):
        before = dict(tests={"old": record("failure", "expected true", ("Example.java:12",))})
        after = dict(tests={"old": record("failure", "expected true", ("Example.java:10",))})
        result = compare(after, before)
        self.assertEqual(result["changed_failures"], ["old"])
        self.assertTrue(result["requires_review"])

    def test_missing_unexpected_skip_and_new_failure_are_separate(self):
        before = dict(tests={k: record() for k in ("missing", "skipped", "failed")})
        after = dict(tests={"skipped": record("skipped"), "failed": record("failure")})
        result = compare(after, before)
        self.assertEqual(result["missing"], ["missing"])
        self.assertEqual(result["new_skips"], ["skipped"])
        self.assertEqual(result["new_failures"], ["failed"])

    def test_added_tests_must_execute_successfully(self):
        after = dict(tests={"pass": record(), "skip": record("skipped"), "fail": record("failure")})
        result = compare(after, dict(tests={}))
        self.assertEqual(result["added"], ["fail", "pass", "skip"])
        self.assertEqual(result["new_skips"], ["skip"])
        self.assertEqual(result["new_failures"], ["fail"])
        self.assertTrue(result["requires_review"])


if __name__ == "__main__":
    unittest.main()
