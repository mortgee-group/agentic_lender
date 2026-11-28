from datetime import datetime
from enum import Enum
from typing import Literal
from pydantic import BaseModel, Field, model_validator
# Predefined mortgage product interest rate: 400 basis points = 4.00% APR.
MORTGAGE_APR_BASIS_POINTS = 400

class UserRole(str, Enum):
    BORROWER = "borrower"
    BROKER = "broker"
    UNDERWRITER = "underwriter"


class UserProfile(BaseModel):
    uid: str
    email: str
    role: UserRole
    display_name: str = Field(default="")
    first_name: str = Field(default="")
    last_name: str = Field(default="")
    phone_number: str | None = None
    createdAt: datetime | None = None
    updatedAt: datetime | None = None

class UserProfileCreate(BaseModel):
    first_name: str = Field(min_length=1, max_length=30)
    last_name: str = Field(min_length=1, max_length=50)
    phone_number: str | None = Field(default=None, max_length=20)

    # Temporary: users can select their role during initial integration.
    # Final sprint: restrict Broker and Underwriter role assignment.
    role: UserRole


class ApplicationStatus(str, Enum):
    APPLIED = "APPLIED"
    AI_DELIBERATED = "AI_DELIBERATED"
    UNDERWRITER_APPROVED = "UNDERWRITER_APPROVED"
    FUNDED = "FUNDED"
    CLOSED = "CLOSED"

class UnderwriterDecision(str, Enum):
    APPROVED = "APPROVED"
    REJECTED = "REJECTED"


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
    county: str = Field(min_length=1)
    floor_area: float = Field(
        gt=0,
        description="Property floor area in square metres (m²)"
    )
    bedrooms: int = Field(ge=0)
    bathrooms: int = Field(ge=0)
    year_built: int = Field(ge=1800, le=2030)
    property_type: PropertyType
    ber_rating: BERCode



class LoanApplicationCreate(BaseModel):
    householdAnnualGrossIncome: float = Field(ge=0)
    existingDebtLiabilities: float = Field(ge=0)
    deposit: float = Field(ge=0)

    requestedMortgageTerm: int = Field(ge=1, le=40)
    sellerAskingPrice: float = Field(gt=0)

    property: PropertyDetails

    @model_validator(mode="after")
    def validate_deposit(self):
        if self.deposit >= self.sellerAskingPrice:
            raise ValueError(
                "Deposit must be less than the property asking price"
            )
        return self


class LoanApplication(LoanApplicationCreate):
    # Application identity and ownership.
    # The application ID is the Firestore document ID.
    
    borrowerId: str
    brokerId: str | None = None

    requestedLoanAmount: float | None = Field(default=None, ge=0)
    approvedLoanAmount: float | None = Field(default=None, gt=0)
    approvedAprBasisPoints: int | None = Field(default=None, ge=0)
    interestRateConfirmed: bool = False
    estimatedLTV: float | None = Field(default=None, ge=0)
    DTI: float | None = Field(default=None, ge=0)
    approvalLikelihood: Literal["High", "Medium", "Low"] | None = None
    riskTier: Literal["Tier 1", "Tier 2", "Tier 3", "Tier 4", "Tier 5"] | None = None
    # Application workflow.
    lifecycleStatus: ApplicationStatus = ApplicationStatus.APPLIED
    # Final decision made by an authorised Underwriter.
    underwriterDecision: UnderwriterDecision | None = None
    decidedBy: str | None = None
    decidedAt: datetime | None = None

    # Firestore timestamps.
    createdAt: datetime
    updatedAt: datetime
    submittedAt: datetime | None = None


class LoanApplicationResponse(LoanApplication):
    applicationId: str


class UnderwriterDecisionRequest(BaseModel):
    decision: UnderwriterDecision
    confirmInterestRate: bool = False

    @model_validator(mode="after")
    def validate_interest_rate_confirmation(self):
        if (
            self.decision == UnderwriterDecision.APPROVED
            and not self.confirmInterestRate
        ):
            raise ValueError(
                "Interest rate confirmation is required for approval"
            )

        return self


class TokenizationStatus(str, Enum):
    PENDING = "PENDING"
    COMPLETED = "COMPLETED"


class TokenizationResponse(BaseModel):
    applicationId: str
    status: TokenizationStatus

    tokenId: int | None = Field(default=None, ge=0)
    transactionHash: str | None = None
    contractAddress: str | None = None
    chainId: int | None = Field(default=None, gt=0)
    auditHash: str | None = None

    createdAt: datetime | None = None
    completedAt: datetime | None = None
