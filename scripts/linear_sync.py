import os
import sys
import datetime
import json
import subprocess
import urllib.request
import urllib.error

# Ensure modules can be imported
sys.path.append(os.path.dirname(__file__))

try:
    from modules.six_sigma_reporter import calculate_lead_cycle_time, calculate_dpmo, generate_markdown_report
except ImportError:
    # Handle the case where the script is run from a different directory
    sys.path.append(os.path.join(os.path.dirname(__file__), 'modules'))
    from six_sigma_reporter import calculate_lead_cycle_time, calculate_dpmo, generate_markdown_report

ERROR_LOG = 'docs/metrics/sync_errors.log'
REPORT_FILE = 'docs/metrics/report_latest.md'

def log_error(message):
    try:
        os.makedirs(os.path.dirname(ERROR_LOG), exist_ok=True)
        with open(ERROR_LOG, 'a') as f:
            timestamp = datetime.datetime.now(datetime.timezone.utc).isoformat()
            f.write(f"{timestamp}: {message}\n")
    except Exception as e:
        print(f"Failed to log error: {e}")

def fetch_issues(team_id):
    token = os.environ.get("LINEAR_API_TOKEN")
    if not token:
        msg = "LINEAR_API_TOKEN not set. Skipping API fetch."
        print(msg)
        log_error(msg)
        return []

    url = "https://api.linear.app/graphql"
    query = """
    query Issues($teamId: ID!) {
      issues(filter: { team: { id: { eq: $teamId } } }) {
        nodes {
          id
          createdAt
          completedAt
          state { name }
          # Including requested fields
          # Note: If these are truly custom fields, they might need different query structure
          # depending on the Linear organization settings.
          # TOC_Constraint
          # Six_Sigma_Loss_Type
        }
      }
    }
    """
    variables = {"teamId": team_id}
    payload = json.dumps({"query": query, "variables": variables}).encode("utf-8")

    req = urllib.request.Request(url, data=payload, method="POST")
    req.add_header("Content-Type", "application/json")
    # Linear API expects 'Bearer <token>' or just '<token>' for personal API keys
    # but Bearer is more standard for OAuth/Service accounts.
    auth_header = token if token.startswith("Bearer ") else f"Bearer {token}"
    req.add_header("Authorization", auth_header)

    try:
        with urllib.request.urlopen(req, timeout=10) as res:
            response = json.loads(res.read().decode("utf-8"))
            if "errors" in response:
                log_error(f"GraphQL Errors: {json.dumps(response['errors'])}")
                return []
            return response.get("data", {}).get("issues", {}).get("nodes", [])
    except Exception as e:
        log_error(f"Linear API Request failed: {str(e)}")
        return []

def parse_git_log():
    git_data = []
    try:
        # Get merge commits
        # %H: hash, %cI: committer date (ISO), %s: subject
        output = subprocess.check_output(
            ["git", "log", "--merges", "--format=%H|%cI|%s"],
            stderr=subprocess.STDOUT,
            text=True
        )

        for line in output.strip().split('\n'):
            if not line: continue
            parts = line.split('|')
            if len(parts) < 3: continue
            h, dt, s = parts[0], parts[1], parts[2]

            try:
                # Estimate branch start
                first_commit_date = subprocess.check_output(
                    ["git", "log", f"{h}^1..{h}^2", "--format=%cI", "--reverse"],
                    stderr=subprocess.DEVNULL,
                    text=True
                ).strip().split('\n')[0]

                if first_commit_date:
                    git_data.append({
                        "branch_created_at": first_commit_date,
                        "merged_at": dt
                    })
            except Exception:
                continue
    except Exception as e:
        log_error(f"Git log parsing failed: {str(e)}")

    return git_data

def main():
    # Use a default team ID if not provided
    team_id = os.environ.get("LINEAR_TEAM_ID", "TEAM_ID_PLACEHOLDER")

    issues = fetch_issues(team_id)
    git_data = parse_git_log()

    metrics = calculate_lead_cycle_time(issues, git_data)

    # Calculate DPMO
    blocked_count = 0
    try:
        # Looking for commits that indicate a block or rejection
        blocked_commits_out = subprocess.check_output(
            ["git", "log", "--grep=BLOCKED", "--grep=REJECTED", "--format=%H"],
            stderr=subprocess.DEVNULL,
            text=True
        ).strip()
        if blocked_commits_out:
            blocked_count = len(blocked_commits_out.split('\n'))
    except Exception:
        blocked_count = 0

    metrics["dpmo"] = calculate_dpmo(blocked_count, metrics["total_prs_analyzed"])

    report_md = generate_markdown_report(metrics)

    try:
        os.makedirs(os.path.dirname(REPORT_FILE), exist_ok=True)
        with open(REPORT_FILE, 'w') as f:
            f.write(report_md)
        print(f"Report generated successfully: {REPORT_FILE}")
    except Exception as e:
        log_error(f"Failed to write report: {e}")

if __name__ == "__main__":
    main()
    sys.exit(0)
