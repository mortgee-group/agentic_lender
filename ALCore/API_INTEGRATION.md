# Agentic Lender — API Integration Guide

This document explains how AL-Mobile, AL-Web, and AL-AI can integrate with the AL-Core backend.

## 1. API Base URL

The backend is deployed on Google Cloud Run. Local development uses `http://127.0.0.1:8000`; the deployed service is not yet verified for end-to-end AL-AI integration.

**Deployed development API:**

https://agentic-lender-525895646792.europe-west2.run.app

**Interactive API Documentation (Swagger):**

https://agentic-lender-525895646792.europe-west2.run.app/docs

Use the deployed base URL for cloud testing or `http://127.0.0.1:8000` for local testing. AL-Web currently configures `VITE_API_BASE_URL=http://127.0.0.1:8000` locally.

For example:

`GET https://agentic-lender-525895646792.europe-west2.run.app/applications`

## 2. Authentication

AL-Core uses Firebase Authentication.

Users must authenticate through Firebase before accessing protected API endpoints.

### Authentication Flow

1. The user signs in through Firebase Authentication in AL-Mobile or AL-Web.
2. Firebase provides an ID token for the authenticated user.
3. The client sends the ID token to AL-Core using the HTTP `Authorization` header.
4. AL-Core verifies the token using the Firebase Admin SDK.
5. AL-Core retrieves the user's profile from Firestore to determine their role.
6. The backend checks whether the user's role permits the requested operation.

### Required HTTP Header

```http
Authorization: Bearer <FIREBASE_ID_TOKEN>
```

Example:

```http
GET /users/me HTTP/1.1
Authorization: Bearer <FIREBASE_ID_TOKEN>
```

**Important:** Send the Firebase ID token, not the Firebase API key or refresh token.

### Supported Roles

| Role | Description |
|---|---|
| `borrower` | Creates mortgage applications and accesses their own applications. |
| `broker` | Accesses mortgage applications assigned to them. |
| `underwriter` | Accesses mortgage applications for underwriting. |

Access to protected endpoints requires a valid Firebase ID token and, where applicable, a registered user profile.

## 3. User Registration and Profiles

AL-Core stores user profiles in Firestore.

Firebase Authentication handles user sign-in, while AL-Core manages user roles and profile information.

### POST /users/me

Registers a user profile after the user has authenticated with Firebase.

**Authentication:** Required (Firebase ID token)

**Important:**
- The user's UID and email are obtained from their verified Firebase token.
- New users are registered with the `borrower` role.
- Clients cannot assign themselves the `broker` or `underwriter` role.
- `display_name` is optional and has a maximum length of 30 characters.
- Attempting to register an existing profile returns an error.

### GET /users/me

Returns the authenticated user's Firestore profile.

**Authentication:** Required (Firebase ID token)

**Example response:**

```json
{
  "uid": "firebase-user-123",
  "email": "john@example.com",
  "role": "borrower",
  "display_name": "John Smith",
  "first_name": "John",
  "last_name": "Smith",
  "phone_number": "+353871234567",
  "createdAt": "2026-11-15T12:00:00Z",
  "updatedAt": "2026-11-15T12:00:00Z"
}
```

**Additional Firestore user fields:** Existing `users` documents have also been observed with `first_name`, `last_name`, `phone_number`, `createdAt`, and `updatedAt` (in addition to the four fields above). The example is intentionally minimal: verify `UserProfile` in `app/models.py` or local `/openapi.json` before assuming all nine Firestore fields are exposed by `GET /users/me` or accepted by `POST /users/me`.

### GET /auth/me

Verifies the Firebase ID token and returns information about the authenticated user.

**Authentication:** Required (Firebase ID token)

This endpoint can be used to check whether the client's Firebase token is accepted by AL-Core.

### Recommended Frontend Flow

1. Sign in or create an account using Firebase Authentication.
2. Obtain the Firebase ID token.
3. Call `GET /users/me` to check whether a profile exists.
4. If no profile exists, call `POST /users/me`.
5. Use the returned profile role to determine which application features to display.
6. Include the Firebase ID token in subsequent protected API requests.

**Security:** Frontend role checks are for the user interface only. AL-Core enforces permissions on the server.

## 4. Mortgage Applications

AL-Core manages mortgage applications using Firestore.

### POST /applications

Creates a new mortgage application.

**Authentication:** Required (Firebase ID token)

**Required role:** `borrower`

**Request body:**

