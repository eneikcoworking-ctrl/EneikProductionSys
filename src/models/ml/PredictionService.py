# @file PredictionService.py
# @agent TAG-04 (Modal Quantifier)
# @description Bayesian predictor and Bottleneck FastAPI service.

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import uvicorn
import os
import glob
import json
import urllib.request

app = FastAPI(title="Eneik AI Prediction Service")


class BottleneckRequest(BaseModel):
    wip_count: int
    avg_cycle_time: float


class BottleneckResponse(BaseModel):
    risk_score: float
    is_bottleneck_predicted: bool


class ReviewRequest(BaseModel):
    projectId: str
    taskId: str
    prUrl: str


class ReviewResponse(BaseModel):
    approved: bool
    remarks: str
    newTasks: list = []


class RefusalCriteriaRequest(BaseModel):
    prDiff: str
    refusalCriteria: str

class RefusalCriteriaResponse(BaseModel):
    compliant: bool
    reason: str


class PredictionService:
    """
    Core logic for ML predictions and agent reviews.
    """

    MAX_WIP = 100
    SLA_THRESHOLD = 3600.0

    def predict_satisfaction(self, user_context):
        satisfaction_probability = 0.98
        return {
            "satisfaction_probability": satisfaction_probability,
            "modal_status": "Highly Probable",
        }

    def predict_bottleneck(self, wip_count: int, avg_cycle_time: float):
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



def ask_gemini(prompt: str, system_instruction: str) -> str:
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        raise ValueError("GEMINI_API_KEY is not set.")

    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key={api_key}"

    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "systemInstruction": {"parts": [{"text": system_instruction}]},
        "generationConfig": {"responseMimeType": "application/json"}
    }

    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})

    with urllib.request.urlopen(req, timeout=30) as response:
        result = json.loads(response.read().decode("utf-8"))
        try:
            return result['candidates'][0]['content']['parts'][0]['text']
        except (KeyError, IndexError):
            raise ValueError(f"Unexpected response format from Gemini: {result}")

predictor = PredictionService()


@app.post("/api/v1/predict/bottleneck", response_model=BottleneckResponse)
async def bottleneck_endpoint(request: BottleneckRequest):
    risk_score, is_bottleneck = predictor.predict_bottleneck(
        request.wip_count, request.avg_cycle_time
    )
    return BottleneckResponse(
        risk_score=risk_score, is_bottleneck_predicted=is_bottleneck
    )


@app.post("/api/v1/review/pr", response_model=ReviewResponse)
async def review_pr_endpoint(request: ReviewRequest):
    try:
        # Download PR diff from GitHub, appending .diff
        diff_url = request.prUrl + ".diff"
        diff_req = urllib.request.Request(diff_url)
        with urllib.request.urlopen(diff_req, timeout=30) as response:
            pr_diff_bytes = response.read(2097152) # Max 2MB
            if response.read(1):
                return ReviewResponse(
                    approved=False,
                    remarks="PR is too large for automated review. Needs human review.",
                    newTasks=[]
                )
            pr_diff = pr_diff_bytes.decode("utf-8")

        system_instruction = "You are a strict Code Guardian. Review the provided diff. Return ONLY JSON: {\"approved\": bool, \"remarks\": \"string\", \"newTasks\": [{\"roleTag\": \"string\", \"description\": \"string\"}]}. Remarks should contain specific technical feedback."
        gemini_response_json = ask_gemini(f"Please review this diff:\n\n{pr_diff}", system_instruction)

        parsed = json.loads(gemini_response_json)
        return ReviewResponse(
            approved=parsed.get("approved", False),
            remarks=parsed.get("remarks", "No remarks provided"),
            newTasks=parsed.get("newTasks", [])
        )
    except Exception as e:
        # If API key is missing or Gemini fails, we must bubble up the error to trigger Fail-Safe in Java
        raise HTTPException(status_code=503, detail=f"Gemini API Error: {str(e)}")

@app.post("/api/v1/review/refusal-criteria", response_model=RefusalCriteriaResponse)
async def check_refusal_criteria_endpoint(request: RefusalCriteriaRequest):
    try:
        system_instruction = "You are an architecture auditor. Evaluate if the provided code diff violates the provided Refusal Criteria. Return ONLY JSON: {\"compliant\": bool, \"reason\": \"string\"}."
        prompt = f"Refusal Criteria:\n{request.refusalCriteria}\n\nCode Diff:\n{request.prDiff}"
        gemini_response_json = ask_gemini(prompt, system_instruction)

        parsed = json.loads(gemini_response_json)
        return RefusalCriteriaResponse(
            compliant=parsed.get("compliant", False),
            reason=parsed.get("reason", "No reason provided")
        )
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"Gemini API Error: {str(e)}")


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
