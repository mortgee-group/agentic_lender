"""Tests for the mortgage tokenization service."""

from unittest.mock import Mock, patch

import pytest
from fastapi import HTTPException

from app.tokenization import tokenize_approved_application


@pytest.fixture
def approved_application():
    """Mock an approved mortgage returned by Firestore."""

    application = Mock()

    application.model_dump.return_value = {
        "lifecycleStatus": "UNDERWRITER_APPROVED",
        "underwriterDecision": "APPROVED",
        "approvedLoanAmount": 250000,
        "approvedAprBasisPoints": 400,
        "interestRateConfirmed": True,
        "requestedMortgageTerm": 30,
        "borrowerId": "BORROWER-001",
    }

    return application


@pytest.fixture
def mock_dependencies(approved_application):
    """Mock Firestore and blockchain operations."""

    with (
        patch(
            "app.tokenization.get_application",
            return_value=approved_application,
        ),
        patch(
            "app.tokenization.get_ai_decision",
            return_value={"credit_memo": "Approved mortgage"},
        ),
        patch(
            "app.tokenization.get_valuation",
            return_value={"valuationPrediction": 300000},
        ),
        patch(
            "app.tokenization.get_existing_tokenization",
            return_value=None,
        ) as existing,
        patch("app.tokenization.reserve_tokenization") as reserve,
        patch(
            "app.tokenization.find_existing_on_chain",
            return_value=None,
        ) as find_on_chain,
        patch(
            "app.tokenization.tokenize_loan",
            return_value={
                "tokenId": 1,
                "transactionHash": "0x1234",
                "contractAddress": "0xContract",
                "chainId": 31337,
            },
        ) as mint,
        patch(
            "app.tokenization.save_tokenization_result",
            return_value={
                "applicationId": "APP-001",
                "status": "COMPLETED",
                "tokenId": 1,
            },
        ) as save,
    ):
        yield {
            "existing": existing,
            "reserve": reserve,
            "find_on_chain": find_on_chain,
            "mint": mint,
            "save": save,
        }


def test_successful_tokenization(mock_dependencies):
    """Approved mortgages are minted and recorded."""

    result = tokenize_approved_application("APP-001", "UNDERWRITER-001")

    assert result["status"] == "COMPLETED"
    assert result["tokenId"] == 1

    mock_dependencies["reserve"].assert_called_once()
    mock_dependencies["mint"].assert_called_once()
    mock_dependencies["save"].assert_called_once()


def test_completed_tokenization_is_idempotent(mock_dependencies):
    """Repeated requests return the existing completed record."""

    mock_dependencies["existing"].return_value = {
        "applicationId": "APP-001",
        "status": "COMPLETED",
        "tokenId": 7,
    }

    result = tokenize_approved_application("APP-001", "UNDERWRITER-001")

    assert result["tokenId"] == 7
    mock_dependencies["mint"].assert_not_called()


def test_missing_application_is_rejected(mock_dependencies):
    with patch("app.tokenization.get_application", return_value=None):
        with pytest.raises(HTTPException) as error:
            tokenize_approved_application("MISSING", "UNDERWRITER-001")

    assert error.value.status_code == 404
    mock_dependencies["mint"].assert_not_called()


def test_unapproved_application_is_rejected(
    approved_application,
    mock_dependencies,
):
    approved_application.model_dump.return_value["lifecycleStatus"] = (
        "AI_DELIBERATED"
    )

    with pytest.raises(HTTPException) as error:
        tokenize_approved_application("APP-001", "UNDERWRITER-001")

    assert error.value.status_code == 409
    mock_dependencies["mint"].assert_not_called()


def test_missing_ai_decision_is_rejected(mock_dependencies):
    with patch("app.tokenization.get_ai_decision", return_value=None):
        with pytest.raises(HTTPException) as error:
            tokenize_approved_application("APP-001", "UNDERWRITER-001")

    assert error.value.status_code == 409
    mock_dependencies["mint"].assert_not_called()


def test_missing_valuation_is_rejected(mock_dependencies):
    with patch("app.tokenization.get_valuation", return_value=None):
        with pytest.raises(HTTPException) as error:
            tokenize_approved_application("APP-001", "UNDERWRITER-001")

    assert error.value.status_code == 409
    mock_dependencies["mint"].assert_not_called()


def test_existing_pending_request_does_not_mint_again(mock_dependencies):
    """An unresolved request must not submit a second transaction."""

    mock_dependencies["existing"].return_value = {
        "applicationId": "APP-001",
        "status": "PENDING",
    }

    with pytest.raises(HTTPException) as error:
        tokenize_approved_application("APP-001", "UNDERWRITER-001")

    assert error.value.status_code == 409
    mock_dependencies["mint"].assert_not_called()


def test_recover_existing_on_chain_token(mock_dependencies):
    """Recover a token when the Firestore result was not saved."""

    mock_dependencies["existing"].return_value = {
        "applicationId": "APP-001",
        "status": "PENDING",
    }

    mock_dependencies["find_on_chain"].return_value = {
        "tokenId": 3,
        "transactionHash": "0x5678",
        "contractAddress": "0xContract",
        "chainId": 31337,
    }

    tokenize_approved_application("APP-001", "UNDERWRITER-001")

    mock_dependencies["mint"].assert_not_called()
    mock_dependencies["save"].assert_called_once()


def test_blockchain_failure_keeps_pending_record(mock_dependencies):
    """An uncertain blockchain outcome must not trigger automatic reminting."""

    mock_dependencies["mint"].side_effect = ConnectionError(
        "Blockchain unavailable"
    )

    with pytest.raises(HTTPException) as error:
        tokenize_approved_application("APP-001", "UNDERWRITER-001")

    assert error.value.status_code == 503
    mock_dependencies["reserve"].assert_called_once()
    mock_dependencies["save"].assert_not_called()
