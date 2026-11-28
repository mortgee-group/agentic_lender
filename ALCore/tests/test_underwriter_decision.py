"""Tests for Underwriter decisions and automatic mortgage tokenization."""

from unittest.mock import patch

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from app.main import app
from app.models import UserProfile, UserRole
from app.auth import verify_firebase_token


client = TestClient(app)

ENDPOINT = "/applications/APP-001/decision"

APPROVAL_REQUEST = {
    "decision": "APPROVED",
    "confirmInterestRate": True,
}


@pytest.fixture(autouse=True)
def clear_overrides():
    """Prevent authentication overrides leaking between tests."""
    yield
    app.dependency_overrides.clear()


@pytest.fixture(autouse=True)
def mock_tokenization():
    """Prevent tests from accessing the live blockchain."""
    with patch(
        "app.main.tokenize_approved_application",
        return_value={
            "applicationId": "APP-001",
            "status": "COMPLETED",
            "tokenId": 1,
            "transactionHash": "0x1234",
            "chainId": 31337,
        },
    ) as mock:
        yield mock


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


def test_underwriter_can_approve_application(mock_tokenization):
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
            json=APPROVAL_REQUEST,
        )

    assert response.status_code == 200
    assert response.json()["underwriterDecision"] == "APPROVED"
    assert response.json()["lifecycleStatus"] == "UNDERWRITER_APPROVED"

    mock_save.assert_called_once()
    mock_tokenization.assert_called_once_with(
        application_id="APP-001",
        underwriter_id="test-user-123",
    )


def test_underwriter_can_reject_application(mock_tokenization):
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
    assert "tokenization" not in response.json()

    mock_tokenization.assert_not_called()


def test_invalid_decision_returns_422(mock_tokenization):
    override_role(UserRole.UNDERWRITER)

    response = client.patch(
        ENDPOINT,
        json={"decision": "MAYBE"},
    )

    assert response.status_code == 422
    mock_tokenization.assert_not_called()


def test_missing_decision_returns_422(mock_tokenization):
    override_role(UserRole.UNDERWRITER)

    response = client.patch(
        ENDPOINT,
        json={},
    )

    assert response.status_code == 422
    mock_tokenization.assert_not_called()


@pytest.mark.parametrize(
    "role",
    [
        UserRole.BORROWER,
        UserRole.BROKER,
    ],
)
def test_non_underwriter_cannot_approve_application(
    role,
    mock_tokenization,
):
    """Only authorised Underwriters can approve mortgages."""

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
            json=APPROVAL_REQUEST,
        )

    assert response.status_code == 403
    assert response.json()["detail"] == "Insufficient permissions"

    mock_save.assert_not_called()
    mock_tokenization.assert_not_called()


def test_unauthenticated_user_cannot_approve_application(
    mock_tokenization,
):
    with patch(
        "app.main.save_underwriter_decision",
    ) as mock_save:
        response = client.patch(
            ENDPOINT,
            json=APPROVAL_REQUEST,
        )

    assert response.status_code == 401
    assert response.json()["detail"] == (
        "Missing or invalid Authorization header"
    )

    mock_save.assert_not_called()
    mock_tokenization.assert_not_called()


def test_underwriter_cannot_approve_without_interest_rate_confirmation(
    mock_tokenization,
):
    override_role(UserRole.UNDERWRITER)

    response = client.patch(
        ENDPOINT,
        json={"decision": "APPROVED"},
    )

    assert response.status_code == 422
    mock_tokenization.assert_not_called()


