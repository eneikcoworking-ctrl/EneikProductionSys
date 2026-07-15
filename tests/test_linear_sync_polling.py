import unittest
from unittest.mock import patch, MagicMock
import os
import sys
import json

# Add scripts and modules to path
sys.path.append(os.path.join(os.path.dirname(__file__), '../scripts'))
sys.path.append(os.path.join(os.path.dirname(__file__), '../scripts/modules'))

import linear_sync

class TestLinearSyncPolling(unittest.TestCase):
    @patch('linear_sync.linear_api_call')
    @patch('linear_sync.db.get_tasks_for_sync')
    @patch('linear_sync.db.update_task_linear_id')
    def test_sync_tasks_polling(self, mock_update_id, mock_get_tasks, mock_api_call):
        # Mock tasks list
        mock_get_tasks.return_value = [
            {'id': 'task-1', 'description': 'Task 1', 'status': 'queued', 'linearIssueId': None, 'role': {'tag': 'BARCAN-TAG-02'}},
            {'id': 'task-2', 'description': 'Task 2', 'status': 'done', 'linearIssueId': 'linear-2', 'role': {'tag': 'BARCAN-TAG-02'}}
        ]

        # Mock API responses
        def side_effect(query, variables):
            if 'WorkflowStates' in query:
                return {'team': {'states': {'nodes': [
                    {'id': 'state-backlog', 'name': 'Backlog'},
                    {'id': 'state-done', 'name': 'Done'}
                ]}}}
            if 'issueCreate' in query:
                return {'issueCreate': {'success': True, 'issue': {'id': 'linear-1'}}}
            if 'issueUpdate' in query:
                return {'issueUpdate': {'success': True}}
            return None

        mock_api_call.side_effect = side_effect

        with patch.dict(os.environ, {"LINEAR_API_TOKEN": "test-token", "LINEAR_ENABLED": "true"}):
            linear_sync.process_polling()

        # Verify workflow states fetched
        self.assertTrue(any('WorkflowStates' in str(call) for call in mock_api_call.call_args_list))

        # Verify first task created
        self.assertTrue(any('issueCreate' in str(call) for call in mock_api_call.call_args_list))
        mock_update_id.assert_called_with('task-1', 'linear-1')

        # Verify second task updated to state-done
        update_call = [call for call in mock_api_call.call_args_list if 'issueUpdate' in str(call)][0]
        self.assertIn('state-done', str(update_call))

if __name__ == '__main__':
    unittest.main()
