from unittest.mock import AsyncMock
from unittest.mock import Mock
from fastapi.testclient import TestClient
import httpx
import app.main as main_module
from app.main import app
from app.models import UserProfile, UserRole, ApplicationStatus


client = TestClient(app)


def test_underwrite_application_success(monkeypatch):
    # Override the exact role dependency registered by FastAPI.
    route = next(
        r for r in app.routes
        if r.path == "/applications/{application_id}/underwrite"
    )
    role_dependency = route.dependant.dependencies[0].call

    app.dependency_overrides[role_dependency] = lambda: UserProfile(
        uid="underwriter-123",
        email="underwriter@example.com",
        role=UserRole.UNDERWRITER,
        display_name="Test Underwriter",
    )

    # Mock the application retrieved from Firestore.
    class MockApplication:
        sellerAskingPrice = 350000
        deposit = 70000
        lifecycleStatus = ApplicationStatus.APPLIED

        def model_dump(self, **kwargs):
            return {
                "borrowerId": "borrower-123",
                "brokerId": "broker-123",
                "requestedLoanAmount": None,
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

    # Mock the response from AL-AI.
    mock_underwrite = AsyncMock(
        return_value={
            "applicationId": "APP-001",
            "decision": "APPROVE",
            "rationale": "Application meets lending criteria.",
            "estimatedLTV": 82.35,
            "DTI": 30.0,
            "approvalLikelihood": "High",
            "riskTier": "Tier 1",
            "lifecycleStatus": "AI_DELIBERATED",
        }
    )

    monkeypatch.setattr(
        main_module,
        "underwrite_application",
        mock_underwrite,
    )
    mock_save_decision = Mock(return_value="decision-123")
    monkeypatch.setattr(main_module, "save_ai_decision", mock_save_decision)

    try:
        response = client.post("/applications/APP-001/underwrite")

        assert response.status_code == 200
        mock_save_decision.assert_called_once_with(
            "APP-001",
            mock_underwrite.return_value,
        )
        assert response.json()["decision"] == "APPROVE"
        assert response.json()["riskTier"] == "Tier 1"

        mock_underwrite.assert_awaited_once()

        sent_data = mock_underwrite.await_args.args[0]

        assert sent_data["applicationId"] == "APP-001"
        assert sent_data["borrowerId"] == "borrower-123"
        assert sent_data["requestedLoanAmount"] == 280000
        assert sent_data["property"]["ber_rating"] == "B2"

    finally:
        app.dependency_overrides.clear()


def test_underwrite_application_not_found(monkeypatch):
    route = next(
        r for r in app.routes
        if r.path == "/applications/{application_id}/underwrite"
    )
    role_dependency = route.dependant.dependencies[0].call

    app.dependency_overrides[role_dependency] = lambda: UserProfile(
        uid="underwriter-123",
        email="underwriter@example.com",
        role=UserRole.UNDERWRITER,
        display_name="Test Underwriter",
    )

    # Simulate an application that doesn't exist in Firestore.
    monkeypatch.setattr(
        main_module,
        "get_application",
        lambda application_id: None,
    )

    # Ensure AL-AI is never called.
    mock_underwrite = AsyncMock()
    monkeypatch.setattr(
        main_module,
        "underwrite_application",
        mock_underwrite,
    )

    try:
        response = client.post("/applications/INVALID-ID/underwrite")

        assert response.status_code == 404
        assert response.json()["detail"] == "Application not found"

        mock_underwrite.assert_not_awaited()

    finally:
        app.dependency_overrides.clear()


def test_borrower_cannot_underwrite(monkeypatch):
    # Mock Firebase authentication.
    from app.auth import verify_firebase_token

    app.dependency_overrides[verify_firebase_token] = lambda: {
        "uid": "borrower-123",
        "email": "borrower@example.com",
    }

    # Mock the borrower's Firestore profile.
    monkeypatch.setattr(
        "app.permissions.get_user_profile",
        lambda uid: UserProfile(
            uid=uid,
            email="borrower@example.com",
            role=UserRole.BORROWER,
            display_name="Test Borrower",
        ),
    )

    # Ensure AL-AI is never called.
    mock_underwrite = AsyncMock()
    monkeypatch.setattr(
        main_module,
        "underwrite_application",
        mock_underwrite,
    )

    try:
        response = client.post("/applications/APP-001/underwrite")

        assert response.status_code == 403
        mock_underwrite.assert_not_awaited()

    finally:
        app.dependency_overrides.clear()


def test_underwrite_returns_503_when_ai_unavailable(monkeypatch):
    # Override the exact role dependency registered by FastAPI.
    route = next(
        r for r in app.routes
        if r.path == "/applications/{application_id}/underwrite"
    )
    role_dependency = route.dependant.dependencies[0].call

    app.dependency_overrides[role_dependency] = lambda: UserProfile(
        uid="underwriter-123",
        email="underwriter@example.com",
        role=UserRole.UNDERWRITER,
        display_name="Test Underwriter",
    )

    # Mock the application retrieved from Firestore.
    class MockApplication:
        sellerAskingPrice = 350000
        deposit = 70000
        lifecycleStatus = ApplicationStatus.APPLIED

        def model_dump(self, **kwargs):
            return {
                "borrowerId": "borrower-123",
                "brokerId": "broker-123",
                "requestedLoanAmount": None,
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

    # Simulate AL-AI being unavailable.
    mock_underwrite = AsyncMock(
        side_effect=httpx.ConnectError("Connection refused")
    )

    monkeypatch.setattr(
        main_module,
        "underwrite_application",
        mock_underwrite,
    )
    mock_save_decision = Mock(return_value="decision-123")
    monkeypatch.setattr(main_module, "save_ai_decision", mock_save_decision)

    try:
        response = client.post("/applications/APP-001/underwrite")

        assert response.status_code == 503
        assert response.json()["detail"] == (
            "AI underwriting service is currently unavailable"
        )

        mock_underwrite.assert_awaited_once()
        mock_save_decision.assert_not_called()

    finally:
        app.dependency_overrides.clear()


def test_underwrite_returns_502_when_ai_returns_error(monkeypatch):
    # Override the exact role dependency registered by FastAPI.
    route = next(
        r for r in app.routes
        if r.path == "/applications/{application_id}/underwrite"
    )
    role_dependency = route.dependant.dependencies[0].call

    app.dependency_overrides[role_dependency] = lambda: UserProfile(
        uid="underwriter-123",
        email="underwriter@example.com",
        role=UserRole.UNDERWRITER,
        display_name="Test Underwriter",
    )

    # Mock the application retrieved from Firestore.
    class MockApplication:
        sellerAskingPrice = 350000
        deposit = 70000
        lifecycleStatus = ApplicationStatus.APPLIED

        def model_dump(self, **kwargs):
            return {
                "borrowerId": "borrower-123",
                "brokerId": "broker-123",
                "requestedLoanAmount": None,
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

    mock_underwrite = AsyncMock(
        side_effect=httpx.HTTPStatusError(
            "Internal Server Error",
            request=httpx.Request(
                "POST",
                "http://localhost:5000/api/v1/ai/underwrite",
            ),
            response=httpx.Response(500),
        )
    )

    monkeypatch.setattr(
        main_module,
        "underwrite_application",
        mock_underwrite,
    )
    mock_save_decision = Mock(return_value="decision-123")
    monkeypatch.setattr(main_module, "save_ai_decision", mock_save_decision)

    try:
        response = client.post("/applications/APP-001/underwrite")

        assert response.status_code == 502
        assert response.json()["detail"] == (
            "AI underwriting service returned an error"
        )

        mock_underwrite.assert_awaited_once()
        mock_save_decision.assert_not_called()

    finally:
        app.dependency_overrides.clear()
