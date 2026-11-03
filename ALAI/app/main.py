# app/main.py
import os
import asyncio
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Any, Dict, List

import httpx
import joblib
import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from ai.models import UnderwritingDecision

from config import RF_MODEL_PATH, KNN_MODEL_PATH
from app.stress_test import StressTestRequest, StressTestResponse, run_stress_test
from ai import ALCrew

# ----- Load models once at startup -----
knn_model = joblib.load(KNN_MODEL_PATH)
rf_model = joblib.load(RF_MODEL_PATH)

app = FastAPI(title="AL-AI Engine API")

# Thread pool for blocking CrewAI deliberation
_crew_executor = ThreadPoolExecutor(max_workers=4)


# ============================================================
# ML models
# ============================================================
class PropertyDetails(BaseModel):
    bedrooms: float = Field(..., ge=0)
    bathrooms: float = Field(..., ge=0)
    floor_area: float = Field(..., gt=0)
    property_type: str
    ber_rating: str
    county: str
    year_built: float = Field(..., ge=1800, le=2030)


FEATURES = [
    "bedrooms",
    "bathrooms",
    "floor_area",
    "property_type",
    "ber_rating",
    "county",
    "year_built",
]


# ============================================================
# Agentic underwriting
# ============================================================
class UnderwriteRequest(BaseModel):
    application_id: str
    income: float = Field(..., gt=0)
    debt: float = Field(..., ge=0)
    deposit: float = Field(..., ge=0)
    term_years: int = Field(..., ge=1, le=40)
    asking_price: float = Field(..., gt=0)
    property_specs: PropertyDetails  # reuse the ML schema


class UnderwriteResponse(BaseModel):
    application_id: str
    decision: str
    rationale: str
    ltv: float
    dti: float
    risk_rating: int
    conditions: List[str]
    credit_memo: str
    agent_trails: str
    agent_outputs: dict = {} 


class AssessRequest(BaseModel):
    income: float = Field(..., gt=0)
    debt: float = Field(..., ge=0)
    deposit: float = Field(..., ge=0)
    term_years: int = Field(..., ge=1, le=40)
    asking_price: float = Field(..., gt=0)
    property_specs: PropertyDetails  # reuse your existing model


class AssessResponse(BaseModel):
    predicted_value: float
    overvaluation_pct: float
    ltv: float
    dti: float
    stress_tested_repayment: float
    max_borrowing_capacity: float


 

def to_dataframe(details: PropertyDetails) -> pd.DataFrame:
    return pd.DataFrame([details.model_dump()])[FEATURES]


@app.post("/api/v1/ml/predict/knn")
def predict_knn(details: PropertyDetails):
    X = to_dataframe(details)
    prediction = knn_model.predict(X)[0]
    return {"model": "knn", "prediction": float(prediction)}


@app.post("/api/v1/ml/predict/rf")
def predict_rf(details: PropertyDetails):
    X = to_dataframe(details)
    prediction = rf_model.predict(X)[0]
    return {"model": "rf", "prediction": float(prediction)}


@app.post("/api/v1/ai/underwrite", response_model=UnderwriteResponse)
async def underwrite(request: UnderwriteRequest):
    """Run the full multi-agent underwriting committee."""
    inputs = {
        "income": request.income,
        "debt": request.debt,
        "deposit": request.deposit,
        "term_years": request.term_years,
        "asking_price": request.asking_price,
        "property_specs": request.property_specs.model_dump(),
    }

    crew = ALCrew().crew()

    try:
        loop = asyncio.get_event_loop()
        result = await asyncio.wait_for(
            loop.run_in_executor(_crew_executor, crew.kickoff, inputs),
            timeout=180.0,
        )
    except asyncio.TimeoutError:
        raise HTTPException(status_code=504, detail="Crew deliberation timed out")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Crew execution failed: {e}")


    decision_obj = result.pydantic
    if decision_obj is None:
        raise HTTPException(status_code=500, detail="No structured decision")

    # Assemble per-agent structured outputs
    agent_outputs = {}
    task_outputs = result.tasks_output  # list[TaskOutput]

    # Order matches the tasks list in crew(): affordability, valuation, market, underwriting
    if len(task_outputs) >= 3:
        if task_outputs[0].pydantic is not None:
            agent_outputs["affordability"] = task_outputs[0].pydantic.model_dump()
        if task_outputs[1].pydantic is not None:
            agent_outputs["valuation"] = task_outputs[1].pydantic.model_dump()
        if task_outputs[2].pydantic is not None:
            agent_outputs["market_research"] = task_outputs[2].pydantic.model_dump()

    return UnderwriteResponse(
        application_id=request.application_id,
        decision=decision_obj.decision,
        rationale=decision_obj.rationale,
        ltv=decision_obj.ltv,
        dti=decision_obj.dti,
        risk_rating=decision_obj.risk_rating,
        conditions=decision_obj.conditions,
        credit_memo=decision_obj.credit_memo,
        agent_trails=str(result),
        agent_outputs=agent_outputs,   # 👈 NEW
    )



