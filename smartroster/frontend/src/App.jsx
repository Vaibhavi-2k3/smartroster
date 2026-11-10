import { useCallback, useEffect, useRef, useState } from "react";
import { api, DAYS, SHIFTS } from "./api.js";

const emptyForm = { name: "", hourlyRate: 18, maxShifts: 5 };

export default function App() {
  const [employees, setEmployees] = useState([]);
  const [demand, setDemand] = useState([]);
  const [schedule, setSchedule] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [useForecast, setUseForecast] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const poll = useRef(null);

  const load = useCallback(async () => {
    try {
      const [e, d, s] = await Promise.all([api.employees(), api.demand(), api.latest()]);
      setEmployees(e);
      setDemand(d);
      setSchedule(s);
    } catch (err) {
      setError(`Could not reach the server (${err.message}). Is the backend running?`);
    }
  }, []);

  useEffect(() => {
    load();
    return () => clearInterval(poll.current);
  }, [load]);

  const generate = async () => {
    setBusy(true);
    setError("");
    try {
      const { id } = await api.generate(useForecast);
      clearInterval(poll.current);
      poll.current = setInterval(async () => {
        let s;
        try {
          s = await api.schedule(id);
        } catch (err) {
          clearInterval(poll.current);
          setBusy(false);
          setError(`Lost connection while scheduling: ${err.message}`);
          return;
        }
        if (s.status === "PENDING") return;
        clearInterval(poll.current);
        setSchedule(s);
        setBusy(false);
        if (s.status === "FAILED") {
          setError(`Scheduling failed: ${s.error}`);
        } else if (useForecast) {
          try {
            setDemand(await api.demand());
          } catch (err) {
            setError(`Could not reload forecasted demand: ${err.message}`);
          }
        }
      }, 800);
    } catch (err) {
      setBusy(false);
      setError(`Could not start scheduling: ${err.message}`);
    }
  };

  const addEmployee = async (ev) => {
    ev.preventDefault();
    if (!form.name.trim()) return;
    await api.addEmployee({ ...form, hourlyRate: +form.hourlyRate, maxShifts: +form.maxShifts, unavailable: [] });
    setForm(emptyForm);
    setEmployees(await api.employees());
  };

  const removeEmployee = async (id) => {
    await api.deleteEmployee(id);
    setEmployees(await api.employees());
  };

  const setRequired = async (day, shift, value) => {
    const required = Math.max(0, Math.min(20, +value || 0));
    const next = demand.map((d) => (d.day === day && d.shift === shift ? { ...d, required } : d));
    setDemand(next);
    await api.saveDemand([{ day, shift, required }]);
  };

  const result = schedule?.result;
  const cell = (d, s) => (result?.assignments || []).filter((a) => a.day === d && a.shift === s);
  const missing = (d, s) => (result?.shortfalls || []).find((g) => g.day === d && g.shift === s)?.missing || 0;
  const need = (d, s) => demand.find((x) => x.day === d && x.shift === s)?.required ?? 0;

  return (
    <main>
      <header className="top">
        <h1>SmartRoster</h1>
        <p>Build a weekly rota that covers every shift at the lowest fair cost.</p>
      </header>

      {error && <div className="alert" role="alert">{error}</div>}

      <section className="panel">
        <div className="panel-head">
          <h2>Weekly rota</h2>
          <div className="actions">
            <label className="check">
              <input type="checkbox" checked={useForecast} onChange={(e) => setUseForecast(e.target.checked)} />
              Use demand forecast
            </label>
            <button className="primary" onClick={generate} disabled={busy || employees.length === 0}>
              {busy ? "Optimizing…" : "Generate rota"}
            </button>
          </div>
        </div>

        {result?.assignments?.length ? (
          <p className="summary">
            Status <b>{schedule.status}</b> · labor cost <b>${schedule.cost?.toFixed(2)}</b>
            {result.shortfalls.length > 0 && <span className="warn"> · {result.shortfalls.length} shifts understaffed</span>}
          </p>
        ) : (
          <p className="summary">No rota yet. Add your team, set how many people each shift needs, then generate.</p>
        )}

        <div className="scroll">
          <table className="grid">
            <thead>
              <tr>
                <th />
                {DAYS.map((d) => <th key={d}>{d}</th>)}
              </tr>
            </thead>
            <tbody>
              {SHIFTS.map((label, s) => (
                <tr key={label}>
                  <th className={`shift s${s}`}>{label}</th>
                  {DAYS.map((_, d) => (
                    <td key={d} className={missing(d, s) ? "gap" : ""}>
                      <div className="names">
                        {cell(d, s).map((a) => <span key={a.employeeId} className={`chip s${s}`}>{a.name}</span>)}
                        {missing(d, s) > 0 && <span className="chip miss">{missing(d, s)} short</span>}
                      </div>
                      <label className="need">
                        needs
                        <input type="number" min="0" max="20" value={need(d, s)}
                               onChange={(e) => setRequired(d, s, e.target.value)} aria-label={`${DAYS[d]} ${label} staff needed`} />
                      </label>
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {result?.stats?.shiftsPerEmployee && (
          <div className="load">
            {Object.entries(result.stats.shiftsPerEmployee).map(([name, n]) => (
              <div key={name} className="load-row">
                <span>{name}</span>
                <span className="bar"><span style={{ width: `${(n / 7) * 100}%` }} /></span>
                <span>{n} shifts, {result.stats.nightsPerEmployee[name]} nights</span>
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="panel">
        <h2>Team</h2>
        <form className="add" onSubmit={addEmployee}>
          <input placeholder="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} aria-label="Name" />
          <label>$/hour <input type="number" step="0.5" min="0" value={form.hourlyRate} onChange={(e) => setForm({ ...form, hourlyRate: e.target.value })} /></label>
          <label>Max shifts/week <input type="number" min="0" max="7" value={form.maxShifts} onChange={(e) => setForm({ ...form, maxShifts: e.target.value })} /></label>
          <button type="submit">Add person</button>
        </form>
        {employees.length === 0 && <p className="summary">No one on the team yet. Add your first person above.</p>}
        <ul className="team">
          {employees.map((e) => (
            <li key={e.id}>
              <span><b>{e.name}</b> · ${e.hourlyRate.toFixed(2)}/h · up to {e.maxShifts} shifts
                {e.unavailable.length > 0 && ` · unavailable for ${e.unavailable.length} shifts`}</span>
              <button className="link" onClick={() => removeEmployee(e.id)}>Remove</button>
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
