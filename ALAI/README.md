# Prequisites
## Ollama
You will need to have Ollama installed on your machine because one of the project requirements is to have at least one local LLM.
- `https://ollama.com/download`

Once installed run these command in terminal
- `ollama`
- `ollama pull llama3.2`

## API KEYS
You will need to register with qroq and openAI or gemini as these will be cloud LLM used in project. Follow the links bellow and create API key for each.
- `https://platform.openai.com/api-keys`
- `https://console.groq.com/keys`
- `https://aistudio.google.com/api-keys`

Once you have API keys. Create a file in root directory .env and add your keys there

`.env file contents`

OPENAI_API_KEY=YOUR_OPENAI_API_KEY\
GEMINI_API_KEY=YOUR_GEMINI_API_KEY\
GROQ_API_KEY=YOUR_GROQ_API_KEY

# How to run
## Create venv using python 3.12
`py -3.12 -m venv .venv`
## Activate venv
`.\.venv\Scripts\Activate`
## Install requirements
`pip install -r requirements.txt`
## Run clean_daft_housing_data
 `python -m ml.clean_daft_housing_data`
## Run train_daft_housing_model_knn 
`python -m ml.train_daft_housing_model_knn`
## Run train_daft_housing_model_rf
`python -m ml.train_daft_housing_model_fr`
## Start api
`uvicorn app.main:app --reload --host 0.0.0.0 --port 8000`

## Aditional notes
Before starting, ensure both models: random forrest and k nerarest neighbour exist in ml/model folder.
If running any other service localy that use port 8000, when starting uvicorn command just use port 5000 or any other.
`uvicorn app.main:app --reload --host 0.0.0.0 --port 5000`

# Endpoints
Once service is running you can find documentation about available 
endpoints via `http://localhost:8000/docs`

