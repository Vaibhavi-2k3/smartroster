"""Shift scheduling as an integer program, solved with OR-Tools CP-SAT.

Decision variable x[e,d,s] = 1 if employee e works shift s on day d.

Objective (all costs in integer cents):
  labor cost  +  overtime premium (piecewise-linear)  +  shortfall penalty  +  fairness penalty

Hard constraints: availability, one shift per day, no night->morning turnaround,
max shifts per week. Coverage is soft (shortfall variable) so the model is always
feasible and reports exactly where staff are missing.
"""
from ortools.sat.python import cp_model

DAYS, SHIFTS, SHIFT_HOURS = 7, 3, 8
OVERTIME_AFTER = 4          # shifts per week before the premium applies
OVERTIME_PREMIUM = 0.5      # +50% on shifts beyond the threshold
SHORTFALL_PENALTY = 1_000_000
FAIRNESS_WEIGHT = 2_000     # per shift of spread between busiest and least busy employee
NIGHT_FAIRNESS_WEIGHT = 1_000


def solve(employees, demand, time_limit=10.0):
    m = cp_model.CpModel()
    need = {(d.day, d.shift): d.required for d in demand}
    x = {(e.id, d, s): m.NewBoolVar(f"x_{e.id}_{d}_{s}")
         for e in employees for d in range(DAYS) for s in range(SHIFTS)}

    # Coverage (soft)
    shortfall = {}
    for d in range(DAYS):
        for s in range(SHIFTS):
            req = need.get((d, s), 0)
            sf = m.NewIntVar(0, req, f"sf_{d}_{s}")
            m.Add(sum(x[e.id, d, s] for e in employees) + sf >= req)
            shortfall[d, s] = sf

    totals, nights, overtime = {}, {}, {}
    for e in employees:
        for u in e.unavailable:
            m.Add(x[e.id, u.day, u.shift] == 0)
        for d in range(DAYS):
            m.Add(sum(x[e.id, d, s] for s in range(SHIFTS)) <= 1)
            if d < DAYS - 1:  # rest rule: night followed by morning is forbidden
                m.Add(x[e.id, d, 2] + x[e.id, d + 1, 0] <= 1)
        total = sum(x[e.id, d, s] for d in range(DAYS) for s in range(SHIFTS))
        m.Add(total <= e.maxShifts)
        t = m.NewIntVar(0, DAYS, f"tot_{e.id}")
        m.Add(t == total)
        ot = m.NewIntVar(0, DAYS, f"ot_{e.id}")
        m.Add(ot >= t - OVERTIME_AFTER)
        n = m.NewIntVar(0, DAYS, f"night_{e.id}")
        m.Add(n == sum(x[e.id, d, 2] for d in range(DAYS)))
        totals[e.id], overtime[e.id], nights[e.id] = t, ot, n

    ids = [e.id for e in employees]
    hi, lo = m.NewIntVar(0, DAYS, "hi"), m.NewIntVar(0, DAYS, "lo")
    nhi, nlo = m.NewIntVar(0, DAYS, "nhi"), m.NewIntVar(0, DAYS, "nlo")
    for i in ids:
        m.Add(hi >= totals[i]); m.Add(lo <= totals[i])
        m.Add(nhi >= nights[i]); m.Add(nlo <= nights[i])

    def cents(rate, mult=1.0):
        return int(round(rate * SHIFT_HOURS * mult * 100))

    labor = sum(x[e.id, d, s] * cents(e.hourlyRate)
                for e in employees for d in range(DAYS) for s in range(SHIFTS))
    ot_cost = sum(overtime[e.id] * cents(e.hourlyRate, OVERTIME_PREMIUM) for e in employees)
    penalty = (SHORTFALL_PENALTY * sum(shortfall.values())
               + FAIRNESS_WEIGHT * (hi - lo) + NIGHT_FAIRNESS_WEIGHT * (nhi - nlo))
    m.Minimize(labor + ot_cost + penalty)

    solver = cp_model.CpSolver()
    solver.parameters.max_time_in_seconds = time_limit
    solver.parameters.num_workers = 8
    status = solver.Solve(m)
    name = solver.StatusName(status)
    if status not in (cp_model.OPTIMAL, cp_model.FEASIBLE):
        return {"status": name, "cost": 0.0, "assignments": [], "shortfalls": [], "stats": {}}

    by_id = {e.id: e for e in employees}
    assignments = [
        {"employeeId": e.id, "name": e.name, "day": d, "shift": s}
        for e in employees for d in range(DAYS) for s in range(SHIFTS)
        if solver.Value(x[e.id, d, s])
    ]
    cost = sum(cents(by_id[a["employeeId"]].hourlyRate) for a in assignments)
    cost += sum(solver.Value(overtime[i]) * cents(by_id[i].hourlyRate, OVERTIME_PREMIUM) for i in ids)
    gaps = [{"day": d, "shift": s, "missing": solver.Value(v)}
            for (d, s), v in shortfall.items() if solver.Value(v) > 0]
    return {
        "status": name,
        "cost": cost / 100,
        "assignments": assignments,
        "shortfalls": gaps,
        "stats": {
            "shiftsPerEmployee": {by_id[i].name: solver.Value(totals[i]) for i in ids},
            "nightsPerEmployee": {by_id[i].name: solver.Value(nights[i]) for i in ids},
            "solveSeconds": round(solver.WallTime(), 3),
        },
    }
