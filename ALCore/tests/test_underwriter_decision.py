from unittest.mock import MagicMock, patch
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.models import UserProfile, UserRole
from app.auth import verify_firebase_token
from app.permissions import get_user_profile


client = TestClient(app)

ENDPOINT = "/applications/APP-001/decision"


@pytest.fixture(autouse=True)
def clear_overrides():
    """Prevent authentication overrides leaking between tests."""
    yield
    app.dependency_overrides.clear()


def override_role(role: UserRole):
    """Override the endpoint's authentication dependency."""
    route = next(
        route
        for route in app.routes
        if route.path == "/applications/{application_id}/decision"
    )

    role_dependency = route.dependant.dependencies[0].call

    app.dependency_overrides[role_dependency] = lambda: UserProfile(
        uid="test-user-123",
        email="test@example.com",
        role=role,
        display_name="Test User",
    )


def mock_decision_response(decision, status):
    return {
        "applicationId": "APP-001",
        "underwriterDecision": decision,
        "lifecycleStatus": status,
        "decidedBy": "test-user-123",
        "decidedAt": "2026-11-20T10:00:00Z",
    }


def test_underwriter_can_approve_application():
    override_role(UserRole.UNDERWRITER)

    expected = mock_decision_response(
        "APPROVED",
        "UNDERWRITER_APPROVED",
    )

    with patch(
        "app.main.save_underwriter_decision",
        return_value=expected,
    ) as mock_save:

        response = client.patch(
            ENDPOINT,
            json={"decision": "APPROVED"},
        )

    assert response.status_code == 200
    assert response.json()["underwriterDecision"] == "APPROVED"
    assert response.json()["lifecycleStatus"] == "UNDERWRITER_APPROVED"

    mock_save.assert_called_once()


def test_underwriter_can_reject_application():
    override_role(UserRole.UNDERWRITER)

    expected = mock_decision_response(
        "REJECTED",
        "CLOSED",
    )

    with patch(
        "app.main.save_underwriter_decision",
        return_value=expected,
    ):

        response = client.patch(
            ENDPOINT,
            json={"decision": "REJECTED"},
        )

    assert response.status_code == 200
    assert response.json()["underwriterDecision"] == "REJECTED"
    assert response.json()["lifecycleStatus"] == "CLOSED"


def test_invalid_decision_returns_422():
    override_role(UserRole.UNDERWRITER)

    response = client.patch(
        ENDPOINT,
        json={"decision": "MAYBE"},
    )

    assert response.status_code == 422


def test_missing_decision_returns_422():
    override_role(UserRole.UNDERWRITER)

    response = client.patch(
        ENDPOINT,
        json={},
    )

    assert response.status_code == 422


@pytest.mark.parametrize(
    "role",
    [
        UserRole.BORROWER,
        UserRole.BROKER,
    ],
)
def test_non_underwriter_cannot_approve_application(role):
    """Verify that borrowers and brokers cannot approve applications."""

    # Mock Firebase authentication.
    app.dependency_overrides[verify_firebase_token] = lambda: {
        "uid": "test-user-123"
    }

    # Mock the user profile returned by Firestore.
    mock_profile = UserProfile(
        uid="test-user-123",
        email="test@example.com",
        role=role,
        display_name="Test User",
    )

    with (
        patch(
            "app.permissions.get_user_profile",
            return_value=mock_profile,
        ),
        patch(
            "app.main.save_underwriter_decision",
        ) as mock_save,
    ):
        response = client.patch(
            ENDPOINT,
            json={"decision": "APPROVED"},
        )

    assert response.status_code == 403
    assert response.json()["detail"] == "Insufficient permissions"

    # Unauthorized users must never reach the decision logic.
    mock_save.assert_not_called()
