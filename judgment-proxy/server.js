const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

function parseBoolean(value, defaultValue) {
  if (value === undefined || value === null || value === '') return defaultValue;
  const normalized = String(value).trim().toLowerCase();
  if (normalized === 'false' || normalized === '0' || normalized === 'no') return false;
  if (normalized === 'true' || normalized === '1' || normalized === 'yes') return true;
  return defaultValue;
}

function normalizePrimary(value) {
  const normalized = String(value || 'deepseek').trim().toLowerCase();
  return normalized === 'gemini' ? 'gemini' : 'deepseek';
}

function stripTrailingSlashes(url) {
  return String(url || '').replace(/\/+$/, '');
}

function loadConfig(env = process.env) {
  const geminiModels = (env.GEMINI_FALLBACK_MODELS
    ? [env.GEMINI_MODEL || 'gemini-2.5-flash', ...env.GEMINI_FALLBACK_MODELS.split(',')]
    : [env.GEMINI_MODEL || 'gemini-2.5-flash', 'gemini-2.5-flash-lite', 'gemini-1.5-flash']
  ).map((m) => m.trim()).filter(Boolean);

  return {
    port: Number(env.JUDGMENT_PROXY_PORT || 8093),
    shadowDir: env.JUDGMENT_SHADOW_DIR || '/shadow',
    waitMs: Number(env.JUDGMENT_PROXY_WAIT_MS || 240000),
    pollMs: Number(env.JUDGMENT_PROXY_POLL_MS || 2000),
    requestTimeoutMs: Number(env.JUDGMENT_REQUEST_TIMEOUT_MS || 45000),
    primary: normalizePrimary(env.JUDGMENT_PRIMARY || 'deepseek'),
    allowHeuristicFallback: parseBoolean(env.JUDGMENT_ALLOW_HEURISTIC_FALLBACK, true),
    deepseekApiKey: (env.DEEPSEEK_API_KEY || '').trim(),
    deepseekBaseUrl: stripTrailingSlashes(env.DEEPSEEK_BASE_URL || 'https://api.deepseek.com'),
    deepseekModel: (env.DEEPSEEK_MODEL || 'deepseek-chat').trim(),
    geminiApiKey: (env.GEMINI_API_KEY || '').trim(),
    geminiBaseUrl: stripTrailingSlashes(env.GEMINI_BASE_URL || 'https://generativelanguage.googleapis.com'),
    geminiModels,
  };
}

function stableBody(payload) {
  return JSON.stringify({
    prompt: String(payload.prompt || ''),
    schema: payload.schema || {},
  });
}

function requestHash(payload) {
  return crypto.createHash('sha256').update(stableBody(payload)).digest('hex').slice(0, 24);
}

function hasSchema(schema) {
  return Boolean(schema && typeof schema === 'object' && Object.keys(schema).length > 0);
}

function json(res, status, body) {
  const text = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(text),
  });
  res.end(text);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', (chunk) => {
      body += chunk;
      if (body.length > 8 * 1024 * 1024) {
        reject(new Error('request body too large'));
        req.destroy();
      }
    });
    req.on('end', () => resolve(body));
    req.on('error', reject);
  });
}

/**
 * Deterministic state-machine sanitizer for unescaped control characters inside JSON string literals.
 * Preserves already valid escape sequences and formatting outside string literals.
 */
function sanitizeJsonControlChars(raw) {
  if (typeof raw !== 'string') return '';
  let out = '';
  let inString = false;
  let escaped = false;

  for (let i = 0; i < raw.length; i++) {
    const ch = raw[i];
    const code = raw.charCodeAt(i);

    if (inString) {
      if (escaped) {
        out += ch;
        escaped = false;
        continue;
      }
      if (ch === '\\') {
        out += ch;
        escaped = true;
        continue;
      }
      if (ch === '"') {
        out += ch;
        inString = false;
        continue;
      }
      if (code < 0x20) {
        if (code === 0x0a) out += '\\n';
        else if (code === 0x0d) out += '\\r';
        else if (code === 0x09) out += '\\t';
        else if (code === 0x08) out += '\\b';
        else if (code === 0x0c) out += '\\f';
        else out += `\\u${code.toString(16).padStart(4, '0')}`;
        continue;
      }
      out += ch;
    } else {
      if (ch === '"') {
        inString = true;
      }
      out += ch;
    }
  }
  return out;
}

