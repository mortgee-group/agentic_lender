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
## AL-Mobile (Fair-Price Property Checker), AL-Web (Collateral Assessment View)
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
    "model": "knn",
    "prediction": 665584.8341768743
}
```
## AL-Mobile (Fair-Price Property Checker), AL-Web (Collateral Assessment View)
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
## AI audit &deliberation .Underwrite agent
## POST /api/v1/ai/underwrite 		
### Request Body
```json
{
  "applicationId": "APP-001",
  "borrowerId": "firebase_uid_123",
  "brokerId": "firebase_uid_456",
  "householdAnnualGrossIncome": 75000,
  "existingDebtLiabilities": 10000,
  "deposit": 70000,
  "requestedMortgageTerm": 30,
  "sellerAskingPrice": 350000,
  "requestedLoanAmount": 280000,
  "property": {
    "county": "Kilkenny",
    "floor_area": 115,
    "bedrooms": 3,
    "bathrooms": 2,
    "year_built": 2005,
    "property_type": "SEMI_DETACHED",
    "ber_rating": "B2"
  }
}
```
### Response
```json
{
    "applicationId": "APP-001",
    "borrowerId": "firebase_uid_123",
    "brokerId": "firebase_uid_456",
    "decision": "DECLINE",
    "rationale": "The requested loan exceeds the Central Bank of Ireland 3.5× income cap of €262,500, so the application fails the regulatory borrowing limit.",
    "ltv": 108.71,
    "dti": 29.9,
    "risk_rating": 4,
    "conditions": [
        "The loan application fails the regulatory borrowing limit as per the Central Bank of Ireland guidelines.",
        "The Loan-to-Value (LTV) ratio is 108.71%, which exceeds the typical maximum threshold of 90%, indicating a high collateral risk."
    ],
    "credit_memo": "The applicant's income is €6,250. The stress-tested mortgage repayment (30-yr at 6% interest) on the €280,000 loan is approx. €1,680. Existing loan payment is estimated at €189, giving total debt service €1,869 and a DTI of 29.9%, within typical limits. However, the requested loan exceeds the Central Bank of Ireland 3.5× income cap of €262,500, so the application fails the regulatory borrowing limit.",
    "agent_trails": "decision='DECLINE' rationale='The requested loan exceeds the Central Bank of Ireland 3.5× income cap of €262,500, so the application fails the regulatory borrowing limit.' ltv=108.71 dti=29.9 risk_rating=4 conditions=['The loan application fails the regulatory borrowing limit as per the Central Bank of Ireland guidelines.', 'The Loan-to-Value (LTV) ratio is 108.71%, which exceeds the typical maximum threshold of 90%, indicating a high collateral risk.'] credit_memo=\"The applicant's income is €6,250. The stress-tested mortgage repayment (30-yr at 6% interest) on the €280,000 loan is approx. €1,680. Existing loan payment is estimated at €189, giving total debt service €1,869 and a DTI of 29.9%, within typical limits. However, the requested loan exceeds the Central Bank of Ireland 3.5× income cap of €262,500, so the application fails the regulatory borrowing limit.\"",
    "agent_outputs": {
        "affordability": {
            "dti": 29.9,
            "stress_tested_repayment": 1680.0,
            "max_borrowing_capacity": 262500.0,
            "affordability_verdict": "Fail",
            "notes": "Monthly gross income is €6,250. Stress‑tested mortgage repayment (30‑yr at 6% interest) on the €280,000 loan is approx. €1,680. Existing loan payment is estimated at €189, giving total debt service €1,869 and a DTI of 29.9%, within typical limits. However, the requested loan exceeds the Central Bank of Ireland 3.5× income cap of €262,500, so the application fails the regulatory borrowing limit."
        },
        "valuation": {
            "predicted_value": 257530.0,
            "overvaluation_pct": 35.89,
            "ltv": 108.71,
            "collateral_risk": "High",
            "notes": "The asking price of €350,000 is overvalued by approximately 35.89% compared to the ML-predicted fair value of €257,530. The Loan-to-Value (LTV) ratio is 108.71%, which exceeds the typical maximum threshold of 90%, indicating a high collateral risk. Additionally, the loan application fails the regulatory borrowing limit as per the Central Bank of Ireland guidelines."
        },
        "market_research": {
            "ecb_rate": 4.5,
            "summary": "As of the latest update, the European Central Bank (ECB) main refinancing rate stands at 4.5%. This rate influences the cost of borrowing across the Eurozone, including Ireland. The Central Bank of Ireland maintains a regulatory borrowing cap of 3.5 times the borrower's income, which impacts mortgage lending trends by limiting the maximum loan amount relative to income. Recent market conditions show that mortgage affordability is challenged by high property valuations and rising interest rates. For instance, a stress-tested mortgage repayment on a €280,000 loan at a 6% interest rate results in a monthly payment of approximately €1,680, which can strain borrowers' budgets. Additionally, the overvaluation of properties, as evidenced by a 35.89% overvaluation in some cases, further complicates the borrowing landscape. These factors combined with a high Loan-to-Value (LTV) ratio exceeding 90% indicate increased collateral risk, making it crucial for borrowers to carefully assess their financial capacity and for lenders to adhere to regulatory guidelines."
        }
    },
    "estimatedLTV": 108.71,
    "DTI": 29.9,
    "approvalLikelihood": "Low",
    "riskTier": "Tier 4",
    "lifecycleStatus": "AI_DELIBERATED"
}
```					
## Macro stress testing simulator/agent
## POST /api/v1/ai/stress-test			
### Request Body
```json
{
  "rate_shock_bps": 200,
  "property_drop_pct": 10,
  "applications": [
    {
      "applicationId": "APP-001",
      "householdAnnualGrossIncome": 95000,
      "existingDebtLiabilities": 12000,
      "deposit": 80000,
      "requestedMortgageTerm": 30,
      "sellerAskingPrice": 520000,
      "current_rate_pct": 3.75,
      "property": {
        "county": "Dublin",
        "floor_area": 110,
        "bedrooms": 3,
        "bathrooms": 2,
        "year_built": 2005,
        "property_type": "DETACHED",
        "ber_rating": "B2"
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
        "total": 1,
        "become_unaffordable": 0,
        "ltv_breaches_90": 1,
        "ltv_breaches_100": 0
    },
    "results": [
        {
            "applicationId": "APP-001",
            "stressed_repayment": 2567.72,
            "stressed_dti": 45.07,
            "stressed_ltv": 95.96,
            "stressed_risk_rating": 5,
            "original_risk_rating": 3,
            "verdict": "MARGINAL"
        }
    ]
}
 
```
## valuation agent/ loan file & collatoral assesment view
## POST /api/v1/ai/assess				
### Request Body
```json
{
  "householdAnnualGrossIncome": 95000,
  "existingDebtLiabilities": 12000,
  "deposit": 80000,
  "requestedMortgageTerm": 30,
  "sellerAskingPrice": 520000,
  "property": {
    "county": "Dublin",
    "floor_area": 110,
    "bedrooms": 3,
    "bathrooms": 2,
    "year_built": 2005,
    "property_type": "DETACHED",
    "ber_rating": "B2"
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
## Origination pipeline view
## POST /api/v1/ml/predict/rf/batch
### Request Body
```json
{
  "items": [
    {
      "property": {
        "county": "Dublin",
        "floor_area": 110,
        "bedrooms": 3,
        "bathrooms": 2,
        "year_built": 2005,
        "property_type": "DETACHED",
        "ber_rating": "B2"
      },
      "asking_price": 520000,
      "loan_amount": 440000
    },
    {
      "property": {
        "county": "Cork",
        "floor_area": 75,
        "bedrooms": 2,
        "bathrooms": 1,
        "year_built": 2018,
        "property_type": "APARTMENT",
        "ber_rating": "A3"
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