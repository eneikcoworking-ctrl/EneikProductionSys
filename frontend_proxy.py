import http.server
import urllib.request
import urllib.error
import socketserver
import os
import sys

PORT = 18081
STATIC_DIR = os.path.join(os.getcwd(), 'runtime-launcher-workspace', 'test-fiftieth', 'frontend')
BACKEND_URL = 'http://127.0.0.1:18080'

class ProxyHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=STATIC_DIR, **kwargs)

    def do_GET(self):
        if self.path.startswith('/api/') or self.path.startswith('/actuator'):
            self.proxy_request('GET')
        else:
            if self.path == '/' or self.path == '':
                self.path = '/index.html'
            super().do_GET()

    def do_POST(self):
        if self.path.startswith('/api/'):
            self.proxy_request('POST')
        else:
            self.send_error(405, 'Method Not Allowed')

    def do_PUT(self):
        if self.path.startswith('/api/'):
            self.proxy_request('PUT')
        else:
            self.send_error(405, 'Method Not Allowed')

    def do_DELETE(self):
        if self.path.startswith('/api/'):
            self.proxy_request('DELETE')
        else:
            self.send_error(405, 'Method Not Allowed')

    def proxy_request(self, method):
        target_url = BACKEND_URL + self.path
        body = None
        content_length = int(self.headers.get('Content-Length', 0))
        if content_length > 0:
            body = self.rfile.read(content_length)

        headers = {}
        for k, v in self.headers.items():
            if k.lower() not in ['host', 'content-length']:
                headers[k] = v

        req = urllib.request.Request(target_url, data=body, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=10) as res:
                self.send_response(res.status)
                for k, v in res.getheaders():
                    if k.lower() not in ['transfer-encoding', 'content-length']:
                        self.send_header(k, v)
                content = res.read()
                self.send_header('Content-Length', str(len(content)))
                self.end_headers()
                self.wfile.write(content)
        except urllib.error.HTTPError as e:
            self.send_response(e.code)
            for k, v in e.headers.items():
                if k.lower() not in ['transfer-encoding', 'content-length']:
                    self.send_header(k, v)
            err_body = e.read()
            self.send_header('Content-Length', str(len(err_body)))
            self.end_headers()
            self.wfile.write(err_body)
        except Exception as e:
            self.send_error(502, f'Bad Gateway: {e}')

if __name__ == '__main__':
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(('0.0.0.0', PORT), ProxyHandler) as httpd:
        print(f'Frontend reverse proxy listening on port {PORT}, serving {STATIC_DIR} and proxying /api/ to {BACKEND_URL}')
        httpd.serve_forever()
