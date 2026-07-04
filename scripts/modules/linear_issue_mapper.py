import json
import urllib.request
import os

class LinearIssueMapper:
    def __init__(self, api_call_func, team_id):
        self.api_call = api_call_func
        self.team_id = team_id
        self.label_cache = {}

    def get_or_create_label(self, label_name):
        if label_name in self.label_cache:
            return self.label_cache[label_name]

        # Check if label exists
        query = """
        query Labels($teamId: String!) {
          team(id: $teamId) {
            labels {
              nodes {
                id
                name
              }
            }
          }
        }
        """
        data = self.api_call(query, {"teamId": self.team_id})
        if data and data.get('team'):
            for label in data['team']['labels']['nodes']:
                self.label_cache[label['name']] = label['id']
                if label['name'] == label_name:
                    return label['id']

        # Create label if not found
        query = """
        mutation LabelCreate($input: IssueLabelCreateInput!) {
          issueLabelCreate(input: $input) {
            success
            issueLabel {
              id
            }
          }
        }
        """
        variables = {
            "input": {
                "name": label_name,
                "teamId": self.team_id
            }
        }
        data = self.api_call(query, variables)
        if data and data.get('issueLabelCreate', {}).get('success'):
            label_id = data['issueLabelCreate']['issueLabel']['id']
            self.label_cache[label_name] = label_id
            return label_id

        return None

    def map_role_to_label(self, tag):
        # BARCAN-TAG-XX
        return self.get_or_create_label(tag)

    def map_assignee_to_label(self, account_name):
        # Account name as label
        return self.get_or_create_label(f"ACC:{account_name}")

    def sync_pr_link(self, issue_id, pr_url):
        if not pr_url:
            return

        query = """
        mutation AttachmentCreate($input: AttachmentCreateInput!) {
          attachmentCreate(input: $input) {
            success
          }
        }
        """
        variables = {
            "input": {
                "issueId": issue_id,
                "title": "Pull Request",
                "url": pr_url
            }
        }
        self.api_call(query, variables)

    def sync_blockers(self, issue_id, blocker_issue_ids):
        if not blocker_issue_ids:
            return

        for blocker_id in blocker_issue_ids:
            query = """
            mutation IssueRelationCreate($input: IssueRelationCreateInput!) {
              issueRelationCreate(input: $input) {
                success
              }
            }
            """
            variables = {
                "input": {
                    "issueId": issue_id,
                    "relatedIssueId": blocker_id,
                    "type": "blocks"
                }
            }
            # Note: Linear relation "blocks" means issueId blocks relatedIssueId.
            # We want blocker_id to block issue_id.
            variables["input"]["issueId"] = blocker_id
            variables["input"]["relatedIssueId"] = issue_id

            self.api_call(query, variables)
