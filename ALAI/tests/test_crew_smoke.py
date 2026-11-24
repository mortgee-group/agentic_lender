# tests/test_crew_smoke.py
import pytest
from ai import ALCrew


@pytest.mark.timeout(240)
@pytest.mark.slow
def test_crew_runs_end_to_end(crew_inputs):
    crew = ALCrew().crew()
    result = crew.kickoff(inputs=crew_inputs)
    assert result is not None
    text = str(result).lower()
    assert any(kw in text for kw in ["approve", "decline", "conditional"])