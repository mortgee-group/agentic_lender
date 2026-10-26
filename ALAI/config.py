from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent

ML_DIR   = BASE_DIR / "ml"
DATA_DIR = ML_DIR / "data"

# ----- Dataset paths -----
RAW_DATA_PATH   = DATA_DIR / "raw"   / "daft_housing_data.csv"
CLEAN_DATA_PATH = DATA_DIR / "clean" / "daft_housing_data_clean.csv"

# ----- Model artifact paths -----
MODEL_DIR         = ML_DIR / "model"
RF_MODEL_PATH     = MODEL_DIR / "daft_housing_model_rf.joblib"
KNN_MODEL_PATH    = MODEL_DIR / "daft_housing_model_knn.joblib"