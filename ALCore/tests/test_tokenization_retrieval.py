"""Tests for mortgage tokenization retrieval and access control."""

from datetime import datetime, timezone
from types import SimpleNamespace
from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

from app.auth import verify_firebase_token
from app.main import app
from app.models import UserProfile, UserRole


client = TestClient(app)

APPLICATION_ID = "APP-001"
ENDPOINT = f"/applications/{APPLICATION_ID}/tokenization"


@pytest.fixture
def application():
    return SimpleNamespace(
        borrowerId="borrower-001",
        brokerId="broker-001",
    )


@pytest.fixture
def completed_record():
    now = datetime.now(timezone.utc)

    return {
        "applicationId": APPLICATION_ID,
        "status": "COMPLETED",
        "tokenId": 2,
        "transactionHash": "0x" + "a" * 64,
        "contractAddress": "0x" + "b" * 40,
        "chainId": 31337,
        "auditHash": "0x" + "c" * 64,
        "createdAt": now,
        "completedAt": now,
        "requestedBy": "underwriter-001",
    }


@pytest.fixture
def authenticate():
    """Mock Firebase authentication while retaining real role checks."""

    original_overrides = app.dependency_overrides.copy()

    def authenticate_user(role, uid):
        profile = UserProfile(
            uid=uid,
            email=f"{uid}@example.com",
            role=role,
        )

        app.dependency_overrides[verify_firebase_token] = (
            lambda: {"uid": uid}
        )

        return patch(
            "app.permissions.get_user_profile",
            return_value=profile,
        )

    yield authenticate_user

    app.dependency_overrides.clear()
    app.dependency_overrides.update(original_overrides)


@pytest.mark.parametrize(
    "role,uid",
    [
        (UserRole.BORROWER, "borrower-001"),
        (UserRole.BROKER, "broker-001"),
        (UserRole.UNDERWRITER, "underwriter-001"),
    ],
)
def test_authorized_user_can_retrieve_completed_tokenization(
    role,
    uid,
    authenticate,
    application,
    completed_record,
):
    with (
        authenticate(role, uid),
        patch(
            "app.main.get_application",
            return_value=application,
        ) as mock_application,
        patch(
            "app.main.get_existing_tokenization",
            return_value=completed_record,
        ) as mock_record,
        patch(
            "app.main.tokenize_approved_application"
        ) as mock_tokenize,
    ):
        response = client.get(ENDPOINT)

    assert response.status_code == 200

    data = response.json()

    assert data["applicationId"] == APPLICATION_ID
    assert data["status"] == "COMPLETED"
    assert data["tokenId"] == 2
    assert data["chainId"] == 31337
    assert data["transactionHash"] == completed_record["transactionHash"]
    assert data["contractAddress"] == completed_record["contractAddress"]
    assert data["auditHash"] == completed_record["auditHash"]

    assert "createdAt" in data
    assert "completedAt" in data

    # Internal user identifiers must not be exposed.
    assert "requestedBy" not in data

    mock_application.assert_called_once_with(APPLICATION_ID)
    mock_record.assert_called_once_with(APPLICATION_ID)
    mock_tokenize.assert_not_called()


def test_retrieve_pending_tokenization(authenticate, application):
    pending_record = {
        "applicationId": APPLICATION_ID,
        "status": "PENDING",
        "createdAt": datetime.now(timezone.utc),
        "requestedBy": "underwriter-001",
    }

    with (
        authenticate(UserRole.UNDERWRITER, "underwriter-001"),
        patch(
            "app.main.get_application",
            return_value=application,
        ),
        patch(
            "app.main.get_existing_tokenization",
            return_value=pending_record,
        ),
        patch(
            "app.main.tokenize_approved_application"
        ) as mock_tokenize,
    ):
        response = client.get(ENDPOINT)

    assert response.status_code == 200

    data = response.json()

    assert data["applicationId"] == APPLICATION_ID
    assert data["status"] == "PENDING"
    assert "createdAt" in data
    assert "tokenId" not in data
    assert "transactionHash" not in data
    assert "completedAt" not in data
    assert "requestedBy" not in data

    mock_tokenize.assert_not_called()


