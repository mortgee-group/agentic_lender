# tests/test_ai_endpoints.py
from unittest.mock import patch, MagicMock
import pytest
from fastapi.testclient import TestClient

from app.main import app
from ai.models import UnderwritingDecision


@pytest.fixture(scope="module")
def client():
    return TestClient(app)


def test_ai_health_structure(client):
    resp = client.get("/api/v1/health")
    assert resp.status_code == 200
    body = resp.json()
    assert "ollama_reachable" in body
    assert "groq_key_set" in body
    #assert "gemini_key_set" in body


def test_underwrite_rejects_bad_input(client):
    resp = client.post("/api/v1/ai/underwrite", json={"applicationId": "x"})
    assert resp.status_code == 422


@patch("app.main.ALCrew")
def test_underwrite_returns_response(mock_crew_cls, client, sample_underwrite_inputs):
    """Mock the crew so we test endpoint wiring, not LLM latency."""
    fake_decision = UnderwritingDecision(
        decision="DECLINE",
        rationale="LTI exceeds regulatory cap",
        ltv=86.36,
        dti=45.06,
        risk_rating=4,
        conditions=["Increase deposit to €187,500"],
        credit_memo="Mocked credit memo",
    )

    # Three mocked TaskOutputs with .pydantic objects
    task0 = MagicMock()
    task0.pydantic = MagicMock()
    task0.pydantic.model_dump.return_value = {"dti": 45.06, "affordability_verdict": "Fail"}

    task1 = MagicMock()
    task1.pydantic = None

    task1.raw = """
    {
        "predicted_value": 385000,
        "overvaluation_pct": -9.09,
        "ltv": 86.36,
        "collateral_risk": "Low",
        "notes": "Mock valuation result"
    }
    """

    task2 = MagicMock()
    task2.pydantic = MagicMock()
    task2.pydantic.model_dump.return_value = {"ecb_rate": 3.75, "summary": "..."}

    mock_result = MagicMock()
    mock_result.pydantic = fake_decision
    mock_result.tasks_output = [task0, task1, task2]
    mock_result.__str__.return_value = "Mocked credit memo"

    mock_crew = mock_crew_cls.return_value.crew.return_value
    mock_crew.kickoff.return_value = mock_result

    resp = client.post("/api/v1/ai/underwrite", json=sample_underwrite_inputs)
    print("STATUS:", resp.status_code)
    print("ERROR DETAILS:", resp.json())

    assert resp.status_code == 200
    body = resp.json()
    assert body["applicationId"] == "APP-001"
    assert body["borrowerId"] == "firebase_uid_123"
    assert body["decision"] == "DECLINE"
    assert body["ltv"] == 86.36
    assert body["dti"] == 45.06
    assert body["risk_rating"] == 4
    assert body["conditions"] == ["Increase deposit to €187,500"]
    assert body["approvalLikelihood"] == "Low"
    assert body["riskTier"] == "Tier 4"
    assert body["lifecycleStatus"] == "AI_DELIBERATED"
    assert set(body["agent_outputs"].keys()) == {"affordability", "valuation", "market_research"}
    mock_crew.kickoff.assert_called_once()


def test_stress_test_runs(client, sample_property):
    payload = {
        "rate_shock_bps": 200,
        "property_drop_pct": 10,
        "applications": [
            {
                "applicationId": "APP-001",
                "householdAnnualGrossIncome": 95000,
                "existingDebtLiabilities": 12000,
                "deposit": 80000,
                "requestedMortgageTerm": 30,
                "sellerAskingPrice": 520000,
                "current_rate_pct": 3.75,
                "property": sample_property,
            }
        ],
    }
    resp = client.post("/api/v1/ai/stress-test", json=payload)
    assert resp.status_code == 200
    body = resp.json()
    assert body["summary"]["total"] == 1
    assert len(body["results"]) == 1
    assert body["results"][0]["verdict"] in ("AFFORDABLE", "MARGINAL", "UNAFFORDABLE")