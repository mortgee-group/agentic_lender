from fastapi import Depends, FastAPI

from app.auth import verify_firebase_token

app = FastAPI(
    title="Agentic Lender Core API",
    version="0.1.0"
)


@app.get("/health")
def health_check():
    return {"status": "ok"}


@app.get("/auth/me")
def get_current_user(decoded_token: dict = Depends(verify_firebase_token)):
    return {
        "uid": decoded_token["uid"]
    }