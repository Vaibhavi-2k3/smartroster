# SmartRoster

Optimization-powered shift scheduling for small businesses (clinics, restaurants, retail, call centers).
A manager enters the team and how many people each shift needs; SmartRoster builds the weekly rota that
covers every shift at the lowest fair cost. An ML model can forecast demand instead of typing it in.

## Architecture

```
React (Vite)  ──►  Spring Boot API (Java 21)  ──►  PostgreSQL
 :3000/:5173            :8080     │  async job
                                  ├──────────►  Optimizer service (Python, FastAPI, OR-Tools CP-SAT)  :8001
                                  └──────────►  ML service (Python, FastAPI, scikit-learn)            :8002
```

| Tier | Tech | Role |
|---|---|---|
| `frontend/` | React 18, Vite, JavaScript | Rota grid, team management, demand editor, job polling |
| `backend-java/` | Spring Boot 3, JDBC, PostgreSQL | REST API, validation, async orchestration, persistence |
| `optimizer-service/` | Python, FastAPI, OR-Tools | Integer-programming scheduler |
| `ml-service/` | Python, FastAPI, scikit-learn | Gradient-boosting demand forecast |
| `db/` | PostgreSQL 16 | Relational schema + seed data |

Request flow: `POST /api/schedules` creates a `PENDING` row and returns an id immediately. A background task
fetches demand (from the table or the ML service), calls the optimizer, and stores the result as JSONB.
The UI polls `GET /api/schedules/{id}` until the status changes.

## Prerequisites

| Tool | Version | Required for |
|---|---|---|
| Docker Desktop | any recent | Running the full stack via Compose |
| Java JDK | 21 | Backend (local dev / tests) |
| Maven | 3.9+ | Backend build & tests |
| Python | 3.12 | Optimizer & ML services (local dev) |
| Node.js | 20+ | Frontend (local dev) |

Install JDK 21 and Maven on Windows with winget (Maven is not on winget, so download it manually):

```powershell
winget install --id Microsoft.OpenJDK.21 -e
# Maven: download from https://maven.apache.org/download.cgi, extract, add bin to PATH
# Or use scoop: scoop install maven
```

## Quick start (Docker Compose)

```bash
docker compose up --build
```

Open **http://localhost:3000**. The frontend, backend, optimizer, ML service, and database
all start together. Seed data (8 employees, unavailability, demand) loads automatically.

| Service | Port | URL |
|---|---|---|
| Frontend | 3000 | http://localhost:3000 |
| Backend API | 8080 | http://localhost:8080/api |
| Optimizer | 8001 | http://localhost:8001/health |
| ML forecast | 8002 | http://localhost:8002/health |
| PostgreSQL | 5432 (internal only) | — |

To stop: `docker compose down` (add `-v` to also wipe the database volume).

## Local development

Start the database on its own, then run each service in a separate terminal:

```bash
# Terminal 1 — database with seed data
docker compose up db

# Terminal 2 — optimizer
cd optimizer-service
pip install -r requirements.txt
uvicorn app.main:app --port 8001

# Terminal 3 — ML forecast service
cd ml-service
pip install -r requirements.txt
uvicorn app.main:app --port 8002

# Terminal 4 — backend
cd backend-java
mvn spring-boot:run

# Terminal 5 — frontend (http://localhost:5173)
cd frontend
npm install
npm run dev
```

The Vite dev server proxies `/api` to `http://localhost:8080` (see `vite.config.js`).

## Testing

```bash
# Python services
cd optimizer-service && pytest
cd ml-service        && pytest

# Backend — unit tests (MockMvc, no Docker needed)
cd backend-java && mvn test

# Backend — integration tests (Testcontainers, requires Docker running)
cd backend-java && mvn verify
```

The backend integration tests (`*IT.java`) use Testcontainers to spin up a real PostgreSQL
container, so Docker must be running. On CI (GitHub Actions, Linux) this works out of the box.

## The optimization model

Binary variable `x[e,d,s] = 1` when employee `e` works shift `s` on day `d`.

**Minimize** `labor cost + overtime premium + 1,000,000 * understaffed slots + fairness penalties`

| Constraint | Type |
|---|---|
| Availability (blocked slots forced to 0) | hard |
| At most one shift per person per day | hard |
| No night shift followed by a morning shift (rest rule) | hard |
| Weekly shift cap per person | hard |
| Coverage per shift | soft (shortfall variable, so the model is always feasible and shows exactly where you are short) |
| Overtime: +50% on shifts beyond 4 a week | piecewise-linear cost |
| Fairness: shrink the gap between busiest and least busy person, and the same for night shifts | penalty |

The solver is OR-Tools CP-SAT. Costs are scaled to integer cents.

## API

| Method | Path | Purpose |
|---|---|---|
| GET / POST | `/api/employees` | List / add (with `unavailable: [{day, shift}]`) |
| DELETE | `/api/employees/{id}` | Remove |
| GET / PUT | `/api/demand` | Required headcount per day and shift |
| POST | `/api/schedules?useForecast=true` | Start an optimization job (202 + id) |
| GET | `/api/schedules/{id}` · `/api/schedules/latest` | Job status and result |

Days are 0 (Mon) to 6 (Sun); shifts are 0 morning, 1 evening, 2 night.

## Roadmap (good next steps)

- Auth and multi-tenancy (add `organization_id` to every table, Spring Security + JWT)
- Message queue (Redis Streams / RabbitMQ / Kafka) between the API and solver workers, so you can scale workers horizontally
- Employee portal: availability entry, time-off requests, shift swaps
- Replace the synthetic training data in `ml-service` with real footfall/sales history; try Prophet or XGBoost
- Nonlinear extensions: fatigue cost that grows with consecutive shifts, tuned with SciPy or Bayesian optimization
- Local-search / simulated-annealing fallback for very large instances, and benchmarks against CP-SAT
- Natural-language constraints ("Priya never closes twice in a row") translated to model constraints with an LLM
- Notifications (Twilio / WhatsApp), Google Calendar sync, payroll CSV export
- Deploy: Render / Railway / Fly.io or Kubernetes, with Flyway migrations replacing `init.sql`

## License

MIT
