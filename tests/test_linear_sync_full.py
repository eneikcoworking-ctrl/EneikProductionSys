import unittest
from unittest.mock import patch, MagicMock
import os
import sys
import json
import uuid

# Add scripts and modules to path
sys.path.append(os.path.join(os.path.dirname(__file__), '../scripts'))
sys.path.append(os.path.join(os.path.dirname(__file__), '../scripts/modules'))

import linear_sync

class TestLinearSyncFull(unittest.TestCase):
    @patch('linear_sync.linear_api_call')
    @patch('linear_sync.db.get_tasks_for_sync')
    @patch('linear_sync.db.update_task_linear_id')
    @patch('linear_sync.db.get_task_metadata')
    @patch('linear_sync.db.get_active_claim')
    @patch('linear_sync.db.get_task_by_id')
    def test_sync_tasks_full(self, mock_get_task_by_id, mock_get_active_claim, mock_get_metadata, mock_update_id, mock_get_tasks, mock_api_call):
        task_id = str(uuid.uuid4())
        blocker_id = str(uuid.uuid4())

        # Mock tasks list
        mock_get_tasks.return_value = [
            {
                'id': task_id,
                'description': 'Main Task',
                'status': 'in_progress',
                'linearIssueId': None,
                'role': {'tag': 'BARCAN-TAG-02'}
            }
        ]

        mock_get_metadata.return_value = {
            'dodText': 'Clean code, tests pass',
            'prUrl': 'https://github.com/pr/1',
            'blockers': blocker_id
        }

        mock_get_active_claim.return_value = {
            'account': {'name': 'ACC-01'}
        }

        mock_get_task_by_id.return_value = {
            'id': blocker_id,
            'linearIssueId': 'linear-blocker-1'
        }

        # Mock API responses
        def side_effect(query, variables):
            if 'WorkflowStates' in query:
                return {'team': {'states': {'nodes': [
                    {'id': 'state-ip', 'name': 'In Progress (MAX 2))'}
                ]}}}
            if 'Labels' in query:
                return {'team': {'labels': {'nodes': []}}}
            if 'LabelCreate' in query:
                return {'issueLabelCreate': {'success': True, 'issueLabel': {'id': 'label-' + variables['input']['name']}}}
            if 'issueCreate' in query:
                return {'issueCreate': {'success': True, 'issue': {'id': 'linear-main'}}}
            if 'AttachmentCreate' in query:
                return {'attachmentCreate': {'success': True}}
            if 'IssueRelationCreate' in query:
                return {'issueRelationCreate': {'success': True}}
            return None

        mock_api_call.side_effect = side_effect

        with patch.dict(os.environ, {"LINEAR_API_TOKEN": "test-token", "LINEAR_ENABLED": "true"}):
            linear_sync.process_polling()

        # Check if issueCreate was called with correct 6 fields
        create_call = [call for call in mock_api_call.call_args_list if 'issueCreate' in str(call)][0]
        variables = create_call[0][1]
        input_data = variables['input']

        # 1. Status (stateId)
        self.assertEqual(input_data['stateId'], 'state-ip')
        # 2. Role (labelId)
        self.assertIn('label-BARCAN-TAG-02', input_data['labelIds'])
        # 3. Assignee (labelId with ACC: prefix)
        self.assertIn('label-ACC:ACC-01', input_data['labelIds'])
        # 4. DoD (in description)
        self.assertIn('Definition of Done', input_data['description'])
        self.assertIn('Clean code, tests pass', input_data['description'])

        # 5. PR (AttachmentCreate)
        pr_call = [call for call in mock_api_call.call_args_list if 'AttachmentCreate' in str(call)][0]
        self.assertEqual(pr_call[0][1]['input']['url'], 'https://github.com/pr/1')

        # 6. Blockers (IssueRelationCreate)
        rel_call = [call for call in mock_api_call.call_args_list if 'IssueRelationCreate' in str(call)][0]
        self.assertEqual(rel_call[0][1]['input']['issueId'], 'linear-blocker-1')
        self.assertEqual(rel_call[0][1]['input']['relatedIssueId'], 'linear-main')

if __name__ == '__main__':
    unittest.main()
