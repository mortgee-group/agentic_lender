from unittest.mock import AsyncMock, Mock
from fastapi.testclient import TestClient
import httpx
from app.main import app
from app.auth import verify_firebase_token
from app.permissions import require_role
from app.models import UserProfile, UserRole
import app.main as main_module


client = TestClient(app)


def test_assess_application_success(monkeypatch):
    # Mock Firebase authentication
    app.dependency_overrides[verify_firebase_token] = lambda: {
        "uid": "broker-123",
        "email": "broker@example.com",
    }

    # Mock broker permissions
    route = next(
        r for r in app.routes
        if r.path == "/applications/{application_id}/assess"
    )

    role_dependency = route.dependant.dependencies[0].call

    app.dependency_overrides[role_dependency] = lambda: UserProfile(
        uid="broker-123",
        email="broker@example.com",
        role=UserRole.BROKER,
        display_name="Test Broker",
    )

    # Mock Firestore application
    class MockApplication:
        borrowerId = "borrower-123"
        brokerId = "broker-123"

        def model_dump(self, **kwargs):
            return {
                "householdAnnualGrossIncome": 75000,
                "existingDebtLiabilities": 10000,
                "deposit": 70000,
                "requestedMortgageTerm": 30,
                "sellerAskingPrice": 350000,
                "property": {
                    "county": "Kilkenny",
                    "floor_area": 115,
                    "bedrooms": 3,
                    "bathrooms": 2,
                    "year_built": 2005,
                    "property_type": "SEMI_DETACHED",
                    "ber_rating": "B2",
                },
            }

    monkeypatch.setattr(
        main_module,
        "get_application",
        lambda application_id: MockApplication(),
    )

    # Mock AL-AI response
    mock_assess = AsyncMock(return_value={
        "predicted_value": 340000,
        "overvaluation_pct": 2.94,
        "ltv": 82.35,
        "dti": 30.0,
        "stress_tested_repayment": 1700,
        "max_borrowing_capacity": 262500,
    })

    monkeypatch.setattr(
        main_module,
        "assess_application",
        mock_assess,
    )
    mock_save_valuation = Mock(return_value="valuation-123")
    monkeypatch.setattr(
        main_module,
        "save_valuation",
        mock_save_valuation,
    )

    try:
        response = client.post("/applications/APP-001/assess")

        assert response.status_code == 200
        mock_save_valuation.assert_called_once_with(
            "APP-001",
            mock_assess.call_args.args[0]["property"],
            mock_assess.return_value,
        )
        assert response.json()["predicted_value"] == 340000
        assert response.json()["ltv"] == 82.35

        mock_assess.assert_awaited_once()

        # Verify the application data was sent to AL-AI
        sent_data = mock_assess.await_args.args[0]
        assert sent_data["sellerAskingPrice"] == 350000
        assert sent_data["property"]["ber_rating"] == "B2"

    finally:
        app.dependency_overrides.clear()


def test_assess_returns_503_when_ai_unavailable(monkeypatch):

    mock_application = Mock()
    mock_application.brokerId = "broker-123"
    mock_application.model_dump.return_value = {
        "property": {"county": "Kildare"},
    }

    monkeypatch.setattr(
        main_module,
        "get_application",
        Mock(return_value=mock_application),
    )

    monkeypatch.setattr(
        main_module,
        "assess_application",
        AsyncMock(side_effect=httpx.ConnectError("Connection refused")),
    )

    route = next(
        r for r in app.routes
        if r.path == "/applications/{application_id}/assess"
    )
    role_dependency = route.dependant.dependencies[0].call

    app.dependency_overrides[role_dependency] = lambda: UserProfile(
        uid="broker-123",
        email="broker@example.com",
        role=UserRole.BROKER,
        display_name="Test Broker",
    )

    try:
        response = TestClient(app).post("/applications/APP-001/assess")

        assert response.status_code == 503
        assert response.json()["detail"] == (
            "AI assessment service is currently unavailable"
        )
    finally:
        app.dependency_overrides.clear()


def test_assess_returns_502_when_ai_returns_error(monkeypatch):

    mock_application = Mock()
    mock_application.brokerId = "broker-123"
    mock_application.model_dump.return_value = {
        "property": {"county": "Kildare"},
    }

    monkeypatch.setattr(
        main_module,
        "get_application",
        Mock(return_value=mock_application),
    )

    monkeypatch.setattr(
        main_module,
        "assess_application",
        AsyncMock(
            side_effect=httpx.HTTPStatusError(
                "Internal Server Error",
                request=httpx.Request(
                    "POST",
                    "http://localhost:5000/api/v1/ai/assess",
                ),
                response=httpx.Response(500),
            )
        ),
    )

    route = next(
        r for r in app.routes
        if r.path == "/applications/{application_id}/assess"
    )
    role_dependency = route.dependant.dependencies[0].call

    app.dependency_overrides[role_dependency] = lambda: UserProfile(
        uid="broker-123",
        email="broker@example.com",
        role=UserRole.BROKER,
        display_name="Test Broker",
    )

    try:
        response = TestClient(app).post("/applications/APP-001/assess")

        assert response.status_code == 502
        assert response.json()["detail"] == (
            "AI assessment service returned an error"
        )
    finally:
        app.dependency_overrides.clear()
