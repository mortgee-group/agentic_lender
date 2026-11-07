from datetime import datetime, timezone

from app.firebase import db
from app.models import (
    LoanApplication,
    LoanApplicationCreate,
    LoanApplicationResponse,
)


def create_application(
    borrower_id: str,
    application_data: LoanApplicationCreate,
) -> str:
    """Create a mortgage application in Firestore."""

    now = datetime.now(timezone.utc)

    # Calculate requested loan amount and estimated LTV.
    requested_loan_amount = (
        application_data.sellerAskingPrice - application_data.deposit
    )

    estimated_ltv = (
        requested_loan_amount / application_data.sellerAskingPrice
    ) * 100

    application = LoanApplication(
        **application_data.model_dump(),
        borrowerId=borrower_id,
        requestedLoanAmount=requested_loan_amount,
        estimatedLTV=round(estimated_ltv, 2),
        createdAt=now,
        updatedAt=now,
        submittedAt=now,
    )


    document = db.collection("applications").document()
    document.set(application.model_dump())

    return document.id


def get_application(application_id: str) -> LoanApplicationResponse | None:
    """Retrieve a mortgage application with its Firestore document ID."""
    document = db.collection("applications").document(application_id).get()

    if not document.exists:
        return None

    return LoanApplicationResponse(
        **document.to_dict(),
        applicationId=document.id,
    )


def list_applications_for_user(
    uid: str,
    role: str,
) -> list[LoanApplicationResponse]:
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
    LoanApplicationResponse(
        **document.to_dict(),
        applicationId=document.id,
    )
    for document in documents
    ]
