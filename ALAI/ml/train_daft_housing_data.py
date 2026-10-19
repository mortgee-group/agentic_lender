
# Train the housing-price model on the cleaned daft_housing_data dataset.
# Run:    python -m ml.train_daft_housing_model
# Reads:  ml/data/clean/daft_housing_data_clean.csv   (auto-generated if missing)
# Writes: ml/model/daft_housing_model.pkl


import joblib
import numpy as np
import pandas as pd

from sklearn.compose import ColumnTransformer
from sklearn.ensemble import GradientBoostingRegressor
from sklearn.metrics import mean_absolute_error, r2_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

from app.config import CLEAN_DATA_PATH, MODEL_DIR, MODEL_PATH
from ml.clean_daft_housing_data import build_clean_dataset



# Feature groups
NUMERIC     = ["bedrooms", "bathrooms", "floor_area", "year_built"]
CATEGORICAL = ["property_type", "ber_rating", "county"]


# Pipeline construction
def build_pipeline() -> Pipeline:
    """
    Preprocessing + model, wrapped in a single sklearn Pipeline.

    Using a Pipeline means the fitted OneHotEncoder / StandardScaler are
    saved inside the model.pkl — no separate encoder artifacts, and no
    train/serve skew at inference time.
    """
    preprocessor = ColumnTransformer(transformers=[
        ("num", StandardScaler(),                            NUMERIC),
        ("cat", OneHotEncoder(handle_unknown="ignore"),      CATEGORICAL),
    ])

    model = GradientBoostingRegressor(
        n_estimators=400,
        learning_rate=0.05,
        max_depth=4,
        random_state=42,
    )

    return Pipeline(steps=[
        ("pre",   preprocessor),
        ("model", model),
    ])


# Data loading
def load_clean_df() -> pd.DataFrame:
    """
    Load the cleaned dataset. If it doesn't exist yet, build it from the
    raw CSV first (so `python -m ml.train_daft_housing_model` is a one-shot
    command).
    """
    if not CLEAN_DATA_PATH.exists():
        print("Cleaned dataset not found — building it now.")
        return build_clean_dataset()

    print(f"Loading cleaned dataset: {CLEAN_DATA_PATH}")
    return pd.read_csv(CLEAN_DATA_PATH)


# Training
def train():
    df = load_clean_df()
    print(f"Clean rows: {len(df)}")

    X = df[NUMERIC + CATEGORICAL]
    # Log-transform the target: Irish house prices span ~€40k–€10M, so
    # log1p makes residuals roughly normal and stabilises training.
    y = np.log1p(df["price"])

    X_tr, X_te, y_tr, y_te = train_test_split(
        X, y, test_size=0.2, random_state=42,
    )

    pipe = build_pipeline()
    print("Fitting pipeline …")
    pipe.fit(X_tr, y_tr)

    # Evaluate on the held-out test set (back-transformed to € terms)
    preds  = np.expm1(pipe.predict(X_te))
    actual = np.expm1(y_te)

    mae = mean_absolute_error(actual, preds)
    r2  = r2_score(actual, preds)

    print(f"MAE : €{mae:,.0f}")
    print(f"R²  : {r2:.3f}")

    # Persist the entire pipeline (preprocessor + model) as one artifact
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    joblib.dump(pipe, MODEL_PATH)
    print(f"Saved -> {MODEL_PATH}")


# CLI entrypoint
if __name__ == "__main__":
    train()