```json
{
  "householdAnnualGrossIncome": 75000,
  "existingDebtLiabilities": 10000,
  "deposit": 70000,
  "requestedMortgageTerm": 30,
  "sellerAskingPrice": 350000,
  "property": {
    "county": "Kilkenny",
    "floor_area": 115,
    "bedrooms": 3,
    "bathrooms": 2,
    "year_built": 2010,
    "property_type": "SEMI_DETACHED",
    "ber_rating": "B2"
  }
}
```

### Field Descriptions

| Field | Description |
|---|---|
| `householdAnnualGrossIncome` | Total household annual gross income. Must be 0 or greater. |
| `existingDebtLiabilities` | Existing debt liabilities. Must be 0 or greater. |
| `deposit` | Deposit amount. Must be 0 or greater. |
| `requestedMortgageTerm` | Requested mortgage term in years. Must be greater than 0. |
| `sellerAskingPrice` | Property asking price. Must be greater than 0. |
| `property.county` | County where the property is located. Cannot be empty. |
| `property.floor_area` | Property floor area. |
| `property.bedrooms` | Number of bedrooms. Must be 0 or greater. |
| `property.property_type` | Property type. See supported values below. |
| `property.ber_rating` | BER energy rating. |
| `property.bathrooms` | Number of bathrooms. |
| `property.year_built` | Year the property was built. |

### Supported Property Types

`APARTMENT`, `DETACHED`, `SEMI_DETACHED`, `TERRACED`, `BUNGALOW`, `TOWN_HOUSE`, `COMMERCIAL`, `OTHER`

### Supported BER Ratings

`A1`, `A2`, `A3`, `B1`, `B2`, `B3`, `C1`, `C2`, `C3`, `D1`, `D2`, `E1`, `E2`, `F`, `G`, `EXEMPT`

Check the current `PropertyDetails` schema in `/docs` for optional fields and accepted enum values.

### Successful Response

**HTTP status:** `201 Created`

```json
{
  "applicationId": "firestore-generated-document-id",
  "message": "Mortgage application created successfully"
}
```

The `applicationId` identifies the newly created Firestore document.

### Automatically Generated Fields

Clients should not include these fields in the creation request:

- `applicationId`
- `borrowerId`
- `brokerId`
- `lifecycleStatus`
- `createdAt`
- `updatedAt`
- `submittedAt`

AL-Core generates or assigns these fields.

New applications begin with the lifecycle status `APPLIED`.

**Important:** Creating an application does not automatically trigger AI underwriting. An authorized user must call the separate `/assess` or `/underwrite` endpoint.

## 5. Retrieving Mortgage Applications

AL-Core provides two endpoints for retrieving mortgage applications from Firestore.

Both endpoints require a valid Firebase ID token.

### GET /applications

Returns mortgage applications accessible to the authenticated user.

**Authentication:** Required

**Allowed roles:** `borrower`, `broker`, `underwriter`

**Access permissions:**

| Role | Applications returned |
|---|---|
| `borrower` | Applications created by the authenticated borrower. |
| `broker` | Applications assigned to the authenticated broker. |
| `underwriter` | All mortgage applications. |

**Successful response:** `200 OK`

```json
[
  {
    "applicationId": "application-001",
    "borrowerId": "borrower-123",
    "brokerId": null,
    "householdAnnualGrossIncome": 75000,
    "existingDebtLiabilities": 10000,
    "deposit": 70000,
    "requestedMortgageTerm": 30,
    "sellerAskingPrice": 350000,
    "property": {
      "location": "Kilkenny City",
      "county": "Kilkenny",
      "floorArea": 115,
      "floorAreaUnit": "SQ_METERS",
      "bedrooms": 3,
      "propertyType": "SEMI_DETACHED",
      "berEnergyRating": "B2"
    },
    "requestedLoanAmount": 280000,
    "estimatedLTV": null,
    "DTI": null,
    "approvalLikelihood": null,
    "riskTier": null,
    "lifecycleStatus": "APPLIED",
    "createdAt": "2026-10-27T01:00:00Z",
    "updatedAt": "2026-10-27T01:00:00Z",
    "submittedAt": "2026-10-27T01:00:00Z"
  }
]
```

If the user has no accessible applications, the endpoint returns an empty array:

```json
[]
```

### GET /applications/{application_id}

Retrieves one mortgage application using its Firestore document ID.

**Authentication:** Required

**Allowed roles:** `borrower`, `broker`, `underwriter`

**Access permissions:**

| Role | Permission |
|---|---|
| `borrower` | Can retrieve their own applications. |
| `broker` | Can retrieve applications assigned to them. |
| `underwriter` | Can retrieve any application. |

**Example request:**

