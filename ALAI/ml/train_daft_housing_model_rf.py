import pandas as pd
import joblib

from sklearn.model_selection import train_test_split
from sklearn.compose import ColumnTransformer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder
from sklearn.impute import SimpleImputer
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, r2_score          # 👈 added r2_score

from config import CLEAN_DATA_PATH, MODEL_DIR, RF_MODEL_PATH
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
# 2. DEFINE FEATURES & TARGET
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
y = df[target]


# ==========================================
# 3. SPLIT DATA
# ==========================================

X_train, X_test, y_train, y_test = train_test_split(
    X,
    y,
    test_size=0.2,
    random_state=42,
)


# ==========================================
# 4. DEFINE FEATURE TYPES
# ==========================================

numeric_features = [
    "bedrooms",
    "bathrooms",
    "floor_area",
    "year_built",
]

categorical_features = [
    "property_type",
    "ber_rating",
    "county",
]


# ==========================================
# 5. PREPROCESSING
# ==========================================

numeric_pipeline = Pipeline([
    ("imputer", SimpleImputer(strategy="median")),
])

categorical_pipeline = Pipeline([
    ("imputer", SimpleImputer(strategy="most_frequent")),
    ("onehot", OneHotEncoder(handle_unknown="ignore")),
])

preprocessor = ColumnTransformer([
    ("numeric", numeric_pipeline, numeric_features),
    ("categorical", categorical_pipeline, categorical_features),
])


# ==========================================
# 6. CREATE RANDOM FOREST MODEL
# ==========================================

model = RandomForestRegressor(
    n_estimators=200,
    random_state=42,
    n_jobs=-1,
    # n_estimators=500,          
    # max_depth=None,
    # min_samples_leaf=2,
    # max_features="sqrt",
    # random_state=42,
    # n_jobs=-1,
)


# ==========================================
# 7. CREATE COMPLETE PIPELINE
# ==========================================

pipeline = Pipeline([
    ("preprocessor", preprocessor),
    ("model", model),
])


# ==========================================
# 8. TRAIN MODEL
# ==========================================

print("\nTraining model...")

pipeline.fit(X_train, y_train)

print("Training complete.")


# ==========================================
# 9. TEST MODEL
# ==========================================

predictions = pipeline.predict(X_test)

mae = mean_absolute_error(y_test, predictions)
r2  = r2_score(y_test, predictions)                                # 👈 new metric

print("\nModel evaluation")
print("----------------")
print("Mean Absolute Error: €", round(mae, 2))
print("R² Score            :", round(r2, 4))                        # 👈 new line

print("\nSample predictions")
print("------------------")

for actual, predicted in zip(y_test.iloc[:10], predictions[:10]):
    print(
        f"Actual: €{actual:,.0f} | "
        f"Predicted: €{predicted:,.0f} | "
        f"Difference: €{abs(actual - predicted):,.0f}"
    )


# ==========================================
# 10. SAVE MODEL
# ==========================================

MODEL_DIR.mkdir(parents=True, exist_ok=True)

joblib.dump(
    pipeline,
    RF_MODEL_PATH,
)

print("\nModel saved as:")
print(RF_MODEL_PATH)