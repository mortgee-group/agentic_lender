from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient

from app.auth import verify_firebase_token
from app.models import UserProfile, UserRole
from app.permissions import require_role
from app.users import get_user_profile


# Create a small test API
test_app = FastAPI()


@test_app.get("/borrower-only")
def borrower_endpoint(
    profile=Depends(require_role(UserRole.BORROWER)),
):
    return {"message": "Access granted"}


@test_app.get("/underwriter-only")
def underwriter_endpoint(
    profile=Depends(require_role(UserRole.UNDERWRITER)),
):
    return {"message": "Access granted"}


client = TestClient(test_app)


def mock_borrower():
    return UserProfile(
        uid="test-user-123",
        email="test@example.com",
        role=UserRole.BORROWER,
        display_name="Test Borrower",
    )


def test_borrower_can_access_borrower_endpoint(monkeypatch):
    test_app.dependency_overrides[verify_firebase_token] = (
        lambda: {"uid": "test-user-123"}
    )
    monkeypatch.setattr(
        "app.permissions.get_user_profile",
        lambda uid: mock_borrower(),
    )

    try:
        response = client.get("/borrower-only")
        assert response.status_code == 200
    finally:
        test_app.dependency_overrides.clear()


def test_borrower_cannot_access_underwriter_endpoint(monkeypatch):
    test_app.dependency_overrides[verify_firebase_token] = (
        lambda: {"uid": "test-user-123"}
    )
    monkeypatch.setattr(
        "app.permissions.get_user_profile",
        lambda uid: mock_borrower(),
    )

    try:
        response = client.get("/underwriter-only")
        assert response.status_code == 403
        assert response.json()["detail"] == "Insufficient permissions"
    finally:
        test_app.dependency_overrides.clear()


def test_unauthenticated_user_cannot_access_protected_endpoint():
    response = client.get("/underwriter-only")
    assert response.status_code == 401