# tests/test_valuation_tool.py
import re
import pytest
from ai.tools import ValuationLookupTool


def _extract_value(result: str) -> float:
    """Parse 'raw=509479.25' from the tool's return string."""
    match = re.search(r"raw=([\d.]+)", result)
    assert match, f"Could not parse valuation from: {result}"
    return float(match.group(1))


def _to_tool_kwargs(specs: dict) -> dict:
    """
    Convert Firestore 'property' dict to the tool's keyword args.
    The tool expects Title Case property_type; Firestore uses UPPER_SNAKE.
    """
    pt = specs["property_type"]
    # Simple normalization matching app.main._normalize_property_type
    key = pt.strip().upper().replace("-", "_").replace(" ", "_")
    mapping = {
        "DETACHED": "Detached",
        "SEMI_DETACHED": "Semi-Detached",
        "TERRACED": "Terraced",
        "END_OF_TERRACE": "End of Terrace",
        "END_OF_TERRACED": "End of Terrace",
        "APARTMENT": "Apartment",
        "FLAT": "Apartment",
        "DUPLEX": "Duplex",
        "BUNGALOW": "Bungalow",
        "SITE": "Site",
        "UNKNOWN": "Unknown",
    }
    return {
        "bedrooms": specs["bedrooms"],
        "bathrooms": specs["bathrooms"],
        "floor_area": specs["floor_area"],
        "property_type": mapping.get(key, pt.strip()),
        "ber_rating": specs["ber_rating"],
        "county": specs["county"],
        "year_built": specs["year_built"],
    }


def test_valuation_returns_positive(sample_property):
    kwargs = _to_tool_kwargs(sample_property)
    result = ValuationLookupTool()._run(**kwargs)
    assert "Predicted fair value" in result
    value = _extract_value(result)
    assert 100_000 < value < 5_000_000


def test_valuation_county_case_insensitive():
    """lowercase 'cork' should be normalised to 'Cork' and still predict."""
    result = ValuationLookupTool()._run(
        bedrooms=2, bathrooms=1, floor_area=75,
        property_type="Apartment", ber_rating="a3",
        county="cork", year_built=2010,
    )
    value = _extract_value(result)
    assert value > 0


def test_valuation_ber_uppercase():
    """'b2' should be normalised to 'B2'."""
    result = ValuationLookupTool()._run(
        bedrooms=3, bathrooms=2, floor_area=110,
        property_type="Detached", ber_rating="b2",
        county="Dublin", year_built=2005,
    )
    assert "Predicted fair value" in result


def test_valuation_deterministic(sample_property):
    kwargs = _to_tool_kwargs(sample_property)
    tool = ValuationLookupTool()
    a = tool._run(**kwargs)
    b = tool._run(**kwargs)
    assert a == b


def test_valuation_unknown_county_does_not_crash():
    """handle_unknown='ignore' in the encoder means unknown counties still predict."""
    result = ValuationLookupTool()._run(
        bedrooms=3, bathrooms=2, floor_area=100,
        property_type="Semi-Detached", ber_rating="C1",
        county="Atlantis", year_built=2000,
    )
    assert "Predicted fair value" in result