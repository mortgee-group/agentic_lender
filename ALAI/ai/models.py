# ai/models.py
from pydantic import BaseModel, Field
from typing import List


class UnderwritingDecision(BaseModel):
    decision: str = Field(
        description="One of: APPROVE, CONDITIONALLY APPROVE, DECLINE"
    )
    rationale: str = Field(description="Short explanation of the decision")
    ltv: float = Field(description="Loan-to-Value as a percentage, e.g. 86.36")
    dti: float = Field(
        description="Debt-to-Income as a percentage of gross income, e.g. 45.06"
    )
    risk_rating: int = Field(ge=1, le=5, description="1 (lowest) to 5 (highest)")
    conditions: List[str] = Field(
        default_factory=list,
        description="List of conditions; empty if decision is APPROVE",
    )
    credit_memo: str = Field(description="Full audit-ready credit memorandum text")


class AffordabilityOutput(BaseModel):
    dti: float = Field(description="Debt-to-Income as percentage of gross income")
    stress_tested_repayment: float = Field(description="Monthly repayment at stress rate (EUR)")
    max_borrowing_capacity: float = Field(description="3.5x income cap (EUR)")
    affordability_verdict: str = Field(description="Pass | Marginal | Fail")
    notes: str = Field(description="Explanatory notes from the Affordability Agent")


class ValuationOutput(BaseModel):
    predicted_value: float = Field(description="ML fair valuation (EUR)")
    overvaluation_pct: float = Field(description="Asking price vs predicted value (%)")
    ltv: float = Field(description="Loan-to-Value as percentage")
    collateral_risk: str = Field(description="Low | Medium | High")
    notes: str = Field(description="Explanatory notes from the Valuation Agent")


class MarketResearchOutput(BaseModel):
    ecb_rate: float = Field(description="Current ECB main refinancing rate (%)")
    summary: str = Field(description="Concise market context summary")