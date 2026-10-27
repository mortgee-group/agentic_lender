# Agentic Lender — API Integration Guide

This document explains how AL-Mobile, AL-Web, and AL-AI can integrate with the AL-Core backend.

## 1. API Base URL

The backend is deployed on Google Cloud Run.

**Production API:**

https://agentic-lender-525895646792.europe-west2.run.app

**Interactive API Documentation (Swagger):**

https://agentic-lender-525895646792.europe-west2.run.app/docs

All API endpoints use the production base URL.

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

**Request body:**

```json
{
  "display_name": "John Smith"
}
```

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
  "display_name": "John Smith"
}
```

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
    "location": "Kilkenny City",
    "county": "Kilkenny",
    "floorArea": 115,
    "floorAreaUnit": "SQ_METERS",
    "bedrooms": 3,
    "propertyType": "SEMI_DETACHED",
    "berEnergyRating": "B2"
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
| `property.location` | Property location. Cannot be empty. |
| `property.county` | County where the property is located. Cannot be empty. |
| `property.floorArea` | Property floor area. Must be greater than 0. |
| `property.floorAreaUnit` | Must be `SQ_METERS`. |
| `property.bedrooms` | Number of bedrooms. Must be 0 or greater. |
| `property.propertyType` | Property type. See supported values below. |
| `property.berEnergyRating` | Optional BER energy rating. |

### Supported Property Types

`APARTMENT`, `DETACHED`, `SEMI_DETACHED`, `TERRACED`, `BUNGALOW`, `TOWN_HOUSE`, `COMMERCIAL`, `OTHER`

### Supported BER Ratings

`A1`, `A2`, `A3`, `B1`, `B2`, `B3`, `C1`, `C2`, `C3`, `D1`, `D2`, `E1`, `E2`, `F`, `G`, `EXEMPT`

The `berEnergyRating` field can also be `null`.

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

**Important:** Creating an application does not automatically trigger AI underwriting. AI processing will require a separate integration.

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
    "requestedLoanAmount": null,
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

AL-Core currently supports creating and retrieving mortgage applications.

The lifecycle statuses are defined in the backend, but **API endpoints for changing application statuses have not yet been implemented**.

Therefore:

- AL-Mobile and AL-Web can display the current application status.
- Clients cannot currently update the status through the API.
- AL-AI cannot yet submit underwriting results through a dedicated endpoint.
- Underwriter approval and funding workflows require additional backend development.

### Planned Integration

Future backend functionality will support:

1. Recording AI underwriting results.
2. Updating application lifecycle statuses.
3. Restricting status changes to authorised roles.
4. Validating allowed status transitions.

**Important:** These features are planned, not currently available. Frontend teams should not assume that status update endpoints exist.

## 7. Integration Notes for Development Teams

### AL-Mobile (Kotlin)

- Use Firebase Authentication to sign users in.
- Obtain a Firebase ID token before calling protected endpoints.
- Include the token in the `Authorization` header.
- Use `POST /applications` to submit mortgage applications.
- Use `GET /applications` to display the user's applications.
- Use `GET /applications/{application_id}` to display application details.

### AL-Web (React)

- Use Firebase Authentication for sign-in.
- Retrieve the user's profile using `GET /users/me`.
- Display navigation and features according to the user's role.
- Use the application GET endpoints for dashboards and application details.
- Handle `401`, `403`, and `404` responses appropriately.

### AL-AI (Underwriting)

- Mortgage application data is stored in Firestore.
- Application records contain financial information, property details, and lifecycle status.
- AI-related fields include `approvalLikelihood`, `riskTier`, `DTI`, and `estimatedLTV`.
- These fields may initially contain `null`.
- An authenticated API endpoint for submitting AI underwriting results has not yet been implemented.
- The AL-AI and AL-Core must agree on the integration method and underwriting result format before implementation.

## 8. API Development Status

| Feature | Status |
|---|---|
| Firebase authentication | Implemented |
| User registration and profiles | Implemented |
| Role-based permissions | Implemented |
| Mortgage application creation | Implemented |
| Mortgage application retrieval | Implemented |
| Firestore persistence | Implemented |
| Cloud Run deployment | Implemented |
| AI underwriting integration | Planned |
| Application status updates | Planned |
| Underwriter approval workflow | Planned |

## 9. API Documentation

For the latest available endpoints, request schemas, and response schemas, consult the deployed Swagger documentation:

https://agentic-lender-525895646792.europe-west2.run.app/docs

This integration guide describes the currently implemented API and identifies functionality planned for future development.