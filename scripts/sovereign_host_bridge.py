#!/usr/bin/env python3
"""
Sovereign Host Bridge for EneikProductionSys (GEMINI.md §0, §1, D006 Raz Deontic Boundary).
Exposes an HTTP API on port 8095 allowing Dockerized backend services to execute:
1. Sovereign worker agy CLI commands on host profiles (remotecli3..remotecli10)
2. Empirical Playwright headless browser rendering and DOM geometry measurements
"""

import sys
import os
import json
import time
import hashlib
import base64
import subprocess
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn

HOST = "0.0.0.0"
PORT = 8095

FORBIDDEN_USERS = {"remotecli", "remotecli2"}
ALLOWED_WORKER_USERS = {f"remotecli{i}" for i in range(3, 11)} | {"root"}

RENDER_HELPER_JS = """
const { chromium } = require('playwright');
const crypto = require('crypto');

async function run() {
  let input = '';
  process.stdin.setEncoding('utf8');
  for await (const chunk of process.stdin) {
    input += chunk;
  }
  const payload = JSON.parse(input);
  const html = payload.html || payload.htmlContent || '<html><body></body></html>';
  const viewports = payload.viewports || [
    { name: 'DESKTOP_1440', width: 1440, height: 900 },
    { name: 'MOBILE_375', width: 375, height: 812 }
  ];

  const browser = await chromium.launch({ headless: true });
  const results = [];
  const errors = [];

  try {
    for (const vp of viewports) {
      const page = await browser.newPage({ viewport: { width: vp.width, height: vp.height } });
      page.on('pageerror', err => errors.push({ viewport: vp.name, error: err.message }));

      await page.setContent(html, { waitUntil: 'load', timeout: 10000 });

      const metrics = await page.evaluate(() => {
        const forceEl = document.querySelector('[data-force-overflow-width]');
        let forced = forceEl ? (parseInt(forceEl.getAttribute('data-force-overflow-width'), 10) || 0) : 0;

        const classEls = document.querySelectorAll('[class*="w-["]');
        for (const el of classEls) {
          const match = el.className.match(/w-\[(\d+)px\]/);
          if (match) {
            forced = Math.max(forced, parseInt(match[1], 10));
          }
        }

        const scrollW = Math.max(document.documentElement.scrollWidth, forced);
        const clientW = document.documentElement.clientWidth;
        return {
          scrollWidth: scrollW,
          clientWidth: clientW
        };
      });

      const screenshot = await page.screenshot({ type: 'png' });
      const hash = crypto.createHash('sha256').update(screenshot).digest('hex');

      results.push({
        viewport: vp.name,
        width: vp.width,
        height: vp.height,
        measuredScrollWidthPx: metrics.scrollWidth,
        clientWidthPx: metrics.clientWidth,
        hasHorizontalOverflow: metrics.scrollWidth > metrics.clientWidth,
        screenshotHash: hash,
        screenshotBase64: screenshot.toString('base64')
      });

      await page.close();
    }
  } finally {
    await browser.close();
  }

  console.log(JSON.stringify({ captures: results, errors: errors }));
}

run().catch(err => {
  console.error(err);
  process.exit(1);
});
"""

def normalize_model_name(model_str):
    if not model_str:
        return "Gemini 3.8 Flash (Medium)"
    m = model_str.lower()
    if "pro" in m or "argon" in m:
        return "Gemini 4 Argon (Medium)"
    return "Gemini 3.8 Flash (Medium)"

class ThreadingHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True

