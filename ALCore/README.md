# AL-Core Backend

This is the backend API for the Agentic Lender project, built with Python and FastAPI.

It handles Firebase Authentication, user profiles, and access to Cloud Firestore. The API is hosted on Google Cloud Run so the Android and Web can use it during development.

## Getting Started

### What you'll need

* Python 3.12
* Git
* Access to the Google Cloud project (only if you're running the backend locally)

### 1. Clone the repository

If you haven't cloned the project yet, run:

```cmd
git clone https://github.com/mortgee-group/agentic_lender.git
```

### 2. Open the backend folder

```cmd
cd agentic_lender\ALCore
```

### 3. Set up a virtual environment

Create a virtual environment using Python 3.12:

```cmd
py -3.12 -m venv .venv
```

Activate it in Windows CMD:

```cmd
.venv\Scripts\activate
```

If you're using PowerShell, you can run Python directly from the virtual environment without activating it:

```powershell
.\.venv\Scripts\python.exe
```

### 4. Install the dependencies

```cmd
python -m pip install -r requirements.txt
```

## Firebase Setup

The backend uses Firebase Authentication and Cloud Firestore.

If you're working on the Android or Web app, you can use the deployed API without setting up backend credentials.

If you want to run the Python backend locally, you'll need access to our Google Cloud project.

Once you have access, install the Google Cloud CLI and configure Application Default Credentials:

```cmd
gcloud auth application-default login
```

Set the correct Google Cloud project:

```cmd
gcloud config set project agentic-lender-8ad70
```

Set the quota project:

```cmd
gcloud auth application-default set-quota-project agentic-lender-8ad70
```

This lets the backend use your Google Cloud account for local development.

Keep any Firebase service account files private and never upload them to GitHub.

## Running the Backend

Once everything is installed and Firebase is configured, start the API:

```cmd
python -m uvicorn app.main:app --reload
```

You can now open:

* **API documentation:** http://127.0.0.1:8000/docs
* **Health check:** http://127.0.0.1:8000/health

The `/docs` page lets you explore and test the available endpoints.

## Firebase Authentication

The backend uses Firebase ID tokens to authenticate users.

After a user signs in through Firebase Authentication, the Android or Web app can obtain their ID token using the Firebase SDK.

The token must be included in requests to protected endpoints:

```http
Authorization: Bearer <FIREBASE_ID_TOKEN>
```

The backend verifies the token using the Firebase Admin SDK.

If the token is missing, invalid, or expired, the API returns **401 Unauthorized**.

Firebase ID tokens normally expire after approximately one hour. The client application should obtain a refreshed token when necessary.

### Testing authentication in Swagger

Swagger UI supports Bearer authentication.

To test a protected endpoint:

1. Open `/docs`.
2. Click **Authorize**.
3. Paste a valid Firebase ID token without the `Bearer` prefix.
4. Click **Authorize**, then **Close**.
5. Open the endpoint you want to test and click **Try it out**.

Swagger automatically adds the Authorization header.

Never share Firebase ID tokens or passwords.

## User Profiles and Roles

User profiles are stored in the Firestore `users` collection.

Each profile uses the user's Firebase UID as its document ID.

A user profile contains:

| Field          | Description                                    |
| -------------- | ---------------------------------------------- |
| `uid`          | Unique user ID from Firebase Authentication    |
| `email`        | Email address from the verified Firebase token |
| `role`         | User's assigned role                           |
| `display_name` | User's display name                            |

### Supported roles

The backend currently defines three user roles:

* **Borrower** — A user applying for a mortgage.
* **Broker** — A mortgage broker who manages client applications.
* **Underwriter** — A bank underwriting officer who reviews mortgage applications.

These roles are defined in `app/models.py`.

### Role-Based Access Control (RBAC)

AL-Core implements role-based access control through the `require_role()` dependency in `app/permissions.py`.

Protected endpoints can restrict access to specific roles:

* **Borrower** — Access to borrower-only endpoints.
* **Broker** — Access to broker-only endpoints.
* **Underwriter** — Access to underwriter-only endpoints.

The backend verifies the user's Firebase ID token, retrieves their role from their Firestore profile, and checks whether they have permission to access the endpoint.

The permission checker returns:

* **401 Unauthorized** — The Firebase ID token is missing or invalid.
* **403 Forbidden** — The authenticated user does not have a Firestore profile or lacks the required role.

RBAC is covered by automated tests in `tests/test_permissions.py`, including all nine combinations of the three roles and three protected test endpoints.

**Note:** The role-specific endpoints currently exist in the test suite to verify the permission checker. Production borrower, broker, and underwriter endpoints will use this dependency as they are implemented.


### Registering a user profile

When a user registers through the backend:

* Their UID comes from their verified Firebase token.
* Their email comes from their verified Firebase token.
* Their role is automatically set to `borrower`.
* Their display name comes from the registration request.

Users cannot assign themselves broker or underwriter roles through the registration endpoint.

### Role Assignment

New users are automatically assigned the `borrower` role when they register through `POST /users/me`.

Users cannot assign themselves `broker` or `underwriter` roles through the registration endpoint.

For the MVP, authorized project administrators can assign broker and underwriter roles directly in Firestore by updating the user's `role` field in the `users` collection.

The supported role values are:

* `borrower`
* `broker`
* `underwriter`

A secure role-management API may be implemented in a later development phase.

**Important:** Only authorized administrators should modify user roles. Client applications must not have permission to change their own roles.


## Available Endpoints

| Method | Endpoint | Authentication | Description |
|---|---|---|---|
| GET | `/health` | No | Checks backend health |
| GET | `/auth/me` | Yes | Returns the authenticated user's Firebase UID |
| GET | `/users/me` | Yes | Retrieves the authenticated user's profile |
| POST | `/users/me` | Yes | Registers a new borrower profile |
| GET | `/borrower/dashboard` | Borrower | Tests borrower access |
| GET | `/broker/dashboard` | Broker | Tests broker access |
| GET | `/underwriter/dashboard` | Underwriter | Tests underwriter access |
| POST | `/applications` | Borrower | Creates a mortgage application |
| GET | `/applications` | Borrower, Broker, Underwriter | Lists applications accessible to the user |
| GET | `/applications/{application_id}` | Borrower, Broker, Underwriter | Retrieves an application by ID, subject to access permissions |

### API Integration Guide

For detailed instructions on integrating AL-Mobile, AL-Web, and AL-AI with the backend, see:

[API Integration Guide](API_INTEGRATION.md)

The guide covers:
- Firebase authentication and user registration
- Mortgage application request and response formats
- Role-based access permissions
- Application lifecycle statuses
- Current and planned API functionality

### GET `/health`

Checks whether the API is running.

Example response:

```json
{
  "status": "ok"
}
```

### GET `/auth/me`

Returns the UID of the authenticated Firebase user.

Example response:

```json
{
  "uid": "firebase-user-id"
}
```

### GET `/users/me`

Retrieves the authenticated user's profile from Firestore.

Example response:

```json
{
  "uid": "firebase-user-id",
  "email": "borrower@example.com",
  "role": "borrower",
  "display_name": "Test Borrower"
}
```

Possible responses:

* **200 OK** — Profile found.
* **401 Unauthorized** — Missing or invalid Firebase ID token.
* **404 Not Found** — The user does not have a Firestore profile.

### POST `/users/me`

Creates a new borrower profile in Firestore.

The user must already have a Firebase Authentication account.

Example request:

```json
{
  "display_name": "Test Borrower"
}
```

The backend obtains the UID and email from the verified Firebase token and automatically assigns the `borrower` role.

Example response:

```json
{
  "uid": "firebase-user-id",
  "email": "borrower@example.com",
  "role": "borrower",
  "display_name": "Test Borrower"
}
```

Possible responses:

* **201 Created** — Profile successfully created.
* **400 Bad Request** — The Firebase account has no email address.
* **401 Unauthorized** — Missing or invalid Firebase ID token.
* **409 Conflict** — A profile already exists for this user.
* **422 Unprocessable Entity** — Invalid request data.

The endpoint uses Firestore's create operation to prevent existing profiles from being overwritten.

## Firestore Structure

User profiles are stored in the `users` collection.

Example:

```text
users/
    firebase-user-id/
        uid: "firebase-user-id"
        email: "borrower@example.com"
        role: "borrower"
        display_name: "Test Borrower"
```

The Firestore connection is configured in `app/firebase.py`.

User profile database operations are implemented in `app/users.py`.

### Mortgage Applications Collection

Mortgage applications are stored in the Firestore `applications` collection.

Each application uses an automatically generated Firestore document ID.

Example structure:

```text
applications/
    application-001/
        borrowerId: "borrower-123"
        brokerId: null
        householdAnnualGrossIncome: 75000
        existingDebtLiabilities: 10000
        deposit: 70000
        requestedMortgageTerm: 30
        sellerAskingPrice: 350000
        property: {
            location: "Kilkenny City"
            county: "Kilkenny"
            floorArea: 115
            floorAreaUnit: "SQ_METERS"
            bedrooms: 3
            propertyType: "SEMI_DETACHED"
            berEnergyRating: "B2"
        }
        lifecycleStatus: "APPLIED"
        createdAt: <Firestore timestamp>
        updatedAt: <Firestore timestamp>
        submittedAt: <Firestore timestamp>
```

The `applicationId` returned by the API corresponds to the Firestore document ID. It is not stored as a separate field inside the document.

Database operations are implemented in `app/applications.py`.

Access permissions:
- Borrowers can create applications and retrieve their own.
- Brokers can retrieve applications assigned to them.
- Underwriters can retrieve all applications.

For complete request and response schemas, see [API Integration Guide](API_INTEGRATION.md).

## Running Tests

To run the backend tests, use:

```cmd
python -m pytest -v
```

If you're using PowerShell without an activated virtual environment:

```powershell
.\.venv\Scripts\python.exe -m pytest -v
```

Make sure you're inside the `ALCore` folder with Firebase credentials configured.

There are currently **48 automated tests**, covering:

* Backend health checks.
* Firebase authentication, including missing and invalid tokens.
* User profile retrieval and missing profiles.
* Borrower registration and prevention of self-assigned privileged roles.
* Duplicate profile registration.
* Role-based access control (RBAC) for Borrower, Broker, and Underwriter.
* All nine combinations of the three roles and three protected test endpoints.
* Access denial for unauthenticated users (`401`).
* Access denial for authenticated users without a Firestore profile (`403`).

The tests use mocked Firebase authentication and Firestore operations, so they do not create real Firestore documents.

All **48 tests pass** using:

```powershell
.\.venv\Scripts\python.exe -m pytest -v
```

## Development API

You don't need to run the backend locally just to test the API. There's already a development version running on Google Cloud Run.

**API URL:**

https://agentic-lender-525895646792.europe-west2.run.app

**Swagger documentation:**

https://agentic-lender-525895646792.europe-west2.run.app/docs

**Health check:**

https://agentic-lender-525895646792.europe-west2.run.app/health

The Android and Web teams can use this URL to connect to the backend.

Protected endpoints require a valid Firebase ID token.

## Deployment

The backend is deployed to Google Cloud Run in the `europe-west2` (London) region.

Deployment is connected to the `feature/core-backend` branch, so pushing changes to that branch triggers a new build and deployment through Google Cloud Build.

The Dockerfile is located at `ALCore/Dockerfile`.

On Cloud Run, Firebase uses the service's Google Cloud credentials, so there's no need to upload a service account JSON file.

### Current development status

The following functionality has been implemented and tested:

* FastAPI backend setup
* Firebase Authentication integration
* Firebase ID token verification
* Firestore connection
* User profile creation
* User profile retrieval
* Borrower role assignment during registration
* Duplicate profile protection
* Automated backend tests
* Automatic deployment to Cloud Run
* Role-based access control (RBAC) using `require_role()`
* Permission checks for Borrower, Broker, and Underwriter roles
* 48 passing automated tests covering authentication, user profiles, role-based permissions, and mortgage applications
* Mortgage application creation with Firestore persistence
* Mortgage application retrieval by ID
* Role-based application listing for borrowers, brokers, and underwriters
* Application ownership and broker assignment checks
* Firestore document IDs included in API responses
* FastAPI request and response validation
* API integration documentation for AL-Mobile, AL-Web, and AL-AI
