import unittest
from scripts.autonomous_factory_relay import (
    directive_retry_decision as d,
    has_active_work_order,
    parse_quota_reset_seconds,
    record_directive_outcome,
    record_work_order_outcome,
    should_mark_audit_ready,
    work_order_retry_decision,
)


class TestRetryGuard(unittest.TestCase):
    def test_first_run(self):
        self.assertEqual(d({}, "h", "", 1000)[0], "run")

    def test_processed_is_done(self):
        self.assertEqual(d({}, "h", "h", 1000)[0], "done")

    def test_backoff_5_15_45(self):
        for att, wait in ((1, 300), (2, 900), (3 - 1, 900)):
            rs = {"hash": "h", "attempts": att, "last": 1000}
            self.assertEqual(d(rs, "h", "", 1000 + wait - 1)[0], "wait")
            self.assertEqual(d(rs, "h", "", 1000 + wait)[0], "run")

    def test_exhausted_after_3(self):
        rs = {"hash": "h", "attempts": 3, "last": 0}
        self.assertEqual(d(rs, "h", "", 10**9)[0], "exhausted")

    def test_new_hash_resets(self):
        rs = {"hash": "old", "attempts": 3, "last": 10**9, "cooldown_until": 10**10, "l1_done": True}
        dec, new = d(rs, "new", "", 10**9)
        self.assertEqual((dec, new["attempts"], new["cooldown_until"], new["l1_done"]), ("run", 0, 0, False))

    def test_parse_quota_reset_seconds(self):
        err = (
            'error: Individual quota reached. Resets in 1h40m56s.\n'
            'AGY_ERROR: {"short_error":"RESOURCE_EXHAUSTED (code 429)"}'
        )
        self.assertEqual(parse_quota_reset_seconds(err), 3600 + 40 * 60 + 56 + 30)
        self.assertEqual(parse_quota_reset_seconds("RESOURCE_EXHAUSTED (code 429): Resets in 45m"), 45 * 60 + 30)
        self.assertEqual(parse_quota_reset_seconds("RESOURCE_EXHAUSTED (code 429) without timer"), 900)
        self.assertEqual(parse_quota_reset_seconds("HTTP 429 Resets in 10h", max_wait=21600), 21600)
        self.assertIsNone(parse_quota_reset_seconds("Timeout"))
        self.assertIsNone(parse_quota_reset_seconds("Traceback: ValueError"))

    def test_429_does_not_burn_attempt_and_sets_cooldown(self):
        _, rs = d({}, "h", "", 1000)
        err = 'RESOURCE_EXHAUSTED (code 429): Individual quota reached. Resets in 10m0s.'
        kind, wait_s, rs = record_directive_outcome(rs, 1000, ok=False, err_text=err, stage="l1")
        self.assertEqual(kind, "quota_wait")
        self.assertEqual(wait_s, 630)
        self.assertEqual(rs["attempts"], 0)
        self.assertEqual(rs["cooldown_until"], 1630)
        self.assertEqual(d(rs, "h", "", 1629)[0], "wait")
        self.assertEqual(d(rs, "h", "", 1630)[0], "run")

    def test_l1_checkpoint_preserved_when_l2_hits_429(self):
        _, rs = d({}, "h", "", 2000)
        kind1, _, rs = record_directive_outcome(rs, 2010, ok=True, stage="l1")
        self.assertEqual(kind1, "ok")
        self.assertTrue(rs["l1_done"])
        self.assertEqual(rs["attempts"], 0)

        err = 'RESOURCE_EXHAUSTED (code 429): Individual quota reached. Resets in 1h40m56s.'
        kind2, wait_s, rs = record_directive_outcome(rs, 2020, ok=False, err_text=err, stage="l2")
        self.assertEqual(kind2, "quota_wait")
        self.assertEqual(rs["attempts"], 0)
        self.assertTrue(rs["l1_done"])
        self.assertEqual(d(rs, "h", "", 2020 + wait_s - 1)[0], "wait")
        dec, rs_ready = d(rs, "h", "", 2020 + wait_s)
        self.assertEqual(dec, "run")
        self.assertTrue(rs_ready["l1_done"])

    def test_non_quota_failure_counts_attempt(self):
        _, rs = d({}, "h", "", 3000)
        kind, _, rs = record_directive_outcome(rs, 3005, ok=False, err_text="Timeout", stage="l1")
        self.assertEqual(kind, "failed")
        self.assertEqual(rs["attempts"], 1)
        self.assertEqual(rs["cooldown_until"], 0)
        self.assertEqual(d(rs, "h", "", 3005 + 299)[0], "wait")
        self.assertEqual(d(rs, "h", "", 3005 + 300)[0], "run")


