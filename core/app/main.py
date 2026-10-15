from fastapi import FastAPI
from app.firebase import db

app = FastAPI(
    title="Agentic Lender Core API",
    version="0.1.0"
)


@app.get("/health")
def health_check():
    return {"status": "ok"}


@app.post("/firestore-test")
def firestore_test():
    doc_ref = db.collection("test").document("connection")

    doc_ref.set({
        "message": "Agentic Lender Core connected successfully"
    })

    return {
        "status": "success",
        "message": "Test document written to Firestore"
    }