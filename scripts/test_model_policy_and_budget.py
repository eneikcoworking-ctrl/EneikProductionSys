#!/usr/bin/env python3
"""Falsification tests for Eneik model routing policy, budget circuit breaker, and DeepSeek->AGY failover."""

import importlib.util
import os
import py_compile
import tempfile
import time
import unittest
from pathlib import Path
from unittest import mock

SCRIPTS_DIR = Path("/opt/EneikProductionSys/scripts")


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, str(path))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


class ModelPolicyAndBudgetTest(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.TemporaryDirectory()
        os.environ["ENEIK_BUDGET_FILE"] = str(Path(self.tmpdir.name) / "budget.json")
        os.environ["ENEIK_BUDGET_LOCK"] = str(Path(self.tmpdir.name) / "budget.lock")
        self.bg = load_module("eneik_budget_guard_test", SCRIPTS_DIR / "eneik_budget_guard.py")

    def tearDown(self):
        self.tmpdir.cleanup()

    def test_deepseek_402_triggers_cooldown_and_allows_bounded_agy_reserve(self):
        self.assertTrue(self.bg.is_deepseek_balance_error("Insufficient Balance", 402))
        self.assertTrue(self.bg.is_deepseek_balance_error("credit balance is too low", 400))
        self.bg.mark_deepseek_exhausted("HTTP 402 Insufficient Balance")
        ok, reason = self.bg.can_use_deepseek(check_remote_balance=False)
        self.assertFalse(ok)
        self.assertIn("cooldown", reason)

        # Bounded AGY worker fallback allows up to 10 calls/day, then blocks
        for _ in range(10):
            a_ok, _ = self.bg.can_use_agy(source="sovereign_bridge", is_worker_fallback=True)
            self.assertTrue(a_ok)
            self.bg.record_agy_call(source="sovereign_bridge", is_worker_fallback=True)
        a_ok, a_reason = self.bg.can_use_agy(source="sovereign_bridge", is_worker_fallback=True)
        self.assertFalse(a_ok)
        self.assertIn("agy_worker_fallback_limit", a_reason)

        # Ops console retains priority even when background daily cap is reached
        ops_ok, ops_reason = self.bg.can_use_agy(source="ops_console")
        self.assertTrue(ops_ok)
        self.assertEqual(ops_reason, "ops_console_priority")

    def test_relay_cooldown_daily_cap_and_pause(self):
        t0 = 1_800_000_000.0
        dec, _, _ = self.bg.check_relay_budget(now=t0)
        self.assertEqual(dec, "run")

        self.bg.record_relay_tick(source="factory_relay:remotecli2", now=t0)
        dec2, wait2, _ = self.bg.check_relay_budget(now=t0 + 60)
        self.assertEqual(dec2, "cooldown")
        self.assertGreater(wait2, 1700)

        # After 30m cooldown, next tick is allowed
        dec3, _, _ = self.bg.check_relay_budget(now=t0 + 1805)
        self.assertEqual(dec3, "run")

        # Hit daily cap (6 ticks total)
        for i in range(1, 6):
            self.bg.record_relay_tick(source="factory_relay:remotecli", now=t0 + i * 2000)
        dec_cap, _, detail_cap = self.bg.check_relay_budget(now=t0 + 20000)
        self.assertEqual(dec_cap, "daily_cap")
        self.assertIn("relay_daily_cap", detail_cap)

        # Operator pause takes precedence
        self.bg.set_factory_paused(True, "operator_budget_hold")
        dec_p, _, detail_p = self.bg.check_relay_budget(now=t0 + 25000)
        self.assertEqual(dec_p, "paused")
        self.assertIn("operator_budget_hold", detail_p)

    def test_bridge_deepseek_model_routing_and_normalization(self):
        bridge = load_module("sovereign_host_bridge_test", SCRIPTS_DIR / "sovereign_host_bridge.py")
        self.assertEqual(bridge.select_deepseek_models("deepseek-flash", {}), ["deepseek-flash", "deepseek-v4-pro"])
        self.assertEqual(bridge.select_deepseek_models("deepseek-flash", {"attempt": 2}), ["deepseek-v4-pro", "deepseek-flash"])
        self.assertEqual(bridge.select_deepseek_models("gemini-4-neon-high", {}), ["deepseek-v4-pro", "deepseek-flash"])
        self.assertEqual(bridge.normalize_model_name("gemini-4-neon-high"), "Gemini 4 Neon (Medium)")
        self.assertEqual(bridge.normalize_model_name("gemini-4-hydrogen-high"), "Gemini 4 Hydrogen (Medium)")

    def test_deepseek_token_usage_pricing_breakdown(self):
        # Exact usage breakdown test
        usage = {
            "prompt_tokens": 10000,
            "prompt_cache_hit_tokens": 8000,
            "prompt_cache_miss_tokens": 2000,
            "completion_tokens": 500,
            "total_tokens": 10500,
        }
        # 8000 * 0.07/1M + 2000 * 0.27/1M + 500 * 1.10/1M = 0.00056 + 0.00054 + 0.00055 = 0.00165 USD
        usd = self.bg.record_deepseek_usage("deepseek-v4-pro", usage=usage)
        self.assertAlmostEqual(usd, 0.00165, places=5)
        st = self.bg.get_status()
        self.assertAlmostEqual(st["deepseek_usd_est"], 0.00165, places=5)

        # Legacy total_tokens call without artificial 500-token inflation
        usd2 = self.bg.record_deepseek_usage("deepseek-flash", total_tokens=100)
        self.assertAlmostEqual(usd2, 0.00002, places=6)

    def test_agy_account_limit_marking_and_status(self):
        self.bg.mark_agy_limit("account_primary", "429 RESOURCE_EXHAUSTED", cooldown_sec=1800)
        st = self.bg.get_agy_account_status("account_primary")
        self.assertEqual(st["status"], "exhausted")
        self.assertIn("429", st["last_error"])
        self.assertGreater(st["rem_sec"], 1700)
        self.assertEqual(st["limit_type"], "5h")

        # Test weekly quota parsing from CLI error string
        err_weekly = "Individual quota reached. Please upgrade your subscription to increase your limits. Resets in 124h11m18s.: RESOURCE_EXHAUSTED (code 429)"
        self.bg.mark_agy_limit("account_primary", err_weekly)
        st_w = self.bg.get_agy_account_status("account_primary")
        self.assertEqual(st_w["status"], "exhausted")
        self.assertEqual(st_w["limit_type"], "weekly")
        self.assertGreater(st_w["rem_sec"], 440000)

        st2 = self.bg.get_agy_account_status("account_secondary")
        self.assertEqual(st2["status"], "ok")
        self.assertEqual(st2["rem_sec"], 0)

    def test_all_modified_scripts_compile_and_honor_policy(self):
        files = [
            SCRIPTS_DIR / "eneik_budget_guard.py",
            SCRIPTS_DIR / "sovereign_host_bridge.py",
            SCRIPTS_DIR / "autonomous_factory_relay.py",
            Path("/opt/client-runtimes/knowledge-base-crie/scripts/crie_daily_ux_engineer.py"),
            Path("/opt/site-localizer/site_localizer.py"),
            Path("/opt/sovereign-ops/server.py"),
        ]
        for f in files:
            py_compile.compile(str(f), doraise=True)

        crie_text = Path("/opt/client-runtimes/knowledge-base-crie/scripts/crie_daily_ux_engineer.py").read_text(encoding="utf-8")
        self.assertIn('"deepseek-flash"', crie_text)
        self.assertNotIn('"deepseek-chat"', crie_text)

        ops_text = Path("/opt/sovereign-ops/server.py").read_text(encoding="utf-8")
        self.assertTrue('DEFAULT_MODEL = "gemini-4-neon-medium"' in ops_text or 'DEFAULT_MODEL = "gemini-3.8-flash-medium"' in ops_text)
        self.assertIn('"deepseek-v4-pro"', ops_text)
        self.assertNotIn('"deepseek-chat"', ops_text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
