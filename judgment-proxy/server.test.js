const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const {
  createJudgmentProxy,
  sanitizeJsonControlChars,
  parseJsonResilient,
} = require('./server');

function startServer(server) {
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address();
      resolve(`http://127.0.0.1:${port}`);
    });
  });
}

function closeServer(server) {
  return new Promise((resolve, reject) => {
    server.close((err) => (err ? reject(err) : resolve()));
  });
}

function createTempShadowDir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), 'judgment-proxy-test-'));
}

async function startMockHttpServer(handler) {
  const requests = [];
  const server = http.createServer((req, res) => {
    let body = '';
    req.on('data', (chunk) => {
      body += chunk;
    });
    req.on('end', () => {
      let parsedBody = null;
      try {
        parsedBody = body ? JSON.parse(body) : null;
      } catch (_) {
        parsedBody = body;
      }
      requests.push({
        method: req.method,
        url: req.url,
        headers: req.headers,
        body: parsedBody,
      });
      handler(req, res, parsedBody, requests.length);
    });
  });
  const baseUrl = await startServer(server);
  return { server, baseUrl, requests };
}

const TEST_SCHEMA = {
  type: 'object',
  properties: {
    verdict: { type: 'string', enum: ['ABSTAIN', 'FINDING'] },
    reason: { type: 'string' },
    title: { type: 'string' },
    action: { type: 'string' },
  },
  required: ['verdict', 'reason', 'title', 'action'],
  additionalProperties: false,
};

test('sanitizeJsonControlChars escapes raw control chars inside strings without breaking valid escapes', () => {
  const raw = '{\n  "verdict": "FINDING",\n  "reason": "Line 1\nLine 2\twith tab",\n  "title": "Already \\"escaped\\"\\nkept",\n  "action": "Fix it"\n}';
  const sanitized = sanitizeJsonControlChars(raw);
  const parsed = JSON.parse(sanitized);
  assert.equal(parsed.verdict, 'FINDING');
  assert.equal(parsed.reason, 'Line 1\nLine 2\twith tab');
  assert.equal(parsed.title, 'Already "escaped"\nkept');
});

test('parseJsonResilient handles markdown fences and unescaped newlines', () => {
  const raw = '```json\n{"verdict":"FINDING","reason":"Raw\nnewline","title":"T1","action":"A1"}\n```';
  const parsed = parseJsonResilient(raw);
  assert.deepEqual(parsed, {
    verdict: 'FINDING',
    reason: 'Raw\nnewline',
    title: 'T1',
    action: 'A1',
  });
});

test('GET /health reports honest DeepSeek primary status with and without API keys', async () => {
  const shadow1 = createTempShadowDir();
  const proxyWithKey = createJudgmentProxy({
    shadowDir: shadow1,
    primary: 'deepseek',
    deepseekApiKey: 'sk-test-key',
    geminiApiKey: '',
    allowHeuristicFallback: true,
  });
  const url1 = await startServer(proxyWithKey.server);
  try {
    const res = await fetch(`${url1}/health`);
    assert.equal(res.status, 200);
    const body = await res.json();
    assert.equal(body.ok, true);
    assert.equal(body.primary, 'deepseek');
    assert.equal(body.activeProvider, 'deepseek');
    assert.equal(body.deepseekAvailable, true);
    assert.equal(body.geminiAvailable, false);
    assert.deepEqual(body.fallbackChain, ['deepseek', 'heuristic-fallback']);
    assert.equal(body.shadowDir, shadow1);
  } finally {
    await closeServer(proxyWithKey.server);
    fs.rmSync(shadow1, { recursive: true, force: true });
  }

  const shadow2 = createTempShadowDir();
  const proxyNoKey = createJudgmentProxy({
    shadowDir: shadow2,
    primary: 'deepseek',
    deepseekApiKey: '',
    geminiApiKey: '',
    allowHeuristicFallback: true,
  });
  const url2 = await startServer(proxyNoKey.server);
  try {
    const res = await fetch(`${url2}/health`);
    const body = await res.json();
    assert.equal(body.primary, 'deepseek');
    assert.equal(body.activeProvider, 'heuristic-fallback');
    assert.equal(body.deepseekAvailable, false);
    assert.equal(body.geminiAvailable, false);
    assert.deepEqual(body.fallbackChain, ['heuristic-fallback']);
  } finally {
    await closeServer(proxyNoKey.server);
    fs.rmSync(shadow2, { recursive: true, force: true });
  }

  const shadow3 = createTempShadowDir();
  const proxyNoKeyNoHeuristic = createJudgmentProxy({
    shadowDir: shadow3,
    primary: 'deepseek',
    deepseekApiKey: '',
    geminiApiKey: '',
    allowHeuristicFallback: false,
  });
  const url3 = await startServer(proxyNoKeyNoHeuristic.server);
  try {
    const res = await fetch(`${url3}/health`);
    const body = await res.json();
    assert.equal(body.primary, 'deepseek');
    assert.equal(body.activeProvider, 'unavailable');
    assert.equal(body.deepseekAvailable, false);
    assert.deepEqual(body.fallbackChain, ['unavailable']);
  } finally {
    await closeServer(proxyNoKeyNoHeuristic.server);
    fs.rmSync(shadow3, { recursive: true, force: true });
  }

  const shadow4 = createTempShadowDir();
  const proxyWithGeminiSecondary = createJudgmentProxy({
    shadowDir: shadow4,
    primary: 'deepseek',
    deepseekApiKey: '',
    geminiApiKey: 'gem-test-key',
    allowHeuristicFallback: true,
  });
  const url4 = await startServer(proxyWithGeminiSecondary.server);
  try {
    const res = await fetch(`${url4}/health`);
    const body = await res.json();
    assert.equal(body.primary, 'deepseek');
    assert.equal(body.activeProvider, 'gemini');
    assert.equal(body.deepseekAvailable, false);
    assert.equal(body.geminiAvailable, true);
    assert.deepEqual(body.fallbackChain, ['gemini', 'heuristic-fallback']);
  } finally {
    await closeServer(proxyWithGeminiSecondary.server);
    fs.rmSync(shadow4, { recursive: true, force: true });
  }
});

