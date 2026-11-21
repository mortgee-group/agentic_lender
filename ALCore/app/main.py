from fastapi import Depends, FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from app.auth import verify_firebase_token
from app.users import get_user_profile, create_user_profile
from app.permissions import require_role
from google.api_core.exceptions import AlreadyExists
import httpx
from datetime import datetime, timezone
from app.ai_client import assess_application, underwrite_application
from app.applications import (
    create_application,
    get_application,
    has_valuation,
    list_applications_for_user,
    save_ai_decision,
    save_valuation,
    save_underwriter_decision,
)
from app.models import (
    UserProfile,
    UserProfileCreate,
    UserRole,
    LoanApplicationCreate,
    LoanApplicationResponse,
    UnderwriterDecisionRequest,
    ApplicationStatus,
)

app = FastAPI(
    title="Agentic Lender Core API",
    version="0.1.0"
)

# Allow the local React dev app
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",
        "http://127.0.0.1:5173"
    ],
    allow_credentials=False,
    allow_methods=["GET", "POST", "PATCH"],
    allow_headers=["*"],
)


@app.get("/health")
def health_check():
    return {"status": "ok"}


@app.get("/auth/me")
def get_current_user(decoded_token: dict = Depends(verify_firebase_token)):
    return {
        "uid": decoded_token["uid"]
    }

@app.get("/users/me")
def get_my_profile(decoded_token: dict = Depends(verify_firebase_token)):
    uid = decoded_token["uid"]

    profile = get_user_profile(uid)

    if profile is None:
        raise HTTPException(
            status_code=404,
            detail="User profile not found"
        )

    return profile

@app.post("/users/me", status_code=201)
def register_user(
    data: UserProfileCreate,
    decoded_token: dict = Depends(verify_firebase_token)
):
    uid = decoded_token["uid"]
    email = decoded_token.get("email")

    if not email:
        raise HTTPException(
            status_code=400,
            detail="Firebase account has no email address"
        )

    now = datetime.now(timezone.utc)

    profile = UserProfile(
        uid=uid,
        email=email,
        role=data.role,
        display_name=f"{data.first_name} {data.last_name}",
        first_name=data.first_name,
        last_name=data.last_name,
        phone_number=data.phone_number,
        createdAt=now,
        updatedAt=now,
    )

    try:
        return create_user_profile(profile)
    except AlreadyExists:
        raise HTTPException(
            status_code=409,
            detail="User profile already exists"
        )


@app.get("/borrower/dashboard")
def borrower_dashboard(
    profile: UserProfile = Depends(require_role(UserRole.BORROWER))
):
    return {
        "message": "Borrower access granted",
        "uid": profile.uid,
        "role": profile.role,
    }


@app.get("/broker/dashboard")
def broker_dashboard(
    profile: UserProfile = Depends(require_role(UserRole.BROKER))
):
    return {
        "message": "Broker access granted",
        "uid": profile.uid,
        "role": profile.role,
    }


@app.get("/underwriter/dashboard")
def underwriter_dashboard(
    profile: UserProfile = Depends(require_role(UserRole.UNDERWRITER))
):
    return {
        "message": "Underwriter access granted",
        "uid": profile.uid,
        "role": profile.role,
    }


@app.post("/applications", status_code=201)
def submit_application(
    data: LoanApplicationCreate,
    profile: UserProfile = Depends(require_role(UserRole.BORROWER)),
):
    application_id = create_application(
        borrower_id=profile.uid,
        application_data=data,
    )

    return {
        "applicationId": application_id,
        "message": "Mortgage application created successfully",
    }


