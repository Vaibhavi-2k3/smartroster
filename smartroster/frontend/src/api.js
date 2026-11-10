async function request(path, options) {
  const res = await fetch(`/api${path}`, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  return res.status === 204 ? null : res.json();
}

export const api = {
  employees: () => request("/employees"),
  addEmployee: (e) => request("/employees", { method: "POST", body: JSON.stringify(e) }),
  deleteEmployee: (id) => request(`/employees/${id}`, { method: "DELETE" }),
  demand: () => request("/demand"),
  saveDemand: (rows) => request("/demand", { method: "PUT", body: JSON.stringify(rows) }),
  generate: (useForecast) => request(`/schedules?useForecast=${useForecast}`, { method: "POST" }),
  schedule: (id) => request(`/schedules/${id}`),
  latest: () => request("/schedules/latest"),
};

export const DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
export const SHIFTS = ["Morning", "Evening", "Night"];
