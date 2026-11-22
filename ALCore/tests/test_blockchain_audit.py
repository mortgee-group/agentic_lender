"""Tests for mortgage audit evidence and blockchain loan terms."""

import pytest

from app.blockchain import (
    convert_loan_terms,
    prepare_loan_audit_evidence,
    prepare_loan_terms,
)


# Reusable approved mortgage data for tests.
@pytest.fixture
def approved_application():
    return {
        "lifecycleStatus": "UNDERWRITER_APPROVED",
        "underwriterDecision": "APPROVED",
        "approvedLoanAmount": 250000,
        "approvedAprBasisPoints": 400,
        "interestRateConfirmed": True,
        "requestedMortgageTerm": 30,
    }


# --------------------------------------------------
# Audit evidence tests
# --------------------------------------------------

def test_unapproved_mortgage_cannot_generate_audit_hash(
    approved_application,
):
    approved_application["lifecycleStatus"] = "AI_DELIBERATED"

    with pytest.raises(
        ValueError,
        match="Only approved mortgages can be tokenized",
    ):
        prepare_loan_audit_evidence(
            approved_application,
            {"credit_memo": "Test credit memorandum"},
            {"valuationPrediction": 300000},
        )


def test_missing_credit_memorandum_is_rejected(
    approved_application,
):
    with pytest.raises(
        ValueError,
        match="AI credit memorandum is missing",
    ):
        prepare_loan_audit_evidence(
            approved_application,
            {},
            {"valuationPrediction": 300000},
        )


def test_missing_property_valuation_is_rejected(
    approved_application,
):
    with pytest.raises(
        ValueError,
        match="Property valuation is missing",
    ):
        prepare_loan_audit_evidence(
            approved_application,
            {"credit_memo": "Test credit memorandum"},
            {},
        )


def test_unconfirmed_interest_rate_is_rejected(
    approved_application,
):
    approved_application["interestRateConfirmed"] = False

    with pytest.raises(
        ValueError,
        match="Mortgage interest rate has not been confirmed",
    ):
        prepare_loan_audit_evidence(
            approved_application,
            {"credit_memo": "Test credit memorandum"},
            {"valuationPrediction": 300000},
        )


def test_approved_mortgage_generates_audit_hash(
    approved_application,
):
    """A valid mortgage produces a SHA-256 audit hash."""

    result = prepare_loan_audit_evidence(
        approved_application,
        {"credit_memo": "Test credit memorandum"},
        {"valuationPrediction": 300000},
    )

    assert result.startswith("0x")
    assert len(result) == 66


def test_audit_hash_is_deterministic(
    approved_application,
):
    """Identical evidence must produce identical hashes."""

    ai_decision = {"credit_memo": "Test credit memorandum"}
    valuation = {"valuationPrediction": 300000}

    first_hash = prepare_loan_audit_evidence(
        approved_application,
        ai_decision,
        valuation,
    )

    second_hash = prepare_loan_audit_evidence(
        approved_application,
        ai_decision,
        valuation,
    )

    assert first_hash == second_hash


# --------------------------------------------------
# Mortgage conversion tests
# --------------------------------------------------

def test_convert_loan_terms():
    principal_cents, term_months = convert_loan_terms(
        loan_amount=250000,
        mortgage_term_years=30,
    )

    assert principal_cents == 25000000
    assert term_months == 360


@pytest.mark.parametrize(
    "invalid_amount",
    [0, -1000, float("nan"), float("inf")],
)
def test_convert_loan_terms_rejects_invalid_amount(
    invalid_amount,
):
    with pytest.raises(ValueError):
        convert_loan_terms(invalid_amount, 30)


@pytest.mark.parametrize(
    "invalid_term",
    [0, -1, 41],
)
def test_convert_loan_terms_rejects_invalid_term(
    invalid_term,
):
    with pytest.raises(ValueError):
        convert_loan_terms(250000, invalid_term)


def test_convert_loan_terms_rejects_fractional_cents():
    """Never silently round mortgage principal."""

    with pytest.raises(
        ValueError,
        match="fractions of a cent",
    ):
        convert_loan_terms(250000.001, 30)


# --------------------------------------------------
# Approved mortgage preparation tests
# --------------------------------------------------

def test_prepare_loan_terms(approved_application):
    result = prepare_loan_terms(approved_application)

    assert result == {
        "principalCents": 25000000,
        "aprBasisPoints": 400,
        "termMonths": 360,
    }


def test_prepare_loan_terms_rejects_unapproved_application(
    approved_application,
):
    approved_application["lifecycleStatus"] = "AI_DELIBERATED"

    with pytest.raises(
        ValueError,
        match="Only approved mortgages can be tokenized",
    ):
        prepare_loan_terms(approved_application)


def test_prepare_loan_terms_rejects_missing_approved_amount(
    approved_application,
):
    approved_application["approvedLoanAmount"] = None

    with pytest.raises(
        ValueError,
        match="Approved loan amount is missing",
    ):
        prepare_loan_terms(approved_application)


def test_prepare_loan_terms_rejects_unconfirmed_rate(
    approved_application,
):
    approved_application["interestRateConfirmed"] = False

    with pytest.raises(
        ValueError,
        match="Approved interest rate is not confirmed",
    ):
        prepare_loan_terms(approved_application)


def test_prepare_loan_terms_rejects_missing_apr(
    approved_application,
):
    approved_application["approvedAprBasisPoints"] = None

    with pytest.raises(
        ValueError,
        match="Approved interest rate is not confirmed",
    ):
        prepare_loan_terms(approved_application)


def test_prepare_loan_terms_rejects_invalid_mortgage_term(
    approved_application,
):
    approved_application["requestedMortgageTerm"] = 45

    with pytest.raises(
        ValueError,
        match="Mortgage term must be between 1 and 40 years",
    ):
        prepare_loan_terms(approved_application)


def test_prepare_loan_terms_rejects_negative_amount(
    approved_application,
):
    approved_application["approvedLoanAmount"] = -1000

    with pytest.raises(
        ValueError,
        match="Mortgage amount must be positive",
    ):
        prepare_loan_terms(approved_application)
