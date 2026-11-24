# tests/conftest.py
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

import pytest
from dotenv import load_dotenv

load_dotenv()


@pytest.fixture(scope="session")
def sample_property():
    """Matches Firestore 'property' object (no location)."""
    return {
        "county": "Dublin",
        "floor_area": 110.0,
        "bedrooms": 3,
        "bathrooms": 2,
        "year_built": 2005,
        "property_type": "DETACHED",
        "ber_rating": "B2",
    }


@pytest.fixture(scope="session")
def sample_underwrite_inputs(sample_property):
    """Matches Firestore application document structure."""
    return {
        "applicationId": "APP-001",
        "borrowerId": "firebase_uid_123",
        "brokerId": "firebase_uid_456",
        "householdAnnualGrossIncome": 95000,
        "existingDebtLiabilities": 12000,
        "deposit": 80000,
        "requestedMortgageTerm": 30,
        "sellerAskingPrice": 520000,
        "requestedLoanAmount": 440000,
        "property": sample_property,
    }


@pytest.fixture(scope="session")
def crew_inputs(sample_property):
    """Short internal names expected by tasks.yaml placeholders."""
    return {
        "income": 95000,
        "debt": 12000,
        "deposit": 80000,
        "term_years": 30,
        "asking_price": 520000,
        "property_specs": {
            "bedrooms": 3, "bathrooms": 2, "floor_area": 110,
            "property_type": "Detached", "ber_rating": "B2",
            "county": "Dublin", "year_built": 2005,
        },
    }