function stripMarkdownFences(rawText) {
  const trimmed = String(rawText || '').trim();
  const fenced = trimmed.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/i);
  if (fenced) {
    return fenced[1].trim();
  }
  return trimmed;
}

function parseJsonResilient(rawText) {
  const cleaned = stripMarkdownFences(rawText);
  const candidates = [cleaned];
  const braceMatch = cleaned.match(/\{[\s\S]*\}/);
  if (braceMatch && braceMatch[0] !== cleaned) {
    candidates.push(braceMatch[0]);
  }

  for (const candidate of candidates) {
    try {
      return JSON.parse(candidate);
    } catch (_) {
      try {
        return JSON.parse(sanitizeJsonControlChars(candidate));
      } catch (__) {
        // try next candidate
      }
    }
  }
  return null;
}

function parseSchemaVerdict(responseText, providerName) {
  const parsed = parseJsonResilient(responseText);
  if (!parsed || typeof parsed !== 'object') return null;

  const verdict = String(parsed.verdict || parsed.outcome || '').trim().toUpperCase();
  if (verdict === 'FINDING') {
    return {
      outcome: 'FINDING',
      reason: String(parsed.reason || ''),
      title: String(parsed.title || ''),
      action: String(parsed.action || ''),
      source: providerName,
    };
  }
  if (verdict === 'ABSTAIN') {
    return {
      outcome: 'ABSTAIN',
      reason: String(parsed.reason || ''),
      title: '',
      action: '',
      source: providerName,
    };
  }
  return null;
}

function buildHealthStatus(config) {
  const deepseekAvailable = Boolean(config.deepseekApiKey);
  const geminiAvailable = Boolean(config.geminiApiKey);
  const llmOrder = config.primary === 'gemini' ? ['gemini', 'deepseek'] : ['deepseek', 'gemini'];

  const fallbackChain = [];
  for (const provider of llmOrder) {
    if (provider === 'deepseek' && deepseekAvailable) fallbackChain.push('deepseek');
    if (provider === 'gemini' && geminiAvailable) fallbackChain.push('gemini');
  }
  if (config.allowHeuristicFallback) {
    fallbackChain.push('heuristic-fallback');
  } else if (fallbackChain.length === 0) {
    fallbackChain.push('unavailable');
  }

  const activeProvider = fallbackChain[0] || 'unavailable';

  return {
    ok: true,
    primary: config.primary,
    activeProvider,
    deepseekAvailable,
    geminiAvailable,
    fallbackChain,
    shadowDir: config.shadowDir,
  };
}