@pytest.mark.parametrize(
    "role,uid",
    [
        (UserRole.BORROWER, "different-borrower"),
        (UserRole.BROKER, "different-broker"),
    ],
)
def test_unauthorized_application_access_is_rejected(
    role,
    uid,
    authenticate,
    application,
):
    with (
        authenticate(role, uid),
        patch(
            "app.main.get_application",
            return_value=application,
        ),
        patch(
            "app.main.get_existing_tokenization"
        ) as mock_record,
    ):
        response = client.get(ENDPOINT)

    assert response.status_code == 403
    assert response.json()["detail"] == "Insufficient permissions"

    mock_record.assert_not_called()


def test_broker_cannot_retrieve_application_without_assignment(
    authenticate,
    application,
):
    application.brokerId = None

    with (
        authenticate(UserRole.BROKER, "broker-001"),
        patch(
            "app.main.get_application",
            return_value=application,
        ),
        patch(
            "app.main.get_existing_tokenization"
        ) as mock_record,
    ):
        response = client.get(ENDPOINT)

    assert response.status_code == 403
    mock_record.assert_not_called()


def test_application_not_found(authenticate):
    with (
        authenticate(UserRole.UNDERWRITER, "underwriter-001"),
        patch(
            "app.main.get_application",
            return_value=None,
        ),
        patch(
            "app.main.get_existing_tokenization"
        ) as mock_record,
    ):
        response = client.get(ENDPOINT)

    assert response.status_code == 404
    assert response.json()["detail"] == "Application not found"

    mock_record.assert_not_called()


def test_tokenization_record_not_found(authenticate, application):
    with (
        authenticate(UserRole.BORROWER, "borrower-001"),
        patch(
            "app.main.get_application",
            return_value=application,
        ),
        patch(
            "app.main.get_existing_tokenization",
            return_value=None,
        ) as mock_record,
    ):
        response = client.get(ENDPOINT)

    assert response.status_code == 404
    assert response.json()["detail"] == "Tokenization record not found"

    mock_record.assert_called_once_with(APPLICATION_ID)


def test_unauthenticated_request_is_rejected():
    with (
        patch("app.main.get_application") as mock_application,
        patch("app.main.get_existing_tokenization") as mock_record,
    ):
        response = client.get(ENDPOINT)

    assert response.status_code in (401, 403)

    mock_application.assert_not_called()
    mock_record.assert_not_called()


def test_missing_user_profile_is_rejected():
    original_overrides = app.dependency_overrides.copy()

    app.dependency_overrides[verify_firebase_token] = (
        lambda: {"uid": "unknown-user"}
    )

    try:
        with (
            patch(
                "app.permissions.get_user_profile",
                return_value=None,
            ),
            patch("app.main.get_application") as mock_application,
            patch("app.main.get_existing_tokenization") as mock_record,
        ):
            response = client.get(ENDPOINT)

        assert response.status_code == 403
        assert response.json()["detail"] == "User profile not found"

        mock_application.assert_not_called()
        mock_record.assert_not_called()

    finally:
        app.dependency_overrides.clear()
        app.dependency_overrides.update(original_overrides)


def test_repeated_retrieval_does_not_trigger_tokenization(
    authenticate,
    application,
    completed_record,
):
    with (
        authenticate(UserRole.UNDERWRITER, "underwriter-001"),
        patch(
            "app.main.get_application",
            return_value=application,
        ),
        patch(
            "app.main.get_existing_tokenization",
            return_value=completed_record,
        ) as mock_record,
        patch(
            "app.main.tokenize_approved_application"
        ) as mock_tokenize,
    ):
        first_response = client.get(ENDPOINT)
        second_response = client.get(ENDPOINT)

    assert first_response.status_code == 200
    assert second_response.status_code == 200
    assert first_response.json() == second_response.json()

    assert mock_record.call_count == 2
    mock_tokenize.assert_not_called()
