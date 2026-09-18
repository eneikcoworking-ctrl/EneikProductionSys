import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.mock_test_runner import (
    ROOT_DIR,
    main,
    run_all_assertions,
    verify_greeting_domain_contract,
    verify_privacy_filter_contract,
)


class TestMockTestRunner(unittest.TestCase):
    """Harness for scripts/mock_test_runner.py enforcing ELVIN_GOLDMAN_01_RELIABILITY_CHAIN (D010).

    Verifies that mock_test_runner conducts genuine structural, invariant, and contract
    assertions against the current codebase, eliminating simulated success and false PASS output.
    """

    def test_greeting_domain_contract_real_repo(self):
        result = verify_greeting_domain_contract(ROOT_DIR)
        self.assertEqual(result["status"], "PASSED")
        self.assertEqual(len(result["failures"]), 0)
        self.assertGreaterEqual(len(result["checks_passed"]), 4)

    def test_greeting_domain_contract_missing_file(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            result = verify_greeting_domain_contract(Path(tmpdir))
            self.assertEqual(result["status"], "FAILED")
            self.assertTrue(any("missing" in f.lower() for f in result["failures"]))

    def test_greeting_domain_contract_missing_invariant(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            base = Path(tmpdir)
            model_dir = base / "src" / "main" / "java" / "com" / "eneik" / "production" / "models" / "domain"
            test_dir = base / "src" / "test" / "java" / "com" / "eneik" / "production" / "models" / "domain"
            model_dir.mkdir(parents=True)
            test_dir.mkdir(parents=True)

            # Model lacks validation check
            (model_dir / "Greeting.java").write_text(
                "package com.eneik.production.models.domain;\npublic class Greeting {}\n",
                encoding="utf-8",
            )
            (test_dir / "GreetingTest.java").write_text(
                "@Test void testGreetingValidation() {}\n",
                encoding="utf-8",
            )

            result = verify_greeting_domain_contract(base)
            self.assertEqual(result["status"], "FAILED")
            self.assertTrue(any("invariant" in f.lower() or "empty" in f.lower() for f in result["failures"]))

    def test_privacy_filter_contract_real_repo(self):
        result = verify_privacy_filter_contract(ROOT_DIR)
        self.assertEqual(result["status"], "PASSED")
        self.assertEqual(len(result["failures"]), 0)
        self.assertGreaterEqual(len(result["checks_passed"]), 4)

    def test_privacy_filter_contract_missing_file(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            result = verify_privacy_filter_contract(Path(tmpdir))
            self.assertEqual(result["status"], "FAILED")
            self.assertTrue(any("missing" in f.lower() for f in result["failures"]))

    def test_privacy_filter_contract_missing_pii_masking(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            base = Path(tmpdir)
            filter_dir = base / "src" / "main" / "java" / "com" / "eneik" / "production" / "controllers" / "policy"
            test_dir = base / "src" / "test" / "java" / "com" / "eneik" / "production" / "controllers" / "policy"
            filter_dir.mkdir(parents=True)
            test_dir.mkdir(parents=True)

            # Missing PII masking logic
            (filter_dir / "PrivacyFilter.java").write_text(
                'public class PrivacyFilter { String token = "VALID_KNOWLEDGE_TOKEN"; }\n',
                encoding="utf-8",
            )
            (test_dir / "PrivacyFilterTest.java").write_text(
                "@Test void testMaskData() {}\n",
                encoding="utf-8",
            )

            result = verify_privacy_filter_contract(base)
            self.assertEqual(result["status"], "FAILED")
            self.assertTrue(any("pii" in f.lower() or "mask" in f.lower() for f in result["failures"]))

    def test_run_all_assertions_real_repo(self):
        report = run_all_assertions(ROOT_DIR, run_mvn=False)
        self.assertEqual(report["overall_status"], "PASSED")
        self.assertFalse(report["simulation_mode"])
        self.assertEqual(report["failures_count"], 0)
        self.assertEqual(report["total_targets"], 2)

    def test_run_all_assertions_fails_closed_when_subcheck_fails(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            report = run_all_assertions(Path(tmpdir), run_mvn=False)
            self.assertEqual(report["overall_status"], "FAILED")
            self.assertGreater(report["failures_count"], 0)

    def test_main_cli_json(self):
        with self.assertRaises(SystemExit) as cm:
            main(["--json"])
        self.assertEqual(cm.exception.code, 0)

    def test_main_cli_text_output(self):
        with self.assertRaises(SystemExit) as cm:
            main([])
        self.assertEqual(cm.exception.code, 0)


if __name__ == "__main__":
    unittest.main()
