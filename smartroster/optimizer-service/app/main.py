from fastapi import FastAPI
from pydantic import BaseModel, Field

from .solver import solve

app = FastAPI(title="SmartRoster Optimizer", version="1.0.0")


class Slot(BaseModel):
    day: int = Field(ge=0, le=6)
    shift: int = Field(ge=0, le=2)


class Employee(BaseModel):
    id: int
    name: str
    hourlyRate: float = Field(ge=0)
    maxShifts: int = Field(default=5, ge=0, le=7)
    unavailable: list[Slot] = []


class Demand(BaseModel):
    day: int = Field(ge=0, le=6)
    shift: int = Field(ge=0, le=2)
    required: int = Field(ge=0)


class SolveRequest(BaseModel):
    employees: list[Employee]
    demand: list[Demand]
    timeLimitSeconds: float = Field(default=10.0, gt=0, le=120)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/solve")
def solve_endpoint(req: SolveRequest):
    return solve(req.employees, req.demand, req.timeLimitSeconds)
