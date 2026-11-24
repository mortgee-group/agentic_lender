# tests/test_ml_endpoints.py
import pytest
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture(scope="module")
def client():
    return TestClient(app)


def test_health(client):
    resp = client.get("/api/v1/health")
    assert resp.status_code == 200
    assert "ollama_reachable" in resp.json()


def test_predict_rf(client, sample_property):
    resp = client.post("/api/v1/ml/predict/rf", json=sample_property)
    assert resp.status_code == 200
    body = resp.json()
    assert body["model"] == "rf"
    assert 100_000 < body["prediction"] < 5_000_000


def test_predict_knn(client, sample_property):
    resp = client.post("/api/v1/ml/predict/knn", json=sample_property)
    assert resp.status_code == 200
    body = resp.json()
    assert body["model"] == "knn"
    assert body["prediction"] > 0


def test_predict_rf_rejects_bad_input(client):
    resp = client.post("/api/v1/ml/predict/rf", json={"bedrooms": 3})
    assert resp.status_code == 422


def test_predict_rf_rejects_negative_area(client, sample_property):
    bad = {**sample_property, "floor_area": -10}
    resp = client.post("/api/v1/ml/predict/rf", json=bad)
    assert resp.status_code == 422


def test_predict_rf_accepts_snake_case_property_type(client, sample_property):
    """Firestore sends SEMI_DETACHED; the endpoint must normalize it."""
    specs = {**sample_property, "property_type": "SEMI_DETACHED"}
    resp = client.post("/api/v1/ml/predict/rf", json=specs)
    assert resp.status_code == 200
    assert 100_000 < resp.json()["prediction"] < 5_000_000


def test_predict_rf_batch(client, sample_property):
    payload = {
        "items": [
            {"property": sample_property, "asking_price": 520000, "loan_amount": 440000},
            {"property": sample_property, "asking_price": 380000, "loan_amount": 300000},
        ]
    }
    resp = client.post("/api/v1/ml/predict/rf/batch", json=payload)
    assert resp.status_code == 200
    body = resp.json()
    assert len(body["results"]) == 2
    for r in body["results"]:
        assert r["prediction"] > 0
        assert r["ltv"] > 0


def test_assess(client, sample_underwrite_inputs):
    payload = {k: v for k, v in sample_underwrite_inputs.items()
               if k in ("householdAnnualGrossIncome", "existingDebtLiabilities",
                        "deposit", "requestedMortgageTerm",
                        "sellerAskingPrice", "property")}
    resp = client.post("/api/v1/ai/assess", json=payload)
    assert resp.status_code == 200
    body = resp.json()
    assert body["predicted_value"] > 0
    assert body["ltv"] > 0
    assert body["dti"] > 0