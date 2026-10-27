from datetime import datetime
from enum import Enum
from typing import Literal

from pydantic import BaseModel, Field


class UserRole(str, Enum):
    BORROWER = "borrower"
    BROKER = "broker"
    UNDERWRITER = "underwriter"


class UserProfile(BaseModel):
    uid: str
    email: str
    role: UserRole
    display_name: str = Field(default="")

class UserProfileCreate(BaseModel):
    display_name: str = Field(default="", max_length=30)


class ApplicationStatus(str, Enum):
    APPLIED = "APPLIED"
    AI_DELIBERATED = "AI_DELIBERATED"
    UNDERWRITER_APPROVED = "UNDERWRITER_APPROVED"
    FUNDED = "FUNDED"
    CLOSED = "CLOSED"


# Accepted property types from AL-Mobile's proposed form.
PropertyType = Literal[
    "APARTMENT",
    "DETACHED",
    "SEMI_DETACHED",
    "TERRACED",
    "BUNGALOW",
    "TOWN_HOUSE",
    "COMMERCIAL",
    "OTHER",
]


BERCode = Literal[
    "A1", "A2", "A3",
    "B1", "B2", "B3",
    "C1", "C2", "C3",
    "D1", "D2",
    "E1", "E2",
    "F", "G", "EXEMPT",
]


class PropertyDetails(BaseModel):
    location: str = Field(min_length=1)
    county: str = Field(min_length=1)

    floorArea: float = Field(gt=0)
    floorAreaUnit: Literal["SQ_METERS"] = "SQ_METERS"

    bedrooms: int = Field(ge=0)
    propertyType: PropertyType
    berEnergyRating: BERCode | None = None



class LoanApplicationCreate(BaseModel):
    # Information supplied by the borrower through AL-Mobile.

    householdAnnualGrossIncome: float = Field(ge=0)
    existingDebtLiabilities: float = Field(ge=0)
    deposit: float = Field(ge=0)

    requestedMortgageTerm: int = Field(gt=0)
    sellerAskingPrice: float = Field(gt=0)

    property: PropertyDetails


class LoanApplication(LoanApplicationCreate):
    # Application identity and ownership.
    # The application ID is the Firestore document ID.
    
    borrowerId: str
    brokerId: str | None = None

    requestedLoanAmount: float | None = Field(default=None, ge=0)
    estimatedLTV: float | None = Field(default=None, ge=0)
    DTI: float | None = Field(default=None, ge=0)
    approvalLikelihood: float | None = Field(
        default=None,
        ge=0,
        le=1,
    )

    # Risk assessment for AL-Web.
    riskTier: Literal["LOW", "MEDIUM", "HIGH"] | None = None

    # Application workflow.
    lifecycleStatus: ApplicationStatus = ApplicationStatus.APPLIED

    # Firestore timestamps.
    createdAt: datetime
    updatedAt: datetime
    submittedAt: datetime | None = None


class LoanApplicationResponse(LoanApplication):
    applicationId: str
    