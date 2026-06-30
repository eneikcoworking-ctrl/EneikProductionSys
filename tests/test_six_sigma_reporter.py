import unittest
from scripts.modules.six_sigma_reporter import calculate_lead_cycle_time, calculate_dpmo

class TestSixSigmaReporter(unittest.TestCase):
    def test_calculate_lead_cycle_time(self):
        issues = [
            {"createdAt": "2026-06-30T10:00:00Z", "completedAt": "2026-06-30T10:00:10Z"}, # 10s
            {"createdAt": "2026-06-30T11:00:00Z", "completedAt": "2026-06-30T11:00:20Z"}, # 20s
        ]
        git_data = [
            {"branch_created_at": "2026-06-30T12:00:00Z", "merged_at": "2026-06-30T12:00:05Z"}, # 5s
        ]

        metrics = calculate_lead_cycle_time(issues, git_data)
        self.assertEqual(metrics["avg_lead_time_seconds"], 15.0)
        self.assertEqual(metrics["avg_cycle_time_seconds"], 5.0)
        self.assertEqual(metrics["total_issues_analyzed"], 2)
        self.assertEqual(metrics["total_prs_analyzed"], 1)

    def test_calculate_dpmo(self):
        # (1 defect / 10 opportunities) * 1,000,000 = 100,000
        dpmo = calculate_dpmo(1, 10)
        self.assertEqual(dpmo, 100000.0)

        # Zero opportunities
        dpmo_zero = calculate_dpmo(5, 0)
        self.assertEqual(dpmo_zero, 0.0)

if __name__ == "__main__":
    unittest.main()
