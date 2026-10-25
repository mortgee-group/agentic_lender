"""
Train a KNeighborsRegressor on the cleaned daft_housing_data dataset.

The KNN regressor is wrapped in TransformedTargetRegressor so that:
  - during fit  → y is transformed with log1p
  - during predict → the model's output is transformed back with expm1

Result: pipeline.predict(X) returns prices in EUR, which is exactly
what the FastAPI layer (app/main.py) expects.

Run:    python -m ml.train_daft_housing_model_knn
Reads:  ml/data/clean/daft_housing_data_clean.csv
Writes: ml/model/daft_housing_model_knn.joblib
"""

import numpy as np
import pandas as pd
import joblib

from sklearn.model_selection import train_test_split
from sklearn.compose import ColumnTransformer, TransformedTargetRegressor
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler
from sklearn.impute import SimpleImputer
from sklearn.neighbors import KNeighborsRegressor
from sklearn.metrics import mean_absolute_error, r2_score

from config import CLEAN_DATA_PATH, MODEL_DIR, KNN_MODEL_PATH
from ml.clean_daft_housing_data import build_clean_dataset


# ==========================================
# 1. LOAD DATASET
# ==========================================

if not CLEAN_DATA_PATH.exists():
    print("Cleaned dataset not found — building it now.")
    df = build_clean_dataset()
else:
    print(f"Loading cleaned dataset: {CLEAN_DATA_PATH}")
    df = pd.read_csv(CLEAN_DATA_PATH)

print("Dataset loaded.")
print("Rows:", len(df))
print("Columns:", list(df.columns))


# ==========================================
# 2. FEATURES & TARGET
# ==========================================

features = [
    "bedrooms",
    "bathrooms",
    "floor_area",
    "property_type",
    "ber_rating",
    "county",
    "year_built",
]
target = "price"

X = df[features]
y = df[target]        # raw euros — the log transform lives inside the pipeline


# ==========================================
# 3. SPLIT
# ==========================================

X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=42,
)


# ==========================================
# 4. FEATURE TYPES
# ==========================================

numeric_features     = ["bedrooms", "bathrooms", "floor_area", "year_built"]
categorical_features = ["property_type", "ber_rating", "county"]


# ==========================================
# 5. PREPROCESSING  (scaling critical for KNN)
# ==========================================

numeric_pipeline = Pipeline([
    ("imputer", SimpleImputer(strategy="median")),
    ("scaler",  StandardScaler()),
])

categorical_pipeline = Pipeline([
    ("imputer", SimpleImputer(strategy="most_frequent")),
    ("onehot",  OneHotEncoder(handle_unknown="ignore")),
])

preprocessor = ColumnTransformer([
    ("numeric",     numeric_pipeline,     numeric_features),
    ("categorical", categorical_pipeline, categorical_features),
])


# ==========================================
# 6. MODEL (wrapped so log/expm1 are automatic)
# ==========================================

base_knn = KNeighborsRegressor(
    n_neighbors=7,
    weights="distance",
    metric="minkowski",
    p=2,
    n_jobs=-1,
)

model = TransformedTargetRegressor(
    regressor=base_knn,
    func=np.log1p,          # applied to y during fit
    inverse_func=np.expm1,  # applied to predictions during predict
)


# ==========================================
# 7. PIPELINE
# ==========================================

pipeline = Pipeline([
    ("preprocessor", preprocessor),
    ("model",        model),
])


# ==========================================
# 8. TRAIN
# ==========================================

print("\nTraining KNeighborsRegressor (log-target, auto-inverse)…")
pipeline.fit(X_train, y_train)
print("Training complete.")


# ==========================================
# 9. EVALUATE  — predictions are already euros
# ==========================================

preds  = pipeline.predict(X_test)      # ✅ euros — no manual expm1 needed
actual = y_test                        # ✅ euros

mae = mean_absolute_error(actual, preds)
r2  = r2_score(actual, preds)

print("\nModel evaluation")
print("----------------")
print(f"Mean Absolute Error: € {mae:,.2f}")
print(f"R² Score            : {r2:.4f}")

print("\nSample predictions")
print("------------------")

for a, p in zip(actual.iloc[:10], preds[:10]):
    print(
        f"Actual: €{a:,.0f} | "
        f"Predicted: €{p:,.0f} | "
        f"Difference: €{abs(a - p):,.0f}"
    )


# ==========================================
# 10. SANITY CHECK — predict on a single request
# ==========================================

sample = pd.DataFrame([{
    "bedrooms": 3,
    "bathrooms": 2,
    "floor_area": 110.5,
    "property_type": "House",
    "ber_rating": "B2",
    "county": "Dublin",
    "year_built": 2005,
}])

print("\nSanity check (single-row prediction):")
print(f"  € {pipeline.predict(sample)[0]:,.2f}   (should be a realistic euro price)")


# ==========================================
# 11. SAVE
# ==========================================

MODEL_DIR.mkdir(parents=True, exist_ok=True)
joblib.dump(pipeline, KNN_MODEL_PATH)

print("\nModel saved as:")
print(KNN_MODEL_PATH)