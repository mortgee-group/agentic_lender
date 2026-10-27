from unittest.mock import patch
from datetime import datetime, timezone
from fastapi.testclient import TestClient

from app.main import app
from app.models import UserProfile, UserRole, LoanApplication, PropertyDetails
from app.permissions import require_role
from app.auth import verify_firebase_token


client = TestClient(app)


def mock_borrower():
    return UserProfile(
        uid="borrower-123",
        email="borrower@example.com",
        role=UserRole.BORROWER,
        display_name="Test Borrower",
    )


def test_borrower_can_create_application():

    borrower_dependency = next(
        dependency.call
        for route in app.routes
        if getattr(route, "path", None) == "/applications"
        and "POST" in getattr(route, "methods", set())
        for dependency in route.dependant.dependencies
    )

    app.dependency_overrides[borrower_dependency] = mock_borrower

    payload = {
        "householdAnnualGrossIncome": 75000,
        "existingDebtLiabilities": 10000,
        "deposit": 70000,
        "requestedMortgageTerm": 30,
        "sellerAskingPrice": 350000,
        "property": {
            "location": "Kilkenny City",
            "county": "Kilkenny",
            "floorArea": 115,
            "bedrooms": 3,
            "propertyType": "SEMI_DETACHED",
            "berEnergyRating": "B2",
        },
    }

    try:
        with patch(
            "app.main.create_application",
            return_value="application-001",
        ) as mock_create:
            response = client.post("/applications", json=payload)

        assert response.status_code == 201
        assert response.json()["applicationId"] == "application-001"

        mock_create.assert_called_once()
        assert mock_create.call_args.kwargs["borrower_id"] == "borrower-123"

    finally:
        app.dependency_overrides.clear()


def test_broker_cannot_create_application():
    from fastapi import HTTPException

    def deny_broker():
        raise HTTPException(
            status_code=403,
            detail="Insufficient permissions",
        )

    borrower_dependency = next(
        dependency.call
        for route in app.routes
        if getattr(route, "path", None) == "/applications"
        and "POST" in getattr(route, "methods", set())
        for dependency in route.dependant.dependencies
    )

    app.dependency_overrides[borrower_dependency] = deny_broker

    try:
        with patch("app.main.create_application") as mock_create:
            response = client.post("/applications", json={})

        assert response.status_code == 403
        assert response.json()["detail"] == "Insufficient permissions"
        mock_create.assert_not_called()

    finally:
        app.dependency_overrides.clear()


def test_real_permissions_reject_broker():
    def mock_firebase_token():
        return {"uid": "broker-123"}

    broker_profile = UserProfile(
        uid="broker-123",
        email="broker@example.com",
        role=UserRole.BROKER,
        display_name="Test Broker",
    )

    app.dependency_overrides[verify_firebase_token] = mock_firebase_token

    try:
        with (
            patch(
                "app.permissions.get_user_profile",
                return_value=broker_profile,
            ),
            patch("app.main.create_application") as mock_create,
        ):
            response = client.post("/applications", json={})

        assert response.status_code == 403
        assert response.json()["detail"] == "Insufficient permissions"
        mock_create.assert_not_called()

    finally:
        app.dependency_overrides.clear()


def test_borrower_can_retrieve_own_application():
    application = LoanApplication(
        borrowerId="borrower-123",
        householdAnnualGrossIncome=75000,
        existingDebtLiabilities=10000,
        deposit=70000,
        requestedMortgageTerm=30,
        sellerAskingPrice=350000,
        property=PropertyDetails(
            location="Kilkenny City",
            county="Kilkenny",
            floorArea=115,
            bedrooms=3,
            propertyType="SEMI_DETACHED",
            berEnergyRating="B2",
        ),
        createdAt=datetime.now(timezone.utc),
        updatedAt=datetime.now(timezone.utc),
    )

    # Find the exact dependency registered for the GET endpoint.
    borrower_dependency = next(
        dependency.call
        for route in app.routes
        if getattr(route, "path", None) == "/applications/{application_id}"
        and "GET" in getattr(route, "methods", set())
        for dependency in route.dependant.dependencies
    )

    app.dependency_overrides[borrower_dependency] = mock_borrower

    try:
        with patch(
            "app.main.get_application",
            return_value=application,
        ) as mock_get:
            response = client.get("/applications/application-001")

        assert response.status_code == 200
        assert response.json()["borrowerId"] == "borrower-123"
        assert response.json()["property"]["county"] == "Kilkenny"

        mock_get.assert_called_once_with("application-001")

    finally:
        app.dependency_overrides.clear()


