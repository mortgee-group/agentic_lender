from datetime import datetime, timezone

from app.firebase import db
from app.models import LoanApplication, LoanApplicationCreate


def create_application(
    borrower_id: str,
    application_data: LoanApplicationCreate,
) -> str:
    """Create a mortgage application in Firestore."""

    now = datetime.now(timezone.utc)

    application = LoanApplication(
        **application_data.model_dump(),
        borrowerId=borrower_id,
        createdAt=now,
        updatedAt=now,
        submittedAt=now,
    )


    document = db.collection("applications").document()
    document.set(application.model_dump())

    return document.id


def get_application(application_id: str) -> LoanApplication | None:
    """Retrieve a mortgage application from Firestore."""

    document = db.collection("applications").document(application_id).get()

    if not document.exists:
        return None

    return LoanApplication.model_validate(document.to_dict())


def list_applications_for_user(
    uid: str,
    role: str,
) -> list[LoanApplication]:
    """Retrieve applications based on the user's role."""

    collection = db.collection("applications")

    if role == "borrower":
        documents = collection.where(
            "borrowerId", "==", uid
        ).stream()

    elif role == "broker":
        documents = collection.where(
            "brokerId", "==", uid
        ).stream()

    elif role == "underwriter":
        documents = collection.stream()

    else:
        return []

    return [
        LoanApplication.model_validate(document.to_dict())
        for document in documents
    ]