class TestMultiTactWorkOrderGuard(unittest.TestCase):
    def test_active_work_order_blocks_audit_ready_overwrite(self):
        active_md = (
            "# STATUS: ACTIVE_AUTONOMOUS_WORK_ORDER — STANDBY СНЯТ (2026-11-24 22:59 UTC)\n\n"
            "- Такт 1 выполнен, в очереди Такты 2–5.\n"
        )
        self.assertTrue(has_active_work_order(active_md))
        self.assertFalse(should_mark_audit_ready(active_md))

    def test_audit_ready_and_closed_states(self):
        audit_md = "# STATUS: AUDIT_READY — 100% МЕХАНИЗМОВ И ПРЕДПИСАНИЙ ЗАВЕРЕНЫ\n"
        self.assertFalse(has_active_work_order(audit_md))
        self.assertFalse(should_mark_audit_ready(audit_md))
        neutral_md = "# Some old report without status header\n"
        self.assertFalse(has_active_work_order(neutral_md))
        self.assertTrue(should_mark_audit_ready(neutral_md))

    def test_work_order_stage_ping_pong_l2_to_l1_to_l2(self):
        dec, ws = work_order_retry_decision({}, 1000)
        self.assertEqual((dec, ws["pending_stage"]), ("run", "l2"))

        kind, _, ws = record_work_order_outcome(ws, 1010, ok=True, stage="l2")
        self.assertEqual((kind, ws["pending_stage"], ws["attempts"]), ("ok", "l1", 0))

        dec, ws = work_order_retry_decision(ws, 1020)
        self.assertEqual((dec, ws["pending_stage"]), ("run", "l1"))

        kind, _, ws = record_work_order_outcome(ws, 1030, ok=True, stage="l1")
        self.assertEqual((kind, ws["pending_stage"], ws["attempts"]), ("ok", "l2", 0))

    def test_work_order_429_preserves_stage_and_attempts(self):
        ws = {"pending_stage": "l2", "attempts": 0, "last": 0, "cooldown_until": 0}
        err = "RESOURCE_EXHAUSTED (code 429): Resets in 5m0s."
        kind, wait_s, ws = record_work_order_outcome(ws, 2000, ok=False, err_text=err, stage="l2")
        self.assertEqual((kind, wait_s, ws["pending_stage"], ws["attempts"]), ("quota_wait", 330, "l2", 0))
        self.assertEqual(work_order_retry_decision(ws, 2329)[0], "wait")
        self.assertEqual(work_order_retry_decision(ws, 2330)[0], "run")

    def test_work_order_non_quota_exhausts_after_3(self):
        ws = {"pending_stage": "l1", "attempts": 2, "last": 1000, "cooldown_until": 0}
        kind, _, ws = record_work_order_outcome(ws, 2000, ok=False, err_text="Timeout", stage="l1")
        self.assertEqual((kind, ws["attempts"], ws["pending_stage"]), ("failed", 3, "l1"))
        self.assertEqual(work_order_retry_decision(ws, 999999)[0], "exhausted")


if __name__ == "__main__":
    unittest.main()