def test_borrower_cannot_retrieve_another_application():
    application = LoanApplication(
        borrowerId="different-borrower-456",
        householdAnnualGrossIncome=75000,
        existingDebtLiabilities=10000,
        deposit=70000,
        requestedMortgageTerm=30,
        sellerAskingPrice=350000,
        property=PropertyDetails(
            location="Kilkenny City",
            county="Kilkenny",
            floorArea=115,
            bedrooms=3,
            propertyType="SEMI_DETACHED",
        ),
        createdAt=datetime.now(timezone.utc),
        updatedAt=datetime.now(timezone.utc),
    )

    borrower_dependency = next(
        dependency.call
        for route in app.routes
        if getattr(route, "path", None) == "/applications/{application_id}"
        and "GET" in getattr(route, "methods", set())
        for dependency in route.dependant.dependencies
    )

    app.dependency_overrides[borrower_dependency] = mock_borrower

    try:
        with patch("app.main.get_application", return_value=application):
            response = client.get("/applications/application-002")

        assert response.status_code == 403
        assert response.json()["detail"] == "Insufficient permissions"

    finally:
        app.dependency_overrides.clear()


def test_broker_can_retrieve_assigned_application():
    application = LoanApplication(
        borrowerId="borrower-123",
        brokerId="broker-456",
        householdAnnualGrossIncome=75000,
        existingDebtLiabilities=10000,
        deposit=70000,
        requestedMortgageTerm=30,
        sellerAskingPrice=350000,
        property=PropertyDetails(
            location="Kilkenny City",
            county="Kilkenny",
            floorArea=115,
            bedrooms=3,
            propertyType="SEMI_DETACHED",
        ),
        createdAt=datetime.now(timezone.utc),
        updatedAt=datetime.now(timezone.utc),
    )

    def mock_broker():
        return UserProfile(
            uid="broker-456",
            email="broker@example.com",
            role=UserRole.BROKER,
            display_name="Test Broker",
        )

    retrieval_dependency = next(
        dependency.call
        for route in app.routes
        if getattr(route, "path", None) == "/applications/{application_id}"
        and "GET" in getattr(route, "methods", set())
        for dependency in route.dependant.dependencies
    )

    app.dependency_overrides[retrieval_dependency] = mock_broker

    try:
        with patch("app.main.get_application", return_value=application):
            response = client.get("/applications/application-001")

        assert response.status_code == 200
        assert response.json()["brokerId"] == "broker-456"

    finally:
        app.dependency_overrides.clear()


def test_broker_cannot_retrieve_unassigned_application():
    application = LoanApplication(
        borrowerId="borrower-123",
        brokerId="another-broker-789",
        householdAnnualGrossIncome=75000,
        existingDebtLiabilities=10000,
        deposit=70000,
        requestedMortgageTerm=30,
        sellerAskingPrice=350000,
        property=PropertyDetails(
            location="Kilkenny City",
            county="Kilkenny",
            floorArea=115,
            bedrooms=3,
            propertyType="SEMI_DETACHED",
        ),
        createdAt=datetime.now(timezone.utc),
        updatedAt=datetime.now(timezone.utc),
    )

    def mock_broker():
        return UserProfile(
            uid="broker-456",
            email="broker@example.com",
            role=UserRole.BROKER,
            display_name="Test Broker",
        )

    retrieval_dependency = next(
        dependency.call
        for route in app.routes
        if getattr(route, "path", None) == "/applications/{application_id}"
        and "GET" in getattr(route, "methods", set())
        for dependency in route.dependant.dependencies
    )

    app.dependency_overrides[retrieval_dependency] = mock_broker

    try:
        with patch("app.main.get_application", return_value=application):
            response = client.get("/applications/application-002")

        assert response.status_code == 403
        assert response.json()["detail"] == "Insufficient permissions"

    finally:
        app.dependency_overrides.clear()


