# app/stress_test.py
from pydantic import BaseModel, Field
from typing import List
import joblib
import pandas as pd

from config import RF_MODEL_PATH

_rf_model = joblib.load(RF_MODEL_PATH)

FEATURES = [
    "bedrooms", "bathrooms", "floor_area", "property_type",
    "ber_rating", "county", "year_built",
]

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
    if not value:
        return "Unknown"
    key = value.strip().upper().replace("-", "_").replace(" ", "_")
    return _PROPERTY_TYPE_MAP.get(key, value.strip())


def _build_features(specs: dict) -> pd.DataFrame:
    return pd.DataFrame([{
        "bedrooms": specs["bedrooms"],
        "bathrooms": specs["bathrooms"],
        "floor_area": specs["floor_area"],
        "property_type": _normalize_property_type(specs["property_type"]),
        "ber_rating": str(specs["ber_rating"]).strip().upper(),
        "county": str(specs["county"]).strip().title(),
        "year_built": specs["year_built"],
    }])[FEATURES]


class StressTestProperty(BaseModel):
    county: str
    floor_area: float
    bedrooms: float
    bathrooms: float
    year_built: float
    property_type: str
    ber_rating: str


class StressTestApplication(BaseModel):
    applicationId: str
    householdAnnualGrossIncome: float
    existingDebtLiabilities: float
    deposit: float
    requestedMortgageTerm: int
    sellerAskingPrice: float
    current_rate_pct: float
    property: StressTestProperty


class StressTestRequest(BaseModel):
    rate_shock_bps: int = Field(..., ge=0, le=500)
    property_drop_pct: float = Field(0.0, ge=0.0, le=50.0)
    applications: List[StressTestApplication]


class StressTestResult(BaseModel):
    applicationId: str
    stressed_repayment: float
    stressed_dti: float
    stressed_ltv: float
    stressed_risk_rating: int
    original_risk_rating: int
    verdict: str  # "AFFORDABLE" | "MARGINAL" | "UNAFFORDABLE"


class StressTestResponse(BaseModel):
    rate_shock_bps: int
    property_drop_pct: float
    summary: dict
    results: List[StressTestResult]


def _monthly_repayment(principal: float, annual_rate_pct: float, years: int) -> float:
    if principal <= 0:
        return 0.0
    r = (annual_rate_pct / 100.0) / 12.0
    n = years * 12
    if r == 0:
        return principal / n
    return principal * r * (1 + r) ** n / ((1 + r) ** n - 1)


def _risk_rating(dti: float, ltv: float) -> int:
    score = 1
    if dti > 35:
        score += 1
    if dti > 45:
        score += 1
    if ltv > 80:
        score += 1
    if ltv > 90:
        score += 1
    return min(score, 5)


def run_stress_test(req: StressTestRequest) -> StressTestResponse:
    results = []
    unaffordable = 0
    ltv_breach_90 = 0
    ltv_breach_100 = 0

    for app in req.applications:
        specs = app.property.model_dump()
        X = _build_features(specs)
        base_value = float(_rf_model.predict(X)[0])
        stressed_value = base_value * (1 - req.property_drop_pct / 100.0)

        loan = max(app.sellerAskingPrice - app.deposit, 0)

        stressed_rate = app.current_rate_pct + (req.rate_shock_bps / 100.0)
        monthly = _monthly_repayment(loan, stressed_rate, app.requestedMortgageTerm)
        annual_debt_service = monthly * 12 + app.existingDebtLiabilities

        stressed_dti = (annual_debt_service / app.householdAnnualGrossIncome * 100) \
            if app.householdAnnualGrossIncome > 0 else 999
        stressed_ltv = (loan / stressed_value * 100) if stressed_value > 0 else 999

        base_monthly = _monthly_repayment(loan, app.current_rate_pct, app.requestedMortgageTerm)
        base_dti = ((base_monthly * 12 + app.existingDebtLiabilities)
                    / app.householdAnnualGrossIncome * 100) \
            if app.householdAnnualGrossIncome > 0 else 999
        base_ltv = (loan / base_value * 100) if base_value > 0 else 999

        original_rating = _risk_rating(base_dti, base_ltv)
        stressed_rating = _risk_rating(stressed_dti, stressed_ltv)

        if stressed_dti > 50 or stressed_ltv > 100:
            verdict = "UNAFFORDABLE"
            unaffordable += 1
        elif stressed_dti > 40 or stressed_ltv > 90:
            verdict = "MARGINAL"
        else:
            verdict = "AFFORDABLE"

        if stressed_ltv > 90:
            ltv_breach_90 += 1
        if stressed_ltv > 100:
            ltv_breach_100 += 1

        results.append(StressTestResult(
            applicationId=app.applicationId,
            stressed_repayment=round(monthly, 2),
            stressed_dti=round(stressed_dti, 2),
            stressed_ltv=round(stressed_ltv, 2),
            stressed_risk_rating=stressed_rating,
            original_risk_rating=original_rating,
            verdict=verdict,
        ))

    return StressTestResponse(
        rate_shock_bps=req.rate_shock_bps,
        property_drop_pct=req.property_drop_pct,
        summary={
            "total": len(results),
            "become_unaffordable": unaffordable,
            "ltv_breaches_90": ltv_breach_90,
            "ltv_breaches_100": ltv_breach_100,
        },
        results=results,
    )