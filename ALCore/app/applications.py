from datetime import datetime, timezone
from pydantic import ValidationError

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

    applications = []

    for document in documents:
        try:
            application = LoanApplicationResponse(
                **document.to_dict(),
                applicationId=document.id,
            )
            applications.append(application)
        except ValidationError:
            # Older Firestore records may not match the current schema.
            continue

    return applications


def save_ai_decision(
    application_id: str,
    decision_data: dict,
) -> None:
    """Save an AI underwriting decision to Firestore."""

    now = datetime.now(timezone.utc)

    # Store the complete AI response for auditing.
    db.collection("aiDecisions").document(application_id).set({
        **decision_data,
        "applicationId": application_id,
        "recordedAt": now,
    })

    # Update the mortgage application with its AI assessment.
    db.collection("applications").document(application_id).update({
        "estimatedLTV": decision_data.get("estimatedLTV"),
        "DTI": decision_data.get("DTI"),
        "approvalLikelihood": decision_data.get("approvalLikelihood"),
        "riskTier": decision_data.get("riskTier"),
        "lifecycleStatus": "AI_DELIBERATED",
        "updatedAt": now,
    })


def save_ai_decision(
    application_id: str,
    decision_data: dict,
) -> str:
    """Save an AI decision and update the application atomically."""

    now = datetime.now(timezone.utc)

    decision_ref = db.collection("aiDecisions").document()
    application_ref = db.collection("applications").document(application_id)

    decision_record = {
        "applicationId": application_id,
        "finalLendingDecision": decision_data["decision"],
        "creditMemorandum": decision_data.get("credit_memo"),
        "reasoningLogs": decision_data.get("agent_trails", []),
        "agentConsensus": decision_data.get("agent_outputs", {}),
        "individualDecisionTrails": decision_data.get("agent_trails", []),
        "createdAt": now,
        # Preserve the complete AL-AI response for auditing.
        "aiResponse": decision_data,
    }

    application_updates = {
        "estimatedLTV": decision_data.get("estimatedLTV"),
        "DTI": decision_data.get("DTI"),
        "approvalLikelihood": decision_data.get("approvalLikelihood"),
        "riskTier": decision_data.get("riskTier"),
        "lifecycleStatus": "AI_DELIBERATED",
        "updatedAt": now,
    }

    batch = db.batch()
    batch.set(decision_ref, decision_record)
    batch.update(application_ref, application_updates)
    batch.commit()

    return decision_ref.id


def save_valuation(
    application_id: str,
    property_data: dict,
    assessment_data: dict,
) -> str:
    """Save an AI property valuation to Firestore."""

    now = datetime.now(timezone.utc)

    valuation_ref = db.collection("valuations").document()

    valuation_record = {
        "applicationId": application_id,
        "valuationPrediction": assessment_data["predicted_value"],
        "valuationData": property_data,
        "createdAt": now,
    }

    valuation_ref.set(valuation_record)

    return valuation_ref.id
