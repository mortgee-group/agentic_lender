from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent

ML_DIR   = BASE_DIR / "ml"
DATA_DIR = ML_DIR / "data"

# ----- Dataset paths -----
RAW_DATA_PATH   = DATA_DIR / "raw"   / "daft_housing_data.csv"
CLEAN_DATA_PATH = DATA_DIR / "clean" / "daft_housing_data_clean.csv"

# ----- Model artifact paths -----
MODEL_DIR  = ML_DIR / "model"
MODEL_PATH = MODEL_DIR / "daft_housing_model.pkl"