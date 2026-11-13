from unittest.mock import MagicMock

from app.applications import save_ai_decision, save_valuation

def test_save_ai_decision_creates_decision_document(monkeypatch):
    mock_db = MagicMock()
    monkeypatch.setattr("app.applications.db", mock_db)

    mock_decision_ref = MagicMock()
    mock_decision_ref.id = "decision-123"

    mock_application_ref = MagicMock()

    mock_decisions_collection = MagicMock()
    mock_decisions_collection.document.return_value = mock_decision_ref

    mock_applications_collection = MagicMock()
    mock_applications_collection.document.return_value = mock_application_ref

    def get_collection(name):
        if name == "aiDecisions":
            return mock_decisions_collection
        if name == "applications":
            return mock_applications_collection
        raise AssertionError(f"Unexpected collection: {name}")


    mock_db.collection.side_effect = get_collection

    decision_data = {
        "decision": "APPROVE",
        "credit_memo": "Applicant meets lending criteria.",
        "agent_trails": "Affordability and valuation checks passed.",
        "agent_outputs": {"affordability": {"status": "PASS"}},
        "estimatedLTV": 80.0,
        "DTI": 25.0,
        "approvalLikelihood": "High",
        "riskTier": "Tier 1",
    }

    decision_id = save_ai_decision("APP-001", decision_data)

    assert decision_id == "decision-123"

    batch = mock_db.batch.return_value
    batch.set.assert_called_once()

    saved_record = batch.set.call_args.args[1]

    assert saved_record["applicationId"] == "APP-001"
    assert saved_record["finalLendingDecision"] == "APPROVE"
    assert saved_record["creditMemorandum"] == (
        "Applicant meets lending criteria."
    )
    assert saved_record["aiResponse"] == decision_data

    batch.update.assert_called_once()

    updated_fields = batch.update.call_args.args[1]

    assert updated_fields["estimatedLTV"] == 80.0
    assert updated_fields["DTI"] == 25.0
    assert updated_fields["approvalLikelihood"] == "High"
    assert updated_fields["riskTier"] == "Tier 1"
    assert updated_fields["lifecycleStatus"] == "AI_DELIBERATED"
    assert "updatedAt" in updated_fields

    # Verify the correct Firestore document references.
    mock_decisions_collection.document.assert_called_once_with()
    mock_applications_collection.document.assert_called_once_with("APP-001")

    batch.set.assert_called_once_with(
        mock_decision_ref,
        saved_record,
    )

    batch.update.assert_called_once_with(
        mock_application_ref,
        updated_fields,
    )

    batch.commit.assert_called_once()


def test_save_valuation_creates_valuation_document(monkeypatch):
    mock_db = MagicMock()
    monkeypatch.setattr("app.applications.db", mock_db)

    mock_valuation_ref = MagicMock()
    mock_valuation_ref.id = "valuation-123"

    mock_db.collection.return_value.document.return_value = mock_valuation_ref

    property_data = {
        "county": "Kildare",
        "floor_area": 120,
        "bedrooms": 3,
    }

    assessment_data = {
        "predicted_value": 350000,
        "ltv": 80.0,
        "dti": 25.0,
    }

    valuation_id = save_valuation(
        "APP-001",
        property_data,
        assessment_data,
    )

    assert valuation_id == "valuation-123"

    mock_db.collection.assert_called_once_with("valuations")

    saved_record = mock_valuation_ref.set.call_args.args[0]

    assert saved_record["applicationId"] == "APP-001"
    assert saved_record["valuationPrediction"] == 350000
    assert saved_record["valuationData"] == property_data
    assert "createdAt" in saved_record

    mock_valuation_ref.set.assert_called_once_with(saved_record)