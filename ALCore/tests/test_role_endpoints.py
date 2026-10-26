from fastapi.testclient import TestClient

from app.main import app
from app.auth import verify_firebase_token
from app.models import UserProfile, UserRole


client = TestClient(app)


def mock_borrower():
    return UserProfile(
        uid="test-borrower-123",
        email="borrower@example.com",
        role=UserRole.BORROWER,
        display_name="Test Borrower",
    )


def mock_broker():
    return UserProfile(
        uid="test-broker-123",
        email="broker@example.com",
        role=UserRole.BROKER,
        display_name="Test Broker",
    )


def mock_underwriter():
    return UserProfile(
        uid="test-underwriter-123",
        email="underwriter@example.com",
        role=UserRole.UNDERWRITER,
        display_name="Test Underwriter",
    )


def test_broker_can_access_dashboard(monkeypatch):
    app.dependency_overrides[verify_firebase_token] = (
        lambda: {"uid": "test-broker-123"}
    )

    monkeypatch.setattr(
        "app.permissions.get_user_profile",
        lambda uid: mock_broker(),
    )

    try:
        response = client.get("/broker/dashboard")

        assert response.status_code == 200
        assert response.json() == {
            "message": "Broker access granted",
            "uid": "test-broker-123",
            "role": "broker",
        }
    finally:
        app.dependency_overrides.clear()


def test_borrower_cannot_access_broker_dashboard(monkeypatch):
    app.dependency_overrides[verify_firebase_token] = (
        lambda: {"uid": "test-borrower-123"}
    )

    monkeypatch.setattr(
        "app.permissions.get_user_profile",
        lambda uid: mock_borrower(),
    )

    try:
        response = client.get("/broker/dashboard")

        assert response.status_code == 403
        assert response.json()["detail"] == "Insufficient permissions"
    finally:
        app.dependency_overrides.clear()


def test_underwriter_cannot_access_broker_dashboard(monkeypatch):
    app.dependency_overrides[verify_firebase_token] = (
        lambda: {"uid": "test-underwriter-123"}
    )

    monkeypatch.setattr(
        "app.permissions.get_user_profile",
        lambda uid: mock_underwriter(),
    )

    try:
        response = client.get("/broker/dashboard")

        assert response.status_code == 403
        assert response.json()["detail"] == "Insufficient permissions"
    finally:
        app.dependency_overrides.clear()


def test_underwriter_can_access_dashboard(monkeypatch):
    app.dependency_overrides[verify_firebase_token] = (
        lambda: {"uid": "test-underwriter-123"}
    )

    monkeypatch.setattr(
        "app.permissions.get_user_profile",
        lambda uid: mock_underwriter(),
    )

    try:
        response = client.get("/underwriter/dashboard")

        assert response.status_code == 200
        assert response.json() == {
            "message": "Underwriter access granted",
            "uid": "test-underwriter-123",
            "role": "underwriter",
        }
    finally:
        app.dependency_overrides.clear()


def test_broker_cannot_access_underwriter_dashboard(monkeypatch):
    app.dependency_overrides[verify_firebase_token] = (
        lambda: {"uid": "test-broker-123"}
    )

    monkeypatch.setattr(
        "app.permissions.get_user_profile",
        lambda uid: mock_broker(),
    )

    try:
        response = client.get("/underwriter/dashboard")

        assert response.status_code == 403
        assert response.json()["detail"] == "Insufficient permissions"
    finally:
        app.dependency_overrides.clear()


def test_borrower_cannot_access_underwriter_dashboard(monkeypatch):
    app.dependency_overrides[verify_firebase_token] = (
        lambda: {"uid": "test-borrower-123"}
    )

    monkeypatch.setattr(
        "app.permissions.get_user_profile",
        lambda uid: mock_borrower(),
    )

    try:
        response = client.get("/underwriter/dashboard")

        assert response.status_code == 403
        assert response.json()["detail"] == "Insufficient permissions"
    finally:
        app.dependency_overrides.clear()


def test_borrower_can_access_dashboard(monkeypatch):
    app.dependency_overrides[verify_firebase_token] = (
        lambda: {"uid": "test-borrower-123"}
    )

    monkeypatch.setattr(
        "app.permissions.get_user_profile",
        lambda uid: mock_borrower(),
    )

    try:
        response = client.get("/borrower/dashboard")

        assert response.status_code == 200
        assert response.json() == {
            "message": "Borrower access granted",
            "uid": "test-borrower-123",
            "role": "borrower",
        }
    finally:
        app.dependency_overrides.clear()


def test_broker_cannot_access_borrower_dashboard(monkeypatch):
    app.dependency_overrides[verify_firebase_token] = (
        lambda: {"uid": "test-broker-123"}
    )

    monkeypatch.setattr(
        "app.permissions.get_user_profile",
        lambda uid: mock_broker(),
    )

    try:
        response = client.get("/borrower/dashboard")

        assert response.status_code == 403
        assert response.json()["detail"] == "Insufficient permissions"
    finally:
        app.dependency_overrides.clear()


def test_underwriter_cannot_access_borrower_dashboard(monkeypatch):
    app.dependency_overrides[verify_firebase_token] = (
        lambda: {"uid": "test-underwriter-123"}
    )

    monkeypatch.setattr(
        "app.permissions.get_user_profile",
        lambda uid: mock_underwriter(),
    )

    try:
        response = client.get("/borrower/dashboard")

        assert response.status_code == 403
        assert response.json()["detail"] == "Insufficient permissions"
    finally:
        app.dependency_overrides.clear()
        