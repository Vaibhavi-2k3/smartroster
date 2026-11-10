from fastapi.testclient import TestClient

from app.main import app


def test_forecast_shape_and_weekend_evening_peak():
    r = TestClient(app).get("/forecast")
    assert r.status_code == 200
    data = {(x["day"], x["shift"]): x["required"] for x in r.json()}
    assert len(data) == 21
    assert data[(5, 1)] >= data[(1, 1)]
    assert all(v >= 1 for v in data.values())
