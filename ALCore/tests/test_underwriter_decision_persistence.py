from unittest.mock import MagicMock, patch

import pytest
from fastapi import HTTPException

from app.applications import save_underwriter_decision
from app.models import UnderwriterDecision


def setup_firestore_mock(status="AI_DELIBERATED", exists=True):
    mock_db = MagicMock()

    mock_ref = mock_db.collection.return_value.document.return_value
    mock_snapshot = mock_ref.get.return_value

    mock_snapshot.exists = exists
    mock_snapshot.to_dict.return_value = {
        "lifecycleStatus": status,
    }

    return mock_db, mock_ref


def test_approval_updates_firestore():
    mock_db, mock_ref = setup_firestore_mock()

    with (
        patch("app.applications.db", mock_db),
        patch(
            "app.applications.firestore.transactional",
            lambda function: function,
        ),
    ):
        result = save_underwriter_decision(
            application_id="APP-001",
            underwriter_id="underwriter-123",
            decision=UnderwriterDecision.APPROVED,
        )

    assert result["underwriterDecision"] == "APPROVED"
    assert result["lifecycleStatus"] == "UNDERWRITER_APPROVED"

    transaction = mock_db.transaction.return_value
    transaction.update.assert_called_once()

    args, kwargs = transaction.update.call_args

    assert args[0] == mock_ref
    assert args[1]["lifecycleStatus"] == "UNDERWRITER_APPROVED"
    assert args[1]["decidedBy"] == "underwriter-123"


def test_rejection_closes_application():
    mock_db, mock_ref = setup_firestore_mock()

    with (
        patch("app.applications.db", mock_db),
        patch(
            "app.applications.firestore.transactional",
            lambda function: function,
        ),
    ):
        result = save_underwriter_decision(
            application_id="APP-001",
            underwriter_id="underwriter-123",
            decision=UnderwriterDecision.REJECTED,
        )

    assert result["underwriterDecision"] == "REJECTED"
    assert result["lifecycleStatus"] == "CLOSED"


def test_application_not_found_returns_404():
    mock_db, _ = setup_firestore_mock(exists=False)

    with (
        patch("app.applications.db", mock_db),
        patch(
            "app.applications.firestore.transactional",
            lambda function: function,
        ),
    ):
        with pytest.raises(HTTPException) as exc:
            save_underwriter_decision(
                application_id="MISSING",
                underwriter_id="underwriter-123",
                decision=UnderwriterDecision.APPROVED,
            )

    assert exc.value.status_code == 404


@pytest.mark.parametrize(
    "status",
    [
        "APPLIED",
        "UNDERWRITER_APPROVED",
        "FUNDED",
        "CLOSED",
    ],
)
def test_invalid_lifecycle_status_returns_409(status):
    mock_db, _ = setup_firestore_mock(status=status)

    with (
        patch("app.applications.db", mock_db),
        patch(
            "app.applications.firestore.transactional",
            lambda function: function,
        ),
    ):
        with pytest.raises(HTTPException) as exc:
            save_underwriter_decision(
                application_id="APP-001",
                underwriter_id="underwriter-123",
                decision=UnderwriterDecision.APPROVED,
            )

    assert exc.value.status_code == 409

    transaction = mock_db.transaction.return_value
    transaction.update.assert_not_called()
