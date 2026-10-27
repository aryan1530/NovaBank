"""Train on the supplied labeled transaction CSV, with time-separated evaluation.

Only features available to the live transfer scorer are used. Identifiers,
post-transaction values, outcome fields and fraud labels are excluded from X.
"""
from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.metrics import average_precision_score, f1_score, precision_recall_curve, precision_score, recall_score
from sklearn.model_selection import train_test_split
from xgboost import XGBClassifier

CHANNELS = {"NEFT": 0, "IMPS": 1, "RTGS": 2, "UPI": 3, "INTERNAL": 4}
FEATURES = ["amount", "hour", "frequency_1h", "new_payee", "channel", "international"]
DEFAULT_INPUT = Path(__file__).parent / "training-data" / "transactions.csv"


def synthetic_dataset(rows: int = 20_000) -> pd.DataFrame:
    rng = np.random.default_rng(42)
    amount = rng.lognormal(8.0, 1.15, rows).clip(1, 1_000_000_000)
    hour = rng.integers(0, 24, rows)
    frequency = rng.poisson(1.2, rows)
    new_payee = rng.binomial(1, 0.12, rows)
    channel = rng.integers(0, 5, rows)
    international = rng.binomial(1, 0.04, rows)
    night = (hour <= 5) | (hour >= 23)
    logits = -5.2 + 0.000004 * amount + 0.38 * frequency + 1.25 * new_payee + 1.1 * international + 0.75 * night
    fraud_probability = 1 / (1 + np.exp(-logits))
    y = rng.binomial(1, fraud_probability)
    return pd.DataFrame(dict(amount=amount, hour=hour, frequency_1h=frequency, new_payee=new_payee,
                             channel=channel, international=international, y=y))


def prepare_csv(path: Path) -> tuple[pd.DataFrame, pd.Series, pd.Series, dict]:
    df = pd.read_csv(path, low_memory=False)
    required = {"timestamp", "amount", "payment_rail", "txn_count_1h", "is_new_beneficiary", "is_international", "is_fraud"}
    missing = sorted(required - set(df.columns))
    if missing:
        raise ValueError(f"Training CSV is missing required fields: {', '.join(missing)}")
    df["timestamp"] = pd.to_datetime(df["timestamp"], errors="coerce", utc=True)
    for field in ("amount", "txn_count_1h", "is_new_beneficiary", "is_international", "is_fraud"):
        df[field] = pd.to_numeric(df[field], errors="coerce")
    df = df.dropna(subset=["timestamp", "amount", "txn_count_1h", "is_new_beneficiary", "is_international", "is_fraud"])
    df = df[(df.amount > 0) & df.is_fraud.isin([0, 1])].sort_values("timestamp")
    if df.empty or df.is_fraud.nunique() != 2:
        raise ValueError("Training CSV must contain both legitimate and fraud-labeled transactions")
    X = pd.DataFrame({
        "amount": df.amount.clip(upper=1_000_000_000),
        "hour": df["timestamp"].dt.hour,
        "frequency_1h": df.txn_count_1h.clip(0, 100),
        "new_payee": df.is_new_beneficiary.clip(0, 1),
        "channel": df.payment_rail.fillna("INTERNAL").str.upper().map(CHANNELS).fillna(CHANNELS["INTERNAL"]),
        "international": df.is_international.clip(0, 1),
    }, columns=FEATURES).astype(float)
    y = df.is_fraud.astype(int)
    times = df.timestamp
    return X, y, times, {"source": path.name, "range_start": times.min().isoformat(), "range_end": times.max().isoformat()}


def choose_threshold(y, scores):
    precision, recall, thresholds = precision_recall_curve(y, scores)
    if not len(thresholds):
        return 0.5
    # Prefer useful recall while holding validation precision to at least 80%.
    eligible = np.flatnonzero(precision[:-1] >= 0.80)
    if len(eligible):
        best = eligible[np.argmax(recall[eligible])]
    else:
        f1 = 2 * precision[:-1] * recall[:-1] / np.maximum(precision[:-1] + recall[:-1], 1e-12)
        best = int(np.argmax(f1))
    return float(thresholds[best])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "models")
    args = parser.parse_args()
    if args.input.exists():
        X, y, timestamps, meta = prepare_csv(args.input)
        train_mask = timestamps < pd.Timestamp("2026-08-25", tz="UTC")
        val_mask = (timestamps >= pd.Timestamp("2026-08-25", tz="UTC")) & (timestamps < pd.Timestamp("2026-09-01", tz="UTC"))
        test_mask = timestamps >= pd.Timestamp("2026-09-01", tz="UTC")
        if min(int(train_mask.sum()), int(val_mask.sum()), int(test_mask.sum())) == 0:
            raise ValueError("Temporal split requires rows in training (before Aug 25), validation (Aug 25–31), and test (September)")
        split = (X[train_mask], y[train_mask], X[val_mask], y[val_mask], X[test_mask], y[test_mask])
        train_x, train_y, val_x, val_y, test_x, test_y = split
        meta["split"] = {"train_before": "2026-08-25", "validation": "2026-08-25..2026-08-31", "test_from": "2026-09-01"}
    else:
        df = synthetic_dataset()
        X, y = df[FEATURES], df.y
        train_x, test_x, train_y, test_y = train_test_split(X, y, test_size=0.25, random_state=42, stratify=y)
        train_x, val_x, train_y, val_y = train_test_split(train_x, train_y, test_size=0.2, random_state=42, stratify=train_y)
        meta = {"source": "built-in synthetic fallback", "split": "stratified 60/20/20"}

    positives = int(train_y.sum())
    negatives = int(len(train_y) - positives)
    model = XGBClassifier(n_estimators=420, max_depth=4, learning_rate=0.045, min_child_weight=2,
                          subsample=0.9, colsample_bytree=0.9, reg_lambda=2.0,
                          scale_pos_weight=max(1.0, negatives / max(positives, 1)),
                          eval_metric="aucpr", random_state=42, n_jobs=2)
    model.fit(train_x, train_y, eval_set=[(val_x, val_y)], verbose=False)
    threshold = choose_threshold(val_y, model.predict_proba(val_x)[:, 1])
    scores = model.predict_proba(test_x)[:, 1]
    predicted = (scores >= threshold).astype(int)
    tn = int(((predicted == 0) & (test_y.to_numpy() == 0)).sum())
    fp = int(((predicted == 1) & (test_y.to_numpy() == 0)).sum())
    metrics = {
        "rows": int(len(y)), "training_rows": int(len(train_y)), "validation_rows": int(len(val_y)),
        "test_rows": int(len(test_y)), "fraud_rows": int(y.sum()), "positive_rate": float(y.mean()),
        "precision": float(precision_score(test_y, predicted, zero_division=0)),
        "recall": float(recall_score(test_y, predicted, zero_division=0)),
        "f1": float(f1_score(test_y, predicted, zero_division=0)),
        "pr_auc": float(average_precision_score(test_y, scores)),
        "false_positive_rate": float(fp / max(tn + fp, 1)), "threshold": threshold,
        "features": FEATURES, "evaluation": "held-out time period; threshold selected on validation period",
        "trained_at_utc": datetime.now(timezone.utc).isoformat(), **meta,
    }
    args.output.mkdir(parents=True, exist_ok=True)
    model.save_model(args.output / "fraud_xgb.json")
    (args.output / "metrics.json").write_text(json.dumps(metrics, indent=2))
    print(json.dumps(metrics, indent=2))


if __name__ == "__main__":
    main()