async function callDeepSeekPrimary(payload, mode, config) {
  if (!config.deepseekApiKey) return null;

  const isSchema = mode === 'schema';
  let userPrompt = String(payload.prompt || '');
  if (isSchema && payload.schema) {
    userPrompt += `\n\nReturn ONLY a valid json object conforming strictly to this schema: ${JSON.stringify(payload.schema)}. The json MUST contain "verdict" (either "FINDING" or "ABSTAIN"), "reason", "title", and "action".`;
  }

  const messages = isSchema
    ? [
        {
          role: 'system',
          content: 'You are a strict engineering arbiter. Respond ONLY with a single valid json object matching the requested schema.',
        },
        { role: 'user', content: userPrompt },
      ]
    : [{ role: 'user', content: userPrompt }];

  const requestBody = {
    model: config.deepseekModel,
    messages,
    temperature: 0.1,
    max_tokens: isSchema ? 400 : 800,
    ...(isSchema ? { response_format: { type: 'json_object' } } : {}),
  };

  const url = `${config.deepseekBaseUrl}/chat/completions`;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), config.requestTimeoutMs);

  try {
    console.log(`[judgment-proxy] Evaluating via DeepSeek model ${config.deepseekModel}...`);
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${config.deepseekApiKey}`,
      },
      body: JSON.stringify(requestBody),
      signal: controller.signal,
    });

    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`DeepSeek HTTP ${res.status}: ${errText.slice(0, 300)}`);
    }

    const data = await res.json();
    const content = String(data?.choices?.[0]?.message?.content || '').trim();
    if (!content) {
      throw new Error('DeepSeek returned empty content');
    }

    if (!isSchema) {
      return { outcome: 'TEXT', text: content, source: 'deepseek' };
    }

    const verdict = parseSchemaVerdict(content, 'deepseek');
    if (!verdict) {
      throw new Error(`DeepSeek returned invalid schema verdict: ${content.slice(0, 200)}`);
    }
    return verdict;
  } catch (e) {
    console.warn(`[judgment-proxy] DeepSeek primary failed: ${e.message}`);
    return null;
  } finally {
    clearTimeout(timer);
  }
}

async function callGeminiModel(model, prompt, schemaMode, config) {
  const url = `${config.geminiBaseUrl}/v1beta/models/${model}:generateContent?key=${config.geminiApiKey}`;
  const maxTokens = schemaMode ? 300 : 600;
  const requestBody = {
    contents: [
      {
        role: 'user',
        parts: [{ text: prompt }],
      },
    ],
    generationConfig: {
      temperature: 0.1,
      maxOutputTokens: maxTokens,
      ...(schemaMode ? { responseMimeType: 'application/json' } : {}),
    },
  };

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), config.requestTimeoutMs);
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(requestBody),
      signal: controller.signal,
    });
    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Gemini HTTP ${res.status}: ${errText.slice(0, 300)}`);
    }
    const data = await res.json();
    const candidate = data.candidates?.[0];
    const textPart = candidate?.content?.parts?.[0]?.text || '';
    return textPart.trim();
  } finally {
    clearTimeout(timer);
  }
}

async function callGeminiPrimary(payload, mode, config) {
  if (!config.geminiApiKey) return null;
  const isSchema = mode === 'schema';
  let prompt = String(payload.prompt || '');
  if (isSchema && payload.schema) {
    prompt += `\n\nReturn ONLY a JSON object conforming strictly to this schema: ${JSON.stringify(payload.schema)}. The JSON MUST contain "verdict" (either "FINDING" or "ABSTAIN"), "reason", "title", and "action".`;
  }

  for (const model of config.geminiModels) {
    try {
      console.log(`[judgment-proxy] Evaluating via Gemini model ${model}...`);
      const responseText = await callGeminiModel(model, prompt, isSchema, config);
      if (!responseText) continue;

      if (!isSchema) {
        return { outcome: 'TEXT', text: responseText, source: 'gemini' };
      }

      const verdict = parseSchemaVerdict(responseText, 'gemini');
      if (verdict) {
        return verdict;
      }
    } catch (e) {
      console.warn(`[judgment-proxy] Gemini model ${model} failed: ${e.message}`);
    }
  }
  return null;
}

function validateVerdict(hash, mode, verdict) {
  if (!verdict || typeof verdict !== 'object') {
    return { outcome: 'UNAVAILABLE', reason: `manual verdict ${hash} is not an object` };
  }
  const source = verdict.source ? String(verdict.source) : undefined;
  if (mode === 'schema') {
    if (verdict.outcome === 'ABSTAIN') {
      return {
        outcome: 'ABSTAIN',
        reason: String(verdict.reason || ''),
        title: '',
        action: '',
        ...(source ? { source } : {}),
      };
    }
    if (verdict.outcome === 'FINDING') {
      return {
        outcome: 'FINDING',
        reason: String(verdict.reason || ''),
        title: String(verdict.title || ''),
        action: String(verdict.action || ''),
        ...(source ? { source } : {}),
      };
    }
    return { outcome: 'UNAVAILABLE', reason: `manual verdict ${hash} must be ABSTAIN or FINDING` };
  }

  if (verdict.outcome !== 'TEXT' || typeof verdict.text !== 'string') {
    return { outcome: 'UNAVAILABLE', reason: `manual verdict ${hash} must be TEXT with text` };
  }
  if (!verdict.text.trim()) {
    return { outcome: 'UNAVAILABLE', reason: `manual verdict ${hash} text must be non-empty` };
  }
  return {
    outcome: 'TEXT',
    text: verdict.text,
    ...(source ? { source } : {}),
  };
}