## GET  /api/v1/health
### Response
```json
{
    "ollama_reachable": true,
    "openai_key_set": true,
    "gemini_key_set": true,
    "groq_key_set": true
}
``` 
## POST /api/v1/ml/predict/knn
### Request Body
```json
{
  "bedrooms": 3,
  "bathrooms": 2,
  "floor_area": 110,
  "property_type": "Detached",
  "ber_rating": "B2",
  "county": "Dublin",
  "year_built": 2005
}
```
### Response
```json
{
  "bedrooms": 3,
  "bathrooms": 2,
  "floor_area": 110,
  "property_type": "Detached",
  "ber_rating": "B2",
  "county": "Dublin",
  "year_built": 2005
}
```
## POST /api/v1/ml/predict/rf			
### Request Body
```json
{
  "bedrooms": 3,
  "bathrooms": 2,
  "floor_area": 110,
  "property_type": "Detached",
  "ber_rating": "B2",
  "county": "Dublin",
  "year_built": 2005
}
```
### Response
```json
{
    "model": "rf",
    "prediction": 509479.25
}
```
## POST /api/v1/ai/underwrite 		
### Request Body
```json
{
  "application_id": "APP-001",
  "income": 95000,
  "debt": 12000,
  "deposit": 80000,
  "term_years": 30,
  "asking_price": 520000,
  "property_specs": {
    "bedrooms": 3,
    "bathrooms": 2,
    "floor_area": 110,
    "property_type": "Detached",
    "ber_rating": "B2",
    "county": "Dublin",
    "year_built": 2005
  }
}
```
### Response
```json
{
    "application_id": "APP-001",
    "decision": "DECLINE",
    "rationale": "Requested loan of 440,000 EUR exceeds the regulatory borrowing cap of 3.5x income (332,500 EUR). Stress-tested monthly repayment at 6% interest is 2,638.10 EUR, leading to an annual debt service of 31,657 EUR. Combined with existing debt repayments (12,000 EUR annually) the total debt service is 43,657 EUR, resulting in a Debt-to-Income ratio of 45.96%, above typical affordability thresholds (~40%). Both the cap breach and high DTI indicate the application fails affordability.",
    "ltv": 86.37,
    "dti": 45.96,
    "risk_rating": 3,
    "conditions": [
        "Exceeds regulatory borrowing cap",
        "High Debt-to-Income ratio"
    ],
    "credit_memo": "Fail",
    "agent_trails": "decision='DECLINE' rationale='Requested loan of 440,000 EUR exceeds the regulatory borrowing cap of 3.5x income (332,500 EUR). Stress-tested monthly repayment at 6% interest is 2,638.10 EUR, leading to an annual debt service of 31,657 EUR. Combined with existing debt repayments (12,000 EUR annually) the total debt service is 43,657 EUR, resulting in a Debt-to-Income ratio of 45.96%, above typical affordability thresholds (~40%). Both the cap breach and high DTI indicate the application fails affordability.' ltv=86.37 dti=45.96 risk_rating=3 conditions=['Exceeds regulatory borrowing cap', 'High Debt-to-Income ratio'] credit_memo='Fail'",
    "agent_outputs": {
        "affordability": {
            "dti": 45.96,
            "stress_tested_repayment": 2638.1,
            "max_borrowing_capacity": 332500.0,
            "affordability_verdict": "Fail",
            "notes": "Requested loan of 440,000 EUR exceeds the regulatory borrowing cap of 3.5x income (332,500 EUR). Stress‑tested monthly repayment at 6% interest is 2,638.10 EUR, leading to an annual debt service of 31,657 EUR. Combined with existing debt repayments (12,000 EUR annually) the total debt service is 43,657 EUR, resulting in a Debt‑to‑Income ratio of 45.96%, above typical affordability thresholds (~40%). Both the cap breach and high DTI indicate the application fails affordability."
        },
        "valuation": {
            "predicted_value": 509479.25,
            "overvaluation_pct": 2.06,
            "ltv": 86.37,
            "collateral_risk": "Medium",
            "notes": "The ML model predicts a fair market value of €509,479 for the property. The seller's asking price of €520,000 is approximately 2.06% higher than the predicted value, indicating a slight overvaluation. The requested loan amount of €440,000 results in a Loan-to-Value (LTV) ratio of 86.37%, which is within acceptable limits but close to the upper threshold. Given the slight overvaluation and the LTV, the collateral risk is assessed as Medium. Additionally, the affordability assessment indicates a failure due to exceeding the regulatory borrowing cap and a high Debt-to-Income ratio."
        },
        "market_research": {
            "ecb_rate": 4.5,
            "summary": "As of the latest data, the European Central Bank (ECB) has set the main refinancing rate at 4.5%. The Central Bank of Ireland continues to enforce a mortgage lending cap of 3.5 times the borrower's income, which is impacting borrowing capacity. Recent trends show a tightening in mortgage lending conditions, with stress-tested repayment requirements at higher interest rates, reflecting broader market conditions. This is contributing to affordability challenges, as evidenced by a Debt-to-Income ratio exceeding typical thresholds. Regulatory changes have not altered the borrowing cap, but the high ECB rate and market conditions are influencing lending practices."
        }
    }
}
```					
## POST /api/v1/ai/stress-test			
### Request Body
```json
{
  "rate_shock_bps": 200,
  "property_drop_pct": 10,
  "applications": [
    {
      "application_id": "APP-001",
      "income": 95000,
      "debt": 12000,
      "deposit": 80000,
      "term_years": 30,
      "asking_price": 520000,
      "current_rate_pct": 3.75,
      "property_specs": {
        "bedrooms": 3, "bathrooms": 2, "floor_area": 110,
        "property_type": "Detached", "ber_rating": "B2",
        "county": "Dublin", "year_built": 2005
      }
    },
    {
      "application_id": "APP-002",
      "income": 68000,
      "debt": 5000,
      "deposit": 40000,
      "term_years": 25,
      "asking_price": 320000,
      "current_rate_pct": 3.75,
      "property_specs": {
        "bedrooms": 2, "bathrooms": 1, "floor_area": 75,
        "property_type": "Apartment", "ber_rating": "A3",
        "county": "Cork", "year_built": 2018
      }
    }
  ]
}
```
### Response
```json
{
    "rate_shock_bps": 200,
    "property_drop_pct": 10.0,
    "summary": {
        "total": 2,
        "become_unaffordable": 1,
        "ltv_breaches_90": 2,
        "ltv_breaches_100": 1
    },
    "results": [
        {
            "application_id": "APP-001",
            "stressed_repayment": 2567.72,
            "stressed_dti": 45.07,
            "stressed_ltv": 95.96,
            "stressed_risk_rating": 5,
            "original_risk_rating": 3,
            "verdict": "MARGINAL"
        },
        {
            "application_id": "APP-002",
            "stressed_repayment": 1761.5,
            "stressed_dti": 38.44,
            "stressed_ltv": 109.77,
            "stressed_risk_rating": 4,
            "original_risk_rating": 3,
            "verdict": "UNAFFORDABLE"
        }
    ]
}
```
## POST /api/v1/ai/assess				
### Request Body
```json
{
  "income": 95000,
  "debt": 12000,
  "deposit": 80000,
  "term_years": 30,
  "asking_price": 520000,
  "property_specs": {
    "bedrooms": 3,
    "bathrooms": 2,
    "floor_area": 110,
    "property_type": "Detached",
    "ber_rating": "B2",
    "county": "Dublin",
    "year_built": 2005
  }
}
```
### Response
```json
{
    "predicted_value": 509479.25,
    "overvaluation_pct": 2.07,
    "ltv": 86.36,
    "dti": 45.07,
    "stress_tested_repayment": 2567.72,
    "max_borrowing_capacity": 332500.0
}
```
## POST /api/v1/ml/predict/rf/batch
### Request Body
```json
{
  "items": [
    {
      "property_specs": {
        "bedrooms": 3, "bathrooms": 2, "floor_area": 110,
        "property_type": "Detached", "ber_rating": "B2",
        "county": "Dublin", "year_built": 2005
      },
      "asking_price": 520000,
      "loan_amount": 440000
    },
    {
      "property_specs": {
        "bedrooms": 2, "bathrooms": 1, "floor_area": 75,
        "property_type": "Apartment", "ber_rating": "A3",
        "county": "Cork", "year_built": 2018
      },
      "asking_price": 320000,
      "loan_amount": 260000
    }
  ]
}
```
### Response
```json
{
    "results": [
        {
            "prediction": 509479.25,
            "ltv": 86.36,
            "overvaluation_pct": 2.07
        },
        {
            "prediction": 283418.03,
            "ltv": 91.74,
            "overvaluation_pct": 12.91
        }
    ]
}
```