import datetime

def calculate_lead_cycle_time(issues: list[dict], git_data: list[dict]) -> dict:
    """
    Calculates average Lead Time and Cycle Time.
    Lead Time: Time from issue creation to closure.
    Cycle Time: Time from work start (or branch creation) to merge.
    """
    lead_times = []
    for issue in issues:
        created_at = issue.get('createdAt')
        completed_at = issue.get('completedAt')
        if created_at and completed_at:
            try:
                # Assuming ISO 8601 format: 2026-06-30T10:00:00Z
                start = datetime.datetime.fromisoformat(created_at.replace('Z', '+00:00'))
                end = datetime.datetime.fromisoformat(completed_at.replace('Z', '+00:00'))
                lead_times.append((end - start).total_seconds())
            except ValueError:
                continue

    cycle_times = []
    for entry in git_data:
        # Expected git_data entry: {'branch_created_at': iso_str, 'merged_at': iso_str}
        start_str = entry.get('branch_created_at')
        end_str = entry.get('merged_at')
        if start_str and end_str:
            try:
                start = datetime.datetime.fromisoformat(start_str.replace('Z', '+00:00'))
                end = datetime.datetime.fromisoformat(end_str.replace('Z', '+00:00'))
                cycle_times.append((end - start).total_seconds())
            except ValueError:
                continue

    avg_lead_time = sum(lead_times) / len(lead_times) if lead_times else 0
    avg_cycle_time = sum(cycle_times) / len(cycle_times) if cycle_times else 0

    return {
        "avg_lead_time_seconds": avg_lead_time,
        "avg_cycle_time_seconds": avg_cycle_time,
        "total_issues_analyzed": len(lead_times),
        "total_prs_analyzed": len(cycle_times)
    }

def calculate_dpmo(blocked_prs: int, total_prs: int) -> float:
    """
    Calculates Defects Per Million Opportunities (DPMO).
    Formula: (defects / opportunities) * 1,000,000
    """
    if total_prs == 0:
        return 0.0
    return (blocked_prs / total_prs) * 1_000_000

def generate_markdown_report(metrics: dict) -> str:
    """
    Generates a Markdown report from metrics.
    """
    now = datetime.datetime.now(datetime.timezone.utc).isoformat()

    report = f"# Six Sigma Production Report\n\n"
    report += f"**Generated At:** {now}\n\n"
    report += "## Key Performance Indicators (KPIs)\n\n"
    report += "| Metric | Value |\n"
    report += "| :--- | :--- |\n"
    report += f"| Avg Lead Time | {metrics.get('avg_lead_time_seconds', 0):.2f}s |\n"
    report += f"| Avg Cycle Time | {metrics.get('avg_cycle_time_seconds', 0):.2f}s |\n"
    report += f"| DPMO | {metrics.get('dpmo', 0):.2f} |\n"
    report += f"| Issues Analyzed | {metrics.get('total_issues_analyzed', 0)} |\n"
    report += f"| PRs Analyzed | {metrics.get('total_prs_analyzed', 0)} |\n"

    return report
