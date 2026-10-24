from fastapi import Depends, FastAPI, HTTPException
from app.auth import verify_firebase_token
from app.users import get_user_profile
from google.api_core.exceptions import AlreadyExists
from app.models import UserProfile, UserProfileCreate, UserRole
from app.users import create_user_profile

app = FastAPI(
    title="Agentic Lender Core API",
    version="0.1.0"
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
        