@app.get(
    "/applications/{application_id}",
    response_model=LoanApplicationResponse,
)
def retrieve_application(
    application_id: str,
    profile: UserProfile = Depends(
        require_role(
            UserRole.BORROWER,
            UserRole.BROKER,
            UserRole.UNDERWRITER,
        )
    ),
):
    application = get_application(application_id)

    if application is None:
        raise HTTPException(
            status_code=404,
            detail="Application not found",
        )

    if profile.role == UserRole.BORROWER:
        if application.borrowerId != profile.uid:
            raise HTTPException(
                status_code=403,
                detail="Insufficient permissions",
            )

    elif profile.role == UserRole.BROKER:
        if application.brokerId != profile.uid:
            raise HTTPException(
                status_code=403,
                detail="Insufficient permissions",
            )

    return application


@app.get("/applications", response_model=list[LoanApplicationResponse])
def list_applications(
    profile: UserProfile = Depends(
        require_role(
            UserRole.BORROWER,
            UserRole.BROKER,
            UserRole.UNDERWRITER,
        )
    ),
):
    return list_applications_for_user(
        uid=profile.uid,
        role=profile.role.value,
    )


@app.post("/applications/{application_id}/assess")
async def assess_mortgage_application(
    application_id: str,
    profile: UserProfile = Depends(
        require_role(UserRole.BROKER, UserRole.UNDERWRITER)
    ),
):
    application = get_application(application_id)

    if application is None:
        raise HTTPException(
            status_code=404,
            detail="Application not found",
        )

    if profile.role == UserRole.BROKER and application.brokerId != profile.uid:
        raise HTTPException(
            status_code=403,
            detail="Insufficient permissions",
        )

    assessment_data = application.model_dump(
        mode="json",
        include={
            "householdAnnualGrossIncome",
            "existingDebtLiabilities",
            "deposit",
            "requestedMortgageTerm",
            "sellerAskingPrice",
            "property",
        },
    )

    try:
        assessment = await assess_application(assessment_data)
    except httpx.RequestError:
        raise HTTPException(
            status_code=503,
            detail="AI assessment service is currently unavailable",
    )
    except httpx.HTTPStatusError:
        raise HTTPException(
            status_code=502,
            detail="AI assessment service returned an error",
    )

    save_valuation(
        application_id,
        assessment_data["property"],
        assessment,
    )

    return assessment


@app.post("/applications/{application_id}/underwrite")
async def underwrite_mortgage_application(
    application_id: str,
    profile: UserProfile = Depends(require_role(UserRole.UNDERWRITER)),
):
    application = get_application(application_id)

    if application is None:
        raise HTTPException(
            status_code=404,
            detail="Application not found",
        )
    if application.lifecycleStatus != ApplicationStatus.APPLIED:
        raise HTTPException(
            status_code=409,
            detail="AI underwriting has already been completed or is no longer allowed",
        )
    if not has_valuation(application_id):
        raise HTTPException(
            status_code=409,
            detail="AI assessment must be completed before underwriting",
        )

    underwriting_data = application.model_dump(
        mode="json",
        include={
            "borrowerId",
            "brokerId",
            "requestedLoanAmount",
            "householdAnnualGrossIncome",
            "existingDebtLiabilities",
            "deposit",
            "requestedMortgageTerm",
            "sellerAskingPrice",
            "property",
        },
    )

    underwriting_data["applicationId"] = application_id

    if not underwriting_data.get("requestedLoanAmount"):
        underwriting_data["requestedLoanAmount"] = (
            application.sellerAskingPrice - application.deposit
        )

    try:
        decision = await underwrite_application(underwriting_data)
    except httpx.RequestError:
        raise HTTPException(
            status_code=503,
            detail="AI underwriting service is currently unavailable",
        )
    except httpx.HTTPStatusError:
        raise HTTPException(
            status_code=502,
            detail="AI underwriting service returned an error",
        )

    save_ai_decision(application_id, decision)

    return decision


@app.patch("/applications/{application_id}/decision")
def decide_mortgage_application(
    application_id: str,
    data: UnderwriterDecisionRequest,
    profile: UserProfile = Depends(require_role(UserRole.UNDERWRITER)),
):
    """Record the final decision of an authorised Underwriter."""

    return save_underwriter_decision(
        application_id=application_id,
        underwriter_id=profile.uid,
        decision=data.decision,
    )
