// The factory's judgment layer, running on the operator's Claude subscription.
//
// Why Node and not FastAPI like runtime-launcher: this image must carry Node anyway, because the
// Claude Code CLI is a Node program. Adding a second runtime to preserve a stylistic match would be
// weight with no bearer. The shape is the same as the launcher's - one endpoint, no state, every
// failure reported back as evidence rather than thrown.
//
// The credential is mounted here and nowhere else, which is the operator's own rule for the docker
// socket, written into docker-compose.yml: contain a privilege in the smallest separately-reviewable
// surface, never in the much larger main backend.
const http = require('http');
const { execFile } = require('child_process');

const PORT = Number(process.env.JUDGMENT_PORT || 8092);
// A cold Claude Code invocation loads its own context before it reads the prompt; the model call itself
// is short. Generous, and bounded - an unbounded call on a shared scheduling pool is what starved this
// factory's thread pool on 2026-07-24.
const TIMEOUT_MS = Number(process.env.JUDGMENT_TIMEOUT_MS || 300000);

function judge(prompt, schema) {
  return new Promise((resolve) => {
    // No tools, for correctness - NOT for cost.
    //
    // The evidence a ruling is about travels IN the prompt: the transition, its statement, its recorded
    // evidence and the invariant's own prior history. Left unrestricted the agent explores the only
    // filesystem it can reach, which is this sidecar's own container, and then correctly reports that it
    // judged nearly blind - measured on the first end-to-end call, where it read /app/server.js and said
    // so in its verdict. Evidence about the wrong bearer is the defect ACP-102 names; denying the tools
    // removes it.
    //
    // The cost argument for this change was mine and measurement refuted it: with tools the same prompt
    // cost $0.33, without them $0.81. Denying tools appears to move spend from tool calls into reasoning.
    // Across four calls the range was $0.06-$0.81, which is too few samples to model - what is recorded
    // here is the refutation, so nobody re-derives the wrong reason from the right change.
    const DENIED = ['Bash', 'Read', 'Write', 'Edit', 'Glob', 'Grep', 'WebFetch', 'WebSearch',
      'NotebookEdit', 'Task', 'TodoWrite'];
    // Effort is the documented lever on depth and token spend, and it is configurable rather than
    // guessed: the operator can raise it if rulings look shallow, lower it if the limit is being eaten.
    const args = ['-p', '--output-format', 'json',
      '--disallowedTools', DENIED.join(' '),
      '--effort', process.env.JUDGMENT_EFFORT || 'medium'];
    // A caller that declares a schema gets a schema-bounded object back; one that does not gets the text.
    // The second mode exists for callers whose reply format is already a contract of their own - the
    // Jules loop classifier's VERDICT/BLOCKER/FIX lines predate this sidecar and parse fine as text.
    // Making them adopt a JSON schema would be a rewrite of a working parser for no gain.
    const wantsSchema = schema && Object.keys(schema).length > 0;
    if (wantsSchema) args.push('--json-schema', JSON.stringify(schema));
    args.push(prompt);
    execFile('claude', args, { timeout: TIMEOUT_MS, maxBuffer: 32 * 1024 * 1024 },
      (err, stdout, stderr) => {
        if (err && !stdout) {
          // No answer at all: a fact about this instrument, not about the transition being judged.
          return resolve({ outcome: 'UNAVAILABLE', reason: String(err.message || err).slice(0, 400) });
        }
        let parsed;
        try {
          parsed = JSON.parse(stdout);
        } catch (e) {
          return resolve({ outcome: 'UNAVAILABLE', reason: 'sidecar could not parse the CLI envelope' });
        }
        if (parsed.is_error) {
          return resolve({ outcome: 'UNAVAILABLE', reason: String(parsed.result || parsed.subtype || 'cli reported an error').slice(0, 400) });
        }
        if (!wantsSchema) {
          const text = typeof parsed.result === 'string' ? parsed.result : '';
          if (!text) return resolve({ outcome: 'UNJUDGEABLE', reason: 'empty answer' });
          return resolve({ outcome: 'TEXT', text, costUsd: parsed.total_cost_usd });
        }
        const out = parsed.structured_output;
        if (!out || (out.verdict !== 'FINDING' && out.verdict !== 'ABSTAIN')) {
          // The schema was declared on the request and the answer did not satisfy it. That is a fact
          // about THIS input - retrying it produces the same non-answer - so the caller may mark the
          // transition read rather than letting one row block the queue behind it forever.
          return resolve({ outcome: 'UNJUDGEABLE', reason: 'answer did not satisfy the declared schema' });
        }
        resolve({
          outcome: out.verdict,
          reason: String(out.reason || ''),
          title: String(out.title || ''),
          action: String(out.action || ''),
          costUsd: parsed.total_cost_usd,
        });
      });
  });
}

const server = http.createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end('{"ok":true}');
  }
  if (req.method !== 'POST' || req.url !== '/judge') {
    res.writeHead(404); return res.end();
  }
  let body = '';
  req.on('data', (c) => { body += c; if (body.length > 4 * 1024 * 1024) req.destroy(); });
  req.on('end', async () => {
    let payload;
    try { payload = JSON.parse(body); } catch (e) {
      res.writeHead(400, { 'Content-Type': 'application/json' });
      return res.end(JSON.stringify({ outcome: 'UNAVAILABLE', reason: 'malformed request' }));
    }
    const result = await judge(String(payload.prompt || ''), payload.schema || {});
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(result));
  });
});

server.listen(PORT, () => console.log(`judgment-sidecar listening on ${PORT}`));