def test_underwriter_can_retrieve_any_application():
    application = LoanApplication(
        borrowerId="borrower-123",
        brokerId="broker-456",
        householdAnnualGrossIncome=75000,
        existingDebtLiabilities=10000,
        deposit=70000,
        requestedMortgageTerm=30,
        sellerAskingPrice=350000,
        property=PropertyDetails(
            location="Kilkenny City",
            county="Kilkenny",
            floorArea=115,
            bedrooms=3,
            propertyType="SEMI_DETACHED",
        ),
        createdAt=datetime.now(timezone.utc),
        updatedAt=datetime.now(timezone.utc),
    )

    def mock_underwriter():
        return UserProfile(
            uid="underwriter-789",
            email="underwriter@example.com",
            role=UserRole.UNDERWRITER,
            display_name="Test Underwriter",
        )

    retrieval_dependency = next(
        dependency.call
        for route in app.routes
        if getattr(route, "path", None) == "/applications/{application_id}"
        and "GET" in getattr(route, "methods", set())
        for dependency in route.dependant.dependencies
    )

    app.dependency_overrides[retrieval_dependency] = mock_underwriter

    try:
        with patch("app.main.get_application", return_value=application):
            response = client.get("/applications/application-001")

        assert response.status_code == 200
        assert response.json()["borrowerId"] == "borrower-123"
        assert response.json()["brokerId"] == "broker-456"

    finally:
        app.dependency_overrides.clear()


def test_retrieve_nonexistent_application():
    retrieval_dependency = next(
        dependency.call
        for route in app.routes
        if getattr(route, "path", None) == "/applications/{application_id}"
        and "GET" in getattr(route, "methods", set())
        for dependency in route.dependant.dependencies
    )

    app.dependency_overrides[retrieval_dependency] = mock_borrower

    try:
        with patch(
            "app.main.get_application",
            return_value=None,
        ) as mock_get:
            response = client.get("/applications/nonexistent-id")

        assert response.status_code == 404
        assert response.json()["detail"] == "Application not found"

        mock_get.assert_called_once_with("nonexistent-id")

    finally:
        app.dependency_overrides.clear()


def test_borrower_can_list_own_applications():
    listing_dependency = next(
        dependency.call
        for route in app.routes
        if getattr(route, "path", None) == "/applications"
        and "GET" in getattr(route, "methods", set())
        for dependency in route.dependant.dependencies
    )

    app.dependency_overrides[listing_dependency] = mock_borrower

    try:
        with patch(
            "app.main.list_applications_for_user",
            return_value=[],
        ) as mock_list:
            response = client.get("/applications")

        assert response.status_code == 200
        assert response.json() == []

        mock_list.assert_called_once_with(
            uid="borrower-123",
            role="borrower",
        )

    finally:
        app.dependency_overrides.clear()


def test_broker_can_list_assigned_applications():
    def mock_broker():
        return UserProfile(
            uid="broker-456",
            email="broker@example.com",
            role=UserRole.BROKER,
            display_name="Test Broker",
        )

    listing_dependency = next(
        dependency.call
        for route in app.routes
        if getattr(route, "path", None) == "/applications"
        and "GET" in getattr(route, "methods", set())
        for dependency in route.dependant.dependencies
    )

    app.dependency_overrides[listing_dependency] = mock_broker

    try:
        with patch(
            "app.main.list_applications_for_user",
            return_value=[],
        ) as mock_list:
            response = client.get("/applications")

        assert response.status_code == 200
        assert response.json() == []

        mock_list.assert_called_once_with(
            uid="broker-456",
            role="broker",
        )

    finally:
        app.dependency_overrides.clear()


def test_underwriter_can_list_all_applications():
    def mock_underwriter():
        return UserProfile(
            uid="underwriter-789",
            email="underwriter@example.com",
            role=UserRole.UNDERWRITER,
            display_name="Test Underwriter",
        )

    listing_dependency = next(
        dependency.call
        for route in app.routes
        if getattr(route, "path", None) == "/applications"
        and "GET" in getattr(route, "methods", set())
        for dependency in route.dependant.dependencies
    )

    app.dependency_overrides[listing_dependency] = mock_underwriter

    try:
        with patch(
            "app.main.list_applications_for_user",
            return_value=[],
        ) as mock_list:
            response = client.get("/applications")

        assert response.status_code == 200
        assert response.json() == []

        mock_list.assert_called_once_with(
            uid="underwriter-789",
            role="underwriter",
        )

    finally:
        app.dependency_overrides.clear()
