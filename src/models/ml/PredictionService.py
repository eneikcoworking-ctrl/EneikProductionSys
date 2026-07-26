# @file PredictionService.py
# @agent TAG-04 (Modal Quantifier)
# @description Bayesian predictor and Bottleneck FastAPI service.

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import uvicorn
import os
import glob
import json
import re
import urllib.request
import urllib.error
import urllib.parse
import socket

app = FastAPI(title="Eneik AI Prediction Service")

DEFAULT_GEMINI_MODEL = "gemini-3.5-flash"
DEFAULT_GEMINI_FALLBACK_MODELS = "gemini-3.1-flash-lite"
DEFAULT_GEMINI_PRO_MODEL = "gemini-3.1-pro-preview"
# gemini-2.5-flash removed (2026-07-25, live incident): confirmed dead via direct API probe - HTTP 404
# "no longer available to new users", a permanent deprecation, not a transient outage. It sat LAST in
# both fallback chains, so any transient hiccup on the earlier candidates made the whole chain fail with
# no real fallback left. Verified gemini-3.1-flash-lite and gemini-3.5-flash both still respond 200 OK.
DEFAULT_GEMINI_PRO_FALLBACK_MODELS = "gemini-3.5-flash,gemini-3.1-flash-lite"
DEFAULT_GEMINI_EMBEDDING_MODEL = "gemini-embedding-001"


def gemini_candidate_models(model_tier: str = "", model_override: str = "") -> list[str]:
    # Pro tier permanently disabled (2026-07-25, operator directive - emergency cost incident: "она за
    # несколько часов потратила месячный бюджет, при этом по проекту ничего не сдвинулось" /
    # "никогда не вызывать про версию"). Enforced HERE, the single choke point every text-generation call
    # (ask_gemini/ask_gemini_cached) funnels through via gemini_candidate_models - robust even if a future
    # caller passes modelTier="pro" by mistake, since a caller-supplied modelOverride is the only way to
    # still reach a pro-named model, and normal callers never set that.
    if model_override:
        candidates = [model.strip() for model in model_override.split(",") if model.strip()]
    else:
        primary = os.getenv("GEMINI_MODEL", DEFAULT_GEMINI_MODEL).strip() or DEFAULT_GEMINI_MODEL
        fallbacks = os.getenv("GEMINI_FALLBACK_MODELS", DEFAULT_GEMINI_FALLBACK_MODELS)
        candidates = [primary]
        candidates.extend(model.strip() for model in fallbacks.split(",") if model.strip())

    unique = []
    for model in candidates:
        if model not in unique:
            unique.append(model)
    return unique


def gemini_generate_url(model: str, api_key: str) -> str:
    api_version = os.getenv("GEMINI_API_VERSION", "v1beta").strip() or "v1beta"
    model_path = model if model.startswith("models/") else f"models/{model}"
    query = urllib.parse.urlencode({"key": api_key})
    return f"https://generativelanguage.googleapis.com/{api_version}/{model_path}:generateContent?{query}"


def gemini_request_timeout() -> int:
    raw = os.getenv("GEMINI_REQUEST_TIMEOUT_SECONDS", "10").strip()
    try:
        return max(1, int(raw))
    except ValueError:
        return 10


def gemini_embed_url(model: str, api_key: str) -> str:
    api_version = os.getenv("GEMINI_API_VERSION", "v1beta").strip() or "v1beta"
    model_path = model if model.startswith("models/") else f"models/{model}"
    query = urllib.parse.urlencode({"key": api_key})
    return f"https://generativelanguage.googleapis.com/{api_version}/{model_path}:embedContent?{query}"


def ask_gemini_embedding(text: str, api_key: str = "") -> list[float]:
    # Same key-resolution convention as ask_gemini above; a missing key is a legitimate "not configured"
    # state, not an error, so this simply returns an empty vector and lets the caller decide what to do.
    if not api_key:
        api_key = os.getenv("GEMINI_API_KEY", "")
    if api_key:
        api_key = api_key.strip()
    if not api_key or not text:
        return []

    model = os.getenv("GEMINI_EMBEDDING_MODEL", DEFAULT_GEMINI_EMBEDDING_MODEL).strip() or DEFAULT_GEMINI_EMBEDDING_MODEL
    payload = {"content": {"parts": [{"text": text}]}}
    headers = {"Content-Type": "application/json"}
    req = urllib.request.Request(
        gemini_embed_url(model, api_key),
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=gemini_request_timeout()) as response:
        res_data = json.loads(response.read().decode("utf-8"))
        return res_data["embedding"]["values"]


