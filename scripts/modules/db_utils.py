import os
import json
import urllib.request

INTERNAL_API_URL = os.environ.get("INTERNAL_API_URL", "http://localhost:8080/internal/tasks")
INTERNAL_SETTINGS_API_URL = os.environ.get("INTERNAL_SETTINGS_API_URL", "http://localhost:8080/internal/settings")

class Database:
    def _api_call(self, path="", method="GET", data=None):
        url = INTERNAL_API_URL + path
        return self._request(url, method, data)

    def _settings_call(self, path="", method="POST", data=None):
        url = INTERNAL_SETTINGS_API_URL + path
        return self._request(url, method, data)

    def _request(self, url, method="GET", data=None):
        payload = json.dumps(data).encode("utf-8") if data else None

        req = urllib.request.Request(url, data=payload, method=method)
        req.add_header("Content-Type", "application/json")

        try:
            with urllib.request.urlopen(req, timeout=5) as res:
                if res.status == 204 or res.status == 200:
                    content = res.read()
                    return json.loads(content.decode("utf-8")) if content else True
                return None
        except Exception as e:
            print(f"Internal API call failed: {e}")
            return None

    def update_task_linear_id(self, task_id, linear_issue_id):
        self._api_call(f"/{task_id}", method="PATCH", data={"linearIssueId": linear_issue_id})

    def update_task_status_by_linear_id(self, linear_issue_id, status):
        # First find the task id
        task = self._api_call(f"/by-linear-id/{linear_issue_id}")
        if task and task.get('id'):
            self._api_call(f"/{task['id']}", method="PATCH", data={"status": status})

    def get_tasks_for_sync(self):
        tasks = self._api_call()
        return tasks if tasks else []

    def get_task_by_id(self, task_id):
        tasks = self.get_tasks_for_sync()
        for task in tasks:
            if task.get("id") == task_id:
                return task
        return None

    def get_task_metadata(self, task_id):
        return self._api_call(f"/{task_id}/metadata")

    def update_task_metadata(self, task_id, updates):
        return self._api_call(f"/{task_id}/metadata", method="PATCH", data=updates)

    def get_active_claim(self, task_id):
        return self._api_call(f"/{task_id}/active-claim")

    def resolve_setting(self, key):
        return self._settings_call("/resolve", method="POST", data={"key": key})

db = Database()
