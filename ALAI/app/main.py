from fastapi import FastAPI
from pydantic import BaseModel, Field
import joblib
import pandas as pd
from pathlib import Path
from config import RF_MODEL_PATH, KNN_MODEL_PATH

knn_model = joblib.load(KNN_MODEL_PATH)
rf_model  = joblib.load(RF_MODEL_PATH)

app = FastAPI(title="Daft Housing Price Prediction API")


class PropertyDetails(BaseModel):
    bedrooms: float = Field(..., ge=0)
    bathrooms: float = Field(..., ge=0)
    floor_area: float = Field(..., gt=0)
    property_type: str
    ber_rating: str
    county: str
    year_built: float = Field(..., ge=1800, le=2030)


FEATURES = [
    "bedrooms",
    "bathrooms",
    "floor_area",
    "property_type",
    "ber_rating",
    "county",
    "year_built",
]

def to_dataframe(details: PropertyDetails) -> pd.DataFrame:
    return pd.DataFrame([details.model_dump()])[FEATURES]


# ----------------------------
# Endpoints
# ----------------------------
@app.post("/api/v1/predict/knn")
def predict_knn(details: PropertyDetails):
    X = to_dataframe(details)
    prediction = knn_model.predict(X)[0]
    return {"model": "knn", "prediction": float(prediction)}


@app.post("/api/v1/predict/rf")
def predict_rf(details: PropertyDetails):
    X = to_dataframe(details)
    prediction = rf_model.predict(X)[0]
    return {"model": "rf", "prediction": float(prediction)}


@app.get("/api/v1/health")
def health():
    return {"status": "ok"}