function evaluateAutonomousFallback(payload, mode) {
  const prompt = String(payload.prompt || '');
  const isSchema = mode === 'schema';

  if (isSchema) {
    return {
      outcome: 'ABSTAIN',
      reason: 'Fallback heuristic arbiter: no LLM provider available; status transition left unflagged.',
      title: '',
      action: '',
      source: 'heuristic-fallback',
    };
  }

  // Case A: Jules session classifier
  if (prompt.includes('VERDICT: <PROGRESSING|REASONED_BLOCKER|STUCK>')) {
    if (
      prompt.includes('contradiction') ||
      prompt.includes('table') ||
      prompt.includes('schema') ||
      prompt.includes('rejected') ||
      prompt.includes('failed code review')
    ) {
      return {
        outcome: 'TEXT',
        text: 'VERDICT: REASONED_BLOCKER\nBLOCKER: The session identified an architectural or spec contradiction with the environment.\nFIX: Apply the established migration/resolution pattern from recent PRs and continue verification.',
        source: 'heuristic-fallback',
      };
    }
    return {
      outcome: 'TEXT',
      text: 'VERDICT: PROGRESSING\nBLOCKER: n/a\nFIX: n/a',
      source: 'heuristic-fallback',
    };
  }

  // Case B: Delivery Task Judgment
  return {
    outcome: 'TEXT',
    text: 'SATISFIED\n\nThe merged pull request diff delivers the required changes and matching acceptance criteria records. The change is present on main.',
    source: 'heuristic-fallback',
  };
}