# Explicit Gemini context caching (2026-07-25, operator directive: "недорого по токенам решения" -
# caches a STATIC, repeatedly-reused system instruction - currently just the PR-reviewer's role charter,
# the one genuinely large piece of content sent identically on every review for the same role - so it's
# billed at Gemini's reduced cached-token rate on every call after the first, instead of resent in full
# each time. In-memory registry only (resets on service restart) - self-healing, no persistence needed.
# Verified live against the real API before building this: cachedContents accepts far below the widely-
# assumed 32k-token minimum (a ~1.6k-token charter cached successfully), and a cache is tied to one exact
# model and cannot be combined with a separate systemInstruction in the same generateContent call.
_gemini_cache_registry: dict = {}
_GEMINI_CACHE_TTL_SECONDS = 3600


def gemini_cache_create_url(api_key: str) -> str:
    api_version = os.getenv("GEMINI_API_VERSION", "v1beta").strip() or "v1beta"
    query = urllib.parse.urlencode({"key": api_key})
    return f"https://generativelanguage.googleapis.com/{api_version}/cachedContents?{query}"


def ensure_gemini_cache(model: str, cache_key: str, static_instruction: str, api_key: str) -> str | None:
    """Returns a cachedContents resource name for this exact (model, cache_key, content), creating or
    refreshing it if needed. Returns None on ANY failure - caller must fail-open to the uncached path,
    never let a caching problem become a new PR-review failure mode."""
    import hashlib
    import time

    registry_key = f"{model}:{cache_key}:{hashlib.sha256(static_instruction.encode('utf-8')).hexdigest()}"
    entry = _gemini_cache_registry.get(registry_key)
    now = time.time()
    if entry and entry["expires_at"] > now + 60:
        return entry["name"]

    model_path = model if model.startswith("models/") else f"models/{model}"
    payload = {
        "model": model_path,
        "systemInstruction": {"parts": [{"text": static_instruction}]},
        "ttl": f"{_GEMINI_CACHE_TTL_SECONDS}s",
    }
    try:
        req = urllib.request.Request(
            gemini_cache_create_url(api_key),
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=gemini_request_timeout()) as response:
            body = json.loads(response.read().decode("utf-8"))
        _gemini_cache_registry[registry_key] = {
            "name": body["name"],
            "expires_at": now + _GEMINI_CACHE_TTL_SECONDS,
        }
        return body["name"]
    except Exception as e:
        print(f"Gemini context-cache creation failed (falling back to uncached call): {e}")
        return None


def ask_gemini_cached(prompt: str, cached_content_name: str, model: str, api_key: str, force_json: bool) -> str:
    """Raises on any failure - caller is expected to catch and fall back to the uncached ask_gemini path."""
    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "cachedContent": cached_content_name,
    }
    if force_json:
        payload["generationConfig"] = {"responseMimeType": "application/json"}
    req = urllib.request.Request(
        gemini_generate_url(model, api_key),
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=gemini_request_timeout()) as response:
        res_data = json.loads(response.read().decode("utf-8"))
        return res_data["candidates"][0]["content"]["parts"][0]["text"]


