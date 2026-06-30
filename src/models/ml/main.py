from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()

class PredictRequest(BaseModel):
    wip_count: int
    avg_cycle_time: float

@app.post("/api/v1/predict/bottleneck")
async def predict_bottleneck(request: PredictRequest):
    return {
        "risk_score": 0.15,
        "is_bottleneck_predicted": False
    }

@app.get("/health")
async def health():
    return {"status": "ok"}