@app.get("/api/v1/health")
async def ai_health():

    """Check Ollama reachability and cloud API key presence."""
    ollama_ok = False
    try:
        async with httpx.AsyncClient() as client:
            resp = await client.get("http://localhost:11434/api/tags", timeout=3.0)
            ollama_ok = resp.status_code == 200
    except Exception:
        pass

    return {
        "ollama_reachable": ollama_ok,
        "openai_key_set": bool(os.getenv("OPENAI_API_KEY")),
        "gemini_key_set": bool(os.getenv("GEMINI_API_KEY")),
        "groq_key_set": bool(os.getenv("GROQ_API_KEY"))
    }


@app.post("/api/v1/ai/stress-test", response_model=StressTestResponse)
async def stress_test(request: StressTestRequest):
    """Simulate rate shocks / property drops against a portfolio. No LLM."""
    return run_stress_test(request)


@app.post("/api/v1/ai/assess", response_model=AssessResponse)
def assess(request: AssessRequest):

    """Deterministic dossier numbers. No LLM, no crew."""
    specs = request.property_specs.model_dump()
    X = pd.DataFrame([specs])[FEATURES]
    predicted = float(rf_model.predict(X)[0])

    loan = max(request.asking_price - request.deposit, 0)
    ltv = (loan / predicted * 100) if predicted > 0 else 999
    overval = ((request.asking_price - predicted) / predicted * 100) if predicted > 0 else 0

    # Stress-test at current ECB benchmark + 2% buffer
    ECB_BENCHMARK = 3.75
    stress_rate = ECB_BENCHMARK + 2.0
    r = (stress_rate / 100) / 12
    n = request.term_years * 12
    if r == 0:
        monthly = loan / n if n else 0
    else:
        monthly = loan * r * (1 + r) ** n / ((1 + r) ** n - 1)

    annual_debt_service = monthly * 12 + request.debt
    dti = (annual_debt_service / request.income * 100) if request.income > 0 else 999
    max_borrowing = request.income * 3.5  # CBI 3.5x rule

    return AssessResponse(
        predicted_value=round(predicted, 2),
        overvaluation_pct=round(overval, 2),
        ltv=round(ltv, 2),
        dti=round(dti, 2),
        stress_tested_repayment=round(monthly, 2),
        max_borrowing_capacity=round(max_borrowing, 2),
    )


class BatchPredictItem(BaseModel):
    property_specs: PropertyDetails
    asking_price: float
    loan_amount: float


class BatchPredictRequest(BaseModel):
    items: List[BatchPredictItem]


class BatchPredictResult(BaseModel):
    prediction: float
    ltv: float
    overvaluation_pct: float


class BatchPredictResponse(BaseModel):
    results: List[BatchPredictResult]


@app.post("/api/v1/ml/predict/rf/batch", response_model=BatchPredictResponse)
def predict_rf_batch(request: BatchPredictRequest):
    results = []
    for item in request.items:
        specs = item.property_specs.model_dump()
        X = pd.DataFrame([specs])[FEATURES]
        pred = float(rf_model.predict(X)[0])
        ltv = (item.loan_amount / pred * 100) if pred > 0 else 999
        overval = ((item.asking_price - pred) / pred * 100) if pred > 0 else 0
        results.append(BatchPredictResult(
            prediction=round(pred, 2),
            ltv=round(ltv, 2),
            overvaluation_pct=round(overval, 2),
        ))
    return BatchPredictResponse(results=results)


