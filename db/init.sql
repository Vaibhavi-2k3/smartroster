-- Shifts: 0 = morning, 1 = evening, 2 = night. Days: 0 = Monday ... 6 = Sunday.
CREATE TABLE employees (
  id           BIGSERIAL PRIMARY KEY,
  name         TEXT NOT NULL,
  hourly_rate  NUMERIC(8,2) NOT NULL CHECK (hourly_rate >= 0),
  max_shifts   INT NOT NULL DEFAULT 5 CHECK (max_shifts BETWEEN 0 AND 7)
);

CREATE TABLE unavailability (
  employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
  day         INT NOT NULL CHECK (day BETWEEN 0 AND 6),
  shift       INT NOT NULL CHECK (shift BETWEEN 0 AND 2),
  PRIMARY KEY (employee_id, day, shift)
);

CREATE TABLE demand (
  day      INT NOT NULL CHECK (day BETWEEN 0 AND 6),
  shift    INT NOT NULL CHECK (shift BETWEEN 0 AND 2),
  required INT NOT NULL CHECK (required >= 0),
  PRIMARY KEY (day, shift)
);

CREATE TABLE schedules (
  id         BIGSERIAL PRIMARY KEY,
  status     TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING | OPTIMAL | FEASIBLE | INFEASIBLE | FAILED
  cost       NUMERIC(12,2),
  result     JSONB,
  error      TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_schedules_created ON schedules (created_at DESC);

-- Seed data
INSERT INTO employees (name, hourly_rate, max_shifts) VALUES
  ('Aarav', 18.00, 5), ('Priya', 20.00, 5), ('Rohan', 16.50, 5), ('Meera', 19.00, 4),
  ('Kabir', 17.00, 5), ('Ananya', 21.00, 5), ('Vikram', 15.50, 5), ('Isha', 18.50, 4);

INSERT INTO unavailability VALUES (2, 5, 0), (2, 5, 1), (2, 5, 2), (4, 0, 2), (4, 1, 2);

INSERT INTO demand (day, shift, required)
SELECT d, s, CASE WHEN s = 2 THEN 1 WHEN d >= 5 AND s = 1 THEN 3 ELSE 2 END
FROM generate_series(0, 6) d, generate_series(0, 2) s;
