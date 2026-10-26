from fastapi import Depends, FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.auth import verify_firebase_token

app = FastAPI(
    title="Agentic Lender Core API",
    version="0.1.0"
)

# Allow the local React dev app
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",
        "http://127.0.0.1:5173"
    ],
    allow_credentials=False,
    allow_methods=["GET"],
    allow_headers=["*"],
)


@app.get("/health")
def health_check():
    return {"status": "ok"}


@app.get("/auth/me")
def get_current_user(decoded_token: dict = Depends(verify_firebase_token)):
    return {
        "uid": decoded_token["uid"]
    }