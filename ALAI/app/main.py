# app/main.py
import os
import asyncio
from concurrent.futures import ThreadPoolExecutor
from typing import List, Optional

import httpx
import joblib
import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from config import RF_MODEL_PATH, KNN_MODEL_PATH
from ai import ALCrew
from ai.models import UnderwritingDecision
from app.stress_test import StressTestRequest, StressTestResponse, run_stress_test

# ----- Load models once at startup -----
knn_model = joblib.load(KNN_MODEL_PATH)
rf_model = joblib.load(RF_MODEL_PATH)

app = FastAPI(title="AL-AI Engine API")

# Thread pool for blocking CrewAI deliberation
_crew_executor = ThreadPoolExecutor(max_workers=4)


# ============================================================
# Property schema (matches Firestore "property" object, no location)
# ============================================================
class PropertyDetails(BaseModel):
    county: str
    floor_area: float = Field(..., gt=0)
    bedrooms: float = Field(..., ge=0)
    bathrooms: float = Field(..., ge=0)
    year_built: float = Field(..., ge=1800, le=2030)
    property_type: str        # Firestore: "SEMI_DETACHED"
    ber_rating: str           # e.g. "B2"


# Firestore uses UPPER_SNAKE_CASE; the RF model was trained on Title Case.
_PROPERTY_TYPE_MAP = {
    "DETACHED": "Detached",
    "SEMI_DETACHED": "Semi-Detached",
    "TERRACED": "Terraced",
    "END_OF_TERRACE": "End of Terrace",
    "END_OF_TERRACED": "End of Terrace",
    "APARTMENT": "Apartment",
    "FLAT": "Apartment",
    "DUPLEX": "Duplex",
    "BUNGALOW": "Bungalow",
    "SITE": "Site",
    "UNKNOWN": "Unknown",
}


def _normalize_property_type(value: str) -> str:
    """Map Firestore enum values to training-data casing."""
    if not value:
        return "Unknown"
    key = value.strip().upper().replace("-", "_").replace(" ", "_")
    return _PROPERTY_TYPE_MAP.get(key, value.strip())


FEATURES = [
    "bedrooms",
    "bathrooms",
    "floor_area",
    "property_type",
    "ber_rating",
    "county",
    "year_built",
]


def to_dataframe(details: PropertyDetails) -> pd.DataFrame:
    """Build the ML feature DataFrame with correct casing."""
    return pd.DataFrame([{
        "bedrooms": details.bedrooms,
        "bathrooms": details.bathrooms,
        "floor_area": details.floor_area,
        "property_type": _normalize_property_type(details.property_type),
        "ber_rating": details.ber_rating.strip().upper(),
        "county": details.county.strip().title(),
        "year_built": details.year_built,
    }])[FEATURES]


# ============================================================
# Underwrite request/response (Firestore field names)
# ============================================================
class UnderwriteRequest(BaseModel):
    applicationId: Optional[str] = None
    borrowerId: Optional[str] = None
    brokerId: Optional[str] = None

    householdAnnualGrossIncome: float = Field(..., gt=0)
    existingDebtLiabilities: float = Field(..., ge=0)
    deposit: float = Field(..., ge=0)
    requestedMortgageTerm: int = Field(..., ge=1, le=40)
    sellerAskingPrice: float = Field(..., gt=0)
    requestedLoanAmount: float = Field(..., gt=0)

    property: PropertyDetails


class UnderwriteResponse(BaseModel):
    applicationId: str
    borrowerId: Optional[str] = None
    brokerId: Optional[str] = None

    decision: str
    rationale: str
    ltv: float
    dti: float
    risk_rating: int
    conditions: List[str]
    credit_memo: str
    agent_trails: str
    agent_outputs: dict = {}

    # Fields AL-Core backfills into Firestore
    estimatedLTV: float
    DTI: float
    approvalLikelihood: str
    riskTier: str
    lifecycleStatus: str = "AI_DELIBERATED"


# ============================================================
# Assess — deterministic dossier (no LLM)
# ============================================================
class AssessRequest(BaseModel):
    householdAnnualGrossIncome: float = Field(..., gt=0)
    existingDebtLiabilities: float = Field(..., ge=0)
    deposit: float = Field(..., ge=0)
    requestedMortgageTerm: int = Field(..., ge=1, le=40)
    sellerAskingPrice: float = Field(..., gt=0)
    property: PropertyDetails


class AssessResponse(BaseModel):
    predicted_value: float
    overvaluation_pct: float
    ltv: float
    dti: float
    stress_tested_repayment: float
    max_borrowing_capacity: float


# ============================================================
# Batch predict
# ============================================================
class BatchPredictItem(BaseModel):
    property: PropertyDetails
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


# ============================================================
# ML endpoints
# ============================================================
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


