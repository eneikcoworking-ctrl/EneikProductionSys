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
    from modules.db_utils import db
    from modules.linear_issue_mapper import LinearIssueMapper
except ImportError:
    # Handle the case where the script is run from a different directory
    sys.path.append(os.path.join(os.path.dirname(__file__), 'modules'))
    from six_sigma_reporter import calculate_lead_cycle_time, calculate_dpmo, generate_markdown_report
    from db_utils import db
    from linear_issue_mapper import LinearIssueMapper

ERROR_LOG = 'docs/metrics/sync_errors.log'
REPORT_FILE = 'docs/metrics/report_latest.md'
TEAM_ID = os.environ.get("LINEAR_TEAM_ID", "TEAM_ID_PLACEHOLDER")

def log_error(message):
    try:
        os.makedirs(os.path.dirname(ERROR_LOG), exist_ok=True)
        with open(ERROR_LOG, 'a') as f:
            timestamp = datetime.datetime.now(datetime.timezone.utc).isoformat()
            f.write(f"{timestamp}: {message}\n")
    except Exception as e:
        print(f"Failed to log error: {e}")

def linear_api_call(query, variables=None):
    token = os.environ.get("LINEAR_API_TOKEN")
    if not token:
        return None

    url = "https://api.linear.app/graphql"
    payload = json.dumps({"query": query, "variables": variables or {}}).encode("utf-8")

    req = urllib.request.Request(url, data=payload, method="POST")
    req.add_header("Content-Type", "application/json")
    auth_header = token if token.startswith("Bearer ") else f"Bearer {token}"
    req.add_header("Authorization", auth_header)

    try:
        with urllib.request.urlopen(req, timeout=10) as res:
            response = json.loads(res.read().decode("utf-8"))
            if "errors" in response:
                log_error(f"GraphQL Errors: {json.dumps(response['errors'])}")
                return None
            return response.get("data")
    except Exception as e:
        log_error(f"Linear API Request failed: {str(e)}")
        return None

def fetch_workflow_states(team_id):
    query = """
    query WorkflowStates($teamId: ID!) {
      team(id: $teamId) {
        states {
          nodes {
            id
            name
          }
        }
      }
    }
    """
    data = linear_api_call(query, {"teamId": team_id})
    if data and data.get('team'):
        return {state['name']: state['id'] for state in data['team']['states']['nodes']}
    return {}

def map_status_to_linear_name(status: str) -> str:
    return {
        'queued': 'Backlog',
        'claimed': 'In Progress',
        'in_progress': 'In Progress',
        'review': 'In Review',
        'done': 'Done',
        'failed': 'Backlog',
    }.get(status, 'Backlog')

def sync_task_to_linear(task, state_mapping, mapper: LinearIssueMapper):
    linear_issue_id = task.get('linearIssueId')
    metadata = db.get_task_metadata(task['id']) or {}

    # 1. Prepare fields
    status_name = map_status_to_linear_name(task['status'])
    state_id = state_mapping.get(status_name)
    if not state_id:
        print(f"Could not find state ID for status {status_name}. Skipping task {task['id']}")
        return

    role_label_id = mapper.map_role_to_label(task['role']['tag'])

    labels = [role_label_id]

    # Assignee logic
    active_claim = db.get_active_claim(task['id'])
    if active_claim:
        account_name = active_claim.get('account', {}).get('name')
        if account_name:
            assignee_label_id = mapper.map_assignee_to_label(account_name)
            labels.append(assignee_label_id)

    # DoD in description
    dod_text = metadata.get('dodText', '')
    description = task['description']
    if dod_text:
        description += f"\n\n### Definition of Done\n{dod_text}"

    if not linear_issue_id:
        query = """
        mutation IssueCreate($input: IssueCreateInput!) {
          issueCreate(input: $input) {
            success
            issue {
              id
            }
          }
        }
        """
        variables = {
            "input": {
                "title": task['description'],
                "description": description,
                "teamId": TEAM_ID,
                "stateId": state_id,
                "labelIds": [l for l in labels if l]
            }
        }
        data = linear_api_call(query, variables)
        if data and data.get('issueCreate', {}).get('success'):
            linear_issue_id = data['issueCreate']['issue']['id']
            db.update_task_linear_id(task['id'], linear_issue_id)
            print(f"Created Linear issue {linear_issue_id} for task {task['id']}")
    else:
        query = """
        mutation IssueUpdate($id: ID!, $input: IssueUpdateInput!) {
          issueUpdate(id: $id, input: $input) {
            success
          }
        }
        """
        variables = {
            "id": linear_issue_id,
            "input": {
                "stateId": state_id,
                "description": description,
                "labelIds": [l for l in labels if l]
            }
        }
        data = linear_api_call(query, variables)
        if data and data.get('issueUpdate', {}).get('success'):
            print(f"Updated Linear issue {linear_issue_id} for task {task['id']}")

    # PR and Blockers (Relations/Attachments)
    if linear_issue_id:
        # PR
        pr_url = metadata.get('prUrl')
        if pr_url:
            mapper.sync_pr_link(linear_issue_id, pr_url)

        # Blockers
        blockers_str = metadata.get('blockers')
        if blockers_str:
            blocker_task_ids = [b.strip() for b in blockers_str.split(',') if b.strip()]
            blocker_linear_ids = []
            for b_id in blocker_task_ids:
                b_task = db.get_task_by_id(b_id)
                if b_task and b_task.get('linearIssueId'):
                    blocker_linear_ids.append(b_task['linearIssueId'])

            if blocker_linear_ids:
                mapper.sync_blockers(linear_issue_id, blocker_linear_ids)

def process_polling():
    if os.environ.get("LINEAR_ENABLED") != "true":
        print("Linear sync is disabled (LINEAR_ENABLED != true). Skipping.")
        return

    state_mapping = fetch_workflow_states(TEAM_ID)
    if not state_mapping and os.environ.get("LINEAR_API_TOKEN"):
        log_error("Could not fetch workflow states from Linear.")
        return

    mapper = LinearIssueMapper(linear_api_call, TEAM_ID)

    tasks = db.get_tasks_for_sync()
    for task in tasks:
        try:
            sync_task_to_linear(task, state_mapping, mapper)
        except Exception as e:
            log_error(f"Failed to sync task {task.get('id')}: {str(e)}")
            import traceback
            traceback.print_exc()

def fetch_issues(team_id):
    query = """
    query Issues($teamId: ID!) {
      issues(filter: { team: { id: { eq: $teamId } } }) {
        nodes {
          id
          createdAt
          completedAt
          state { name }
        }
      }
    }
    """
    data = linear_api_call(query, {"teamId": team_id})
    if data:
        return data.get("issues", {}).get("nodes", [])
    return []

def parse_git_log():
    git_data = []
    try:
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
    process_polling()

    issues = fetch_issues(TEAM_ID)
    git_data = parse_git_log()
    metrics = calculate_lead_cycle_time(issues, git_data)

    blocked_count = 0
    try:
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
