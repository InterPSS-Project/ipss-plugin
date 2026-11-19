"""Regression tests for the documentation/new-Java wording policy."""

import unittest
from check_neutral_benchmark_language import is_document, violations


class NeutralBenchmarkLanguageTest(unittest.TestCase):
    def test_all_documentation_is_checked(self):
        phrase = "PSS" + "/E 36.7"
        for path in ("README.md", "docs/benchmark.txt", "module/docs/model.html",
                     "module/testData/README.md", "docs/reference.rst", "docs/test.adoc"):
            with self.subTest(path=path):
                self.assertEqual(1, len(violations(path, phrase, set())))

    def test_existing_java_is_exempt(self):
        self.assertEqual([], violations("Old.java", "PSS" + "/E", {"Old.java"}))

    def test_new_java_is_checked_case_insensitively(self):
        for phrase in ("PSS" + "/E", "pss" + "/e 36.7", "PSS" + "\N{REGISTERED SIGN}E"):
            self.assertEqual(1, len(violations("New.java", phrase, set())))

    def test_compatibility_names_and_neutral_prose_are_accepted(self):
        text = "class PsseReader {} // native DYR benchmark; PSSE_API compatibility"
        self.assertEqual([], violations("PsseReader.java", text, set()))

    def test_data_files_are_not_rewritten_as_documentation(self):
        self.assertFalse(is_document("module/testData/raw-data.txt"))
        self.assertFalse(is_document("reference/model.dyr"))

    def test_line_numbers_are_actionable(self):
        result = violations("docs/test.md", "neutral\nPSS" + "/E\n", set())
        self.assertTrue(result[0].startswith("docs/test.md:2:"))


if __name__ == "__main__":
    unittest.main()
