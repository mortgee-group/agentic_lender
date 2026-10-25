# How to run
## 1. Create venv using python3.12
 `python -m venv .venv`
## 2. Activate venv 
`.\.venv\Scripts\Activate`
## 3. Install requirements 
`pip install -r requirements.txt`
## 4. Run clean_daft_housing_data
 `python -m ml.clean_daft_housing_data`
## 5. Run train_daft_housing_model_knn 
`python -m ml.train_daft_housing_model_knn`
## 6. Run train_daft_housing_model_rf
`python -m ml.train_daft_housing_model_rf`
## 7. how to run endpoints
`uvicorn app.main:app --reload --port 5000`  

# Available endpoints
``` 
http://localhost:5000/api/v1/predict/rf
http://localhost:5000/api/v1/predict/knn
```

## Example payload

```
{
  "bedrooms": 3,
  "bathrooms": 2,
  "floor_area": 110.5,
  "property_type": "House",
  "ber_rating": "B2",
  "county": "Cork",
  "year_built": 2005
}
```

## Example response

```
{
    "model": "rf",
    "prediction": 304195.0
}

```
```

{
    "model": "knn",
    "prediction": 279925.31612682564
}

```
 # Imortant notes
 ` ensure that both models exist that both model path exist`
