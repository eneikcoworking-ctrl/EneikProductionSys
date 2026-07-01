import unittest
from unittest.mock import patch, MagicMock
import os
import sys
import json
from http.server import HTTPServer
import threading
import urllib.request

# Add scripts and modules to path
sys.path.append(os.path.join(os.path.dirname(__file__), '../scripts'))
sys.path.append(os.path.join(os.path.dirname(__file__), '../scripts/modules'))

import linear_webhook
from db_utils import db

class TestLinearWebhook(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Start server in a background thread
        cls.port = 8002
        cls.server = HTTPServer(('127.0.0.1', cls.port), linear_webhook.WebhookHandler)
        cls.thread = threading.Thread(target=cls.server.serve_forever)
        cls.thread.daemon = True
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()

    @patch('linear_webhook.db.update_task_status_by_linear_id')
    def test_webhook_post(self, mock_update_status):
        payload = {
            'type': 'Issue',
            'action': 'update',
            'data': {
                'id': 'linear-123',
                'state': {'name': 'Done'}
            }
        }
        data = json.dumps(payload).encode('utf-8')

        req = urllib.request.Request(f'http://127.0.0.1:{self.port}/webhooks/linear', data=data, method='POST')
        req.add_header('Content-Type', 'application/json')

        with urllib.request.urlopen(req) as res:
            self.assertEqual(res.status, 200)
            self.assertEqual(res.read(), b'OK')

        mock_update_status.assert_called_with('linear-123', 'done')

if __name__ == '__main__':
    unittest.main()
