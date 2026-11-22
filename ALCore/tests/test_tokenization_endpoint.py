"""Tests for the underwriter-only mortgage tokenization endpoint."""

from unittest.mock import patch

from fastapi.testclient import TestClient

from app.main import app
from app.models import UserRole
from app.permissions import require_role


client = TestClient(app)


def test_underwriter_can_tokenize():
    """An authorized underwriter can access the endpoint."""

    dependency = require_role(UserRole.UNDERWRITER)

    # Override the actual dependency registered on the route.
    route = next(
        route
        for route in app.routes
        if getattr(route, "path", None) == "/applications/{application_id}/tokenize"
    )

    actual_dependency = route.dependant.dependencies[0].call

    def mock_underwriter():
        return type("Profile", (), {"uid": "UNDERWRITER-001"})()

    app.dependency_overrides[actual_dependency] = mock_underwriter

    try:
        with patch(
            "app.main.tokenize_approved_application",
            return_value={
                "applicationId": "APP-001",
                "status": "COMPLETED",
                "tokenId": 1,
            },
        ) as mock_tokenize:
            response = client.post("/applications/APP-001/tokenize")

        assert response.status_code == 200
        assert response.json()["tokenId"] == 1
        mock_tokenize.assert_called_once_with(
            application_id="APP-001",
            underwriter_id="UNDERWRITER-001",
        )

    finally:
        app.dependency_overrides.pop(actual_dependency, None)


def test_unauthenticated_request_is_rejected():
    """Requests without Firebase authentication must be rejected."""

    response = client.post("/applications/APP-001/tokenize")

    assert response.status_code in (401, 403)
