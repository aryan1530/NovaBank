from pathlib import Path
import json
from fastapi import FastAPI
from pydantic import BaseModel, Field
import xgboost as xgb
import numpy as np

MODEL_PATH = Path(__file__).parent / "models" / "fraud_xgb.json"
METRICS_PATH = Path(__file__).parent / "models" / "metrics.json"
SUMMARY_PATH = Path(__file__).parent / "models" / "demo-overview.json"
CHANNELS = {"NEFT": 0, "IMPS": 1, "RTGS": 2, "UPI": 3, "INTERNAL": 4}
FEATURES = ["amount", "hour", "frequency_1h", "new_payee", "channel", "international"]

app = FastAPI(title="NovaBank Fraud ML", version="1.0.0")
model = xgb.Booster() if MODEL_PATH.exists() else None
if model is not None:
    model.load_model(str(MODEL_PATH))
MODEL_METRICS = json.loads(METRICS_PATH.read_text()) if METRICS_PATH.exists() else {}


class Features(BaseModel):
    amount: float = Field(gt=0, le=1_000_000_000)
    channel: str = "IMPS"
    userId: int | None = None
    debitAccountId: int | None = None
    hour: int = Field(default=12, ge=0, le=23)
    frequency_1h: int = Field(default=1, ge=0, le=100)
    new_payee: int = Field(default=0, ge=0, le=1)
    international: int = Field(default=0, ge=0, le=1)


@app.get("/health")
def health():
    return {"status": "up", "modelLoaded": model is not None}


@app.get("/metrics")
def metrics():
    if METRICS_PATH.exists():
        return json.loads(METRICS_PATH.read_text())
    return {"error": "train.py has not been run"}


@app.get("/demo/overview")
def demo_overview():
    if SUMMARY_PATH.exists():
        return json.loads(SUMMARY_PATH.read_text())
    return {"available": False, "message": "No synthetic CSV bundle was supplied for this build."}


@app.post("/predict")
def predict(f: Features):
    ch = CHANNELS.get(f.channel.upper(), 1)
    x = np.array([[f.amount, f.hour, f.frequency_1h, f.new_payee, ch, f.international]], dtype=float)
    if model is None:
        score = min(0.99, f.amount / 500000)
        return {"score": round(score, 4), "reasons": ["fallback_amount_heuristic"]}
    matrix = xgb.DMatrix(x, feature_names=FEATURES)
    proba = float(model.predict(matrix)[0])
    contributions = model.predict(matrix, pred_contribs=True)[0]
    feature_contributions = [
        {"feature": FEATURES[i], "contribution": round(float(contributions[i]), 5)}
        for i in np.argsort(np.abs(contributions[:-1]))[::-1][:3]
    ]
    reasons = []
    if f.amount > 100000:
        reasons.append("high_amount")
    if f.hour <= 5 or f.hour >= 23:
        reasons.append("off_hours")
    if f.frequency_1h >= 5:
        reasons.append("velocity")
    if f.new_payee:
        reasons.append("new_payee")
    if not reasons:
        reasons.extend(item["feature"] for item in feature_contributions if item["contribution"] > 0)
    if not reasons:
        reasons.append("model_score")
    threshold = float(MODEL_METRICS.get("threshold", 0.5))
    risk_level = "HIGH" if proba >= 0.85 else "REVIEW" if proba >= threshold else "LOW"
    return {"score": round(proba, 4), "threshold": round(threshold, 4), "riskLevel": risk_level,
            "reasons": reasons, "explain": reasons, "featureContributions": feature_contributions}
