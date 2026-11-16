# AL-Core Backend

This is the backend API for the Agentic Lender project, built with Python and FastAPI.

It handles Firebase Authentication, user profiles, role-based permissions, mortgage applications, and access to Cloud Firestore. AL-Core also integrates with AL-AI for property valuation and multi-agent underwriting. AL-Core is deployed on Google Cloud Run; the AL-AI integration has been verified locally, not yet end-to-end in Cloud Run.

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

If you're using PowerShell, activate the environment with `.\.venv\Scripts\Activate.ps1`, or run Python directly from the virtual environment without activating it:

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

* ****API documentation:**** http://127.0.0.1:8000/docs

* ****Health check:**** http://127.0.0.1:8000/health

The `/docs` page lets you explore and test the available endpoints.

## Firebase Authentication

The backend uses Firebase ID tokens to authenticate users.

After a user signs in through Firebase Authentication, the Android or Web app can obtain their ID token using the Firebase SDK.

The token must be included in requests to protected endpoints:

```http

Authorization: Bearer <FIREBASE_ID_TOKEN>

```

The backend verifies the token using the Firebase Admin SDK.

If the token is missing, invalid, or expired, the API returns ****401 Unauthorized****.

Firebase ID tokens normally expire after approximately one hour. The client application should obtain a refreshed token when necessary.

### Testing authentication in Swagger

Swagger UI supports Bearer authentication.

To test a protected endpoint:

1. Open `/docs`.

2. Click ****Authorize****.

3. Paste a valid Firebase ID token without the `Bearer` prefix.

4. Click ****Authorize****, then ****Close****.

5. Open the endpoint you want to test and click ****Try it out****.

Swagger automatically adds the Authorization header.

Never share Firebase ID tokens or passwords.

## User Profiles and Roles

User profiles are stored in the Firestore `users` collection.

Each profile uses the user's Firebase UID as its document ID.


### Supported roles

The backend currently defines three user roles:

* ****Borrower**** — A user applying for a mortgage.

* ****Broker**** — A mortgage broker who manages client applications.

* ****Underwriter**** — A bank underwriting officer who reviews mortgage applications.

These roles are defined in `app/models.py`.

### Role-Based Access Control (RBAC)

AL-Core implements role-based access control through the `require_role()` dependency in `app/permissions.py`.

Protected endpoints can restrict access to specific roles:

* ****Borrower**** — Access to borrower-only endpoints.

* ****Broker**** — Access to broker-only endpoints.

* ****Underwriter**** — Access to underwriter-only endpoints.

The backend verifies the user's Firebase ID token, retrieves their role from their Firestore profile, and checks whether they have permission to access the endpoint.

The permission checker returns:

* ****401 Unauthorized**** — The Firebase ID token is missing or invalid.

* ****403 Forbidden**** — The authenticated user does not have a Firestore profile or lacks the required role.

RBAC is covered by automated tests in `tests/test_permissions.py`, including all nine combinations of the three roles and three protected test endpoints.

****Note:**** The role-specific endpoints currently exist in the test suite to verify the permission checker. Production borrower, broker, and underwriter endpoints will use this dependency as they are implemented.

### Registering a user profile

When a user registers through the backend:

* Their UID comes from their verified Firebase token.

* Their email comes from their verified Firebase token.

The supported role values are:

* `borrower`

* `broker`

* `underwriter`

## Available Endpoints

\| Method | Endpoint | Authentication | Description |

\|---|---|---|---|

\| GET | `/health` | No | Checks backend health |

\| GET | `/auth/me` | Yes | Returns the authenticated user's Firebase UID |

\| GET | `/users/me` | Yes | Retrieves the authenticated user's profile |

\| POST | `/users/me` | Yes | Registers a new borrower profile |

\| GET | `/borrower/dashboard` | Borrower | Tests borrower access |

\| GET | `/broker/dashboard` | Broker | Tests broker access |

\| GET | `/underwriter/dashboard` | Underwriter | Tests underwriter access |

\| POST | `/applications` | Borrower | Creates a mortgage application |

\| GET | `/applications` | Borrower, Broker, Underwriter | Lists applications accessible to the user |

