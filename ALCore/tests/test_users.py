from fastapi.testclient import TestClient
from app.auth import verify_firebase_token
from app.models import UserProfile
from app.main import app
from google.api_core.exceptions import AlreadyExists

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
        role="borrower",
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
            "display_name": "Test User"
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
                "display_name": "Test User",
                "role": "underwriter",
                "uid": "fake-uid"
            }
        )

        assert response.status_code == 201

        data = response.json()

        assert data["uid"] == "test-user-123"
        assert data["email"] == "test@example.com"
        assert data["role"] == "borrower"
        assert data["display_name"] == "Test User"

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
            json={"display_name": "Test User"}
        )

        assert response.status_code == 409
        assert response.json()["detail"] == "User profile already exists"

    finally:
        app.dependency_overrides.clear()
        