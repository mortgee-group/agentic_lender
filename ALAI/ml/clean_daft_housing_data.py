import re
import pandas as pd
import numpy as np

from config import RAW_DATA_PATH, CLEAN_DATA_PATH

# ---------------------------------------------------------------------------
# Valid categorical values
# ---------------------------------------------------------------------------
VALID_BER = {
    "A1", "A2", "A3",
    "B1", "B2", "B3",
    "C1", "C2", "C3",
    "D1", "D2",
    "E1", "E2",
    "F", "G",
}


# ---------------------------------------------------------------------------
# Column-level cleaners
# ---------------------------------------------------------------------------
def clean_price(value) -> float:
    """
    Parse a price cell into a float.

    Handles real-world messy values such as:
        "AMV: 1400000"            -> 1400000.0
        "From 575000"             -> 575000.0
        "From 460000"             -> 460000.0
        "Price on Application"    -> NaN
        "AMV: Price on Application" -> NaN
        "€1,250,000"              -> 1250000.0
        250000 (already numeric)  -> 250000.0
    """
    if pd.isna(value):
        return np.nan

    # Already numeric (int / float from pandas)
    if isinstance(value, (int, float)):
        try:
            return float(value)
        except (ValueError, TypeError):
            return np.nan

    # String path
    s = str(value).strip()

    # Remove common noise tokens
    s = re.sub(r"(?i)(amv\s*:?|price\s*on\s*application|from)", "", s)
    s = s.replace("€", "").replace(",", "").replace(" ", "").strip(":;.-")

    if s == "":
        return np.nan

    try:
        return float(s)
    except ValueError:
        return np.nan


def clean_floor_area(value) -> float:
    """
    Floor area must be a positive m² value in a sane residential range.
    Non-numeric entries and absurd values (e.g. site acreage mixed into the
    column) become NaN so they get dropped.
    """
    if pd.isna(value):
        return np.nan

    # Some rows have strings like "N/A" or "0.5" (site sizes)
    try:
        v = float(value)
    except (ValueError, TypeError):
        return np.nan

    # Lower bound: 15 m² (small studio). Upper: 2000 m² (large mansion).
    if 15 <= v <= 2000:
        return v
    return np.nan


def clean_year(value) -> float:
    """
    Year of construction must be a plausible calendar year.
    'NA' and 0 both become NaN.
    """
    if pd.isna(value):
        return np.nan

    try:
        y = float(value)
    except (ValueError, TypeError):
        return np.nan

    if 1700 <= y <= 2025:
        return y
    return np.nan


def clean_ber(value) -> str:
    """
    Normalise BER rating.

    Valid codes: A1..A3, B1..B3, C1..C3, D1..D2, E1..E2, F, G.
    Exempt ("SI_666"), N/A, blanks -> "Unknown".
    """
    if pd.isna(value):
        return "Unknown"

    s = str(value).strip().upper()

    if s in VALID_BER:
        return s

    return "Unknown"


def clean_county(value) -> str:
    """
    County is a plain string; standardise casing and blanks.
    """
    if pd.isna(value):
        return "Unknown"

    s = str(value).strip()

    if s.casefold() in ("", "n/a", "na", "none"):
        return "Unknown"

    return s.title()


def clean_property_type(value) -> str:
    """
    Property type string; keep the original values (they're already
    consistent-ish), just trim whitespace and handle blanks.
    """
    if pd.isna(value):
        return "Unknown"

    s = str(value).strip()

    if s in ("", "N/A", "NA", "None"):
        return "Unknown"

    return s


# ---------------------------------------------------------------------------
# Dataframe-level pipeline
# ---------------------------------------------------------------------------
def load_and_clean(csv_path) -> pd.DataFrame:
    """
    Load the raw CSV and return a cleaned DataFrame with standardised
    column names, ready for model training.

    Output columns:
        bedrooms, bathrooms, floor_area,
        property_type, ber_rating, county, year_built, price
    """
    print(f"Reading raw dataset: {csv_path}")
    df = pd.read_csv(csv_path)
    print(f"Raw rows: {len(df)}")

    # ----- 1. Apply per-column cleaning -----
    df["price_clean"]      = df["Price"].apply(clean_price)
    df["floor_area_clean"] = df["Floor Area (m2)"].apply(clean_floor_area)
    df["year_clean"]       = df["Date of Construction"].apply(clean_year)
    df["ber_clean"]        = df["BER Rating"].apply(clean_ber)
    df["county_clean"]     = df["County"].apply(clean_county)
    df["ptype_clean"]      = df["Property Type"].apply(clean_property_type)

    # ----- 2. Rename & coerce numerical columns -----
    df = df.rename(columns={
        "Number of Bedrooms":  "bedrooms",
        "Number of Bathrooms": "bathrooms",
    })
    df["bedrooms"]  = pd.to_numeric(df["bedrooms"],  errors="coerce")
    df["bathrooms"] = pd.to_numeric(df["bathrooms"], errors="coerce")

    # ----- 3. Drop rows with missing critical fields -----
    before = len(df)
    df = df.dropna(subset=[
        "price_clean", "floor_area_clean", "bedrooms", "bathrooms",
    ])
    print(f"Dropped {before - len(df)} rows with missing critical fields")

    # ----- 4. Sanity filters -----
    before = len(df)
    df = df[(df["price_clean"] > 20_000) & (df["price_clean"] < 20_000_000)]
    df = df[df["bedrooms"].between(0, 25)]
    df = df[df["bathrooms"].between(0, 25)]
    print(f"Dropped {before - len(df)} rows via sanity filters")

    # ----- 5. Fill remaining optional NaNs -----
    median_year = df["year_clean"].median()
    df["year_clean"] = df["year_clean"].fillna(median_year)
    print(f"Filled missing year_built with median year: {median_year:.0f}")

    # ----- 6. Select & rename final columns -----
    cleaned = df[[
        "bedrooms",
        "bathrooms",
        "floor_area_clean",
        "ptype_clean",
        "ber_clean",
        "county_clean",
        "year_clean",
        "price_clean",
    ]].rename(columns={
        "floor_area_clean": "floor_area",
        "ptype_clean":      "property_type",
        "ber_clean":        "ber_rating",
        "county_clean":     "county",
        "year_clean":       "year_built",
        "price_clean":      "price",
    })

    # ----- 7. Cast to proper dtypes -----
    cleaned["bedrooms"]  = cleaned["bedrooms"].astype(int)
    cleaned["bathrooms"] = cleaned["bathrooms"].astype(int)
    cleaned["year_built"] = cleaned["year_built"].astype(int)
    cleaned["floor_area"] = cleaned["floor_area"].astype(float)
    cleaned["price"]      = cleaned["price"].astype(float)

    cleaned = cleaned.reset_index(drop=True)
    print(f"Clean rows: {len(cleaned)}")
    return cleaned


def build_clean_dataset(raw_path=RAW_DATA_PATH,clean_path=CLEAN_DATA_PATH,) -> pd.DataFrame:

    df = load_and_clean(raw_path)
    clean_path.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(clean_path, index=False)
    print(f"Wrote cleaned dataset -> {clean_path}")

    return df


# --------------------------------
# CLI entrypoint
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    build_clean_dataset()