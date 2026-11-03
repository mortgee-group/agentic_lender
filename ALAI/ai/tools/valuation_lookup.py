# ai/tools/valuation_lookup.py
import joblib
import pandas as pd
from crewai.tools import BaseTool
from pydantic import BaseModel, Field
from typing import Type

from config import RF_MODEL_PATH

# Exact feature order used at training time (see train_daft_housing_model_rf.py)
FEATURES = [
    "bedrooms",
    "bathrooms",
    "floor_area",
    "property_type",
    "ber_rating",
    "county",
    "year_built",
]

# Load once at import time — same pattern as app/main.py
_rf_model = joblib.load(RF_MODEL_PATH)


class ValuationInput(BaseModel):
    """Input schema mirroring app.main.PropertyDetails."""
    bedrooms: float = Field(..., ge=0)
    bathrooms: float = Field(..., ge=0)
    floor_area: float = Field(..., gt=0, description="Floor area in m²")
    property_type: str = Field(..., description="e.g. 'Detached', 'Apartment'")
    ber_rating: str = Field(..., description="e.g. 'A1', 'C3', 'Unknown'")
    county: str = Field(..., description="e.g. 'Dublin', 'Cork'")
    year_built: float = Field(..., ge=1700, le=2030)


class ValuationLookupTool(BaseTool):
    name: str = "valuation_lookup"
    description: str = (
        "Predict the fair market value of an Irish property using the trained "
        "RandomForest model. Returns a numeric EUR valuation. Input must include: "
        "bedrooms, bathrooms, floor_area, property_type, ber_rating, county, "
        "year_built."
    )
    args_schema: Type[BaseModel] = ValuationInput

    def _run(
        self,
        bedrooms: float,
        bathrooms: float,
        floor_area: float,
        property_type: str,
        ber_rating: str,
        county: str,
        year_built: float,
    ) -> str:
        # Normalise the same way clean_daft_housing_data.py does
        normalised = {
            "bedrooms": int(bedrooms),
            "bathrooms": int(bathrooms),
            "floor_area": float(floor_area),
            "property_type": property_type.strip(),
            "ber_rating": str(ber_rating).strip().upper(),
            "county": str(county).strip().title(),
            "year_built": int(year_built),
        }
        X = pd.DataFrame([normalised])[FEATURES]
        prediction = float(_rf_model.predict(X)[0])
        return f"Predicted fair value: €{prediction:,.0f} (raw={prediction:.2f})"