def test_approval_returns_tokenization_details(mock_tokenization):
    """Successful approval includes the blockchain result."""
    override_role(UserRole.UNDERWRITER)

    expected = mock_decision_response(
        "APPROVED",
        "UNDERWRITER_APPROVED",
    )

    with patch(
        "app.main.save_underwriter_decision",
        return_value=expected,
    ):
        response = client.patch(
            ENDPOINT,
            json=APPROVAL_REQUEST,
        )

    assert response.status_code == 200

    result = response.json()

    assert result["applicationId"] == "APP-001"
    assert result["underwriterDecision"] == "APPROVED"
    assert result["lifecycleStatus"] == "UNDERWRITER_APPROVED"
    assert result["decidedBy"] == "test-user-123"
    assert result["decidedAt"] == "2026-11-20T10:00:00Z"

    assert result["tokenization"]["status"] == "COMPLETED"
    assert result["tokenization"]["tokenId"] == 1
    assert result["tokenization"]["transactionHash"] == "0x1234"
    assert result["tokenization"]["chainId"] == 31337


def test_approval_is_saved_before_tokenization(mock_tokenization):
    """Blockchain interaction must follow successful persistence."""
    override_role(UserRole.UNDERWRITER)

    operations = []

    def save_decision(**kwargs):
        operations.append("approval_saved")

        return mock_decision_response(
            "APPROVED",
            "UNDERWRITER_APPROVED",
        )

    def tokenize(**kwargs):
        operations.append("tokenization_started")

        return {
            "status": "COMPLETED",
            "tokenId": 1,
        }

    mock_tokenization.side_effect = tokenize

    with patch(
        "app.main.save_underwriter_decision",
        side_effect=save_decision,
    ):
        response = client.patch(
            ENDPOINT,
            json=APPROVAL_REQUEST,
        )

    assert response.status_code == 200
    assert operations == [
        "approval_saved",
        "tokenization_started",
    ]


def test_failed_approval_does_not_trigger_tokenization(
    mock_tokenization,
):
    """An unsuccessful approval must never mint a token."""
    override_role(UserRole.UNDERWRITER)

    with patch(
        "app.main.save_underwriter_decision",
        side_effect=HTTPException(
            status_code=409,
            detail="Application cannot be approved",
        ),
    ):
        response = client.patch(
            ENDPOINT,
            json=APPROVAL_REQUEST,
        )

    assert response.status_code == 409
    mock_tokenization.assert_not_called()


def test_tokenization_failure_preserves_approval(mock_tokenization):
    """Blockchain failure must not invalidate mortgage approval."""
    override_role(UserRole.UNDERWRITER)

    mock_tokenization.side_effect = HTTPException(
        status_code=503,
        detail="Blockchain unavailable",
    )

    with patch(
        "app.main.save_underwriter_decision",
        return_value=mock_decision_response(
            "APPROVED",
            "UNDERWRITER_APPROVED",
        ),
    ):
        response = client.patch(
            ENDPOINT,
            json=APPROVAL_REQUEST,
        )

    assert response.status_code == 200

    result = response.json()

    assert result["underwriterDecision"] == "APPROVED"
    assert result["lifecycleStatus"] == "UNDERWRITER_APPROVED"
    assert result["tokenization"]["status"] == "UNCONFIRMED"
    assert result["tokenization"]["detail"] == "Blockchain unavailable"


def test_unexpected_tokenization_error_preserves_approval(
    mock_tokenization,
):
    """Unexpected errors must not misrepresent a saved approval."""
    override_role(UserRole.UNDERWRITER)

    mock_tokenization.side_effect = RuntimeError(
        "Unexpected service error"
    )

    with patch(
        "app.main.save_underwriter_decision",
        return_value=mock_decision_response(
            "APPROVED",
            "UNDERWRITER_APPROVED",
        ),
    ):
        response = client.patch(
            ENDPOINT,
            json=APPROVAL_REQUEST,
        )

    assert response.status_code == 200

    result = response.json()

    assert result["underwriterDecision"] == "APPROVED"
    assert result["lifecycleStatus"] == "UNDERWRITER_APPROVED"
    assert result["tokenization"]["status"] == "UNCONFIRMED"
    assert result["tokenization"]["detail"] == (
        "Automatic tokenization could not be confirmed"
    )