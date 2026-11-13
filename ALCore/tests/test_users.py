from fastapi.testclient import TestClient
from app.auth import verify_firebase_token
from app.models import UserProfile, UserProfileCreate, UserRole
from app.main import app
from google.api_core.exceptions import AlreadyExists
from pydantic import ValidationError
import pytest


client = TestClient(app)


def test_get_my_profile_without_token():
    response = client.get("/users/me")

    assert response.status_code == 401

def test_get_my_profile_not_found(monkeypatch):
    app.dependency_overrides[verify_firebase_token] = lambda: {
        "uid": "test-user-123"
    }

    monkeypatch.setattr(
        "app.main.get_user_profile",
        lambda uid: None
    )

    try:
        response = client.get("/users/me")

        assert response.status_code == 404
        assert response.json()["detail"] == "User profile not found"
    finally:
        app.dependency_overrides.clear()

def test_get_my_profile_success(monkeypatch):
    app.dependency_overrides[verify_firebase_token] = lambda: {
        "uid": "test-user-123"
    }

    mock_profile = UserProfile(
        uid="test-user-123",
        email="test@example.com",
        role=UserRole.BORROWER,
        display_name="Test User"
    )

    monkeypatch.setattr(
        "app.main.get_user_profile",
        lambda uid: mock_profile
    )

    try:
        response = client.get("/users/me")

        assert response.status_code == 200
        assert response.json() == {
            "uid": "test-user-123",
            "email": "test@example.com",
            "role": "borrower",
            "display_name": "Test User",
            "first_name": "",
            "last_name": "",
            "phone_number": None,
            "createdAt": None,
            "updatedAt": None
        }
    finally:
        app.dependency_overrides.clear()

def test_register_user_as_borrower(monkeypatch):
    app.dependency_overrides[verify_firebase_token] = lambda: {
        "uid": "test-user-123",
        "email": "test@example.com"
    }

    monkeypatch.setattr(
        "app.main.create_user_profile",
        lambda profile: profile
    )

    try:
        response = client.post(
            "/users/me",
            json={
                "first_name": "Test",
                "last_name": "User",
                "phone_number": "+353871234567",
                "role": "borrower",
                "uid": "fake-uid"
            }
        )

        assert response.status_code == 201

        data = response.json()

        assert data["uid"] == "test-user-123"
        assert data["email"] == "test@example.com"
        assert data["role"] == "borrower"
        assert data["display_name"] == "Test User"
        assert data["first_name"] == "Test"
        assert data["last_name"] == "User"
        assert data["phone_number"] == "+353871234567"
        assert data["createdAt"] is not None
        assert data["updatedAt"] is not None

    finally:
        app.dependency_overrides.clear()


@pytest.mark.parametrize("role", ["broker", "underwriter"])
def test_register_user_with_selected_role(monkeypatch, role):
    app.dependency_overrides[verify_firebase_token] = lambda: {
        "uid": "test-user-123",
        "email": "test@example.com"
    }

    monkeypatch.setattr(
        "app.main.create_user_profile",
        lambda profile: profile
    )

    try:
        response = client.post(
            "/users/me",
            json={
                "first_name": "John",
                "last_name": "Conner",
                "role": role
            }
        )

        assert response.status_code == 201

        data = response.json()

        assert data["role"] == role
        assert data["display_name"] == "John Conner"
        assert data["first_name"] == "John"
        assert data["last_name"] == "Conner"
        assert data["phone_number"] is None

    finally:
        app.dependency_overrides.clear()        


def test_register_user_already_exists(monkeypatch):
    app.dependency_overrides[verify_firebase_token] = lambda: {
        "uid": "test-user-123",
        "email": "test@example.com"
    }

    def mock_create_user_profile(profile):
        raise AlreadyExists("Profile already exists")

    monkeypatch.setattr(
        "app.main.create_user_profile",
        mock_create_user_profile
    )

    try:
        response = client.post(
            "/users/me",
            json={
                "first_name": "Test",
                "last_name": "User",
                "role": "borrower"
            }
        )

        assert response.status_code == 409
        assert response.json()["detail"] == "User profile already exists"

    finally:
        app.dependency_overrides.clear()

def test_user_name_validation():
    # Maximum allowed lengths are accepted
    profile = UserProfileCreate(
        first_name="A" * 30,
        last_name="B" * 50,
        role="borrower"
    )

    assert len(profile.first_name) == 30
    assert len(profile.last_name) == 50

    # First name exceeding 30 characters is rejected
    with pytest.raises(ValidationError):
        UserProfileCreate(
            first_name="A" * 31,
            last_name="User",
            role="borrower"
        )

    # Last name exceeding 50 characters is rejected
    with pytest.raises(ValidationError):
        UserProfileCreate(
            first_name="Test",
            last_name="B" * 51,
            role="borrower"
        )

    # Empty first name is rejected
    with pytest.raises(ValidationError):
        UserProfileCreate(
            first_name="",
            last_name="User",
            role="borrower"
        )

    # Empty last name is rejected
    with pytest.raises(ValidationError):
        UserProfileCreate(
            first_name="Test",
            last_name="",
            role="borrower"
        )
