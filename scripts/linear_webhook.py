import json
from http.server import BaseHTTPRequestHandler, HTTPServer
import sys
import os

# Ensure modules can be imported
sys.path.append(os.path.join(os.path.dirname(__file__), 'modules'))
from db_utils import db

def map_linear_status_to_internal(status: str) -> str:
    mapping = {
        'Backlog': 'queued',
        'In Progress': 'in_progress',
        'In Review': 'review',
        'Done': 'done',
        'Canceled': 'failed'
    }
    return mapping.get(status, 'queued')

class WebhookHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path == '/webhooks/linear':
            try:
                content_length = int(self.headers['Content-Length'])
                post_data = self.rfile.read(content_length)
                payload = json.loads(post_data.decode('utf-8'))

                print(f"Received Linear Webhook: {payload.get('type')} {payload.get('action')}")

                if payload.get('type') == 'Issue' and payload.get('action') in ['update', 'create']:
                    issue_id = payload['data']['id']

                    # Update status if state changed
                    state = payload['data'].get('state', {})
                    new_status_name = state.get('name')
                    if new_status_name:
                        internal_status = map_linear_status_to_internal(new_status_name)
                        print(f"Updating task for issue {issue_id} to status {internal_status}")
                        db.update_task_status_by_linear_id(issue_id, internal_status)

                self.send_response(200)
                self.end_headers()
                self.wfile.write(b'OK')
            except Exception as e:
                print(f"Error processing webhook: {e}")
                self.send_response(500)
                self.end_headers()
        else:
            self.send_response(404)
            self.end_headers()

def run(server_class=HTTPServer, handler_class=WebhookHandler, port=8001):
    server_address = ('', port)
    httpd = server_class(server_address, handler_class)
    print(f"Starting linear webhook server on port {port}...")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    httpd.server_close()

if __name__ == '__main__':
    port = int(os.environ.get("WEBHOOK_PORT", 8001))
    run(port=port)
