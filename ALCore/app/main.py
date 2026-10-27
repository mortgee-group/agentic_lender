from fastapi import Depends, FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from app.auth import verify_firebase_token
from app.users import get_user_profile, create_user_profile
from app.permissions import require_role
from google.api_core.exceptions import AlreadyExists
from app.applications import (
    create_application,
    get_application,
    list_applications_for_user,
)
from app.models import (
    UserProfile,
    UserProfileCreate,
    UserRole,
    LoanApplicationCreate,
    LoanApplicationResponse,
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
    allow_methods=["GET", "POST"],
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

    profile = UserProfile(
        uid=uid,
        email=email,
        role=UserRole.BORROWER,
        display_name=data.display_name,
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