\| GET | `/applications/{application_id}` | Borrower, Broker, Underwriter | Retrieves an application by ID, subject to access permissions |
| POST | `/applications/{application_id}/assess` | Broker (assigned), Underwriter | Calls AL-AI for valuation and affordability metrics; stores a valuation |
| POST | `/applications/{application_id}/underwrite` | Underwriter | Calls AL-AI multi-agent underwriting; saves decision and updates application |

### API Integration Guide

For detailed instructions on integrating AL-Mobile, AL-Web, and AL-AI with the backend, see:

[API Integration Guide](API_INTEGRATION.md)

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

Possible responses:

* ****200 OK**** — Profile found.

* ****401 Unauthorized**** — Missing or invalid Firebase ID token.

* ****404 Not Found**** — The user does not have a Firestore profile.

### POST `/users/me`

Creates a new borrower profile in Firestore.

The user must already have a Firebase Authentication account.

The backend obtains the UID and email from the verified Firebase token and automatically assigns the `borrower` role.

Example response:

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

Possible responses:

* ****201 Created**** — Profile successfully created.

* ****400 Bad Request**** — The Firebase account has no email address.

* ****401 Unauthorized**** — Missing or invalid Firebase ID token.

* ****409 Conflict**** — A profile already exists for this user.

* ****422 Unprocessable Entity**** — Invalid request data.

The endpoint uses Firestore's create operation to prevent existing profiles from being overwritten.

## Firestore Structure

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

            
            county: "Kilkenny"

            floor_area: 115

            
            bedrooms: 3

            bathrooms: 2
            year_built: 2010
            property_type: "SEMI_DETACHED"

            ber_rating: "B2"

        }

        lifecycleStatus: "APPLIED"

        createdAt: <Firestore timestamp>

        updatedAt: <Firestore timestamp>

        submittedAt: <Firestore timestamp>

```

The `applicationId` returned by the API corresponds to the Firestore document ID. It is not stored as a separate field inside the document.

Database operations are implemented in `app/applications.py`.

Access permissions:

\- Borrowers can create applications and retrieve their own.

\- Brokers can retrieve applications assigned to them.

\- Underwriters can retrieve all applications.

### AI valuation and underwriting storage

- `valuations`: each assessment creates a record containing `applicationId`, `valuationPrediction` (the AI `predicted_value`), `valuationData` (property details), and `createdAt`. The other assessment metrics are returned to the caller but are not currently persisted in this collection.
- `aiDecisions`: each underwriting run saves `applicationId`, `finalLendingDecision`, `creditMemorandum`, `reasoningLogs`, `agentConsensus`, `individualDecisionTrails`, `aiResponse` (complete AL-AI response), and `createdAt`.
- The underwriting persistence function uses a Firestore batch to create the decision record and update the associated application (`estimatedLTV`, `DTI`, `approvalLikelihood`, `riskTier`, `lifecycleStatus`, `updatedAt`) atomically.

The implemented AI underwriting transition sets `lifecycleStatus` to `AI_DELIBERATED`. Later transitions to `UNDERWRITER_APPROVED`, `FUNDED`, and `CLOSED` are not yet implemented.

## Running Tests

To run the backend tests, use:

```cmd

python -m pytest -v

```

If you're using PowerShell without an activated virtual environment:

```powershell

.\.venv\Scripts\python.exe -m pytest -v

```

## Development API

You don't need to run the backend locally just to test the API. There's already a development version running on Google Cloud Run.

****API URL:****

https://agentic-lender-525895646792.europe-west2.run.app

****Swagger documentation:****

https://agentic-lender-525895646792.europe-west2.run.app/docs

****Health check:****

https://agentic-lender-525895646792.europe-west2.run.app/health

The Android and Web teams can use this URL to connect to the backend.

Protected endpoints require a valid Firebase ID token.

## Deployment

The backend is deployed to Google Cloud Run in the `europe-west2` (London) region.

The existing deployment uses Google Cloud Build. Verify the current trigger and branch configuration in Google Cloud before relying on automatic deployment; the previously documented `feature/core-backend` trigger may no longer reflect the active setup.

The Dockerfile is located at `ALCore/Dockerfile`.

On Cloud Run, Firebase uses the service's Google Cloud credentials, so there's no need to upload a service account JSON file.
