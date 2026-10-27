from unittest.mock import MagicMock, patch

from app.models import LoanApplicationCreate, PropertyDetails
from app.applications import (
    create_application,
    get_application,
    list_applications_for_user,
)


def test_get_application_not_found():
    """Return None when an application does not exist."""

    mock_document = MagicMock()
    mock_document.exists = False

    with patch("app.applications.db") as mock_db:
        mock_db.collection.return_value.document.return_value.get.return_value = (
            mock_document
        )

        result = get_application("nonexistent-application")

    assert result is None

    mock_db.collection.assert_called_once_with("applications")
    mock_db.collection.return_value.document.assert_called_once_with(
        "nonexistent-application"
    )


def test_get_application_found():
    """Return application data when the Firestore document exists."""

    mock_document = MagicMock()
    mock_document.exists = True
    mock_document.id = "application-001"
    mock_document.to_dict.return_value = {
        "borrowerId": "borrower-123",
        "brokerId": None,
        "householdAnnualGrossIncome": 75000,
        "existingDebtLiabilities": 10000,
        "deposit": 70000,
        "requestedMortgageTerm": 30,
        "sellerAskingPrice": 350000,
        "property": {
            "location": "Kilkenny City",
            "county": "Kilkenny",
            "floorArea": 115,
            "floorAreaUnit": "SQ_METERS",
            "bedrooms": 3,
            "propertyType": "SEMI_DETACHED",
            "berEnergyRating": "B2",
        },
        "lifecycleStatus": "APPLIED",
        "createdAt": "2026-10-03T14:00:00Z",
        "updatedAt": "2026-10-03T14:00:00Z",
    }

    with patch("app.applications.db") as mock_db:
        mock_db.collection.return_value.document.return_value.get.return_value = (
            mock_document
        )

        result = get_application("application-001")

    assert result is not None
    assert result.applicationId == "application-001"
    assert result.borrowerId == "borrower-123"
    assert result.property.county == "Kilkenny"
    assert result.lifecycleStatus.value == "APPLIED"


def test_create_application():
    """Create an application using validated borrower data."""

    application_data = LoanApplicationCreate(
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
    )

    with patch("app.applications.db") as mock_db:
        mock_document = mock_db.collection.return_value.document.return_value
        mock_document.id = "application-001"

        result = create_application("borrower-123", application_data)

    assert result == "application-001"

    mock_db.collection.assert_called_once_with("applications")
    mock_document.set.assert_called_once()

    saved_data = mock_document.set.call_args.args[0]

    assert saved_data["borrowerId"] == "borrower-123"
    assert saved_data["property"]["county"] == "Kilkenny"
    assert saved_data["lifecycleStatus"] == "APPLIED"


def test_list_applications_for_borrower():
    """Borrowers should only query applications belonging to them."""

    mock_db = MagicMock()
    mock_db.collection.return_value.where.return_value.stream.return_value = []

    with patch("app.applications.db", mock_db):
        result = list_applications_for_user(
            uid="borrower-123",
            role="borrower",
        )

    assert result == []

    mock_db.collection.assert_called_once_with("applications")
    mock_db.collection.return_value.where.assert_called_once_with(
        "borrowerId",
        "==",
        "borrower-123",
    )


def test_list_applications_for_broker():
    """Brokers should only query applications assigned to them."""

    mock_db = MagicMock()
    mock_db.collection.return_value.where.return_value.stream.return_value = []

    with patch("app.applications.db", mock_db):
        result = list_applications_for_user(
            uid="broker-456",
            role="broker",
        )

    assert result == []

    mock_db.collection.assert_called_once_with("applications")
    mock_db.collection.return_value.where.assert_called_once_with(
        "brokerId",
        "==",
        "broker-456",
    )


def test_list_applications_for_underwriter():
    """Underwriters should query all applications."""

    mock_db = MagicMock()
    mock_db.collection.return_value.stream.return_value = []

    with patch("app.applications.db", mock_db):
        result = list_applications_for_user(
            uid="underwriter-789",
            role="underwriter",
        )

    assert result == []

    mock_db.collection.assert_called_once_with("applications")

    # Underwriters should access the full collection.
    mock_db.collection.return_value.stream.assert_called_once_with()

    # No borrower or broker filtering should be applied.
    mock_db.collection.return_value.where.assert_not_called()


def test_list_applications_includes_document_id():
    """Listed applications should include their Firestore document IDs."""

    mock_document = MagicMock()
    mock_document.id = "application-001"
    mock_document.to_dict.return_value = {
        "borrowerId": "borrower-123",
        "householdAnnualGrossIncome": 75000,
        "existingDebtLiabilities": 10000,
        "deposit": 70000,
        "requestedMortgageTerm": 30,
        "sellerAskingPrice": 350000,
        "property": {
            "location": "Kilkenny City",
            "county": "Kilkenny",
            "floorArea": 115,
            "floorAreaUnit": "SQ_METERS",
            "bedrooms": 3,
            "propertyType": "SEMI_DETACHED",
            "berEnergyRating": "B2",
        },
        "lifecycleStatus": "APPLIED",
        "createdAt": "2026-10-03T14:00:00Z",
        "updatedAt": "2026-10-03T14:00:00Z",
    }

    mock_db = MagicMock()
    mock_db.collection.return_value.where.return_value.stream.return_value = [
        mock_document
    ]

    with patch("app.applications.db", mock_db):
        result = list_applications_for_user(
            uid="borrower-123",
            role="borrower",
        )

    assert len(result) == 1
    assert result[0].applicationId == "application-001"
    assert result[0].borrowerId == "borrower-123"
    