```http
GET /applications/application-001
Authorization: Bearer <FIREBASE_ID_TOKEN>
```

**Successful response:** `200 OK`

Returns a single mortgage application object using the same fields shown in the list response.

Unlike `GET /applications`, this endpoint returns an object (`{}`), not an array (`[]`).

### Error Responses

| HTTP Status | Meaning |
|---|---|
| `401 Unauthorized` | Missing or invalid Firebase authentication token. |
| `403 Forbidden` | The authenticated user does not have permission to access the application. |
| `404 Not Found` | The requested application does not exist. |
| `422 Unprocessable Entity` | Request data fails validation, where applicable. |

### Important Integration Notes

- Use `applicationId` to identify applications when navigating between screens.
- The backend enforces access permissions; clients must not rely solely on frontend role checks.
- Dates are returned as ISO 8601 timestamp strings.
- Fields such as `approvalLikelihood`, `riskTier`, and `estimatedLTV` may initially be `null`.
- AI underwriting results are not automatically generated when an application is created.

## 6. Mortgage Application Lifecycle

Each mortgage application has a `lifecycleStatus` field that represents its current stage.

### Supported Statuses

| Status | Description |
|---|---|
| `APPLIED` | The borrower has submitted a mortgage application. |
| `AI_DELIBERATED` | AI underwriting analysis has been completed. |
| `UNDERWRITER_APPROVED` | An underwriter has approved the application. |
| `FUNDED` | Mortgage funding has been completed. |
| `CLOSED` | The application has reached its final stage. |

### Initial Status

When a borrower creates an application using `POST /applications`, AL-Core automatically assigns:

```json
{
  "lifecycleStatus": "APPLIED"
}
```

### Current Implementation

Applications start at `APPLIED`. After a successful call to `POST /applications/{application_id}/underwrite`, AL-Core saves the AI decision and updates the application to `AI_DELIBERATED` in one Firestore batch. This transition has been verified locally. Human approval, funding, closing, and general-purpose status-transition endpoints have not been verified.

### AI Valuation and Underwriting Endpoints

| Method | Endpoint | Allowed roles | Purpose |
|---|---|---|---|
| POST | `/applications/{application_id}/assess` | `broker` (assigned application), `underwriter` | Requests AL-AI valuation and saves a record in `valuations`. |
| POST | `/applications/{application_id}/underwrite` | `underwriter` | Runs the AL-AI underwriting committee and saves a decision in `aiDecisions`. |

Both endpoints require a Firebase ID token. AL-Core retrieves the application by ID and builds the AL-AI request; clients do **not** submit the mortgage financial/property fields again in the endpoint body.

**Assessment response:** `predicted_value`, `overvaluation_pct`, `ltv`, `dti`, `stress_tested_repayment`, and `max_borrowing_capacity`. The `valuations` document stores `applicationId`, `valuationPrediction` (from `predicted_value`), `valuationData` (property details), and `createdAt`; the other returned metrics are not currently saved by `save_valuation()`.

**Underwriting response:** Includes `decision`, `rationale`, `ltv`, `dti`, `risk_rating`, `conditions`, `credit_memo`, `agent_trails`, `agent_outputs`, `estimatedLTV`, `DTI`, `approvalLikelihood`, `riskTier`, and `lifecycleStatus`. AL-Core stores the complete response for auditing alongside the lending decision, credit memorandum and agent outputs. It updates `estimatedLTV`, `DTI`, `approvalLikelihood`, `riskTier`, `lifecycleStatus` (`AI_DELIBERATED`) and `updatedAt` on the application.

If AL-AI cannot be reached, AL-Core returns `503`; if AL-AI returns an unsuccessful HTTP status, AL-Core returns `502`. Application access and role restrictions still apply.

## API Development Status

| Feature | Status |
|---|---|
| Firebase authentication | Implemented |
| User registration and profiles | Implemented |
| Role-based permissions | Implemented |
| Mortgage application creation | Implemented |
| Mortgage application retrieval | Implemented |
| Firestore persistence | Implemented |
| AL-Core Cloud Run deployment | Implemented; deployed AI integration not yet verified |
| Authenticated AI HTTP endpoints and AL-Web browser flow | Not yet tested end-to-end |
| AI valuation and underwriting integration | Implemented; locally tested |
| `APPLIED` → `AI_DELIBERATED` update | Implemented; locally tested |

## 9. API Documentation

For the latest available endpoints, request schemas, and response schemas, consult the deployed Swagger documentation:

https://agentic-lender-525895646792.europe-west2.run.app/docs

This integration guide describes the implemented API, locally verified integrations, and functionality still requiring testing or development.