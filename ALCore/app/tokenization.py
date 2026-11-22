"""Coordinate approved mortgage tokenization and Firestore persistence."""

from datetime import datetime, timezone

from fastapi import HTTPException
from google.cloud import firestore
from web3 import Web3

from app.applications import (
    get_application,
    get_ai_decision,
    get_valuation,
)
from app.blockchain import (
    get_loan_contract,
    get_web3,
    prepare_tokenization_data,
    tokenize_loan,
)
from app.firebase import db


def get_existing_tokenization(application_id: str) -> dict | None:
    """Retrieve an existing tokenization record."""

    snapshot = (
        db.collection("loanTokenizations")
        .document(application_id)
        .get()
    )

    return snapshot.to_dict() if snapshot.exists else None


def reserve_tokenization(application_id: str, underwriter_id: str) -> None:
    """Reserve an application before submitting a blockchain transaction."""

    reference = db.collection("loanTokenizations").document(application_id)
    transaction = db.transaction()

    @firestore.transactional
    def reserve(transaction):
        snapshot = reference.get(transaction=transaction)

        if snapshot.exists:
            raise HTTPException(
                status_code=409,
                detail="Application tokenization already exists or is in progress",
            )

        transaction.set(
            reference,
            {
                "applicationId": application_id,
                "status": "PENDING",
                "requestedBy": underwriter_id,
                "createdAt": datetime.now(timezone.utc),
            },
        )

    reserve(transaction)


def save_tokenization_result(
    application_id: str,
    result: dict,
    audit_hash: str,
) -> dict:
    """Store the confirmed blockchain transaction."""

    record = {
        "applicationId": application_id,
        "status": "COMPLETED",
        "tokenId": result["tokenId"],
        "transactionHash": result["transactionHash"],
        "contractAddress": result["contractAddress"],
        "chainId": result["chainId"],
        "auditHash": audit_hash,
        "completedAt": datetime.now(timezone.utc),
    }

    db.collection("loanTokenizations").document(application_id).update(record)

    return record


def find_existing_on_chain(application_hash: bytes) -> dict | None:
    """Recover a token already minted when Firestore saving failed."""

    web3 = get_web3()
    contract = get_loan_contract(web3)

    if not contract.functions.tokenizedApplications(application_hash).call():
        return None

    # Search the contract's indexed application hash in mint events.
    events = contract.events.LoanTokenized().get_logs(
        from_block=0,
        argument_filters={"applicationHash": application_hash},
    )

    if len(events) != 1:
        raise RuntimeError("Cannot uniquely identify the existing loan token")

    event = events[0]

    return {
        "tokenId": event["args"]["tokenId"],
        "transactionHash": event["transactionHash"].hex(),
        "contractAddress": contract.address,
        "chainId": web3.eth.chain_id,
    }


def tokenize_approved_application(
    application_id: str,
    underwriter_id: str,
) -> dict:
    """Validate, tokenize, and persist an approved mortgage."""

    application = get_application(application_id)

    if application is None:
        raise HTTPException(
            status_code=404,
            detail="Application not found",
        )

    # Convert the Pydantic model into the format used by blockchain.py.
    application_data = application.model_dump(mode="python")

    ai_decision = get_ai_decision(application_id)
    valuation = get_valuation(application_id)

    if ai_decision is None:
        raise HTTPException(
            status_code=409,
            detail="AI credit memorandum is missing",
        )

    if valuation is None:
        raise HTTPException(
            status_code=409,
            detail="Property valuation is missing",
        )

    try:
        token_data = prepare_tokenization_data(
            application_id,
            application_data,
            ai_decision,
            valuation,
        )
    except (ValueError, TypeError) as error:
        raise HTTPException(status_code=409, detail=str(error)) from error

    existing = get_existing_tokenization(application_id)

    if existing and existing.get("status") == "COMPLETED":
        return existing

    if existing and existing.get("status") != "PENDING":
        raise HTTPException(
            status_code=409,
            detail="Application tokenization requires manual review",
        )

    if existing is None:
        reserve_tokenization(application_id, underwriter_id)

    # A previous request may have minted successfully before Firestore failed.
    try:
        recovered = find_existing_on_chain(token_data["applicationHash"])

        if recovered is not None:
            result = recovered
        else:
            if existing is not None:
                raise HTTPException(
                    status_code=409,
                    detail="Tokenization is already pending; check its status before retrying",
                )

            result = tokenize_loan(
                application_hash=token_data["applicationHash"],
                principal_cents=token_data["principalCents"],
                apr_basis_points=token_data["aprBasisPoints"],
                term_months=token_data["termMonths"],
                borrower_public_id=token_data["borrowerPublicId"],
                audit_hash=token_data["auditHash"],
            )

        return save_tokenization_result(
            application_id,
            result,
            Web3.to_hex(token_data["auditHash"]),
        )

    except HTTPException:
        raise
    except Exception as error:
        # Preserve PENDING: the blockchain outcome may be uncertain.
        raise HTTPException(
            status_code=503,
            detail="Tokenization could not be confirmed; check the pending record before retrying",
        ) from error
