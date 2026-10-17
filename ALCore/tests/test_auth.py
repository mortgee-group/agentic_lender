from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_auth_me_without_token():
    response = client.get("/auth/me")

    assert response.status_code == 401
    assert response.json()["detail"] == "Missing or invalid Authorization header"

def test_auth_me_with_invalid_token():
    response = client.get(
        "/auth/me",
        headers={"Authorization": "Bearer invalid-token"}
    )

    assert response.status_code == 401
    assert response.json()["detail"] == "Invalid or expired Firebase ID token"