from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def employees(n=6):
    return [{"id": i, "name": f"E{i}", "hourlyRate": 15 + i, "maxShifts": 5, "unavailable": []}
            for i in range(1, n + 1)]


def demand(req=1):
    return [{"day": d, "shift": s, "required": req} for d in range(7) for s in range(3)]


def solve(emps, dem):
    r = client.post("/solve", json={"employees": emps, "demand": dem, "timeLimitSeconds": 5})
    assert r.status_code == 200
    return r.json()


def test_full_coverage_when_enough_staff():
    res = solve(employees(), demand(1))
    assert res["status"] in ("OPTIMAL", "FEASIBLE")
    assert res["shortfalls"] == []
    assert len(res["assignments"]) == 21


def test_respects_unavailability_and_one_shift_per_day():
    emps = employees()
    emps[0]["unavailable"] = [{"day": 0, "shift": s} for s in range(3)]
    res = solve(emps, demand(1))
    mine = [a for a in res["assignments"] if a["employeeId"] == 1]
    assert all(a["day"] != 0 for a in mine)
    per_day = {}
    for a in res["assignments"]:
        per_day[(a["employeeId"], a["day"])] = per_day.get((a["employeeId"], a["day"]), 0) + 1
    assert max(per_day.values()) == 1


def test_no_night_then_morning():
    res = solve(employees(), demand(1))
    worked = {(a["employeeId"], a["day"], a["shift"]) for a in res["assignments"]}
    for (e, d, s) in worked:
        if s == 2:
            assert (e, d + 1, 0) not in worked


def test_reports_shortfall_when_understaffed():
    res = solve(employees(1), demand(2))
    assert res["shortfalls"], "expected unfilled shifts"