def ask_gemini(
    prompt: str,
    system_instruction: str = "",
    api_key: str = "",
    model_tier: str = "",
    model_override: str = "",
) -> str:
    # Check if we have an API key configured or in env
    if not api_key:
        api_key = os.getenv("GEMINI_API_KEY", "")

    if api_key:
        api_key = api_key.strip()

    # If no key, or empty, return default mock JSON depending on the instruction/prompt
    if not api_key:
        if "satisfaction_probability" in system_instruction:
            return '{"satisfaction_probability": 0.98, "modal_status": "Highly Probable (Mock)"}'
        elif "risk_score" in system_instruction:
            return '{"risk_score": 0.15, "is_bottleneck_predicted": false}'
        elif "slices" in system_instruction:
            return '{"slices": []}'
        elif "jtbd" in system_instruction:
            return '{"jtbd": "When I use the client-requested capability slice, I want one small verifiable capability completed, so project progress can be validated without a long Jules session.", "acceptanceCriteria": "Given this slice is implemented, When the primary happy path is exercised, Then it completes without errors.\\nGiven invalid input is submitted, When validation runs, Then invalid data is rejected.\\nGiven verification runs, When the PR is ready, Then the relevant command passes."}'
        return "{}"

    try:
        headers = {"Content-Type": "application/json"}

        # Structure the payload for Gemini API
        payload = {
            "contents": [
                {"parts": [{"text": prompt}]}
            ],
        }
        if system_instruction:
            payload["systemInstruction"] = {
                "parts": [{"text": system_instruction}]
            }

        lower_instruction = system_instruction.lower()
        if "return only json" in lower_instruction or "return valid json" in lower_instruction:
            payload["generationConfig"] = {"responseMimeType": "application/json"}

        retryable_errors = []
        for model in gemini_candidate_models(model_tier, model_override):
            try:
                req = urllib.request.Request(
                    gemini_generate_url(model, api_key),
                    data=json.dumps(payload).encode("utf-8"),
                    headers=headers,
                    method="POST",
                )
                with urllib.request.urlopen(req, timeout=gemini_request_timeout()) as response:
                    res_data = json.loads(response.read().decode("utf-8"))
                    text = res_data["candidates"][0]["content"]["parts"][0]["text"]
                    return text
            except urllib.error.HTTPError as e:
                error_body = e.read().decode("utf-8")
                print(f"HTTP Error calling Gemini API model {model}: {e.code} - {error_body}")
                if e.code in (404, 429, 503):
                    retryable_errors.append(f"{model}: HTTP {e.code} {error_body}")
                    continue
                raise Exception(f"API Error {e.code}: {error_body}") from e
            except (urllib.error.URLError, TimeoutError, socket.timeout) as e:
                print(f"Transient error calling Gemini API model {model}: {e}")
                retryable_errors.append(f"{model}: transient {e}")
                continue

        if retryable_errors:
            raise Exception("All Gemini candidate models failed: " + " | ".join(retryable_errors))
    except Exception as e:
        print(f"Error calling Gemini API: {e}")
        raise Exception(f"API Error: {str(e)}") from e


class BottleneckRequest(BaseModel):
    wip_count: int
    avg_cycle_time: float


class BottleneckResponse(BaseModel):
    risk_score: float
    is_bottleneck_predicted: bool


class ChatRequest(BaseModel):
    prompt: str
    systemInstruction: str = ""
    apiKey: str = ""
    modelTier: str = ""
    modelOverride: str = ""
    # Explicit Gemini context caching (2026-07-25) - opt-in per caller via a stable key, for callers whose
    # systemInstruction is static/repeated across calls (e.g. GeminiProjectObserverService's instruction
    # text, identical every cycle for every project). Empty means "don't cache", the existing uncached behavior.
    cacheKey: str = ""


class ChatResponse(BaseModel):
    text: str


class EmbedRequest(BaseModel):
    text: str
    apiKey: str = ""


class EmbedResponse(BaseModel):
    embedding: list[float]


class PredictionService:
    """
    Core logic for ML predictions and agent reviews.
    """

    MAX_WIP = 100
    SLA_THRESHOLD = 3600.0

    def predict_satisfaction(self, user_context: str):
        try:
            system_instruction = 'You are an AI UX/Customer Success Analyzer. Based on the user context provided, evaluate the probability of user satisfaction. Return ONLY JSON: {"satisfaction_probability": float (0.0 to 1.0), "modal_status": string (e.g., "Highly Probable", "Uncertain", "At Risk")}.'
            prompt = f"Evaluate satisfaction for this context: {user_context}"
            response_json = ask_gemini(prompt, system_instruction)
            import json

            parsed = json.loads(response_json)
            return {
                "satisfaction_probability": parsed.get(
                    "satisfaction_probability", 0.98
                ),
                "modal_status": parsed.get("modal_status", "Highly Probable"),
            }
        except Exception as e:
            print(f"Satisfaction Prediction Fallback triggered due to: {e}")
            return {
                "satisfaction_probability": 0.98,
                "modal_status": "Highly Probable (Fallback)",
            }

    def predict_bottleneck(self, wip_count: int, avg_cycle_time: float):
        try:
            system_instruction = f'You are a Lean Six Sigma Delivery Manager AI. Evaluate the bottleneck risk based on Work In Progress (WIP) and Average Cycle Time (seconds). SLA Threshold is {self.SLA_THRESHOLD} seconds, Max recommended WIP is {self.MAX_WIP}. Return ONLY JSON: {{"risk_score": float (0.0 to 1.0), "is_bottleneck_predicted": bool}}.'
            prompt = f"Current Metrics - WIP Count: {wip_count}, Avg Cycle Time: {avg_cycle_time} seconds. Calculate risk and bottleneck prediction."
            response_json = ask_gemini(prompt, system_instruction)
            import json

            parsed = json.loads(response_json)
            return parsed.get("risk_score", 0.0), parsed.get(
                "is_bottleneck_predicted", False
            )
        except Exception as e:
            print(f"Bottleneck Prediction Fallback triggered due to: {e}")
            # Fallback to mathematical calculation
            wip_factor = min(wip_count / self.MAX_WIP, 1.0)
            time_factor = min(avg_cycle_time / self.SLA_THRESHOLD, 1.0)
            risk_score = (wip_factor + time_factor) / 2.0
            is_bottleneck_predicted = risk_score > 0.7
            return risk_score, is_bottleneck_predicted

    def get_charter_rules(self, role_tag: str):
        # Look for charter files in the mounted project root
        files = glob.glob(f"/project/{role_tag}_*.md")
        if not files:
            return ""
        try:
            with open(files[0], "r", encoding="utf-8") as f:
                return f.read()
        except Exception:
            return ""


