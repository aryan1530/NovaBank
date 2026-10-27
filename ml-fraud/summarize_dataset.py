"""Build a privacy-minimized dashboard summary from the synthetic CSV bundle."""
import json
from pathlib import Path

import pandas as pd


def main():
    data = Path(__file__).parent / "training-data"
    out = Path(__file__).parent / "models" / "demo-overview.json"
    tx = pd.read_csv(data / "transactions.csv", low_memory=False)
    accounts = pd.read_csv(data / "accounts.csv", low_memory=False)
    customers = pd.read_csv(data / "customers.csv", low_memory=False)
    loans = pd.read_csv(data / "loans.csv", low_memory=False)
    tx["timestamp"] = pd.to_datetime(tx["timestamp"], errors="coerce", utc=True)
    tx["amount"] = pd.to_numeric(tx["amount"], errors="coerce").fillna(0)
    tx["is_fraud"] = pd.to_numeric(tx["is_fraud"], errors="coerce").fillna(0).astype(int)
    tx["month_key"] = tx.timestamp.dt.strftime("%Y-%m")
    monthly = []
    for month, rows in tx.groupby("month_key", sort=True):
        monthly.append({
            "month": pd.to_datetime(month + "-01").strftime("%b"), "transactions": int(len(rows)),
            "volumeInr": round(float(rows.amount.sum()), 2), "fraudCount": int(rows.is_fraud.sum()),
        })
    categories = tx[tx.txn_type.eq("DEBIT")].groupby("txn_category").amount.sum().sort_values(ascending=False).head(6)
    fraud_types = tx[tx.is_fraud.eq(1)].groupby("fraud_type").size().sort_values(ascending=False)
    active_accounts = accounts[accounts.status.eq("ACTIVE")]
    sample_accounts = []
    for row in active_accounts.head(4).itertuples(index=False):
        number = str(getattr(row, "account_number", ""))
        sample_accounts.append({
            "type": str(getattr(row, "account_type", "Savings")),
            "bank": str(getattr(row, "bank_name", "")),
            "lastFour": number[-4:],
            "balanceInr": round(float(pd.to_numeric(getattr(row, "current_balance", 0), errors="coerce") or 0), 2),
        })
    recent = tx.sort_values("timestamp", ascending=False).head(6)
    out.write_text(json.dumps({
        "label": "Synthetic portfolio · Jul–Sep 2026",
        "dataIsSynthetic": True,
        "customers": int(len(customers)), "accounts": int(len(accounts)),
        "activeAccounts": int(len(active_accounts)),
        "sampleBalanceInr": round(float(pd.to_numeric(active_accounts.current_balance, errors="coerce").fillna(0).sum()), 2),
        "transactions": int(len(tx)), "fraudTransactions": int(tx.is_fraud.sum()),
        "fraudRate": float(tx.is_fraud.mean()),
        "loans": int(len(loans)),
        "loanOutstandingInr": round(float(pd.to_numeric(loans.outstanding_inr, errors="coerce").fillna(0).sum()), 2),
        "sampleAccounts": sample_accounts,
        "recentActivity": [{
            "date": row.timestamp.isoformat(), "amountInr": round(float(row.amount), 2),
            "category": str(row.txn_category), "rail": str(row.payment_rail),
            "direction": str(row.txn_type), "fraudFlagged": bool(row.is_fraud),
        } for row in recent.itertuples(index=False)],
        "monthly": monthly,
        "categories": [{"name": str(k), "amountInr": round(float(v), 2)} for k, v in categories.items()],
        "fraudTypes": [{"name": str(k).replace("_", " ").title(), "count": int(v)} for k, v in fraud_types.items()],
    }, indent=2))
    print(f"Wrote privacy-minimized dashboard summary: {out}")


if __name__ == "__main__":
    main()
