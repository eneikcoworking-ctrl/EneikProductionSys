import unittest
from scripts.autonomous_factory_relay import directive_retry_decision as d


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
        rs = {"hash": "old", "attempts": 3, "last": 10**9}
        dec, new = d(rs, "new", "", 10**9)
        self.assertEqual((dec, new["attempts"]), ("run", 0))


if __name__ == "__main__":
    unittest.main()