test('POST /judge schema mode resolves FINDING and ABSTAIN via DeepSeek primary with json_object and resilient parsing', async () => {
  const shadowDir = createTempShadowDir();
  const mockDeepSeek = await startMockHttpServer((req, res, body, callCount) => {
    if (callCount === 1) {
      // Return markdown-fenced JSON with raw newline inside string value
      const rawContent = '```json\n{"verdict":"FINDING","reason":"Loop detected\nin retry budget","title":"Retry loop","action":"Fix ClaimService"}\n```';
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ choices: [{ message: { content: rawContent } }] }));
      return;
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(
      JSON.stringify({
        choices: [
          {
            message: {
              content: JSON.stringify({
                verdict: 'ABSTAIN',
                reason: 'Normal transition',
                title: '',
                action: '',
              }),
            },
          },
        ],
      })
    );
  });

  const proxy = createJudgmentProxy({
    shadowDir,
    primary: 'deepseek',
    deepseekApiKey: 'sk-mock-deepseek',
    deepseekBaseUrl: mockDeepSeek.baseUrl,
    deepseekModel: 'deepseek-chat',
    geminiApiKey: '',
    allowHeuristicFallback: false,
  });
  const proxyUrl = await startServer(proxy.server);

  try {
    const res1 = await fetch(`${proxyUrl}/judge`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt: 'Inspect finding transition', schema: TEST_SCHEMA }),
    });
    assert.equal(res1.status, 200);
    const verdict1 = await res1.json();
    assert.equal(verdict1.outcome, 'FINDING');
    assert.equal(verdict1.reason, 'Loop detected\nin retry budget');
    assert.equal(verdict1.title, 'Retry loop');
    assert.equal(verdict1.action, 'Fix ClaimService');
    assert.equal(verdict1.source, 'deepseek');

    assert.equal(mockDeepSeek.requests.length, 1);
    assert.equal(mockDeepSeek.requests[0].url, '/chat/completions');
    assert.equal(mockDeepSeek.requests[0].headers.authorization, 'Bearer sk-mock-deepseek');
    assert.equal(mockDeepSeek.requests[0].body.model, 'deepseek-chat');
    assert.equal(mockDeepSeek.requests[0].body.temperature, 0.1);
    assert.deepEqual(mockDeepSeek.requests[0].body.response_format, { type: 'json_object' });

    const res2 = await fetch(`${proxyUrl}/judge`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt: 'Inspect normal transition', schema: TEST_SCHEMA }),
    });
    assert.equal(res2.status, 200);
    const verdict2 = await res2.json();
    assert.equal(verdict2.outcome, 'ABSTAIN');
    assert.equal(verdict2.reason, 'Normal transition');
    assert.equal(verdict2.source, 'deepseek');
  } finally {
    await closeServer(proxy.server);
    await closeServer(mockDeepSeek.server);
    fs.rmSync(shadowDir, { recursive: true, force: true });
  }
});

test('POST /judge text mode resolves TEXT via DeepSeek primary without response_format', async () => {
  const shadowDir = createTempShadowDir();
  const mockDeepSeek = await startMockHttpServer((req, res) => {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(
      JSON.stringify({
        choices: [{ message: { content: 'VERDICT: PROGRESSING\nBLOCKER: n/a\nFIX: n/a' } }],
      })
    );
  });

  const proxy = createJudgmentProxy({
    shadowDir,
    primary: 'deepseek',
    deepseekApiKey: 'sk-mock-deepseek',
    deepseekBaseUrl: mockDeepSeek.baseUrl,
    geminiApiKey: '',
    allowHeuristicFallback: false,
  });
  const proxyUrl = await startServer(proxy.server);

  try {
    const res = await fetch(`${proxyUrl}/judge`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt: 'Classify session progress', schema: {} }),
    });
    assert.equal(res.status, 200);
    const verdict = await res.json();
    assert.equal(verdict.outcome, 'TEXT');
    assert.equal(verdict.text, 'VERDICT: PROGRESSING\nBLOCKER: n/a\nFIX: n/a');
    assert.equal(verdict.source, 'deepseek');
    assert.equal(mockDeepSeek.requests[0].body.response_format, undefined);
  } finally {
    await closeServer(proxy.server);
    await closeServer(mockDeepSeek.server);
    fs.rmSync(shadowDir, { recursive: true, force: true });
  }
});