@app.post("/api/v1/ml/predict/rf/batch", response_model=BatchPredictResponse)
def predict_rf_batch(request: BatchPredictRequest):
    results = []
    for item in request.items:
        X = to_dataframe(item.property)
        pred = float(rf_model.predict(X)[0])
        ltv = (item.loan_amount / pred * 100) if pred > 0 else 999
        overval = ((item.asking_price - pred) / pred * 100) if pred > 0 else 0
        results.append(BatchPredictResult(
            prediction=round(pred, 2),
            ltv=round(ltv, 2),
            overvaluation_pct=round(overval, 2),
        ))
    return BatchPredictResponse(results=results)


# ============================================================
# Underwrite (full AI committee)
# ============================================================
@app.post("/api/v1/ai/underwrite", response_model=UnderwriteResponse)
async def underwrite(request: UnderwriteRequest):
    """Run the full multi-agent underwriting committee."""
    # Translate Firestore names → crew input names used by tasks.yaml
    specs = request.property.model_dump()
    specs["property_type"] = _normalize_property_type(specs["property_type"])
    specs["county"] = specs["county"].strip().title()
    specs["ber_rating"] = specs["ber_rating"].strip().upper()

    inputs = {
        "income": request.householdAnnualGrossIncome,
        "debt": request.existingDebtLiabilities,
        "deposit": request.deposit,
        "term_years": request.requestedMortgageTerm,
        "asking_price": request.sellerAskingPrice,
        "property_specs": specs,
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

    decision_obj: UnderwritingDecision | None = result.pydantic
    if decision_obj is None:
        raise HTTPException(status_code=500, detail="No structured decision")

    # Assemble per-agent structured outputs
    agent_outputs = {}
    task_outputs = result.tasks_output
    if len(task_outputs) >= 3:
        if task_outputs[0].pydantic is not None:
            agent_outputs["affordability"] = task_outputs[0].pydantic.model_dump()
        if task_outputs[1].pydantic is not None:
            agent_outputs["valuation"] = task_outputs[1].pydantic.model_dump()
        if task_outputs[2].pydantic is not None:
            agent_outputs["market_research"] = task_outputs[2].pydantic.model_dump()

    likelihood = (
        "High" if decision_obj.decision == "APPROVE"
        else "Medium" if decision_obj.decision == "CONDITIONALLY APPROVE"
        else "Low"
    )
    risk_tier = f"Tier {decision_obj.risk_rating}"

    return UnderwriteResponse(
        applicationId=request.applicationId or "",
        borrowerId=request.borrowerId,
        brokerId=request.brokerId,
        decision=decision_obj.decision,
        rationale=decision_obj.rationale,
        ltv=decision_obj.ltv,
        dti=decision_obj.dti,
        risk_rating=decision_obj.risk_rating,
        conditions=decision_obj.conditions,
        credit_memo=decision_obj.credit_memo,
        agent_trails=str(result),
        agent_outputs=agent_outputs,
        estimatedLTV=decision_obj.ltv,
        DTI=decision_obj.dti,
        approvalLikelihood=likelihood,
        riskTier=risk_tier,
        lifecycleStatus="AI_DELIBERATED",
    )


# ============================================================
# Health
# ============================================================
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
        "groq_key_set": bool(os.getenv("GROQ_API_KEY")),
    }


# ============================================================
# Stress test (pure math)
# ============================================================
@app.post("/api/v1/ai/stress-test", response_model=StressTestResponse)
async def stress_test(request: StressTestRequest):
    """Simulate rate shocks / property drops against a portfolio. No LLM."""
    return run_stress_test(request)


# ============================================================
# Assess (deterministic dossier, no LLM)
# ============================================================
@app.post("/api/v1/ai/assess", response_model=AssessResponse)
def assess(request: AssessRequest):
    """Deterministic dossier numbers. No LLM, no crew."""
    X = to_dataframe(request.property)
    predicted = float(rf_model.predict(X)[0])

    loan = max(request.sellerAskingPrice - request.deposit, 0)
    ltv = (loan / predicted * 100) if predicted > 0 else 999
    overval = ((request.sellerAskingPrice - predicted) / predicted * 100) if predicted > 0 else 0

    # Stress-test at ECB benchmark + 2% buffer
    ECB_BENCHMARK = 3.75
    stress_rate = ECB_BENCHMARK + 2.0
    r = (stress_rate / 100) / 12
    n = request.requestedMortgageTerm * 12
    if r == 0:
        monthly = loan / n if n else 0
    else:
        monthly = loan * r * (1 + r) ** n / ((1 + r) ** n - 1)

    annual_debt_service = monthly * 12 + request.existingDebtLiabilities
    dti = (annual_debt_service / request.householdAnnualGrossIncome * 100) \
        if request.householdAnnualGrossIncome > 0 else 999
    max_borrowing = request.householdAnnualGrossIncome * 3.5  # CBI 3.5x rule

    return AssessResponse(
        predicted_value=round(predicted, 2),
        overvaluation_pct=round(overval, 2),
        ltv=round(ltv, 2),
        dti=round(dti, 2),
        stress_tested_repayment=round(monthly, 2),
        max_borrowing_capacity=round(max_borrowing, 2),
    )