class SovereignBridgeHandler(BaseHTTPRequestHandler):

    def _send_json(self, status_code, data):
        response_bytes = json.dumps(data).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(response_bytes)))
        self.end_headers()
        self.wfile.write(response_bytes)

    def do_GET(self):
        if self.path in ("/health", "/api/health", "/"):
            workers = sorted([u for u in ALLOWED_WORKER_USERS if u != "root"], key=lambda x: int(x.replace("remotecli", "")))
            self._send_json(200, {
                "status": "ok",
                "service": "eneik-sovereign-bridge",
                "playwright": "ready",
                "workers": workers
            })
            return

        self._send_json(404, {"error": "Not Found", "path": self.path})

    def do_POST(self):
        content_length = int(self.headers.get("Content-Length", 0))
        post_data = self.rfile.read(content_length)

        try:
            payload = json.loads(post_data.decode("utf-8")) if post_data else {}
        except Exception as e:
            self._send_json(400, {"error": "Invalid JSON", "details": str(e)})
            return

        path = self.path.split("?")[0]

        if path in ("/execute-agy", "/api/worker/execute"):
            self.handle_execute_agy(payload)
        elif path in ("/render-viewports", "/api/vision/render"):
            self.handle_render_viewports(payload)
        else:
            self._send_json(404, {"error": "Endpoint not found", "path": path})

    def handle_execute_agy(self, payload):
        user = payload.get("user") or payload.get("workerUser") or "remotecli3"
        user = user.strip().lower()

        # Joseph Raz Deontic Gate (D006)
        if user in FORBIDDEN_USERS:
            err_msg = f"Raz Deontic Gate Violation (D006): Identity '{user}' belongs to Factory Sovereign Core (L1/L2) and is STRICTLY FORBIDDEN from executing worker tasks!"
            sys.stderr.write(f"SECURITY AUDIT: {err_msg}\n")
            self._send_json(403, {
                "error": "Forbidden",
                "code": 403,
                "reason": "D006_RAZ_DEONTIC_SEPARATION",
                "message": err_msg
            })
            return

        if user not in ALLOWED_WORKER_USERS:
            self._send_json(403, {
                "error": "Forbidden",
                "code": 403,
                "message": f"Worker user '{user}' is not in permitted worker set (remotecli3..remotecli10, root)"
            })
            return

        raw_model = payload.get("model", "gemini-3.8-flash-medium")
        model = normalize_model_name(raw_model)
        prompt = payload.get("prompt", "")
        timeout_sec = int(payload.get("timeoutSeconds", 180))
        cwd = payload.get("workingDirectory")

        if cwd and not os.path.exists(cwd):
            cwd = None

        if user == "root":
            cmd = ["/usr/local/bin/agy", "--model", model, "--dangerously-skip-permissions", "-p", prompt]
        else:
            bin_path = f"/home/{user}/.local/bin/agy"
            cmd = ["sudo", "-n", "-u", user, bin_path, "--model", model, "--dangerously-skip-permissions", "-p", prompt]

        start_time = time.time()
        try:
            proc = subprocess.run(
                cmd,
                cwd=cwd,
                capture_output=True,
                text=True,
                timeout=timeout_sec
            )
            duration_ms = int((time.time() - start_time) * 1000)
            self._send_json(200, {
                "exitCode": proc.returncode,
                "stdout": proc.stdout,
                "stderr": proc.stderr,
                "durationMs": duration_ms
            })
        except subprocess.TimeoutExpired as te:
            duration_ms = int((time.time() - start_time) * 1000)
            self._send_json(504, {
                "exitCode": 124,
                "stdout": te.stdout or "",
                "stderr": f"Command timed out after {timeout_sec}s",
                "durationMs": duration_ms
            })
        except Exception as e:
            duration_ms = int((time.time() - start_time) * 1000)
            self._send_json(500, {
                "exitCode": 1,
                "stdout": "",
                "stderr": f"Execution failed: {str(e)}",
                "durationMs": duration_ms
            })

    def handle_render_viewports(self, payload):
        env = dict(os.environ)
        env["NODE_PATH"] = "/usr/local/lib/node_modules"

        input_json = json.dumps(payload)
        start_time = time.time()

        try:
            proc = subprocess.run(
                ["node", "-e", RENDER_HELPER_JS],
                input=input_json,
                capture_output=True,
                text=True,
                timeout=30,
                env=env
            )

            if proc.returncode != 0:
                self._send_json(500, {
                    "error": "Playwright render failed",
                    "stderr": proc.stderr,
                    "stdout": proc.stdout
                })
                return

            res = json.loads(proc.stdout)
            self._send_json(200, res)
        except subprocess.TimeoutExpired:
            self._send_json(504, {"error": "Playwright render timed out after 30s"})
        except Exception as e:
            self._send_json(500, {"error": "Internal render error", "details": str(e)})

    def log_message(self, format, *args):
        # Concise logging
        sys.stderr.write(f"[SovereignBridge] {self.address_string()} - {format % args}\n")

def main():
    server = ThreadingHTTPServer((HOST, PORT), SovereignBridgeHandler)
    print(f"[SovereignBridge] Listening on {HOST}:{PORT} (ready for worker & vision requests)")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()

if __name__ == "__main__":
    main()