test('POST /judge falls back to secondary LLM (Gemini) when primary DeepSeek returns HTTP 500', async () => {
  const shadowDir = createTempShadowDir();
  const mockDeepSeek = await startMockHttpServer((req, res) => {
    res.writeHead(500, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'internal server error' }));
  });
  const mockGemini = await startMockHttpServer((req, res) => {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(
      JSON.stringify({
        candidates: [
          {
            content: {
              parts: [
                {
                  text: JSON.stringify({
                    verdict: 'FINDING',
                    reason: 'Caught by Gemini secondary',
                    title: 'Secondary finding',
                    action: 'Inspect logs',
                  }),
                },
              ],
            },
          },
        ],
      })
    );
  });

  const proxy = createJudgmentProxy({
    shadowDir,
    primary: 'deepseek',
    deepseekApiKey: 'sk-mock-deepseek',
    deepseekBaseUrl: mockDeepSeek.baseUrl,
    geminiApiKey: 'gem-mock-key',
    geminiBaseUrl: mockGemini.baseUrl,
    geminiModels: ['gemini-2.5-flash'],
    allowHeuristicFallback: false,
  });
  const proxyUrl = await startServer(proxy.server);

  try {
    const res = await fetch(`${proxyUrl}/judge`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt: 'Evaluate fallback to secondary', schema: TEST_SCHEMA }),
    });
    assert.equal(res.status, 200);
    const verdict = await res.json();
    assert.equal(verdict.outcome, 'FINDING');
    assert.equal(verdict.source, 'gemini');
    assert.equal(verdict.title, 'Secondary finding');
    assert.equal(mockDeepSeek.requests.length, 1);
    assert.equal(mockGemini.requests.length, 1);
  } finally {
    await closeServer(proxy.server);
    await closeServer(mockDeepSeek.server);
    await closeServer(mockGemini.server);
    fs.rmSync(shadowDir, { recursive: true, force: true });
  }
});

test('POST /judge uses explicit heuristic-fallback on HTTP 429 when allowed, and UNAVAILABLE when disabled', async () => {
  const shadowAllowed = createTempShadowDir();
  const mockDeepSeek429 = await startMockHttpServer((req, res) => {
    res.writeHead(429, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'rate limited' }));
  });

  const proxyAllowed = createJudgmentProxy({
    shadowDir: shadowAllowed,
    primary: 'deepseek',
    deepseekApiKey: 'sk-mock-deepseek',
    deepseekBaseUrl: mockDeepSeek429.baseUrl,
    geminiApiKey: '',
    allowHeuristicFallback: true,
  });
  const urlAllowed = await startServer(proxyAllowed.server);

  try {
    const res = await fetch(`${urlAllowed}/judge`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt: 'Evaluate under 429', schema: TEST_SCHEMA }),
    });
    assert.equal(res.status, 200);
    const verdict = await res.json();
    assert.equal(verdict.outcome, 'ABSTAIN');
    assert.equal(verdict.source, 'heuristic-fallback');
    // Heuristic fallback must NOT poison verdicts/ cache
    const cachedFiles = fs.readdirSync(path.join(shadowAllowed, 'verdicts'));
    assert.equal(cachedFiles.length, 0);
  } finally {
    await closeServer(proxyAllowed.server);
    fs.rmSync(shadowAllowed, { recursive: true, force: true });
  }

  const shadowDisabled = createTempShadowDir();
  const proxyDisabled = createJudgmentProxy({
    shadowDir: shadowDisabled,
    primary: 'deepseek',
    deepseekApiKey: 'sk-mock-deepseek',
    deepseekBaseUrl: mockDeepSeek429.baseUrl,
    geminiApiKey: '',
    allowHeuristicFallback: false,
  });
  const urlDisabled = await startServer(proxyDisabled.server);

  try {
    const res = await fetch(`${urlDisabled}/judge`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt: 'Evaluate under 429 strict', schema: TEST_SCHEMA }),
    });
    assert.equal(res.status, 200);
    const verdict = await res.json();
    assert.equal(verdict.outcome, 'UNAVAILABLE');
    assert.equal(verdict.source, 'unavailable');
  } finally {
    await closeServer(proxyDisabled.server);
    await closeServer(mockDeepSeek429.server);
    fs.rmSync(shadowDisabled, { recursive: true, force: true });
  }
});
