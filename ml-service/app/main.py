"""Demand forecasting service.

Trains a gradient-boosted regressor at startup on synthetic hourly-footfall history
(weekday/weekend and time-of-day patterns plus noise) and predicts the number of staff
needed per (day, shift). To use real data, replace `synthetic_history()` with a query
against your own sales / footfall / patient-visit table.
"""
import numpy as np
from fastapi import FastAPI
from sklearn.ensemble import GradientBoostingRegressor

app = FastAPI(title="SmartRoster ML", version="1.0.0")
SHIFT_BASE = {0: 2.0, 1: 2.6, 2: 0.9}   # morning, evening, night
WEEKEND_LIFT = {0: 0.2, 1: 1.0, 2: 0.5}


def synthetic_history(weeks=104, seed=7):
    rng = np.random.default_rng(seed)
    X, y = [], []
    for _ in range(weeks):
        for d in range(7):
            for s in range(3):
                weekend = 1 if d >= 5 else 0
                demand = SHIFT_BASE[s] + weekend * WEEKEND_LIFT[s] + rng.normal(0, 0.25)
                X.append([d, s, weekend])
                y.append(max(0.0, demand))
    return np.array(X), np.array(y)


_X, _y = synthetic_history()
model = GradientBoostingRegressor(n_estimators=120, max_depth=3, random_state=7).fit(_X, _y)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/forecast")
def forecast():
    rows = [[d, s, 1 if d >= 5 else 0] for d in range(7) for s in range(3)]
    preds = model.predict(np.array(rows))
    return [{"day": r[0], "shift": r[1], "required": max(1, int(round(p)))}
            for r, p in zip(rows, preds)]
