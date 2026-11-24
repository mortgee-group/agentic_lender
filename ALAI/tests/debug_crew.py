# tests/debug_crew.py
import os
os.environ["CREWAI_DISABLE_TRACING"] = "true"
os.environ["CREWAI_DISABLE_TRACKING"] = "true"
os.environ["OTEL_SDK_DISABLED"] = "true"
os.environ["CREWAI_DISABLE_TELEMETRY"] = "true"

from ai import ALCrew

# Crew uses short internal names (tasks.yaml placeholders)
inputs = {
    "income": 95000, "debt": 12000, "deposit": 80000,
    "term_years": 30, "asking_price": 520000,
    "property_specs": {
        "bedrooms": 3, "bathrooms": 2, "floor_area": 110,
        "property_type": "Detached", "ber_rating": "B2",
        "county": "Dublin", "year_built": 2005,
    },
}

crew = ALCrew().crew()
print("Starting kickoff...", flush=True)
result = crew.kickoff(inputs=inputs)

print("\n=== PARSED DECISION ===")
d = result.pydantic
print(type(d))
print(d)
print("\n=== FIELD TYPES ===")
print("dti type:", type(d.dti), "value:", d.dti)
print("ltv type:", type(d.ltv), "value:", d.ltv)
print("conditions type:", type(d.conditions), "value:", d.conditions)
print("risk_rating type:", type(d.risk_rating), "value:", d.risk_rating)