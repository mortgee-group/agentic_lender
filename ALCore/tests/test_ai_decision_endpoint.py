from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.auth import verify_firebase_token
from app.models import UserProfile, UserRole


client = TestClient(app)

ENDPOINT = "/applications/APP-001/ai-decision"


@pytest.fixture(autouse=True)
def clear_overrides():
    """Prevent authentication overrides leaking between tests."""
    yield
    app.dependency_overrides.clear()


def override_underwriter():
    """Authenticate requests as an underwriter."""
    route = next(
        route
        for route in app.routes
        if route.path == "/applications/{application_id}/ai-decision"
    )

    role_dependency = route.dependant.dependencies[0].call

    app.dependency_overrides[role_dependency] = lambda: UserProfile(
        uid="underwriter-123",
        email="underwriter@example.com",
        role=UserRole.UNDERWRITER,
        display_name="Test Underwriter",
    )


def test_underwriter_can_retrieve_ai_decision():
    """Return the saved AI response without rerunning underwriting."""
    override_underwriter()

    expected_decision = {
        "decision": "APPROVED",
        "credit_memo": "Application meets lending criteria.",
        "agent_trails": [],
        "agent_outputs": {},
    }

    with (
        patch("app.main.get_application", return_value={"applicationId": "APP-001"}),
        patch("app.main.get_ai_decision", return_value=expected_decision),
    ):
        response = client.get(ENDPOINT)

    assert response.status_code == 200
    assert response.json() == expected_decision


def test_missing_application_returns_404():
    """Return 404 when the mortgage application does not exist."""
    override_underwriter()

    with (
        patch("app.main.get_application", return_value=None),
        patch("app.main.get_ai_decision") as mock_get_decision,
    ):
        response = client.get(ENDPOINT)

    assert response.status_code == 404
    assert response.json()["detail"] == "Application not found"
    mock_get_decision.assert_not_called()


def test_missing_ai_decision_returns_404():
    """Return 404 when underwriting has not been completed."""
    override_underwriter()

    with (
        patch("app.main.get_application", return_value={"applicationId": "APP-001"}),
        patch("app.main.get_ai_decision", return_value=None),
    ):
        response = client.get(ENDPOINT)

    assert response.status_code == 404
    assert response.json()["detail"] == "AI underwriting decision not found"


@pytest.mark.parametrize(
    "role",
    [UserRole.BORROWER, UserRole.BROKER],
)
def test_non_underwriter_cannot_retrieve_ai_decision(role):
    """Borrowers and brokers must not access AI underwriting results."""

    app.dependency_overrides[verify_firebase_token] = lambda: {
        "uid": "test-user-123"
    }

    mock_profile = UserProfile(
        uid="test-user-123",
        email="test@example.com",
        role=role,
        display_name="Test User",
    )

    with (
        patch("app.permissions.get_user_profile", return_value=mock_profile),
        patch("app.main.get_ai_decision") as mock_get_decision,
    ):
        response = client.get(ENDPOINT)

    assert response.status_code == 403
    assert response.json()["detail"] == "Insufficient permissions"
    mock_get_decision.assert_not_called()


def test_unauthenticated_user_cannot_retrieve_ai_decision():
    """Requests without a Firebase token must be rejected."""

    with patch("app.main.get_ai_decision") as mock_get_decision:
        response = client.get(ENDPOINT)

    assert response.status_code == 401
    mock_get_decision.assert_not_called()