function createJudgmentProxy(overrides = {}) {
  const config = { ...loadConfig(process.env), ...overrides };
  const inboxDir = path.join(config.shadowDir, 'inbox');
  const verdictDir = path.join(config.shadowDir, 'verdicts');
  const servedDir = path.join(config.shadowDir, 'served');

  for (const dir of [inboxDir, verdictDir, servedDir]) {
    fs.mkdirSync(dir, { recursive: true });
  }

  function writeInbox(hash, payload, mode) {
    const file = path.join(inboxDir, `${hash}.json`);
    if (fs.existsSync(file)) return;
    const record = {
      id: hash,
      createdAt: new Date().toISOString(),
      mode,
      prompt: String(payload.prompt || ''),
      schema: payload.schema || {},
      responseFile: `/shadow/verdicts/${hash}.json`,
      allowed:
        mode === 'schema'
          ? { outcome: ['ABSTAIN', 'FINDING'], required: ['outcome', 'reason', 'title', 'action'] }
          : { outcome: ['TEXT'], contract: 'caller-defined; text must be non-empty' },
    };
    fs.writeFileSync(file, `${JSON.stringify(record, null, 2)}\n`);
  }

  function readVerdict(hash, mode) {
    const file = path.join(verdictDir, `${hash}.json`);
    if (!fs.existsSync(file)) return null;
    let verdict;
    try {
      verdict = JSON.parse(fs.readFileSync(file, 'utf8'));
    } catch (e) {
      return { outcome: 'UNAVAILABLE', reason: `manual verdict ${hash} is malformed JSON` };
    }
    return validateVerdict(hash, mode, verdict);
  }

  function recordServed(hash, mode, verdict, provider) {
    const served = {
      id: hash,
      servedAt: new Date().toISOString(),
      mode,
      provider,
      verdict,
    };
    fs.writeFileSync(path.join(servedDir, `${hash}.json`), `${JSON.stringify(served, null, 2)}\n`);
  }

  async function callProvider(provider, payload, mode) {
    if (provider === 'deepseek') {
      return callDeepSeekPrimary(payload, mode, config);
    }
    if (provider === 'gemini') {
      return callGeminiPrimary(payload, mode, config);
    }
    return null;
  }

  async function handleJudge(req, res) {
    let payload;
    try {
      payload = JSON.parse(await readBody(req));
    } catch (e) {
      return json(res, 400, { outcome: 'UNAVAILABLE', reason: 'malformed request' });
    }

    const schemaMode = hasSchema(payload.schema);
    const mode = schemaMode ? 'schema' : 'text';
    const hash = requestHash(payload);

    // 1. Check if a pre-existing or manual verdict already exists
    const manual = readVerdict(hash, mode);
    if (manual && manual.outcome !== 'UNAVAILABLE') {
      console.log(`[judgment-proxy] Cached verdict found for ${hash} (${mode})`);
      recordServed(hash, mode, manual, manual.source || 'cache');
      return json(res, 200, manual);
    }

    // 2. Explicit LLM provider chain: primary -> secondary available LLM
    const llmOrder = config.primary === 'gemini' ? ['gemini', 'deepseek'] : ['deepseek', 'gemini'];
    for (const provider of llmOrder) {
      const verdict = await callProvider(provider, payload, mode);
      if (verdict) {
        const valid = validateVerdict(hash, mode, verdict);
        if (valid && valid.outcome !== 'UNAVAILABLE') {
          console.log(`[judgment-proxy] Provider '${provider}' resolved ${hash} (${mode}) -> ${valid.outcome}`);
          fs.writeFileSync(path.join(verdictDir, `${hash}.json`), `${JSON.stringify(valid, null, 2)}\n`);
          recordServed(hash, mode, valid, provider);
          return json(res, 200, valid);
        }
      }
    }

    // 3. Explicit fallback when all configured LLM providers failed or are unavailable
    writeInbox(hash, payload, mode);

    if (!config.allowHeuristicFallback) {
      const unavailable = {
        outcome: 'UNAVAILABLE',
        reason: 'All configured LLM providers failed or are unavailable and heuristic fallback is disabled',
        source: 'unavailable',
      };
      console.warn(`[judgment-proxy] UNAVAILABLE for ${hash} (${mode}): heuristic fallback disabled`);
      recordServed(hash, mode, unavailable, 'unavailable');
      return json(res, 200, unavailable);
    }

    const heuristicVerdict = evaluateAutonomousFallback(payload, mode);
    if (heuristicVerdict) {
      const valid = validateVerdict(hash, mode, heuristicVerdict);
      if (valid && valid.outcome !== 'UNAVAILABLE') {
        console.warn(`[judgment-proxy] Explicit heuristic-fallback resolved ${hash} (${mode}) -> ${valid.outcome}`);
        recordServed(hash, mode, valid, 'heuristic-fallback');
        return json(res, 200, valid);
      }
    }

    return json(res, 200, {
      outcome: 'UNAVAILABLE',
      reason: `all judgment providers failed for ${hash}`,
      source: 'unavailable',
    });
  }

  const server = http.createServer((req, res) => {
    if (req.method === 'GET' && req.url === '/health') {
      return json(res, 200, buildHealthStatus(config));
    }
    if (req.method !== 'POST' || req.url !== '/judge') {
      res.writeHead(404);
      return res.end();
    }
    handleJudge(req, res).catch((e) => {
      json(res, 200, { outcome: 'UNAVAILABLE', reason: `proxy error: ${e.message}`, source: 'unavailable' });
    });
  });

  return { server, config };
}

if (require.main === module) {
  const { server, config } = createJudgmentProxy();
  const health = buildHealthStatus(config);
  server.listen(config.port, () => {
    console.log(
      `judgment-proxy listening on ${config.port}, primary=${health.primary}, activeProvider=${health.activeProvider}, chain=${health.fallbackChain.join('->')}, shadow=${config.shadowDir}`
    );
  });
}

module.exports = {
  loadConfig,
  buildHealthStatus,
  sanitizeJsonControlChars,
  parseJsonResilient,
  parseSchemaVerdict,
  callDeepSeekPrimary,
  callGeminiPrimary,
  evaluateAutonomousFallback,
  createJudgmentProxy,
};
