from datetime import datetime, timezone
from pydantic import ValidationError
from collections import Counter
from fastapi import HTTPException
from google.cloud import firestore
from google.cloud.firestore_v1 import FieldFilter
from app.firebase import db
from app.models import (
    LoanApplication,
    LoanApplicationCreate,
    LoanApplicationResponse,
    UnderwriterDecision,
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

    broker_id = select_least_busy_broker()

    application = LoanApplication(
        **application_data.model_dump(),
        borrowerId=borrower_id,
        brokerId=broker_id,
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


def save_underwriter_decision(
    application_id: str,
    underwriter_id: str,
    decision: UnderwriterDecision,
) -> dict:
    """Save a final Underwriter decision atomically."""

    application_ref = db.collection("applications").document(application_id)
    transaction = db.transaction()

    @firestore.transactional
    def update_decision(transaction):
        snapshot = application_ref.get(transaction=transaction)

        if not snapshot.exists:
            raise HTTPException(
                status_code=404,
                detail="Application not found",
            )

        application = snapshot.to_dict()

        if application.get("lifecycleStatus") != "AI_DELIBERATED":
            raise HTTPException(
                status_code=409,
                detail="Only AI-deliberated applications can receive an Underwriter decision",
            )

        now = datetime.now(timezone.utc)

        new_status = (
            "UNDERWRITER_APPROVED"
            if decision == UnderwriterDecision.APPROVED
            else "CLOSED"
        )

        updates = {
            "underwriterDecision": decision.value,
            "decidedBy": underwriter_id,
            "decidedAt": now,
            "lifecycleStatus": new_status,
            "updatedAt": now,
        }

        transaction.update(application_ref, updates)

        return {
            "applicationId": application_id,
            "underwriterDecision": decision.value,
            "lifecycleStatus": new_status,
            "decidedBy": underwriter_id,
            "decidedAt": now,
        }

    return update_decision(transaction)

def select_least_busy_broker() -> str:
    """Return the UID of the broker with the fewest active applications."""

    brokers = list(
        db.collection("users")
        .where(filter=FieldFilter("role", "==", "broker"))
        .stream()
    )

    if not brokers:
        raise HTTPException(
            status_code=503,
            detail="No brokers are currently available",
        )

    counts = Counter()

    for application in db.collection("applications").stream():
        application_data = application.to_dict()

        broker_id = application_data.get("brokerId")
        lifecycle_status = application_data.get("lifecycleStatus")

        if broker_id and lifecycle_status != "CLOSED":
            counts[broker_id] += 1

    return min(
        (broker.id for broker in brokers),
        key=lambda uid: (counts[uid], uid),
    )