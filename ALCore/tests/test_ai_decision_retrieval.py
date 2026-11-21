from unittest.mock import MagicMock

from app.applications import get_ai_decision


def test_get_ai_decision_returns_saved_response(monkeypatch):
    """Return the original AI response stored in Firestore."""

    expected = {
        "decision": "APPROVED",
        "credit_memo": "Application meets lending criteria.",
    }

    mock_document = MagicMock()
    mock_document.to_dict.return_value = {
        "applicationId": "APP-001",
        "aiResponse": expected,
    }

    mock_db = MagicMock()
    mock_db.collection.return_value.where.return_value.limit.return_value.stream.return_value = [
        mock_document
    ]

    monkeypatch.setattr("app.applications.db", mock_db)

    result = get_ai_decision("APP-001")

    assert result == expected
    mock_db.collection.assert_called_once_with("aiDecisions")


def test_get_ai_decision_returns_none_when_missing(monkeypatch):
    """Return None when Firestore contains no matching decision."""

    mock_db = MagicMock()
    mock_db.collection.return_value.where.return_value.limit.return_value.stream.return_value = []

    monkeypatch.setattr("app.applications.db", mock_db)

    result = get_ai_decision("APP-001")

    assert result is None