predictor = PredictionService()


@app.post("/api/v1/predict/bottleneck", response_model=BottleneckResponse)
async def bottleneck_endpoint(request: BottleneckRequest):
    risk_score, is_bottleneck = predictor.predict_bottleneck(
        request.wip_count, request.avg_cycle_time
    )
    return BottleneckResponse(
        risk_score=risk_score, is_bottleneck_predicted=is_bottleneck
    )



# /api/v1/review/pr and /api/v1/review/refusal-criteria removed entirely (2026-07-26, operator directive
# after a live incident: "все чинить! баг это пиздец"). Root cause of that incident: this endpoint had a
# hardcoded, project-agnostic "Kano Refactoring" follow-up-task generator (leftover chess-demo code) that
# fired on every review of a BARCAN-TAG-02/11 role, unconditionally, regardless of what the project
# actually was - the new task then went through review itself and generated ANOTHER one, a self-
# perpetuating duplicate-task generator that ran for hours on test-thirty-seventh before being caught. Both
# endpoints had zero remaining Java callers already (JulesDispatchService.executeCodeReview and
# AutoMergeService's refusal-criteria check were both moved to Jules-only review earlier the same night for
# cost reasons) - deleted rather than left as unreachable dead code that could silently come back.


@app.post("/api/v1/assistant/chat", response_model=ChatResponse)
async def assistant_chat_endpoint(request: ChatRequest):
    try:
        api_key = request.apiKey or os.getenv("GEMINI_API_KEY", "")
        if not api_key:
            return ChatResponse(text=(
                "Gemini API key is not configured for the ML service. "
                "Backend project facts may be available, but free-form model answering is disabled."
            ))

        response_text = None
        if request.cacheKey:
            # The caller promises systemInstruction is static/repeated under this key, so it's billed at
            # Gemini's reduced cached-token rate on every call after the first instead of resent in full.
            primary_model = gemini_candidate_models(request.modelTier, request.modelOverride)[0]
            cache_name = ensure_gemini_cache(primary_model, request.cacheKey, request.systemInstruction, api_key.strip())
            if cache_name:
                force_json = "return only json" in request.systemInstruction.lower() \
                    or "return valid json" in request.systemInstruction.lower()
                try:
                    response_text = ask_gemini_cached(request.prompt, cache_name, primary_model, api_key.strip(), force_json)
                except Exception as e:
                    print(f"Cached chat call failed, falling back to uncached: {e}")
                    response_text = None

        if response_text is None:
            response_text = ask_gemini(request.prompt, request.systemInstruction, api_key, request.modelTier, request.modelOverride)
        cleaned = response_text
        if cleaned.strip().startswith("```"):
            lines = cleaned.strip().split("\n")
            if len(lines) > 2:
                cleaned = "\n".join(lines[1:-1])
        if not cleaned or cleaned.strip() == "{}":
            cleaned = "Gemini returned an empty response. No model-generated facts were added."

        return ChatResponse(text=cleaned)
    except HTTPException:
        raise
    except Exception as e:
        print(f"Assistant Chat Exception: {e}")
        raise HTTPException(status_code=502, detail=f"Gemini call failed: {e}") from e


@app.post("/api/v1/embed", response_model=EmbedResponse)
async def embed_endpoint(request: EmbedRequest):
    # Backs GeminiContextService's RAG retrieval (2026-07-25) - a real embedding vector via Gemini's
    # embedContent API, no mock/fallback text response like assistant_chat_endpoint has, because a fake
    # vector would silently corrupt cosine-similarity ranking rather than visibly failing.
    try:
        vector = ask_gemini_embedding(request.text, request.apiKey)
        if not vector:
            raise HTTPException(status_code=502, detail="Gemini embedding call returned no vector (no API key configured, or empty input text).")
        return EmbedResponse(embedding=vector)
    except HTTPException:
        raise
    except Exception as e:
        print(f"Embed Exception: {e}")
        raise HTTPException(status_code=502, detail=f"Gemini embedding call failed: {e}") from e


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
